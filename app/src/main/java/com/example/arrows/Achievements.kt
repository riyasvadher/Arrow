package com.example.arrows

import com.example.arrows.data.DatabaseHelper

object Achievements {

    data class Metrics(
        val levelsCompleted: Int,
        val totalStars: Int,
        val threeStarLevels: Int,
        val maxLevelCleared: Int,
        val fastWins: Int,
        val winRun: Int,
        val gamesPlayed: Int,
        val dailyBest: Int,
        val weeklyBest: Int,
        val monthlyBest: Int
    )

    data class Def(
        val id: String,
        val emoji: String,
        val title: String,
        val desc: String,
        val target: Int,
        val value: (Metrics) -> Int
    )

    data class Status(val def: Def, val value: Int, val unlocked: Boolean, val unlockedAt: String?)

    val all: List<Def> = listOf(
        Def("first_win", "🎯", "First Steps", "Clear your first level", 1) { it.levelsCompleted },
        Def("levels_10", "🧩", "Warming Up", "Clear 10 levels", 10) { it.levelsCompleted },
        Def("levels_25", "🗺️", "Maze Runner", "Clear 25 levels", 25) { it.levelsCompleted },
        Def("levels_50", "🧭", "Pathfinder", "Clear 50 levels", 50) { it.levelsCompleted },
        Def("levels_100", "👑", "Arrow Master", "Clear all 100 levels", 100) { it.levelsCompleted },

        Def("perfect_1", "⭐", "Flawless", "Earn 3 stars on a level", 1) { it.threeStarLevels },
        Def("perfect_10", "🌟", "Perfectionist", "Earn 3 stars on 10 levels", 10) { it.threeStarLevels },
        Def("stars_50", "✨", "Star Collector", "Collect 50 stars", 50) { it.totalStars },
        Def("stars_150", "💫", "Constellation", "Collect 150 stars", 150) { it.totalStars },

        Def("run_5", "⚡", "On a Roll", "Win 5 games in a row", 5) { it.winRun },
        Def("speed", "⏱️", "Speed Demon", "Clear a level 5 or higher in under 45 seconds", 1) { it.fastWins },
        Def("hard", "🏔️", "Into the Deep", "Clear a Hard level (31 or higher)", 1) { if (it.maxLevelCleared >= 31) 1 else 0 },
        Def("games_50", "🎮", "Dedicated", "Play 50 games", 50) { it.gamesPlayed },

        Def("daily_3", "📅", "Three in a Row", "Reach a 3-day streak", 3) { it.dailyBest },
        Def("daily_7", "🔥", "Week Warrior", "Reach a 7-day streak", 7) { it.dailyBest },
        Def("daily_30", "🏆", "Unstoppable", "Reach a 30-day streak", 30) { it.dailyBest },
        Def("weekly_2", "📆", "Consistent", "Reach a 2-week streak", 2) { it.weeklyBest },
        Def("weekly_4", "🗓️", "Four Weeks Strong", "Reach a 4-week streak", 4) { it.weeklyBest },
        Def("monthly_1", "🌙", "Monthly Habit", "Play on ${StreakCalculator.MONTH_GOAL_DAYS} days in one month", 1) { it.monthlyBest },
        Def("monthly_3", "🏅", "Quarter Champion", "Reach a 3-month streak", 3) { it.monthlyBest }
    )

    fun metrics(db: DatabaseHelper): Metrics {
        val s = db.stats()
        val c = db.counters()
        val st = StreakCalculator.compute(db.activeDays())
        return Metrics(
            levelsCompleted = s.levelsCompleted,
            totalStars = s.totalStars,
            threeStarLevels = c.threeStarLevels,
            maxLevelCleared = c.maxLevelCleared,
            fastWins = c.fastWins,
            winRun = c.winRun,
            gamesPlayed = s.wins + s.losses,
            dailyBest = st.daily.best,
            weeklyBest = st.weekly.best,
            monthlyBest = st.monthly.best
        )
    }

    fun statuses(db: DatabaseHelper): List<Status> {
        val m = metrics(db)
        val unlocked = db.unlockedAchievements()
        return all.map { d ->
            Status(d, d.value(m).coerceAtMost(d.target), d.id in unlocked, unlocked[d.id])
        }
    }

    /** Unlocks every achievement whose target has been reached and returns the new ones. */
    fun checkNewlyUnlocked(db: DatabaseHelper): List<Def> {
        val m = metrics(db)
        val already = db.unlockedAchievements()
        val fresh = all.filter { it.id !in already && it.value(m) >= it.target }
        fresh.forEach { db.unlockAchievement(it.id) }
        return fresh
    }
}
