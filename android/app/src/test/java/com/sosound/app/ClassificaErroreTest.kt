package com.sosound.app

import com.sosound.app.data.download.FailureKind
import com.sosound.app.data.download.classificaMessaggioYtDlp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * `classify()` traduce il testo grezzo di yt-dlp in un messaggio in
 * italiano. La parte che dipende dal telefono (c'e' rete?) resta fuori:
 * qui si guarda solo il riconoscimento dei pattern, che e' quello che
 * ha lasciato passare un errore grezzo fino a una persona vera.
 */
class ClassificaErroreTest {

    @Test
    fun `un formato non disponibile ha un messaggio leggibile`() {
        val errore = classificaMessaggioYtDlp(
            "ERROR: [youtube] Gom3xUAUtfl: Requested format is not available. " +
                "Use --list-formats for a list of available formats"
        )
        assertEquals(FailureKind.MOTORE_DISALLINEATO, errore.kind)
        assertNotEquals(true, errore.message?.contains("Requested format"))
        assertNotEquals(true, errore.message?.contains("--list-formats"))
    }

    @Test
    fun `un 403 ha un messaggio leggibile, non il testo di yt-dlp`() {
        val errore = classificaMessaggioYtDlp(
            "ERROR: unable to download video data: HTTP Error 403: Forbidden"
        )
        assertEquals(FailureKind.MOTORE_DISALLINEATO, errore.kind)
        assertNotEquals(true, errore.message?.contains("HTTP Error 403"))
    }

    @Test
    fun `un errore mai visto prima non mostra comunque il testo grezzo`() {
        val grezzo = "ERROR: qualcosa che yt-dlp non ha mai detto finora, in inglese tecnico"
        val errore = classificaMessaggioYtDlp(grezzo)
        assertEquals(FailureKind.MOTORE_DISALLINEATO, errore.kind)
        assertNotEquals(grezzo, errore.message)
    }

    @Test
    fun `video privato o non disponibile e' un contenuto, non da ritentare aggiornando`() {
        assertEquals(
            FailureKind.CONTENUTO,
            classificaMessaggioYtDlp("ERROR: [youtube] xyz: Video unavailable").kind,
        )
        assertEquals(
            FailureKind.CONTENUTO,
            classificaMessaggioYtDlp("ERROR: [youtube] xyz: Private video").kind,
        )
    }

    @Test
    fun `l'anti-bot si riconosce ed e' un caso a parte`() {
        val errore = classificaMessaggioYtDlp(
            "ERROR: [youtube] xyz: Sign in to confirm you're not a bot"
        )
        assertEquals(FailureKind.ANTIBOT, errore.kind)
    }
}
