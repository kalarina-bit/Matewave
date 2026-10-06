package cc.skysparkle.matewave.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class SavedGame(
    val fen: String,
    val myColorWhite: Boolean,
    val difficultyName: String,
    val initialSeconds: Int,
    val incrementSeconds: Int,
    val whiteMillis: Long = -1,
    val blackMillis: Long = -1,
    /** Moves from the standard start in UCI, space separated; empty for saves made before version 3. */
    val movesUci: String = ""
)

/** Persists the unfinished game against the AI: moves, position and remaining clock time. */
object GameSaveStore {
    private const val DB_NAME = "chess_save.db"
    private const val DB_VERSION = 3
    private const val TABLE = "saved_game"

    @Volatile private var helper: DbHelper? = null

    fun init(context: Context) {
        if (helper == null) synchronized(this) {
            if (helper == null) helper = DbHelper(context.applicationContext)
        }
    }

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

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN white_ms INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN black_ms INTEGER NOT NULL DEFAULT -1")
            }
            if (oldVersion < 3) {
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN moves TEXT NOT NULL DEFAULT ''")
            }
        }
    }

    fun save(game: SavedGame) {
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

    fun load(): SavedGame? {
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
        runCatching { helper?.writableDatabase?.delete(TABLE, "id = 1", null) }
    }
}
