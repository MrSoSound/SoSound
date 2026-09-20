package com.sosound.app

import android.app.Application
import androidx.media3.common.util.UnstableApi
import androidx.room.Room
import com.sosound.app.data.catalog.InnerTubeClient
import com.sosound.app.data.download.DownloadQueue
import com.sosound.app.data.download.DownloadService
import com.sosound.app.data.download.Downloader
import com.sosound.app.data.library.LibraryDatabase
import com.sosound.app.data.storage.BackupStore
import com.sosound.app.data.storage.MusicStorage

/**
 * Tutto quello che vive quanto l'app.
 *
 * Non c'e' un contenitore di dipendenze: i pezzi sono cinque e si
 * costruiscono in ordine. Aggiungere Hilt qui vorrebbe dire piu' codice
 * di quello che risparmia.
 */
@UnstableApi
class SoSoundApp : Application() {

    val database: LibraryDatabase by lazy {
        Room.databaseBuilder(this, LibraryDatabase::class.java, "sosound.db")
            // Senza questa, Room butterebbe via il database al cambio di
            // versione, e con lui l'indice dei file gia' scaricati.
            .addMigrations(
                LibraryDatabase.MIGRATION_1_2,
                LibraryDatabase.MIGRATION_2_3,
                LibraryDatabase.MIGRATION_3_4,
            )
            .build()
    }

    val catalog: InnerTubeClient by lazy { InnerTubeClient() }

    val storage: MusicStorage by lazy { MusicStorage(this) }

    val backup: BackupStore by lazy {
        BackupStore(this, storage, database.tracks(), database.playlists())
    }

    val downloader: Downloader by lazy { Downloader(this, storage) }

    val downloadQueue: DownloadQueue by lazy {
        DownloadQueue(this, downloader, database.tracks())
    }

    override fun onCreate() {
        super.onCreate()
        DownloadService.ensureChannel(this)
        // Tocca la coda cosi' l'inizializzazione di Python parte subito,
        // invece che al primo download: sono qualche secondo che e' meglio
        // spendere mentre l'utente guarda la libreria.
        downloadQueue
    }
}
