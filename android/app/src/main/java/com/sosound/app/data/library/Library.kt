package com.sosound.app.data.library

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Un brano nella libreria del telefono.
 *
 * I metadati stanno qui e non dentro i tag del file: senza ffmpeg non
 * potremmo scriverli, e soprattutto non servono: e' l'app l'unica cosa
 * che legge questi file, e un database si interroga meglio di 500 tag.
 */
@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationSeconds: Int?,
    /** Percorso assoluto del file audio sul telefono. */
    val path: String,
    /** Percorso della copertina, se scaricata. */
    val coverPath: String?,
    val sizeBytes: Long,
    val addedAt: Long,
    /**
     * Il programma, se questo e' una puntata di podcast.
     *
     * Serve a distinguere le puntate dai brani: senza, una puntata e' un
     * brano il cui album e' il nome del programma, e non c'e' modo di
     * elencare i podcast che si hanno.
     */
    val showId: String? = null,
    /**
     * Vero se questo brano fa parte della libreria, non e' solo passato.
     *
     * Lo diventa quando lo si mette in una playlist o si sceglie di
     * tenerlo anche senza rete. Un brano ascoltato una volta dalla
     * ricerca resta falso: sta nella cache finche' c'e' posto, si
     * riascolta se capita, e **non compare negli elenchi ne' nell'indice**.
     *
     * Senza questa distinzione bastava provare venti canzoni per
     * ritrovarsene venti in libreria e venti nel backup su Drive — e
     * l'app diceva «ce l'hai» di cose che erano solo passate di li'.
     */
    val salvato: Boolean = true,
) {
    /**
     * Vero se il brano ha un file suo, sul telefono o nella cartella.
     *
     * Un percorso vuoto non e' un errore: e' un brano che fa parte della
     * libreria senza esserne stato scaricato — si ascolta dalla rete, e
     * si puo' decidere in qualunque momento di tenerselo. E' la
     * differenza fra «ce l'ho» e «lo conosco».
     */
    val haFile: Boolean get() = path.isNotBlank()

    val durationText: String
        get() = durationSeconds?.let { "%d:%02d".format(it / 60, it % 60) } ?: "--:--"

    val sizeText: String
        get() = "%.1f MB".format(sizeBytes / 1024.0 / 1024.0)

    val formatText: String
        get() = path.substringAfterLast('.', "").uppercase().ifEmpty { "?" }
}

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

/**
 * Un brano dentro una playlist.
 *
 * `position` tiene l'ordine scelto dall'utente: senza, una playlist
 * tornerebbe indietro in ordine di inserimento nel database, che non e'
 * un ordine che qualcuno ha deciso.
 *
 * La cancellazione a cascata evita il caso peggiore: un brano tolto dal
 * telefono che resta dentro tre playlist come riga fantasma, e le fa
 * fallire alla riproduzione.
 */
@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "videoId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["videoId"],
            childColumns = ["videoId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("videoId"), Index("playlistId")],
)
data class PlaylistTrackEntity(
    val playlistId: Long,
    val videoId: String,
    val position: Int,
    val addedAt: Long,
)

/** Una playlist con quanti brani contiene: e' quello che serve all'elenco. */
data class PlaylistSummary(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val trackCount: Int,
    /** La copertina del primo brano, per dare una faccia alla playlist. */
    val coverPath: String?,
)

@Dao
interface TrackDao {

