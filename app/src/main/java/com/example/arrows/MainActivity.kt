package com.example.arrows

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment

/** Hosts the five tabs (Home, Levels, Streaks, Awards, Settings) behind a bottom navigation bar. */
class MainActivity : AppCompatActivity() {

    private class Tab(val navId: Int, val iconId: Int, val labelId: Int, val create: () -> Fragment)

    private val tabs = listOf(
        Tab(R.id.navHome, R.id.navHomeIcon, R.id.navHomeLabel) { HomeFragment() },
        Tab(R.id.navLevels, R.id.navLevelsIcon, R.id.navLevelsLabel) { LevelsFragment() },
        Tab(R.id.navStreaks, R.id.navStreaksIcon, R.id.navStreaksLabel) { StreaksFragment() },
        Tab(R.id.navAwards, R.id.navAwardsIcon, R.id.navAwardsLabel) { AchievementsFragment() },
        Tab(R.id.navSettings, R.id.navSettingsIcon, R.id.navSettingsLabel) { SettingsFragment() }
    )
    private var selected = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tabs.forEachIndexed { i, t -> findViewById<android.view.View>(t.navId).setOnClickListener { select(i) } }

        selected = savedInstanceState?.getInt("tab") ?: 0
        applyStyle()
        if (savedInstanceState == null) showFragment(selected)   // otherwise the system restores it

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (selected != 0) select(0) else finish()
            }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", selected)
    }

    private fun select(i: Int) {
        if (i == selected) return
        selected = i
        applyStyle()
        showFragment(i)
    }

    private fun showFragment(i: Int) {
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.container, tabs[i].create())
            .commit()
    }

    private fun applyStyle() {
        tabs.forEachIndexed { i, t ->
            val on = i == selected
            val color = getColor(if (on) R.color.accent else R.color.locked)
            findViewById<ImageView>(t.iconId).imageTintList = ColorStateList.valueOf(color)
            findViewById<TextView>(t.labelId).apply {
                setTextColor(color)
                setTypeface(null, if (on) Typeface.BOLD else Typeface.NORMAL)
            }
        }
    }
}
