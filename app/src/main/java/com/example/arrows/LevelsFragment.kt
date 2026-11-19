package com.example.arrows

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.arrows.data.DatabaseHelper

class LevelsFragment : Fragment(R.layout.fragment_levels) {

    private lateinit var db: DatabaseHelper
    private lateinit var rv: RecyclerView
    private lateinit var tvStarsTotal: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        db = DatabaseHelper.get(requireContext())
        rv = view.findViewById(R.id.rvLevels)
        tvStarsTotal = view.findViewById(R.id.tvStarsTotal)
        rv.layoutManager = GridLayoutManager(requireContext(), 4)
    }

    override fun onResume() {
        super.onResume()
        val highest = db.highestUnlocked()
        val stars = db.allStars()
        tvStarsTotal.text = "★ ${stars.values.sum()}"
        rv.adapter = LevelAdapter(DatabaseHelper.TOTAL_LEVELS, highest, stars) { level ->
            startActivity(Intent(requireContext(), GameActivity::class.java).putExtra(GameActivity.EXTRA_LEVEL, level))
        }
    }

    private class LevelAdapter(
        val count: Int,
        val highest: Int,
        val stars: Map<Int, Int>,
        val onClick: (Int) -> Unit
    ) : RecyclerView.Adapter<LevelAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val num: TextView = v.findViewById(R.id.tvNum)
            val stars: TextView = v.findViewById(R.id.tvStars)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_level, parent, false))

        override fun getItemCount() = count

        override fun onBindViewHolder(h: VH, position: Int) {
            val level = position + 1
            val ctx = h.itemView.context
            val open = level <= highest
            h.num.text = level.toString()
            h.num.setTextColor(ctx.getColor(if (open) R.color.navy else R.color.locked))
            h.itemView.setBackgroundResource(
                when {
                    !open -> R.drawable.bg_level_locked
                    level == highest -> R.drawable.bg_level_current
                    else -> R.drawable.bg_level_open
                }
            )
            val earned = stars[level] ?: 0
            h.stars.text = if (open && stars.containsKey(level)) "★".repeat(earned) + "☆".repeat(3 - earned) else ""
            h.stars.setTextColor(ctx.getColor(R.color.star))
            h.itemView.setOnClickListener { if (open) onClick(level) }
        }
    }
}
