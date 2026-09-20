package com.sosound.app.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/** Lancia l'installazione di un APK gia' scaricato. Un oggetto solo,
 *  senza stato: non c'e' niente qui che debba sopravvivere. */
object ApkInstaller {

    /** Da Android 8, ogni app deve avere il permesso concesso per la
     *  propria fonte specifica — non esiste piu' un interruttore unico
     *  «fonti sconosciute» per tutto il telefono. */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    /** Porta l'utente dritto alla schermata di sistema dove concede
     *  quel permesso per SoSound — non alle impostazioni generali. */
    fun requestInstallPermission(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Il file sta nella cache privata dell'app: un content:// del
     *  FileProvider e' l'unico modo per farlo leggere al programma di
     *  installazione di sistema, che gira come un altro processo. */
    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(intent)
    }
}
