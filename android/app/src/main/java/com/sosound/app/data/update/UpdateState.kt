package com.sosound.app.data.update

import java.io.File

/** Lo stato del controllo aggiornamenti, cosi' com'e' mostrato in
 *  Impostazioni e sul badge della barra di navigazione. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Available(val release: GithubRelease, val asset: GithubAsset) : UpdateState
    data class Downloading(val release: GithubRelease, val progress: Float) : UpdateState
    data class Installing(val release: GithubRelease) : UpdateState
    data class Error(val message: String) : UpdateState

    /** L'utente deve prima concedere «installa da questa fonte»: non e'
     *  un errore, e' un passaggio in piu' che tocca a lui. */
    data class NeedsInstallPermission(val release: GithubRelease, val file: File) : UpdateState
}

/** Vero per ogni stato in cui vale la pena mostrare il pallino sulla
 *  scheda Impostazioni: c'e' qualcosa che aspetta l'utente. */
fun UpdateState.showsBadge(): Boolean = when (this) {
    is UpdateState.Available,
    is UpdateState.Downloading,
    is UpdateState.Installing,
    is UpdateState.NeedsInstallPermission,
    -> true
    UpdateState.Idle, UpdateState.Checking, is UpdateState.Error -> false
}