    @Query("SELECT * FROM tracks WHERE salvato = 1 ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<TrackEntity>>

    /** Tutti, salvati o no: serve a chi deve ripulire la cache. */
    @Query("SELECT * FROM tracks ORDER BY addedAt DESC")
    suspend fun tutti(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE salvato = 0")
    suspend fun diPassaggio(): List<TrackEntity>

    @Query("UPDATE tracks SET salvato = 1 WHERE videoId = :videoId")
    suspend fun segnaSalvato(videoId: String)

    @Query(
        "SELECT * FROM tracks WHERE title LIKE '%' || :q || '%' " +
            "OR artist LIKE '%' || :q || '%' OR album LIKE '%' || :q || '%' " +
            "ORDER BY addedAt DESC"
    )
    fun observeSearch(q: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE videoId = :videoId")
    suspend fun byId(videoId: String): TrackEntity?

    @Query("SELECT videoId FROM tracks WHERE salvato = 1")
    fun observeIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(track: TrackEntity)

    @Query("DELETE FROM tracks WHERE videoId = :videoId")
    suspend fun delete(videoId: String)

    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun count(): Int

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM tracks")
    fun observeTotalBytes(): Flow<Long>

    @Query("SELECT * FROM tracks")
    suspend fun all(): List<TrackEntity>

    /** I programmi di cui si ha almeno una puntata. */
    @Query(
        "SELECT * FROM tracks WHERE showId IS NOT NULL " +
            "GROUP BY showId ORDER BY MAX(addedAt) DESC"
    )
    fun observeShows(): Flow<List<TrackEntity>>

    @Query("SELECT COUNT(*) FROM tracks WHERE showId = :showId")
    suspend fun countInShow(showId: String): Int
}

@Dao
interface PlaylistDao {

    @Query(
        """
        SELECT p.id, p.name, p.createdAt,
               COUNT(pt.videoId) AS trackCount,
               (SELECT t.coverPath FROM playlist_tracks pt2
                  JOIN tracks t ON t.videoId = pt2.videoId
                 WHERE pt2.playlistId = p.id AND t.coverPath IS NOT NULL
                 ORDER BY pt2.position LIMIT 1) AS coverPath
          FROM playlists p
          LEFT JOIN playlist_tracks pt ON pt.playlistId = p.id
         GROUP BY p.id
         ORDER BY p.createdAt DESC
        """
    )
    fun observeAll(): Flow<List<PlaylistSummary>>

    @Query(
        "SELECT t.* FROM tracks t " +
            "JOIN playlist_tracks pt ON pt.videoId = t.videoId " +
            "WHERE pt.playlistId = :playlistId ORDER BY pt.position"
    )
    fun observeTracks(playlistId: Long): Flow<List<TrackEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observeOne(id: Long): Flow<PlaylistEntity?>

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun nextPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrack(entry: PlaylistTrackEntity)

    /** Accoda in fondo. Se il brano c'e' gia', non fa niente. */
    @Transaction
    suspend fun addTrack(playlistId: Long, videoId: String) {
        insertTrack(
            PlaylistTrackEntity(
                playlistId = playlistId,
                videoId = videoId,
                position = nextPosition(playlistId),
                addedAt = System.currentTimeMillis(),
            )
        )
    }

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun removeTrack(playlistId: Long, videoId: String)

    @Query("UPDATE playlist_tracks SET position = :position WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun setPosition(playlistId: Long, videoId: String, position: Int)

    /** Riscrive l'ordine completo, in una transazione sola. */
    @Transaction
    suspend fun reorder(playlistId: Long, videoIdsInOrder: List<String>) {
        videoIdsInOrder.forEachIndexed { i, videoId ->
            setPosition(playlistId, videoId, i)
        }
    }

    @Query("SELECT * FROM playlists ORDER BY createdAt")
    suspend fun allPlaylists(): List<PlaylistEntity>

    @Query("SELECT videoId FROM playlist_tracks WHERE playlistId = :id ORDER BY position")
    suspend fun trackIdsOf(id: Long): List<String>

    /** Ogni playlist con i suoi brani in ordine: serve all'indice. */
    @Transaction
    suspend fun allWithTracks(): List<Pair<PlaylistEntity, List<String>>> =
        allPlaylists().map { it to trackIdsOf(it.id) }

    /** In quali playlist sta gia' questo brano: serve alla scheda dettagli. */
    @Query("SELECT playlistId FROM playlist_tracks WHERE videoId = :videoId")
    fun observePlaylistsOf(videoId: String): Flow<List<Long>>
}

@Database(
    entities = [TrackEntity::class, PlaylistEntity::class, PlaylistTrackEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun tracks(): TrackDao
    abstract fun playlists(): PlaylistDao

    companion object {
        /**
         * Aggiunge le playlist senza toccare i brani gia' scaricati.
         *
         * Non e' una formalita': con `fallbackToDestructiveMigration` Room
         * avrebbe buttato via il database, e con lui l'indice dei file —
         * che sarebbero rimasti su disco come byte orfani, invisibili
         * all'app e impossibili da cancellare dall'interfaccia.
         */
        /**
         * Aggiunge il riferimento al programma sulle puntate.
         *
         * Una colonna in piu' e nient'altro: le righe esistenti restano
         * con null, che e' giusto — erano brani, non puntate.
         */
        /**
         * Distingue i brani della libreria da quelli solo ascoltati.
         *
         * Le righe che c'erano prima nascono tutte salvate, ed e'
         * giusto: erano state scaricate apposta, una per una.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN salvato INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN showId TEXT")
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS playlists (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS playlist_tracks (
                        playlistId INTEGER NOT NULL,
                        videoId TEXT NOT NULL,
                        position INTEGER NOT NULL,
                        addedAt INTEGER NOT NULL,
                        PRIMARY KEY(playlistId, videoId),
                        FOREIGN KEY(playlistId) REFERENCES playlists(id) ON DELETE CASCADE,
                        FOREIGN KEY(videoId) REFERENCES tracks(videoId) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_tracks_videoId ON playlist_tracks(videoId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_tracks_playlistId ON playlist_tracks(playlistId)")
            }
        }
    }
}
