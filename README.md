# Arrows (Android, Kotlin, XML views)

Open this folder in Android Studio (Koala or newer), let Gradle sync, run on a device/emulator (API 26+).
If Android Studio offers to upgrade the Android Gradle Plugin, accept.

## Screens
One `MainActivity` with a bottom navigation bar and five tabs (fragments):
Home, Levels, Streaks, Awards, Settings. Play / a level opens `GameActivity` (zoomable maze) -> result pop-up.

## Shaped levels
Every level is a different silhouette (100 shapes: heart, horse, fish, rocket, castle ...) stored in
`app/src/main/assets/shapes.txt`. The maze only fills the cells of the shape; clearing the last arrow zooms out and
fills the silhouette in. `tools/build_shapes.py` regenerates the file (needs Pillow, scipy and the Noto Color Emoji font).

## Streaks (day = at least one level won)
- Daily: consecutive active days.
- Weekly: consecutive weeks (Mon-Sun) with 3+ active days.
- Monthly: consecutive months with 10+ active days.
A running streak is not broken until the current day / week / month is over. Rules live in `StreakCalculator.kt`.

## Achievements
20 achievements (levels cleared, stars, win run, speed, hard level, games played, daily / weekly / monthly streaks),
defined in `Achievements.kt` and stored in the `achievements` table. New ones show in the result pop-up.

## Where things are
- `ArrowBoardView.kt`  maze generation, pinch-zoom/pan, tap handling, slide + bump animations
- `GameActivity.kt`    hearts, timer, win / game-over pop-up (stars, next level, play again), sounds
- `data/DatabaseHelper.kt`  SQLite (v2): level_progress, game_history, player_stats, settings, daily_activity, achievements
- `SoundManager.kt` + `res/raw/*.wav`  slide, collision, game over, win (made by `tools/generate_sounds.py`)
- `LevelConfig.kt` + `ShapeLibrary.kt`  shape, size and arrow length per level (100 levels)
- `docs/schema_sqlite.sql`, `docs/schema_mysql.sql`  the schema for your report

## Rules
Tap an arrow: if nothing is in front of its head it slides out along its path. If something is in the way
you lose a heart (3 hearts). Clear all arrows to win: 3 hearts left = 3 stars, 2 = 2 stars, 1 = 1 star.
