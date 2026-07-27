package com.example.embeddedsystemscareerguide.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.embeddedsystemscareerguide.databinding.ItemAchievementBinding
import com.example.embeddedsystemscareerguide.models.Achievement

/** Horizontally-scrollable row of earned achievement badges on the Home dashboard. */
class AchievementAdapter(private val items: List<Achievement>) :
    RecyclerView.Adapter<AchievementAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemAchievementBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAchievementBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val achievement = items[position]
        holder.binding.textAchievementEmoji.text = achievement.emoji
        holder.binding.textAchievementTitle.text = achievement.title
    }
}
