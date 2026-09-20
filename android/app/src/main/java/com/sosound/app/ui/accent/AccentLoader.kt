package com.sosound.app.ui.accent

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Legge la copertina dal disco e ne ricava il colore dei controlli.
 *
 * La copertina viene ridotta a 24x24 prima di guardarla: bastano per
 * trovare la tinta, e risparmiano di decodificare un'immagine grande a
 * ogni cambio di brano.
 */
class AccentLoader {

    private var lastPath: String? = null
    private var lastResult: TrackAccent = TrackAccent.Default

    suspend fun load(coverPath: String?): TrackAccent = withContext(Dispatchers.IO) {
        if (coverPath == null) return@withContext TrackAccent.Default
        if (coverPath == lastPath) return@withContext lastResult

        val result = runCatching {
            if (!File(coverPath).exists()) return@runCatching TrackAccent.Default

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(coverPath, bounds)
            if (bounds.outWidth <= 0) return@runCatching TrackAccent.Default

            val opts = BitmapFactory.Options().apply {
                inSampleSize = (bounds.outWidth / 96).coerceAtLeast(1)
            }
            val full = BitmapFactory.decodeFile(coverPath, opts)
                ?: return@runCatching TrackAccent.Default

            val small = Bitmap.createScaledBitmap(full, SIZE, SIZE, true)
            if (small !== full) full.recycle()

            val pixels = IntArray(SIZE * SIZE)
            small.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
            small.recycle()

            AccentExtractor.from(pixels)
        }.getOrElse { TrackAccent.Default }

        lastPath = coverPath
        lastResult = result
        result
    }

    private companion object {
        const val SIZE = 24
    }
}
