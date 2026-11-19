package com.example.arrows

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.arrows.StreakCalculator.MONTH_GOAL_DAYS
import com.example.arrows.StreakCalculator.WEEK_GOAL_DAYS
import com.example.arrows.data.DatabaseHelper
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

class StreaksFragment : Fragment(R.layout.fragment_streaks) {

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun plural(n: Int, unit: String) = "$n $unit${if (n == 1) "" else "s"}"

    private fun render() {
        val v = view ?: return
        val ctx = requireContext()
        val info = StreakCalculator.compute(DatabaseHelper.get(ctx).activeDays())

        // ---- daily
        v.findViewById<TextView>(R.id.tvDailyCount).text = plural(info.daily.current, "day")
        v.findViewById<TextView>(R.id.tvDailyBest).text = "Best: ${plural(info.daily.best, "day")}"
        v.findViewById<TextView>(R.id.tvDailyMsg).text = when {
            info.activeToday -> "Today is done. Your streak is safe."
            info.daily.current > 0 -> "Win a level today to keep your ${info.daily.current}-day streak alive."
            else -> "Win a level today to start a streak."
        }

        // last 7 days strip
        val strip = v.findViewById<LinearLayout>(R.id.weekStrip)
        strip.removeAllViews()
        val dp = resources.displayMetrics.density
        val today = LocalDate.now()
        info.last7.forEach { (date, active) ->
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val label = TextView(ctx).apply {
                text = date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
                textSize = 12f
                setTextColor(ctx.getColor(R.color.ink_soft))
            }
            val dot = TextView(ctx).apply {
                gravity = Gravity.CENTER
                text = if (active) "✓" else ""
                textSize = 15f
                setTextColor(ctx.getColor(R.color.white))
                setBackgroundResource(
                    when {
                        active -> R.drawable.bg_day_active
                        date == today -> R.drawable.bg_day_today
                        else -> R.drawable.bg_day_idle
                    }
                )
                layoutParams = LinearLayout.LayoutParams((34 * dp).toInt(), (34 * dp).toInt()).apply {
                    topMargin = (6 * dp).toInt()
                }
            }
            col.addView(label)
            col.addView(dot)
            strip.addView(col)
        }

        // ---- weekly
        v.findViewById<TextView>(R.id.tvWeeklyCount).text = plural(info.weekly.current, "week")
        v.findViewById<TextView>(R.id.tvWeeklyBest).text = "Best: ${plural(info.weekly.best, "week")}"
        v.findViewById<ProgressBar>(R.id.pbWeek).apply {
            max = WEEK_GOAL_DAYS
            progress = info.daysThisWeek.coerceAtMost(WEEK_GOAL_DAYS)
        }
        v.findViewById<TextView>(R.id.tvWeekProgress).text =
            "${info.daysThisWeek} / $WEEK_GOAL_DAYS active days this week" +
                if (info.daysThisWeek >= WEEK_GOAL_DAYS) "  ✓ goal reached" else ""

        // ---- monthly
        v.findViewById<TextView>(R.id.tvMonthlyCount).text = plural(info.monthly.current, "month")
        v.findViewById<TextView>(R.id.tvMonthlyBest).text = "Best: ${plural(info.monthly.best, "month")}"
        v.findViewById<ProgressBar>(R.id.pbMonth).apply {
            max = MONTH_GOAL_DAYS
            progress = info.daysThisMonth.coerceAtMost(MONTH_GOAL_DAYS)
        }
        v.findViewById<TextView>(R.id.tvMonthProgress).text =
            "${info.daysThisMonth} / $MONTH_GOAL_DAYS active days this month" +
                if (info.daysThisMonth >= MONTH_GOAL_DAYS) "  ✓ goal reached" else ""
    }
}
