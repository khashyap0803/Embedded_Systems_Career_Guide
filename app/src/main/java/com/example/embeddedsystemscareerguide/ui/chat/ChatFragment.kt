package com.example.embeddedsystemscareerguide.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.embeddedsystemscareerguide.databinding.FragmentChatBinding
import com.example.embeddedsystemscareerguide.services.GeminiChatService
import com.example.embeddedsystemscareerguide.services.InputSanitizer
import kotlinx.coroutines.launch

class ChatFragment : Fragment() {

    companion object {
        // H2 fix: Limit messages to prevent memory leak
        private const val MAX_MESSAGES = 100
    }

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var chatService: GeminiChatService
    private lateinit var chatAdapter: ChatAdapter
    private val messages = mutableListOf<ChatMessage>()

    data class ChatMessage(
        val content: String,
        val isUser: Boolean
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        chatService = GeminiChatService()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupInputHandlers()
        setupSuggestionChips()
        setupClearButton()
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(messages)
        binding.recyclerMessages.apply {
            layoutManager = LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true
            }
            adapter = chatAdapter
        }
    }

    private fun setupInputHandlers() {
        // Send button click
        binding.btnSend.setOnClickListener {
            sendMessage()
        }

        // Enter key to send
        binding.editMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }
    }

    private fun setupSuggestionChips() {
        binding.chipSuggestion1.setOnClickListener {
            binding.editMessage.setText(binding.chipSuggestion1.text)
            sendMessage()
        }
        binding.chipSuggestion2.setOnClickListener {
            binding.editMessage.setText(binding.chipSuggestion2.text)
            sendMessage()
        }
        binding.chipSuggestion3.setOnClickListener {
            binding.editMessage.setText(binding.chipSuggestion3.text)
            sendMessage()
        }
        binding.chipSuggestion4.setOnClickListener {
            binding.editMessage.setText(binding.chipSuggestion4.text)
            sendMessage()
        }
    }

    private fun setupClearButton() {
        binding.btnClearChat.setOnClickListener {
            messages.clear()
            chatAdapter.notifyDataSetChanged()
            chatService.clearHistory()
            binding.layoutSuggestions.visibility = View.VISIBLE
        }
    }

    private fun sendMessage() {
        val messageText = binding.editMessage.text?.toString()?.trim() ?: return
        if (messageText.isEmpty()) return
        
        // H6 fix: Sanitize user input before sending to API
        val sanitizedMessage = InputSanitizer.sanitizeForApi(messageText, maxLength = 2000)

        // Hide suggestions after first message
        binding.layoutSuggestions.visibility = View.GONE

        // H2 fix: Limit message count to prevent memory leak
        addMessageWithLimit(ChatMessage(messageText, true)) // Show original to user
        binding.recyclerMessages.scrollToPosition(messages.size - 1)
        binding.editMessage.text?.clear()

        // Show loading
        binding.progressLoading.visibility = View.VISIBLE
        binding.btnSend.isEnabled = false

        // Get AI response using sanitized message
        lifecycleScope.launch {
            try {
                val response = chatService.sendMessage(sanitizedMessage)
                
                // H2 fix: Add AI response with limit
                addMessageWithLimit(ChatMessage(response, false))
                binding.recyclerMessages.scrollToPosition(messages.size - 1)
                
            } catch (e: Exception) {
                // Add error message with limit
                addMessageWithLimit(ChatMessage("Sorry, I couldn't process that. Please try again! 🔄", false))
            } finally {
                binding.progressLoading.visibility = View.GONE
                binding.btnSend.isEnabled = true
            }
        }
    }

    /**
     * H2 fix: Add message with limit to prevent memory leak
     */
    private fun addMessageWithLimit(message: ChatMessage) {
        if (messages.size >= MAX_MESSAGES) {
            messages.removeAt(0)
            chatAdapter.notifyItemRemoved(0)
        }
        messages.add(message)
        chatAdapter.notifyItemInserted(messages.size - 1)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
