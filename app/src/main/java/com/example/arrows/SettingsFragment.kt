package com.example.arrows

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import com.example.arrows.data.DatabaseHelper

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private lateinit var db: DatabaseHelper

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        db = DatabaseHelper.get(requireContext())

        view.findViewById<SwitchCompat>(R.id.swSound).apply {
            isChecked = db.isSoundOn()
            setOnCheckedChangeListener { _, on -> db.setSoundOn(on) }
        }
        view.findViewById<Button>(R.id.btnReset).setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Reset progress?")
                .setMessage("All stars, records, streaks, achievements and unlocked levels will be deleted.")
                .setPositiveButton("Reset") { _, _ -> db.resetProgress(); refreshStats() }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStats()
    }

    private fun refreshStats() {
        val v = view ?: return
        val s = db.stats()
        v.findViewById<TextView>(R.id.tvStatLevels).text =
            "Levels completed: ${s.levelsCompleted} / ${DatabaseHelper.TOTAL_LEVELS}"
        v.findViewById<TextView>(R.id.tvStatStars).text =
            "Stars collected: ${s.totalStars} / ${DatabaseHelper.TOTAL_LEVELS * 3}"
        v.findViewById<TextView>(R.id.tvStatRecord).text = "Wins: ${s.wins}    Losses: ${s.losses}"
        v.findViewById<TextView>(R.id.tvStatHighest).text = "Highest level unlocked: ${s.highestUnlocked}"
    }
}
