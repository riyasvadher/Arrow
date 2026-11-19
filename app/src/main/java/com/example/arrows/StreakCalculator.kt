package com.example.arrows

import java.time.LocalDate

/**
 * Streak rules (an "active day" = a day with at least one level won):
 *  - Daily   streak: consecutive days that are active.
 *  - Weekly  streak: consecutive weeks (Mon-Sun) with at least WEEK_GOAL_DAYS active days.
 *  - Monthly streak: consecutive calendar months with at least MONTH_GOAL_DAYS active days.
 * A streak is not broken until the current day/week/month is over, so a running streak
 * is still counted while today's (or this week's / month's) goal is not reached yet.
 */
object StreakCalculator {

    const val WEEK_GOAL_DAYS = 3
    const val MONTH_GOAL_DAYS = 10

    data class Streak(val current: Int, val best: Int)

    data class Info(
        val daily: Streak,
        val weekly: Streak,
        val monthly: Streak,
        val daysThisWeek: Int,
        val daysThisMonth: Int,
        val activeToday: Boolean,
        val last7: List<Pair<LocalDate, Boolean>>
    )

    fun compute(days: Set<LocalDate>, today: LocalDate = LocalDate.now()): Info {
        val dayIdx = days.map { it.toEpochDay() }.toSet()
        val daily = streak(dayIdx, today.toEpochDay())

        // epoch day 4 (1970-01-05) was a Monday -> Monday-based week index
        fun weekIdx(d: LocalDate) = Math.floorDiv(d.toEpochDay() - 4L, 7L)
        fun monthIdx(d: LocalDate) = d.year * 12L + (d.monthValue - 1)

        val weekCounts = days.groupingBy { weekIdx(it) }.eachCount()
        val monthCounts = days.groupingBy { monthIdx(it) }.eachCount()

        val weekly = streak(weekCounts.filterValues { it >= WEEK_GOAL_DAYS }.keys, weekIdx(today))
        val monthly = streak(monthCounts.filterValues { it >= MONTH_GOAL_DAYS }.keys, monthIdx(today))

        return Info(
            daily = daily,
            weekly = weekly,
            monthly = monthly,
            daysThisWeek = weekCounts[weekIdx(today)] ?: 0,
            daysThisMonth = monthCounts[monthIdx(today)] ?: 0,
            activeToday = today in days,
            last7 = (6 downTo 0).map { i -> today.minusDays(i.toLong()).let { it to (it in days) } }
        )
    }

    private fun streak(good: Set<Long>, currentIdx: Long): Streak {
        // current run: ends at this period, or at the previous one if this one is not done yet
        val end = when {
            currentIdx in good -> currentIdx
            (currentIdx - 1) in good -> currentIdx - 1
            else -> null
        }
        var current = 0
        if (end != null) {
            var i: Long = end
            while (i in good) { current++; i-- }
        }
        // best run ever
        var best = 0
        for (x in good) {
            if ((x - 1) !in good) {
                var len = 0
                var i: Long = x
                while (i in good) { len++; i++ }
                if (len > best) best = len
            }
        }
        return Streak(current, best)
    }
}
