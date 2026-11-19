package com.example.arrows.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate

/**
 * Local SQLite database (arrows.db).
 *
 *  level_progress : one row per attempted level (stars, best time, attempts)
 *  game_history   : one row per finished game (WIN / LOSS)  -> FK to level_progress
 *  player_stats   : single-row table with global counters + highest unlocked level
 *  settings       : key/value (sound on/off)
 *  daily_activity : one row per calendar day the player played (wins / losses) -> used for streaks
 *  achievements   : one row per unlocked achievement
 */
class DatabaseHelper private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    data class Stats(
        val highestUnlocked: Int,
        val wins: Int,
        val losses: Int,
        val levelsCompleted: Int,
        val totalStars: Int
    )

    data class Counters(
        val threeStarLevels: Int,
        val maxLevelCleared: Int,
        val fastWins: Int,
        val winRun: Int
    )

    companion object {
        private const val DB_NAME = "arrows.db"
        private const val DB_VERSION = 2
        const val TOTAL_LEVELS = 100

        @Volatile private var instance: DatabaseHelper? = null

        fun get(context: Context): DatabaseHelper =
            instance ?: synchronized(this) {
                instance ?: DatabaseHelper(context).also { instance = it }
            }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE level_progress (
                   level        INTEGER PRIMARY KEY,
                   stars        INTEGER NOT NULL DEFAULT 0 CHECK (stars BETWEEN 0 AND 3),
                   best_time_ms INTEGER,
                   attempts     INTEGER NOT NULL DEFAULT 0,
                   completed    INTEGER NOT NULL DEFAULT 0,
                   updated_at   TEXT    NOT NULL DEFAULT CURRENT_TIMESTAMP
               )"""
        )
        db.execSQL(
            """CREATE TABLE game_history (
                   id        INTEGER PRIMARY KEY AUTOINCREMENT,
                   level     INTEGER NOT NULL REFERENCES level_progress(level) ON DELETE CASCADE,
                   result    TEXT    NOT NULL CHECK (result IN ('WIN','LOSS')),
                   stars     INTEGER NOT NULL DEFAULT 0,
                   time_ms   INTEGER NOT NULL DEFAULT 0,
                   mistakes  INTEGER NOT NULL DEFAULT 0,
                   played_at TEXT    NOT NULL DEFAULT CURRENT_TIMESTAMP
               )"""
        )
        db.execSQL(
            """CREATE TABLE player_stats (
                   id               INTEGER PRIMARY KEY CHECK (id = 1),
                   highest_unlocked INTEGER NOT NULL DEFAULT 1,
                   total_wins       INTEGER NOT NULL DEFAULT 0,
                   total_losses     INTEGER NOT NULL DEFAULT 0
               )"""
        )
        db.execSQL("CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        db.execSQL("CREATE INDEX idx_history_level ON game_history(level)")
        createV2Tables(db)

        db.execSQL("INSERT INTO player_stats (id) VALUES (1)")
        db.execSQL("INSERT INTO settings (key, value) VALUES ('sound_on', '1')")
    }

    /** v2 adds streaks + achievements. Existing progress is kept. */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            createV2Tables(db)
            // rebuild the activity calendar from the old game history
            db.execSQL(
                """INSERT OR IGNORE INTO daily_activity (day, wins, losses)
                   SELECT date(played_at, 'localtime'),
                          SUM(CASE WHEN result = 'WIN'  THEN 1 ELSE 0 END),
                          SUM(CASE WHEN result = 'LOSS' THEN 1 ELSE 0 END)
                   FROM game_history
                   GROUP BY date(played_at, 'localtime')"""
            )
        }
    }

    private fun createV2Tables(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS daily_activity (
                   day    TEXT PRIMARY KEY,
                   wins   INTEGER NOT NULL DEFAULT 0,
                   losses INTEGER NOT NULL DEFAULT 0
               )"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS achievements (
                   id          TEXT PRIMARY KEY,
                   unlocked_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
               )"""
        )
    }

    // ------------------------------------------------------------------ writes

    /** Saves the outcome of one game (history, level progress, stats and today's activity) in one transaction. */
    fun saveResult(level: Int, won: Boolean, stars: Int, timeMs: Long, mistakes: Int) {
        val db = writableDatabase
        val today = LocalDate.now().toString()          // yyyy-MM-dd, local time
        db.beginTransaction()
        try {
            db.execSQL("INSERT OR IGNORE INTO level_progress (level) VALUES (?)", arrayOf<Any>(level))
            db.execSQL("UPDATE level_progress SET attempts = attempts + 1 WHERE level = ?", arrayOf<Any>(level))
            db.execSQL(
                "INSERT INTO game_history (level, result, stars, time_ms, mistakes) VALUES (?, ?, ?, ?, ?)",
                arrayOf<Any>(level, if (won) "WIN" else "LOSS", stars, timeMs, mistakes)
            )
            db.execSQL("INSERT OR IGNORE INTO daily_activity (day) VALUES (?)", arrayOf<Any>(today))
            db.execSQL(
                "UPDATE daily_activity SET wins = wins + ?, losses = losses + ? WHERE day = ?",
                arrayOf<Any>(if (won) 1 else 0, if (won) 0 else 1, today)
            )
            if (won) {
                db.execSQL(
                    """UPDATE level_progress
                       SET completed = 1,
                           stars = MAX(stars, ?),
                           best_time_ms = CASE WHEN best_time_ms IS NULL OR ? < best_time_ms
                                               THEN ? ELSE best_time_ms END,
                           updated_at = CURRENT_TIMESTAMP
                       WHERE level = ?""",
                    arrayOf<Any>(stars, timeMs, timeMs, level)
                )
                db.execSQL(
                    """UPDATE player_stats
                       SET total_wins = total_wins + 1,
                           highest_unlocked = MAX(highest_unlocked, ?)
                       WHERE id = 1""",
                    arrayOf<Any>(minOf(level + 1, TOTAL_LEVELS))
                )
            } else {
                db.execSQL("UPDATE player_stats SET total_losses = total_losses + 1 WHERE id = 1")
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun setSoundOn(on: Boolean) {
        writableDatabase.execSQL(
            "INSERT OR REPLACE INTO settings (key, value) VALUES ('sound_on', ?)",
            arrayOf<Any>(if (on) "1" else "0")
        )
    }

    fun unlockAchievement(id: String) {
        writableDatabase.execSQL("INSERT OR IGNORE INTO achievements (id) VALUES (?)", arrayOf<Any>(id))
    }

    fun resetProgress() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM game_history")
            db.execSQL("DELETE FROM level_progress")
            db.execSQL("DELETE FROM daily_activity")
            db.execSQL("DELETE FROM achievements")
            db.execSQL("UPDATE player_stats SET highest_unlocked = 1, total_wins = 0, total_losses = 0 WHERE id = 1")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ------------------------------------------------------------------ reads

    private fun scalar(sql: String): Int =
        readableDatabase.rawQuery(sql, null).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun highestUnlocked(): Int =
        readableDatabase.rawQuery("SELECT highest_unlocked FROM player_stats WHERE id = 1", null).use {
            if (it.moveToFirst()) it.getInt(0) else 1
        }

    /** level -> stars, for every completed level. */
    fun allStars(): Map<Int, Int> {
        val map = HashMap<Int, Int>()
        readableDatabase.rawQuery("SELECT level, stars FROM level_progress WHERE completed = 1", null).use {
            while (it.moveToNext()) map[it.getInt(0)] = it.getInt(1)
        }
        return map
    }

    fun stats(): Stats =
        readableDatabase.rawQuery(
            """SELECT p.highest_unlocked, p.total_wins, p.total_losses,
                      (SELECT COUNT(*) FROM level_progress WHERE completed = 1),
                      (SELECT COALESCE(SUM(stars), 0) FROM level_progress)
               FROM player_stats p WHERE p.id = 1""", null
        ).use {
            if (it.moveToFirst()) Stats(it.getInt(0), it.getInt(1), it.getInt(2), it.getInt(3), it.getInt(4))
            else Stats(1, 0, 0, 0, 0)
        }

    fun counters(): Counters {
        var run = 0
        readableDatabase.rawQuery("SELECT result FROM game_history ORDER BY id DESC LIMIT 100", null).use {
            while (it.moveToNext()) {
                if (it.getString(0) == "WIN") run++ else break
            }
        }
        return Counters(
            threeStarLevels = scalar("SELECT COUNT(*) FROM level_progress WHERE stars = 3"),
            maxLevelCleared = scalar("SELECT COALESCE(MAX(level), 0) FROM level_progress WHERE completed = 1"),
            fastWins = scalar("SELECT COUNT(*) FROM game_history WHERE result = 'WIN' AND level >= 5 AND time_ms < 45000"),
            winRun = run
        )
    }

    /** Every calendar day on which the player won at least one level. */
    fun activeDays(): Set<LocalDate> {
        val days = HashSet<LocalDate>()
        readableDatabase.rawQuery("SELECT day FROM daily_activity WHERE wins > 0", null).use {
            while (it.moveToNext()) {
                try { days.add(LocalDate.parse(it.getString(0))) } catch (_: Exception) { }
            }
        }
        return days
    }

    fun winsToday(): Int =
        readableDatabase.rawQuery(
            "SELECT wins FROM daily_activity WHERE day = ?", arrayOf(LocalDate.now().toString())
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    /** achievement id -> unlock timestamp. */
    fun unlockedAchievements(): Map<String, String> {
        val map = HashMap<String, String>()
        readableDatabase.rawQuery("SELECT id, unlocked_at FROM achievements", null).use {
            while (it.moveToNext()) map[it.getString(0)] = it.getString(1)
        }
        return map
    }

    fun isSoundOn(): Boolean =
        readableDatabase.rawQuery("SELECT value FROM settings WHERE key = 'sound_on'", null).use {
            !it.moveToFirst() || it.getString(0) == "1"
        }
}
