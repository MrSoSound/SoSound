package com.sosound.app.data.update

import android.content.Context
import android.os.Build
import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Controlla se c'e' una versione nuova di SoSound su GitHub, la scarica
 * e lancia l'installazione.
 *
 * Una sola istanza per tutta l'app (vive in SoSoundApp, come
 * DownloadQueue): cosi' il badge nella barra di navigazione e la scheda
 * in Impostazioni guardano lo stesso stato, senza doverselo passare.
 */
class AppUpdateManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val releases = GithubReleaseClient()
    private val policy = AppUpdatePolicy(context)
    private val http = HttpClient(OkHttp) { expectSuccess = false }

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    val currentVersion: String
        get() = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"

    /** Chiamato una volta all'avvio dell'app: il freno di [AppUpdatePolicy]
     *  decide se e' davvero il momento di controllare. */
    fun checkOnStartIfNeeded() {
        if (policy.shouldCheckOnStart()) check(manual = false)
    }

    /** [manual] = true e' il tasto in Impostazioni: salta il freno
     *  giornaliero e ignora una versione eventualmente scartata prima —
     *  l'utente lo sta chiedendo apposta. */
    fun check(manual: Boolean) = scope.launch {
        _state.value = UpdateState.Checking
        policy.markChecked()
        runCatching { releases.latestRelease() }
            .onSuccess { release ->
                val asset = pickAsset(release)
                _state.value = when {
                    asset == null -> UpdateState.Idle
                    !isNewer(release.tagName, currentVersion) -> UpdateState.Idle
                    !manual && policy.isSkipped(release.tagName) -> UpdateState.Idle
                    else -> UpdateState.Available(release, asset)
                }
            }
            .onFailure { e ->
                Log.w(TAG, "controllo aggiornamenti fallito", e)
                // Un controllo automatico che fallisce non deve disturbare:
                // non c'e' niente che l'utente possa farci, e ricompare da
                // solo il giorno dopo. Un errore si mostra solo se l'ha
                // chiesto lui col tasto.
                _state.value =
                    if (manual) UpdateState.Error(e.message ?: "Controllo non riuscito")
                    else UpdateState.Idle
            }
    }

    fun skipCurrent() {
        (_state.value as? UpdateState.Available)?.let {
            policy.skip(it.release.tagName)
            _state.value = UpdateState.Idle
        }
    }

    /** Scarica e installa in un solo gesto: e' quello che l'utente ha
     *  chiesto toccando il tasto, il resto avviene da se'. */
    fun downloadAndInstall() {
        val available = _state.value as? UpdateState.Available ?: return
        scope.launch {
            _state.value = UpdateState.Downloading(available.release, 0f)
            runCatching { download(available.release, available.asset) }
                .onSuccess { file -> proceedToInstall(available.release, file) }
                .onFailure { e ->
                    Log.w(TAG, "scaricamento aggiornamento fallito", e)
                    _state.value = UpdateState.Error(e.message ?: "Scaricamento non riuscito")
                }
        }
    }

    /** Da richiamare dopo che l'utente e' tornato da «installa da questa
     *  fonte»: il file c'e' gia', riparte solo l'installazione. */
    fun retryInstall() {
        (_state.value as? UpdateState.NeedsInstallPermission)?.let {
            proceedToInstall(it.release, it.file)
        }
    }

    fun dismissError() {
        if (_state.value is UpdateState.Error) _state.value = UpdateState.Idle
    }

    fun requestInstallPermission() = ApkInstaller.requestInstallPermission(context)

    private fun proceedToInstall(release: GithubRelease, file: File) {
        if (!ApkInstaller.canInstall(context)) {
            _state.value = UpdateState.NeedsInstallPermission(release, file)
            return
        }
        _state.value = UpdateState.Installing(release)
        ApkInstaller.install(context, file)
    }

    private suspend fun download(release: GithubRelease, asset: GithubAsset): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "aggiornamenti").apply { mkdirs() }
            // Un file parziale di un tentativo precedente non deve
            // confondersi con questo, ne' restare a occupare spazio.
            dir.listFiles()?.forEach { it.delete() }
            val dest = File(dir, asset.name)

            val response: HttpResponse = http.get(asset.browserDownloadUrl) {
                onDownload { bytesSentTotal, contentLength ->
                    val totale = contentLength?.takeIf { it > 0 } ?: asset.size
                    if (totale > 0) {
                        _state.value = UpdateState.Downloading(
                            release,
                            (bytesSentTotal.toFloat() / totale).coerceIn(0f, 1f),
                        )
                    }
                }
            }
            if (!response.status.isSuccess()) {
                throw IOException("GitHub ha risposto ${response.status.value}")
            }
            dest.writeBytes(response.bodyAsBytes())
            dest
        }

    /** arm64 se il telefono lo supporta, altrimenti l'universale — lo
     *  stesso dubbio che oggi l'utente doveva risolvere leggendo il
     *  README, ora lo risolve il telefono da solo. */
    private fun pickAsset(release: GithubRelease): GithubAsset? {
        val vaBeneArm64 = Build.SUPPORTED_ABIS.contains("arm64-v8a")
        if (vaBeneArm64) {
            release.assets.firstOrNull { it.name.contains("arm64", ignoreCase = true) }
                ?.let { return it }
        }
        return release.assets.firstOrNull { it.name.contains("universal", ignoreCase = true) }
    }

    private fun parseVersion(v: String): List<Int> =
        v.removePrefix("v").split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }

    private fun isNewer(remoteTag: String, localVersionName: String): Boolean {
        val remote = parseVersion(remoteTag)
        val local = parseVersion(localVersionName)
        for (i in 0 until maxOf(remote.size, local.size)) {
            val r = remote.getOrElse(i) { 0 }
            val l = local.getOrElse(i) { 0 }
            if (r != l) return r > l
        }
        return false
    }

    private companion object {
        const val TAG = "AppUpdateManager"
    }
}
