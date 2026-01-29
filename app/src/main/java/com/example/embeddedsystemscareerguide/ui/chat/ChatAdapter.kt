package com.example.embeddedsystemscareerguide.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.embeddedsystemscareerguide.databinding.ItemChatMessageBinding

class ChatAdapter(
    private val messages: List<ChatFragment.ChatMessage>
) : RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    inner class MessageViewHolder(
        private val binding: ItemChatMessageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: ChatFragment.ChatMessage) {
            if (message.isUser) {
                // Show user message (right side)
                binding.cardUserMessage.visibility = View.VISIBLE
                binding.layoutAiMessage.visibility = View.GONE
                binding.textUserMessage.text = message.content
            } else {
                // Show AI message (left side)
                binding.cardUserMessage.visibility = View.GONE
                binding.layoutAiMessage.visibility = View.VISIBLE
                binding.textAiMessage.text = message.content
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemChatMessageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount(): Int = messages.size
}
