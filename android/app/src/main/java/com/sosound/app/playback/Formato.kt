package com.sosound.app.playback

/**
 * Che tipo di audio e' un file, guardandone l'inizio.
 *
 * ## Perche' non basta il nome
 *
 * Un brano che sta nella cache non ha un nome: la cache lo conosce per
 * identificativo, e il formato lo sapeva solo chi l'ha scaricato. Per
 * promuoverlo a file vero — «tienilo anche senza rete» — bisogna dargli
 * un'estensione, e darne una sbagliata significa un file che nessun
 * lettore aprira' piu'.
 *
 * I primi byte lo dicono senza ambiguita'. Sono tre formati, perche'
 * sono i tre che chiediamo a yt-dlp.
 */
object Formato {

    /** L'estensione giusta per questi byte, o null se non li riconosco. */
    fun daiPrimiByte(testa: ByteArray): String? {
        if (testa.size < 12) return null

        // MP4/M4A: i byte 4..7 sono «ftyp». La lunghezza del riquadro
        // viene prima, e non e' fissa: per questo si guarda dal quarto.
        if (testa[4] == 'f'.code.toByte() && testa[5] == 't'.code.toByte() &&
            testa[6] == 'y'.code.toByte() && testa[7] == 'p'.code.toByte()
        ) return "m4a"

        // Matroska/WebM: la firma EBML, uguale per tutti e due.
        if (testa[0] == 0x1A.toByte() && testa[1] == 0x45.toByte() &&
            testa[2] == 0xDF.toByte() && testa[3] == 0xA3.toByte()
        ) return "webm"

        // Ogg, che e' il contenitore di Opus.
        if (testa[0] == 'O'.code.toByte() && testa[1] == 'g'.code.toByte() &&
            testa[2] == 'g'.code.toByte() && testa[3] == 'S'.code.toByte()
        ) return "opus"

        // MP3: o il riquadro ID3 all'inizio, o direttamente un fotogramma.
        if (testa[0] == 'I'.code.toByte() && testa[1] == 'D'.code.toByte() &&
            testa[2] == '3'.code.toByte()
        ) return "mp3"
        if (testa[0] == 0xFF.toByte() && (testa[1].toInt() and 0xE0) == 0xE0) return "mp3"

        return null
    }
}
