package com.example.embeddedsystemscareerguide.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.embeddedsystemscareerguide.databinding.ItemChatMessageBinding

class ChatAdapter(
    private val messages: List<ChatFragment.ChatMessage>
) : RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    companion object {
        /**
         * Strip raw AI artifacts so the plain TextView looks clean:
         * - <think>...</think> reasoning blocks (Qwen3)
         * - Markdown headers (##, ###, etc.)
         * - Bold/italic markers (**, *, __)
         * - Inline code backticks
         * - Bullet-point markers (- at line start)
         * - Numbered list markers (1. at line start)
         * - Fenced code block markers (```)
         * - Excessive blank lines
         */
        fun cleanAiResponse(raw: String): String {
            var text = raw
            // 1. Strip <think>...</think> blocks (safety net)
            text = text.replace(Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE), "")
            // 2. Strip fenced code block markers but keep the code inside
            text = text.replace(Regex("```[a-zA-Z]*\\s*\n?"), "")
            // 3. Convert markdown headers to plain text (keep the text, remove #'s)
            text = text.replace(Regex("(?m)^#{1,6}\\s+"), "")
            // 4. Strip bold/italic markers (**, __, *, _) but keep the text
            text = text.replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
            text = text.replace(Regex("__(.+?)__"), "$1")
            text = text.replace(Regex("(?<!\\w)\\*(.+?)\\*(?!\\w)"), "$1")
            text = text.replace(Regex("(?<!\\w)_(.+?)_(?!\\w)"), "$1")
            // 5. Strip inline code backticks but keep text
            text = text.replace(Regex("`([^`]+)`"), "$1")
            // 6. Clean bullet markers: "- " at line start → "• "
            text = text.replace(Regex("(?m)^\\s*[-*]\\s+"), "• ")
            // 7. Collapse excessive blank lines to at most one
            text = text.replace(Regex("\n{3,}"), "\n\n")
            return text.trim()
        }
    }

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
                binding.textAiMessage.text = cleanAiResponse(message.content)
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
