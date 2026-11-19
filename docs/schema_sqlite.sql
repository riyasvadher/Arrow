-- Schema used by the app (created in DatabaseHelper.onCreate) - SQLite
CREATE TABLE level_progress (
    level        INTEGER PRIMARY KEY,
    stars        INTEGER NOT NULL DEFAULT 0 CHECK (stars BETWEEN 0 AND 3),
    best_time_ms INTEGER,
    attempts     INTEGER NOT NULL DEFAULT 0,
    completed    INTEGER NOT NULL DEFAULT 0,
    updated_at   TEXT    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE game_history (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    level     INTEGER NOT NULL REFERENCES level_progress(level) ON DELETE CASCADE,
    result    TEXT    NOT NULL CHECK (result IN ('WIN','LOSS')),
    stars     INTEGER NOT NULL DEFAULT 0,
    time_ms   INTEGER NOT NULL DEFAULT 0,
    mistakes  INTEGER NOT NULL DEFAULT 0,
    played_at TEXT    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE player_stats (
    id               INTEGER PRIMARY KEY CHECK (id = 1),
    highest_unlocked INTEGER NOT NULL DEFAULT 1,
    total_wins       INTEGER NOT NULL DEFAULT 0,
    total_losses     INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL);
CREATE INDEX idx_history_level ON game_history(level);

-- added in database version 2 (streaks + achievements)
CREATE TABLE daily_activity (
    day    TEXT PRIMARY KEY,              -- yyyy-MM-dd (local date)
    wins   INTEGER NOT NULL DEFAULT 0,
    losses INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE achievements (
    id          TEXT PRIMARY KEY,         -- e.g. 'first_win', 'daily_7'
    unlocked_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
