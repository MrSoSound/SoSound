package com.sosound.app

import com.sosound.app.data.storage.Impronta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.MessageDigest

/**
 * L'impronta deve dipendere dal contenuto e da niente altro.
 *
 * In particolare non dal **ritmo** con cui il flusso consegna i byte:
 * un file letto dal telefono arriva a blocchi pieni, lo stesso file
 * letto da Google Drive arriva a pezzi grandi quanto decide la rete.
 * Se l'impronta cambia, l'indice scritto ieri non corrisponde piu' a
 * niente e l'import non importa niente — senza nessun errore.
 */
class ImprontaTest {

    /** Un flusso che consegna al massimo [pezzo] byte per volta. */
    private class ARate(dati: ByteArray, val pezzo: Int) : InputStream() {
        private val d = dati
        private var i = 0
        override fun read(): Int = if (i < d.size) d[i++].toInt() and 0xff else -1
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (i >= d.size) return -1
            val n = minOf(pezzo, len, d.size - i)
            System.arraycopy(d, i, b, off, n)
            i += n
            return n
        }
    }

    private fun contenuto(n: Int): ByteArray =
        ByteArray(n) { ((it * 31 + 7) % 251).toByte() }

    @Test
    fun `lo stesso file letto a ritmi diversi da la stessa impronta`() {
        // 300 KB: piu' grande di testa piu' coda, quindi passa per
        // l'anello. E' il caso in cui la vecchia versione sbagliava.
        val dati = contenuto(300 * 1024)

        val tutto = Impronta.di(ByteArrayInputStream(dati))
        val aPezzi = listOf(1, 7, 1024, 4096, 64 * 1024, 999_999).map {
            Impronta.di(ARate(dati, it))
        }
        println("--- impronta: ${tutto.take(16)}… ---")
        for ((i, imp) in aPezzi.withIndex()) {
            assertEquals("ritmo $i da un'impronta diversa", tutto, imp)
        }
    }

    @Test
    fun `e cosi anche per un file piu piccolo del frammento`() {
        val dati = contenuto(3 * 1024)
        assertEquals(
            Impronta.di(ByteArrayInputStream(dati)),
            Impronta.di(ARate(dati, 13)),
        )
    }

    @Test
    fun `e per un file esattamente lungo quanto il frammento`() {
        // Il confine: testa piena e coda vuota. Sbagliare di un byte qui
        // si vede solo su file di questa lunghezza esatta.
        val dati = contenuto(Impronta.FRAMMENTO)
        assertEquals(
            Impronta.di(ByteArrayInputStream(dati)),
            Impronta.di(ARate(dati, 100)),
        )
    }

    @Test
    fun `un file troncato ha un'impronta diversa`() {
        val dati = contenuto(300 * 1024)
        val tronco = dati.copyOf(dati.size - 1)
        assertNotEquals(
            Impronta.di(ByteArrayInputStream(dati)),
            Impronta.di(ByteArrayInputStream(tronco)),
        )
    }

    @Test
    fun `un byte cambiato in testa o in coda si vede`() {
        val dati = contenuto(300 * 1024)
        for (dove in listOf(0, 100, dati.size - 1, dati.size - 5000)) {
            val alterato = dati.copyOf()
            alterato[dove] = (alterato[dove] + 1).toByte()
            assertNotEquals(
                "cambiare il byte $dove non si e' visto",
                Impronta.di(ByteArrayInputStream(dati)),
                Impronta.di(ByteArrayInputStream(alterato)),
            )
        }
    }

    @Test
    fun `la vecchia versione invece cambiava idea a ogni lettura`() {
        // Ricostruita qui com'era, per mostrare il difetto invece di
        // raccontarlo: una sola read() per la testa, e l'ultima read()
        // qualunque essa sia per la coda.
        fun vecchia(input: InputStream): String {
            val md = MessageDigest.getInstance("SHA-256")
            val testa = ByteArray(Impronta.FRAMMENTO)
            val letti = input.read(testa)
            if (letti > 0) md.update(testa, 0, letti)
            val buf = ByteArray(Impronta.FRAMMENTO)
            var ultimo = ByteArray(0)
            var n = input.read(buf)
            while (n > 0) { ultimo = buf.copyOf(n); n = input.read(buf) }
            if (ultimo.isNotEmpty()) md.update(ultimo)
            return md.digest().joinToString("") { "%02x".format(it) }
        }

        val dati = contenuto(300 * 1024)
        val daLocale = vecchia(ByteArrayInputStream(dati))
        val daRete = vecchia(ARate(dati, 8192))
        println("--- vecchia, tutto in una volta: ${daLocale.take(16)}… ---")
        println("--- vecchia, a pezzi da 8 KB:    ${daRete.take(16)}… ---")
        assertNotEquals(
            "se queste due coincidono, la diagnosi era sbagliata",
            daLocale, daRete,
        )
    }
}
