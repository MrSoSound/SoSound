package com.sosound.app

import com.sosound.app.data.library.TrackEntity
import com.sosound.app.ui.mixPlaylists
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il mix di piu' playlist: unione dei brani, deduplica per videoId,
 * mescolamento vero della lista risultante.
 */
class PlaylistMixTest {

    private fun track(id: String) = TrackEntity(
        videoId = id,
        title = "Brano $id",
        artist = "Artista",
        album = null,
        durationSeconds = 180,
        path = "",
        coverPath = null,
        sizeBytes = 0,
        addedAt = 0,
    )

    @Test
    fun `unisce i brani di piu' playlist`() {
        val a = listOf(track("1"), track("2"))
        val b = listOf(track("3"), track("4"))

        val mix = mixPlaylists(listOf(a, b))

        assertEquals(setOf("1", "2", "3", "4"), mix.map { it.videoId }.toSet())
        assertEquals(4, mix.size)
    }

    @Test
    fun `i doppioni per videoId spariscono di default`() {
        val a = listOf(track("1"), track("2"))
        val b = listOf(track("2"), track("3"))

        val mix = mixPlaylists(listOf(a, b))

        assertEquals(setOf("1", "2", "3"), mix.map { it.videoId }.toSet())
        assertEquals(3, mix.size)
    }

    @Test
    fun `con dedupe a false i doppioni restano`() {
        val a = listOf(track("1"), track("2"))
        val b = listOf(track("2"), track("3"))

        val mix = mixPlaylists(listOf(a, b), dedupe = false)

        assertEquals(4, mix.size)
        assertEquals(2, mix.count { it.videoId == "2" })
    }

    @Test
    fun `una chiamata ripetuta non da' sempre lo stesso ordine`() {
        val playlist = (1..12).map { track(it.toString()) }

        val ordini = (1..8).map { mixPlaylists(listOf(playlist)).map { it.videoId } }

        // Non e' un test statistico sulla casualita': solo un controllo di
        // sanita' che almeno un ordine, su otto tentativi, sia diverso dal
        // primo. Con 12! ordini possibili la probabilita' di un falso
        // negativo e' trascurabile.
        assertTrue(ordini.any { it != ordini.first() })
    }

    @Test
    fun `una lista vuota non da' problemi`() {
        assertEquals(emptyList<TrackEntity>(), mixPlaylists(emptyList()))
        assertEquals(emptyList<TrackEntity>(), mixPlaylists(listOf(emptyList(), emptyList())))
    }
}
