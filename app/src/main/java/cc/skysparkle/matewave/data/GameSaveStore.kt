package cc.skysparkle.matewave.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

data class SavedGame(
    val fen: String,
    val myColorWhite: Boolean,
    val difficultyName: String,
    val initialSeconds: Int,
    val incrementSeconds: Int,
    val whiteMillis: Long = -1,
    val blackMillis: Long = -1,
    /** Moves from the standard start in UCI, space separated; empty for a game that started elsewhere. */
    val movesUci: String = ""
)

/**
 * Persists the unfinished game against the AI: moves, position and remaining clock time.
 * The game is kept in memory once read or saved; the database is written in the background,
 * in order, so moves never wait for the disk.
 */
object GameSaveStore {
    private const val DB_NAME = "chess_save.db"
    private const val DB_VERSION = 3
    private const val TABLE = "saved_game"

    @Volatile private var helper: DbHelper? = null

    /** One thread at most, so writes keep their order; it ends when idle. */
    private val io = ThreadPoolExecutor(0, 1, 10, TimeUnit.SECONDS, LinkedBlockingQueue()) { task ->
        Thread(task, "game-save").apply { isDaemon = true }
    }

    private val lock = Any()
    /** Whether [current] matches what is (or is about to be) on disk. */
    private var known = false
    private var current: SavedGame? = null

    fun init(context: Context) {
        if (helper == null) synchronized(this) {
            if (helper == null) helper = DbHelper(context.applicationContext)
        }
    }

    /** Reads the save in the background, so the first screen finds it in memory. */
    fun preload() {
        io.execute { load() }
    }

    /** Whether [load] would return without reading the database. */
    fun isLoaded(): Boolean = synchronized(lock) { known }

    private class DbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    fen TEXT NOT NULL,
                    my_color_white INTEGER NOT NULL,
                    difficulty TEXT NOT NULL,
                    initial_seconds INTEGER NOT NULL,
                    increment_seconds INTEGER NOT NULL,
                    white_ms INTEGER NOT NULL DEFAULT -1,
                    black_ms INTEGER NOT NULL DEFAULT -1,
                    moves TEXT NOT NULL DEFAULT ''
                )
                """.trimIndent()
            )
        }

        // Saves from older versions are not carried over.
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE")
            onCreate(db)
        }
    }

    fun save(game: SavedGame) {
        synchronized(lock) { current = game; known = true }
        io.execute { write(game) }
    }

    private fun write(game: SavedGame) {
        val db = runCatching { helper?.writableDatabase }.getOrNull() ?: return
        val values = ContentValues().apply {
            put("id", 1)
            put("fen", game.fen)
            put("my_color_white", if (game.myColorWhite) 1 else 0)
            put("difficulty", game.difficultyName)
            put("initial_seconds", game.initialSeconds)
            put("increment_seconds", game.incrementSeconds)
            put("white_ms", game.whiteMillis)
            put("black_ms", game.blackMillis)
            put("moves", game.movesUci)
        }
        runCatching { db.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE) }
    }

    /** The saved game; the first call reads the database, so make it off the main thread. */
    fun load(): SavedGame? {
        synchronized(lock) { if (known) return current }
        val read = read()
        synchronized(lock) {
            // A save or clear made while reading is newer than what was read.
            if (!known) {
                current = read
                known = true
            }
            return current
        }
    }

    private fun read(): SavedGame? {
        val db = runCatching { helper?.readableDatabase }.getOrNull() ?: return null
        return runCatching {
            db.query(TABLE, null, "id = 1", null, null, null, null).use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                SavedGame(
                    fen = cursor.getString(cursor.getColumnIndexOrThrow("fen")),
                    myColorWhite = cursor.getInt(cursor.getColumnIndexOrThrow("my_color_white")) == 1,
                    difficultyName = cursor.getString(cursor.getColumnIndexOrThrow("difficulty")),
                    initialSeconds = cursor.getInt(cursor.getColumnIndexOrThrow("initial_seconds")),
                    incrementSeconds = cursor.getInt(cursor.getColumnIndexOrThrow("increment_seconds")),
                    whiteMillis = cursor.getLong(cursor.getColumnIndexOrThrow("white_ms")),
                    blackMillis = cursor.getLong(cursor.getColumnIndexOrThrow("black_ms")),
                    movesUci = cursor.getString(cursor.getColumnIndexOrThrow("moves")) ?: ""
                )
            }
        }.getOrNull()
    }

    fun clear() {
        synchronized(lock) { current = null; known = true }
        io.execute { runCatching { helper?.writableDatabase?.delete(TABLE, "id = 1", null) } }
    }
}
