package com.example.arrows

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.arrows.data.DatabaseHelper

class AchievementsFragment : Fragment(R.layout.fragment_achievements) {

    private lateinit var rv: RecyclerView
    private lateinit var tvSummary: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        rv = view.findViewById(R.id.rvAchievements)
        tvSummary = view.findViewById(R.id.tvSummary)
        rv.layoutManager = LinearLayoutManager(requireContext())
    }

    override fun onResume() {
        super.onResume()
        val list = Achievements.statuses(DatabaseHelper.get(requireContext()))
        tvSummary.text = "${list.count { it.unlocked }} / ${list.size}"
        rv.adapter = Adapter(list.sortedByDescending { it.unlocked })   // unlocked first
    }

    private class Adapter(val items: List<Achievements.Status>) : RecyclerView.Adapter<Adapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val emoji: TextView = v.findViewById(R.id.tvEmoji)
            val title: TextView = v.findViewById(R.id.tvTitle)
            val desc: TextView = v.findViewById(R.id.tvDesc)
            val pb: ProgressBar = v.findViewById(R.id.pbAch)
            val count: TextView = v.findViewById(R.id.tvCount)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_achievement, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val s = items[position]
            val ctx = h.itemView.context
            h.emoji.text = s.def.emoji
            h.emoji.alpha = if (s.unlocked) 1f else 0.35f
            h.emoji.setBackgroundResource(if (s.unlocked) R.drawable.bg_emoji_done else R.drawable.bg_emoji_locked)
            h.title.text = s.def.title
            h.title.setTextColor(ctx.getColor(if (s.unlocked) R.color.navy else R.color.ink_soft))
            h.desc.text = s.def.desc
            h.pb.max = s.def.target
            h.pb.progress = s.value
            if (s.unlocked) {
                h.count.text = "✓"
                h.count.setTextColor(ctx.getColor(R.color.accent))
                h.count.textSize = 22f
            } else {
                h.count.text = "${s.value}/${s.def.target}"
                h.count.setTextColor(ctx.getColor(R.color.ink_soft))
                h.count.textSize = 14f
            }
        }
    }
}
