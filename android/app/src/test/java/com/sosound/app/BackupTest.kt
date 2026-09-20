package com.sosound.app

import com.sosound.app.data.storage.BackupFormat
import com.sosound.app.data.storage.BackupIndex
import com.sosound.app.data.storage.BackupPlaylist
import com.sosound.app.data.storage.BackupTrack
import com.sosound.app.data.storage.ImportProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le garanzie dell'indice di backup.
 *
 * Quello che l'indice promette e' **integrita'**, non provenienza: non
 * si puo' dimostrare che una cartella venga da questa app, perche' una
 * chiave per firmarla starebbe dentro l'APK ed estrarla e' banale. Si
 * puo' pero' dimostrare che l'indice non e' stato troncato ne' modificato
 * dopo la scrittura — che e' il guasto che capita davvero, una
 * sincronizzazione interrotta a meta'.
 *
 * Questi test verificano esattamente quella promessa.
 */
class BackupTest {

    private fun brano(id: String, titolo: String = "Un brano") = BackupTrack(
        videoId = id,
        titolo = titolo,
        artista = "Tizio",
        album = "Un album",
        durata = 200,
        file = "Brani/Tizio/Tizio - $titolo [$id].m4a",
        copertina = "Copertine/$id.jpg",
        byte = 4_000_000,
        frammento = "abc123",
        aggiuntoIl = 1_700_000_000_000,
    )

    private fun indiceValido(): BackupIndex {
        val base = BackupIndex(
            creatoIl = 1_700_000_000_000,
            brani = listOf(brano("aaaaaaaaaaa"), brano("bbbbbbbbbbb", "Un altro")),
            playlist = listOf(
                BackupPlaylist("Corsa", 1_700_000_000_000, listOf("aaaaaaaaaaa", "bbbbbbbbbbb"))
            ),
        )
        return base.copy(impronta = BackupFormat.fingerprint(base))
    }

    @Test
    fun `un indice appena scritto e' valido`() {
        val i = indiceValido()
        println("--- impronta: ${i.impronta?.take(16)}… ---")
        assertNull("rifiutato un indice buono", BackupFormat.validate(i))
    }

    @Test
    fun `sopravvive al giro completo di scrittura e rilettura`() {
        val originale = indiceValido()
        val testo = BackupFormat.json.encodeToString(BackupIndex.serializer(), originale)
        val riletto = BackupFormat.json.decodeFromString(BackupIndex.serializer(), testo)

        assertEquals(originale, riletto)
        assertNull("l'indice riletto non passa la verifica", BackupFormat.validate(riletto))
        // L'ordine dentro una playlist e' un'informazione: se si perde,
        // il ripristino restituisce le canzoni mescolate.
        assertEquals(
            listOf("aaaaaaaaaaa", "bbbbbbbbbbb"),
            riletto.playlist.first().brani,
        )
    }

    @Test
    fun `modificare l'indice a mano viene riconosciuto`() {
        val buono = indiceValido()

        // Qualcuno aggiunge un brano senza ricalcolare l'impronta: e'
        // quello che succede se un file viene copiato a meta'.
        val manomesso = buono.copy(brani = buono.brani + brano("ccccccccccc"))
        assertTrue(
            "una modifica non e' stata riconosciuta",
            BackupFormat.validate(manomesso) is ImportProblem.IndiceAlterato,
        )

        // Anche cambiare un solo titolo deve farlo saltare.
        val ritoccato = buono.copy(
            brani = buono.brani.mapIndexed { i, b -> if (i == 0) b.copy(titolo = "Altro") else b }
        )
        assertTrue(
            "un ritocco non e' stato riconosciuto",
            BackupFormat.validate(ritoccato) is ImportProblem.IndiceAlterato,
        )
    }

    @Test
    fun `l'impronta cambia con il contenuto`() {
        val a = BackupIndex(brani = listOf(brano("aaaaaaaaaaa")))
        val b = BackupIndex(brani = listOf(brano("bbbbbbbbbbb")))
        assertNotEquals(BackupFormat.fingerprint(a), BackupFormat.fingerprint(b))

        // E resta uguale se il contenuto e' uguale: senza questo la
        // verifica fallirebbe sempre e nessun backup sarebbe importabile.
        assertEquals(
            BackupFormat.fingerprint(a),
            BackupFormat.fingerprint(BackupIndex(brani = listOf(brano("aaaaaaaaaaa")))),
        )
    }

    @Test
    fun `una cartella che non e' nostra viene rifiutata`() {
        val estraneo = BackupIndex(formato = "altra-cosa", brani = listOf(brano("aaaaaaaaaaa")))
        assertTrue(
            BackupFormat.validate(estraneo) is ImportProblem.NonRiconosciuta,
        )
    }

    @Test
    fun `un backup di una versione futura non si importa a caso`() {
        // Meglio fermarsi che leggere a meta' un formato che non
        // conosciamo, e importare una libreria monca.
        val futuro = BackupIndex(versione = 99, brani = listOf(brano("aaaaaaaaaaa")))
            .let { it.copy(impronta = BackupFormat.fingerprint(it)) }
        val p = BackupFormat.validate(futuro)
        assertTrue(p is ImportProblem.VersioneFutura)
        assertEquals(99, (p as ImportProblem.VersioneFutura).trovata)
    }

    @Test
    fun `un indice senza brani non passa`() {
        val vuoto = BackupIndex().let { it.copy(impronta = BackupFormat.fingerprint(it)) }
        assertTrue(BackupFormat.validate(vuoto) is ImportProblem.Vuoto)
    }

    @Test
    fun `un indice senza impronta si legge ma non si verifica`() {
        // Capita se qualcuno lo riscrive a mano. Non e' un errore fatale:
        // i controlli sui singoli file restano, ed e' l'utente a scegliere
        // se fidarsi.
        val senza = BackupIndex(brani = listOf(brano("aaaaaaaaaaa")), impronta = null)
        assertNull(BackupFormat.validate(senza))
    }
}
