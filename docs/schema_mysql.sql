-- Same schema in MySQL syntax (for the project report / an optional online backend).
-- The Android app itself runs on SQLite (see schema_sqlite.sql); MySQL cannot run on the phone.
CREATE DATABASE IF NOT EXISTS arrows_game;
USE arrows_game;

CREATE TABLE level_progress (
    level        INT PRIMARY KEY,
    stars        TINYINT NOT NULL DEFAULT 0,
    best_time_ms BIGINT NULL,
    attempts     INT NOT NULL DEFAULT 0,
    completed    TINYINT(1) NOT NULL DEFAULT 0,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_stars CHECK (stars BETWEEN 0 AND 3)
) ENGINE=InnoDB;

CREATE TABLE game_history (
    id        INT AUTO_INCREMENT PRIMARY KEY,
    level     INT NOT NULL,
    result    ENUM('WIN','LOSS') NOT NULL,
    stars     TINYINT NOT NULL DEFAULT 0,
    time_ms   BIGINT NOT NULL DEFAULT 0,
    mistakes  INT NOT NULL DEFAULT 0,
    played_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (level) REFERENCES level_progress(level) ON DELETE CASCADE,
    INDEX idx_history_level (level)
) ENGINE=InnoDB;

CREATE TABLE player_stats (
    id               TINYINT PRIMARY KEY,
    highest_unlocked INT NOT NULL DEFAULT 1,
    total_wins       INT NOT NULL DEFAULT 0,
    total_losses     INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_single_row CHECK (id = 1)
) ENGINE=InnoDB;

CREATE TABLE settings (
    `key`   VARCHAR(50) PRIMARY KEY,
    `value` VARCHAR(255) NOT NULL
) ENGINE=InnoDB;

INSERT INTO player_stats (id) VALUES (1);
INSERT INTO settings (`key`, `value`) VALUES ('sound_on', '1');

-- added in database version 2 (streaks + achievements)
CREATE TABLE daily_activity (
    day    DATE PRIMARY KEY,
    wins   INT NOT NULL DEFAULT 0,
    losses INT NOT NULL DEFAULT 0
) ENGINE=InnoDB;

CREATE TABLE achievements (
    id          VARCHAR(40) PRIMARY KEY,
    unlocked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- Example: daily streak helper query (days with a win, newest first)
-- SELECT day FROM daily_activity WHERE wins > 0 ORDER BY day DESC;
