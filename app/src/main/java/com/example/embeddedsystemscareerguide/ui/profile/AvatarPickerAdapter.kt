package com.example.embeddedsystemscareerguide.ui.profile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.embeddedsystemscareerguide.R
import com.example.embeddedsystemscareerguide.databinding.ItemAvatarPickerBinding

/**
 * Grid of the app's 16 built-in avatar drawables (avatar_1.xml..avatar_16.xml),
 * shown in [AvatarPickerDialog]. These drawables previously had zero
 * references anywhere in the app - Profile only ever showed a generic
 * [R.drawable.ic_profile] placeholder icon with no way to change it.
 */
class AvatarPickerAdapter(
    private val avatarResIds: List<Int>,
    private var selectedIndex: Int,
    private val onSelected: (index: Int, resId: Int) -> Unit
) : RecyclerView.Adapter<AvatarPickerAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemAvatarPickerBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAvatarPickerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = avatarResIds.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val resId = avatarResIds[position]
        holder.binding.imageAvatarOption.setImageResource(resId)
        holder.binding.cardAvatarOption.strokeWidth = if (position == selectedIndex) {
            holder.binding.root.resources.getDimensionPixelSize(R.dimen.avatar_picker_selected_stroke)
        } else {
            0
        }
        holder.binding.cardAvatarOption.setOnClickListener {
            val previous = selectedIndex
            selectedIndex = position
            notifyItemChanged(previous)
            notifyItemChanged(position)
            onSelected(position, resId)
        }
    }
}
