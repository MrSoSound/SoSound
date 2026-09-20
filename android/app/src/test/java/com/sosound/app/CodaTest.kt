package com.sosound.app

import com.sosound.app.playback.CodaSalvata
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Riprendere la coda di ieri quando qualche brano non c'e' piu'.
 *
 * L'indice salvato e' un numero, e un numero in una lista che si e'
 * accorciata indica un'altra canzone. E' un errore che non da' nessun
 * segnale: la musica riparte, solo dal punto sbagliato.
 */
class CodaTest {

    private val ieri = listOf("a", "b", "c", "d", "e")

    @Test
    fun `se non e' sparito niente, l'indice resta quello`() {
        for (i in ieri.indices) {
            assertEquals(i, CodaSalvata.indiceDopoLaPotatura(ieri, ieri.toSet(), i))
        }
    }

    @Test
    fun `i brani spariti prima di quello corrente lo fanno scalare`() {
        // Erano cinque, «a» e «b» non ci sono piu': «d» era il quarto e
        // adesso e' il secondo.
        val rimasti = setOf("c", "d", "e")
        assertEquals(1, CodaSalvata.indiceDopoLaPotatura(ieri, rimasti, 3))
    }

    @Test
    fun `i brani spariti dopo non lo spostano`() {
        val rimasti = setOf("a", "b", "c")
        assertEquals(1, CodaSalvata.indiceDopoLaPotatura(ieri, rimasti, 1))
    }

    @Test
    fun `se e' sparito proprio quello corrente si riprende dal precedente`() {
        // «c» non c'e' piu': il posto sensato e' «b», che ora e' primo.
        val rimasti = setOf("a", "b", "d", "e")
        assertEquals(1, CodaSalvata.indiceDopoLaPotatura(ieri, rimasti, 2))
    }

    @Test
    fun `se e' sparito tutto quello che veniva prima, si riparte da capo`() {
        val rimasti = setOf("d", "e")
        assertEquals(0, CodaSalvata.indiceDopoLaPotatura(ieri, rimasti, 2))
    }

    @Test
    fun `i casi limite non esplodono`() {
        assertEquals(0, CodaSalvata.indiceDopoLaPotatura(emptyList(), setOf("a"), 3))
        assertEquals(0, CodaSalvata.indiceDopoLaPotatura(ieri, emptySet(), 3))
        // Un indice fuori dai bordi capita se la coda e' stata
        // riscritta a meta' da un'altra versione.
        assertEquals(4, CodaSalvata.indiceDopoLaPotatura(ieri, ieri.toSet(), 99))
        assertEquals(0, CodaSalvata.indiceDopoLaPotatura(ieri, ieri.toSet(), -3))
    }
}
