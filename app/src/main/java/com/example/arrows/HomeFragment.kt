package com.example.arrows

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.arrows.data.DatabaseHelper

class HomeFragment : Fragment(R.layout.fragment_home) {

    private lateinit var db: DatabaseHelper
    private lateinit var tvLevel: TextView
    private lateinit var tvChip: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        db = DatabaseHelper.get(requireContext())
        tvLevel = view.findViewById(R.id.tvLevel)
        tvChip = view.findViewById(R.id.tvStreakChip)
        view.findViewById<Button>(R.id.btnPlay).setOnClickListener {
            startActivity(
                Intent(requireContext(), GameActivity::class.java)
                    .putExtra(GameActivity.EXTRA_LEVEL, db.highestUnlocked())
            )
        }
    }

    override fun onResume() {
        super.onResume()
        tvLevel.text = "Level ${db.highestUnlocked()}"
        val streak = StreakCalculator.compute(db.activeDays()).daily.current
        tvChip.text = if (streak > 0) "🔥 $streak-day streak" else "Win a level today to start a streak"
    }
}
