package com.sosound.app.data.storage

import java.io.InputStream
import java.security.MessageDigest

/**
 * L'impronta parziale di un file: lunghezza, primi e ultimi 64 KB.
 *
 * Leggere un file intero a ogni verifica vorrebbe dire scorrere gigabyte
 * su una libreria vera; questa costa qualche millisecondo e riconosce
 * comunque i due guasti che capitano — il file troncato e il file
 * sostituito.
 *
 * ## Perche' e' una classe a parte
 *
 * Perche' va provata. La prima versione chiamava `read(buffer)` una
 * volta e si teneva quello che tornava — ma `read` **non promette di
 * riempire il buffer**: torna i byte che ha pronti. Su un file locale ne
 * ha sempre 64 KB, su un flusso di Google Drive quelli che sono
 * arrivati dalla rete.
 *
 * Risultato: lo stesso identico file dava due impronte diverse a
 * seconda di come il flusso aveva spezzettato la lettura. L'indice
 * veniva scritto leggendo dal telefono e riletto leggendo da Drive, le
 * due impronte non combaciavano mai, e ogni brano finiva fra quelli
 * «che non corrispondono». Da fuori si vedeva solo che l'import non
 * importava niente.
 */
object Impronta {

    const val FRAMMENTO = 64 * 1024

    /**
     * Calcola l'impronta leggendo il flusso una volta sola.
     *
     * Il flusso non si puo' riavvolgere — su un contenuto remoto non si
     * torna indietro — quindi la coda si tiene in una finestra
     * scorrevole mentre si va avanti.
     */
    fun di(input: InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")

        val testa = ByteArray(FRAMMENTO)
        val quantaTesta = leggiEsatti(input, testa)
        var totale = quantaTesta.toLong()

        // La coda: un anello di 64 KB in cui si continua a scrivere.
        // Quando il flusso finisce, contiene esattamente gli ultimi 64 KB
        // letti dopo la testa, qualunque sia stato il ritmo delle letture.
        val anello = ByteArray(FRAMMENTO)
        var scrittura = 0
        var girato = false

        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            totale += n
            for (i in 0 until n) {
                anello[scrittura] = buf[i]
                scrittura++
                if (scrittura == FRAMMENTO) { scrittura = 0; girato = true }
            }
        }

        // La lunghezza dentro l'impronta: e' il controllo che riconosce
        // un file troncato anche quando testa e coda combaciano.
        md.update(totale.toString().toByteArray())
        md.update(testa, 0, quantaTesta)

        if (girato) {
            // L'anello e' pieno: si rilegge dal punto di scrittura in poi.
            md.update(anello, scrittura, FRAMMENTO - scrittura)
            if (scrittura > 0) md.update(anello, 0, scrittura)
        } else if (scrittura > 0) {
            md.update(anello, 0, scrittura)
        }

        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Riempie il buffer fino in fondo, o fino alla fine del flusso.
     *
     * E' il pezzo che mancava: `read` da solo torna quello che ha
     * pronto, e quanto ha pronto dipende dalla rete.
     */
    private fun leggiEsatti(input: InputStream, buf: ByteArray): Int {
        var scritti = 0
        while (scritti < buf.size) {
            val n = input.read(buf, scritti, buf.size - scritti)
            if (n <= 0) break
            scritti += n
        }
        return scritti
    }
}
