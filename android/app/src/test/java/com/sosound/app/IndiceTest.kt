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
 * L'impronta dell'indice, e cosa succede quando non torna.
 *
 * ## Il guaio che questi test raccontano
 *
 * L'impronta era il SHA dell'intero indice serializzato. Sembra
 * ragionevole finche' non si aggiunge un campo: da quel momento lo
 * stesso identico contenuto si serializza in modo diverso, l'impronta
 * non torna piu' per nessun indice scritto prima, e — siccome chi
 * legge se ne accorgeva e passava a ricostruire dai nomi dei file —
 * **le playlist sparivano**. I brani tornavano, le playlist no.
 *
 * Ora l'impronta si calcola su una proiezione esplicita: solo i campi
 * che identificano il contenuto. Un campo nuovo non la sposta.
 */
class IndiceTest {

    private fun brano(id: String, titolo: String) = BackupTrack(
        videoId = id, titolo = titolo, artista = "Tizio",
        file = "Brani/Tizio/$titolo [$id].m4a", byte = 1234,
    )

    private fun indice() = BackupIndex(
        creatoIl = 1_700_000_000_000,
        brani = listOf(brano("dQw4w9WgXcQ", "Uno"), brano("_-Ab12cd34E", "Due")),
        playlist = listOf(BackupPlaylist(nome = "Da Spotify", creataIl = 1, brani = listOf("dQw4w9WgXcQ"))),
    )

    @Test
    fun `un indice appena scritto si verifica`() {
        val i = indice().let { it.copy(impronta = BackupFormat.fingerprint(it)) }
        assertNull("appena scritto e gia' non torna", BackupFormat.validate(i))
    }

    @Test
    fun `un campo nuovo nel formato non sposta l'impronta`() {
        // E' il guaio che questi test raccontano. «con_file» e' arrivato
        // dopo, e con l'impronta calcolata sul file serializzato ogni
        // indice gia' scritto e' diventato «alterato» di colpo — e chi
        // leggeva ripiegava sui nomi dei file, che i brani li ritrova e
        // le playlist no.
        //
        // Un campo che non dice COSA c'e' in libreria non deve entrare
        // nel conto.
        val base = indice()
        for (variante in listOf(
            base.copy(brani = base.brani.map { it.copy(conFile = false) }),
            base.copy(brani = base.brani.map { it.copy(frammento = "abc123") }),
            base.copy(brani = base.brani.map { it.copy(copertina = "Copertine/x.jpg") }),
            base.copy(brani = base.brani.map { it.copy(aggiuntoIl = 99) }),
            base.copy(creatoIl = 42),
        )) {
            assertEquals(
                "un dettaglio che non cambia il contenuto ha spostato l'impronta",
                BackupFormat.fingerprint(base),
                BackupFormat.fingerprint(variante),
            )
        }
    }

    @Test
    fun `un indice con l'impronta sbagliata resta usabile`() {
        // La regola che impedisce al guaio di ripresentarsi: leggibile e
        // verificabile sono due cose diverse, e solo la prima decide se
        // le playlist di qualcuno tornano indietro.
        val i = indice().copy(impronta = "0".repeat(64))
        assertTrue("segnalato ma buttato via", BackupFormat.usabile(i))
        assertTrue(BackupFormat.validate(i) is ImportProblem.IndiceAlterato)
    }

    @Test
    fun `un indice di un'altra applicazione invece non si usa`() {
        assertTrue(!BackupFormat.usabile(indice().copy(formato = "altro")))
        assertTrue(!BackupFormat.usabile(indice().copy(versione = 99)))
        assertTrue(!BackupFormat.usabile(indice().copy(brani = emptyList(), playlist = emptyList())))
    }

    @Test
    fun `un indice con le sole playlist e' comunque usabile`() {
        // Capita: i file spostati altrove, l'elenco no. Buttarlo
        // vorrebbe dire perdere l'unica cosa non ricostruibile.
        val soloListe = indice().copy(brani = emptyList())
        assertTrue(BackupFormat.usabile(soloListe))
    }

    @Test
    fun `ma cambia se cambia il contenuto`() {
        val base = indice()
        for (diverso in listOf(
            base.copy(brani = base.brani.dropLast(1)),
            base.copy(brani = base.brani.map { it.copy(titolo = it.titolo + "!") }),
            base.copy(brani = base.brani.map { it.copy(file = "altrove/" + it.file) }),
            base.copy(playlist = emptyList()),
            base.copy(playlist = listOf(BackupPlaylist(nome = "Altra", creataIl = 1, brani = listOf("x")))),
        )) {
            assertNotEquals(
                "una differenza vera non si e' vista",
                BackupFormat.fingerprint(base),
                BackupFormat.fingerprint(diverso),
            )
        }
    }

    @Test
    fun `un'impronta che non torna si segnala, e resta leggibile`() {
        val i = indice().copy(impronta = "0".repeat(64))
        val problema = BackupFormat.validate(i)
        assertTrue("non segnalato", problema is ImportProblem.IndiceAlterato)
        // Il punto: l'indice e' comunque intero e le playlist ci sono.
        // Chi legge deve usarle, non buttarle.
        assertEquals(2, i.brani.size)
        assertEquals(1, i.playlist.size)
    }

    @Test
    fun `l'ordine dei brani conta, perche' e' l'ordine della playlist`() {
        val base = indice()
        val girato = base.copy(brani = base.brani.reversed())
        assertNotEquals(BackupFormat.fingerprint(base), BackupFormat.fingerprint(girato))
    }
}
