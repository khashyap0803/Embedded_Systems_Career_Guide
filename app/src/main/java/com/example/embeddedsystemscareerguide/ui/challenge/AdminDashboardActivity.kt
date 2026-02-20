package com.example.embeddedsystemscareerguide.ui.challenge

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.embeddedsystemscareerguide.R
import com.example.embeddedsystemscareerguide.databinding.ActivityAdminDashboardBinding
import com.example.embeddedsystemscareerguide.models.challenge.ParticipantStatus
import com.example.embeddedsystemscareerguide.services.PreReleaseEventService
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Admin Dashboard Activity
 * Manages event participants, toggles challenge access per-user, and views all evaluation data
 */
class AdminDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminDashboardBinding
    private lateinit var eventService: PreReleaseEventService
    private lateinit var auth: FirebaseAuth
    
    private lateinit var participantsAdapter: ParticipantsAdapter
    private var allParticipants: List<ParticipantStatus> = emptyList()
    private var currentFilter: String = "All"
    private var participantsJob: kotlinx.coroutines.Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        eventService = PreReleaseEventService.getInstance()
        auth = FirebaseAuth.getInstance()

        setupUI()
        observeParticipants()
        checkEventStatus()
    }

    private fun setupUI() {
        // Participants RecyclerView
        participantsAdapter = ParticipantsAdapter(
            onToggleCh2 = { participant, enabled -> toggleChallenge2(participant, enabled) },
            onToggleCh3 = { participant, enabled -> toggleChallenge3(participant, enabled) },
            onDelete = { participant -> confirmDelete(participant) },
            onResume = { participant -> resumeSession(participant) },
            onAddTime = { participant -> showAddExtraTimeDialog(participant) },
            onViewDetails = { participant -> viewSolutionDetails(participant) }
        )
        binding.rvParticipants.adapter = participantsAdapter
        binding.rvParticipants.layoutManager = LinearLayoutManager(this)
        
        // Export CSV button - Not available in current layout
        // TODO: Add btnExportCSV to layout if needed
        // binding.btnExportCSV?.setOnClickListener { exportParticipantsCSV() }
        
        // Tab filter
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentFilter = when (tab.position) {
                    0 -> "All"
                    1 -> "in_progress"
                    2 -> "completed"
                    3 -> "terminated"
                    else -> "All"
                }
                applyFilter()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        
        // Swipe to refresh
        binding.swipeRefresh.setOnRefreshListener {
            observeParticipants()
            // M-04: isRefreshing is now dismissed inside observeParticipants() callback
        }
        
        // Logout button
        binding.btnLogout.setOnClickListener {
            // Cancel participants observer BEFORE signing out to prevent Firebase permission error
            participantsJob?.cancel()
            auth.signOut()
            val intent = Intent(this, com.example.embeddedsystemscareerguide.ui.auth.LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finishAffinity()
        }
    }

    private fun checkEventStatus() {
        lifecycleScope.launch {
            val isActive = eventService.isEventActive()
            binding.tvEventStatus.text = if (isActive) "Event Active" else "Event Inactive"
        }
    }

    private fun observeParticipants() {
        binding.loadingOverlay.visibility = View.VISIBLE
        
        // Cancel any existing observer before starting a new one
        participantsJob?.cancel()
        participantsJob = lifecycleScope.launch {
            eventService.observeAllParticipants().collectLatest { participants ->
                binding.loadingOverlay.visibility = View.GONE
                // M-04: Dismiss refresh spinner after data arrives
                binding.swipeRefresh.isRefreshing = false
                allParticipants = participants
                updateStats(participants)
                applyFilter()
            }
        }
    }

    private fun updateStats(participants: List<ParticipantStatus>) {
        val inProgress = participants.count { it.currentStatus == "in_progress" }
        val completed = participants.count { it.currentStatus == "completed" }
        val terminated = participants.count { it.isTerminated }
        
        binding.tvActiveCount.text = inProgress.toString()
        binding.tvCompletedCount.text = completed.toString()
        binding.tvTerminatedCount.text = terminated.toString()
    }

    private fun applyFilter() {
        val filtered = when (currentFilter) {
            "in_progress" -> allParticipants.filter { it.currentStatus == "in_progress" }
            "completed" -> allParticipants.filter { it.currentStatus == "completed" && !it.isTerminated }
            "terminated" -> allParticipants.filter { it.isTerminated }
            else -> allParticipants
        }
        participantsAdapter.updateData(filtered)
    }

    private fun toggleChallenge2(participant: ParticipantStatus, enabled: Boolean) {
        if (participant.isTerminated) {
            Toast.makeText(this, "Cannot unlock for terminated participant", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            val success = eventService.toggleChallengeAccess(participant.rollNumber, 2, enabled)
            if (success) {
                Toast.makeText(
                    this@AdminDashboardActivity,
                    "Challenge 2 ${if (enabled) "enabled" else "disabled"} for ${participant.rollNumber}",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(this@AdminDashboardActivity, "Failed to update", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleChallenge3(participant: ParticipantStatus, enabled: Boolean) {
        if (participant.isTerminated) {
            Toast.makeText(this, "Cannot unlock for terminated participant", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            val success = eventService.toggleChallengeAccess(participant.rollNumber, 3, enabled)
            if (success) {
                Toast.makeText(
                    this@AdminDashboardActivity,
                    "Challenge 3 ${if (enabled) "enabled" else "disabled"} for ${participant.rollNumber}",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(this@AdminDashboardActivity, "Failed to update", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmDelete(participant: ParticipantStatus) {
        AlertDialog.Builder(this)
            .setTitle("Delete Participant")
            .setMessage("Are you sure you want to delete ${participant.rollNumber}? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteParticipant(participant)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteParticipant(participant: ParticipantStatus) {
        lifecycleScope.launch {
            val success = eventService.deleteParticipant(participant.rollNumber)
            if (success) {
                Toast.makeText(
                    this@AdminDashboardActivity,
                    "Deleted ${participant.rollNumber}",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(this@AdminDashboardActivity, "Failed to delete", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ========== GAP 5: RESUME SESSION (for terminated OR timed-out users) ==========
    private fun resumeSession(participant: ParticipantStatus) {
        val isTimedOut = participant.currentStatus == ParticipantStatus.STATUS_TIMEOUT
        
        if (!participant.isTerminated && !isTimedOut) {
            Toast.makeText(this, "Participant is not terminated or timed out", Toast.LENGTH_SHORT).show()
            return
        }

        // For timed-out users, show Add Extra Time dialog first
        if (isTimedOut) {
            showAddExtraTimeDialog(participant)
            return
        }
        
        // For terminated users, show regular resume dialog
        AlertDialog.Builder(this)
            .setTitle("🔄 Resume Session")
            .setMessage("Resume session for ${participant.rollNumber}?\n\nThis user was terminated.\n\nThis will:\n• Clear termination status\n• Reset warning count\n• Allow them to continue from where they left off")
            .setPositiveButton("Resume") { _, _ ->
                performResumeSession(participant)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performResumeSession(participant: ParticipantStatus) {
        lifecycleScope.launch {
            val success = eventService.resumeParticipantSession(participant.rollNumber)
            if (success) {
                Toast.makeText(
                    this@AdminDashboardActivity,
                    "✅ Session resumed for ${participant.rollNumber}",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    this@AdminDashboardActivity,
                    "❌ Failed to resume session",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // ========== GAP 6: ADD EXTRA TIME ==========
    private fun showAddExtraTimeDialog(participant: ParticipantStatus) {
        val options = arrayOf("5 minutes", "10 minutes", "15 minutes", "20 minutes", "Custom...")
        val timeValues = arrayOf(5, 10, 15, 20, -1)

        AlertDialog.Builder(this)
            .setTitle("⏰ Add Extra Time for ${participant.rollNumber}")
            .setItems(options) { _, which ->
                val minutes = timeValues[which]
                if (minutes == -1) {
                    showCustomTimeDialog(participant)
                } else {
                    addExtraTime(participant, minutes)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCustomTimeDialog(participant: ParticipantStatus) {
        val input = android.widget.EditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        input.hint = "Enter minutes"
        input.setPadding(48, 24, 48, 24)

        AlertDialog.Builder(this)
            .setTitle("Custom Time")
            .setMessage("Enter minutes to add:")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val minutes = input.text.toString().toIntOrNull() ?: 0
                if (minutes > 0 && minutes <= 60) {
                    addExtraTime(participant, minutes)
                } else {
                    Toast.makeText(this, "Please enter 1-60 minutes", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addExtraTime(participant: ParticipantStatus, minutes: Int) {
        lifecycleScope.launch {
            val success = eventService.addExtraTime(participant.rollNumber, minutes)
            if (success) {
                Toast.makeText(
                    this@AdminDashboardActivity,
                    "✅ Added $minutes minutes to ${participant.rollNumber}",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    this@AdminDashboardActivity,
                    "❌ Failed to add extra time",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // ========== GAP 8: VIEW SOLUTION DETAILS ==========
    private fun viewSolutionDetails(participant: ParticipantStatus) {
        lifecycleScope.launch {
            val details = eventService.getParticipantDetails(participant.rollNumber)
            
            val message = buildString {
                appendLine("📊 EVALUATION DETAILS")
                appendLine("━━━━━━━━━━━━━━━━━━━━━")
                appendLine()
                appendLine("Roll Number: ${participant.rollNumber}")
                appendLine("Status: ${participant.currentStatus}")
                appendLine("Total Score: ${participant.totalScore}")
                appendLine()
                appendLine("Challenge 1: ${details?.challenge1Score ?: 0}/100")
                appendLine("Challenge 2: ${details?.challenge2Score ?: 0}/100")
                appendLine("Challenge 3: ${details?.challenge3Score ?: 0}/100")
                appendLine()
                appendLine("Warnings: ${participant.warningCount}")
                if (participant.isTerminated) {
                    appendLine("Termination: ${participant.terminationReason}")
                }
            }
            
            AlertDialog.Builder(this@AdminDashboardActivity)
                .setTitle("📋 ${participant.rollNumber}")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show()
        }
    }

    // ========== GAP 8: EXPORT CSV ==========
    private fun exportParticipantsCSV() {
        val csvContent = buildString {
            appendLine("Roll Number,Total Score,Status,Challenge1,Challenge2,Challenge3,Warnings,Terminated")
            allParticipants.forEach { p ->
                appendLine("${p.rollNumber},${p.totalScore},${p.currentStatus},${p.challenge1Score},${p.challenge2Score},${p.challenge3Score},${p.warningCount},${p.isTerminated}")
            }
        }
        
        // Copy to clipboard
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Participants CSV", csvContent)
        clipboard.setPrimaryClip(clip)
        
        Toast.makeText(this, "📋 CSV copied to clipboard (${allParticipants.size} participants)", Toast.LENGTH_LONG).show()
    }
}

// ============== ADAPTER ==============

class ParticipantsAdapter(
    private val onToggleCh2: (ParticipantStatus, Boolean) -> Unit,
    private val onToggleCh3: (ParticipantStatus, Boolean) -> Unit,
    private val onDelete: (ParticipantStatus) -> Unit,
    private val onResume: (ParticipantStatus) -> Unit,
    private val onAddTime: (ParticipantStatus) -> Unit,
    private val onViewDetails: (ParticipantStatus) -> Unit
) : RecyclerView.Adapter<ParticipantsAdapter.ViewHolder>() {
    
    private var participants: List<ParticipantStatus> = emptyList()
    
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView.findViewById(R.id.cardParticipant)
        val statusIndicator: View = itemView.findViewById(R.id.viewStatus)
        val rollNumber: TextView = itemView.findViewById(R.id.tvRollNumber)
        val score: TextView = itemView.findViewById(R.id.tvScore)
        val statusText: TextView = itemView.findViewById(R.id.tvStatusText)
        val chipCh2: Chip = itemView.findViewById(R.id.chipCh2)
        val chipCh3: Chip = itemView.findViewById(R.id.chipCh3)
        val btnDelete: ImageView = itemView.findViewById(R.id.btnDelete)
        val layoutTerminated: LinearLayout = itemView.findViewById(R.id.layoutTerminated)
        val terminationReason: TextView = itemView.findViewById(R.id.tvTerminationReason)
        val btnResume: com.google.android.material.button.MaterialButton = itemView.findViewById(R.id.btnResume)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_participant_admin, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val participant = participants[position]
        
        holder.rollNumber.text = participant.rollNumber
        holder.score.text = "${participant.totalScore} pts"
        
        // Status indicator color
        val statusColor = when {
            participant.isTerminated -> android.R.color.holo_red_dark
            participant.currentStatus == ParticipantStatus.STATUS_TIMEOUT -> android.R.color.holo_orange_dark
            participant.currentStatus == "completed" -> android.R.color.holo_green_dark
            participant.currentStatus == "in_progress" -> android.R.color.holo_orange_light
            else -> android.R.color.darker_gray
        }
        holder.statusIndicator.setBackgroundColor(holder.itemView.context.getColor(statusColor))
        
        // Status text
        holder.statusText.text = when {
            participant.isTerminated -> "❌ Terminated"
            participant.currentStatus == ParticipantStatus.STATUS_TIMEOUT -> "⏰ Timed Out"
            participant.currentStatus == "completed" -> "✅ Waiting for next challenge"
            participant.currentStatus == "in_progress" -> "🔄 In Progress"
            participant.currentStatus == ParticipantStatus.STATUS_RESUMABLE -> "🔄 Resumable"
            else -> "Not started"
        }
        
        // Challenge toggles - removed listener first to avoid triggering while setting
        holder.chipCh2.setOnCheckedChangeListener(null)
        holder.chipCh3.setOnCheckedChangeListener(null)
        
        holder.chipCh2.isChecked = participant.canAccessChallenge2
        holder.chipCh3.isChecked = participant.canAccessChallenge3
        
        holder.chipCh2.isEnabled = !participant.isTerminated
        holder.chipCh3.isEnabled = !participant.isTerminated
        
        holder.chipCh2.setOnCheckedChangeListener { _, isChecked ->
            onToggleCh2(participant, isChecked)
        }
        holder.chipCh3.setOnCheckedChangeListener { _, isChecked ->
            onToggleCh3(participant, isChecked)
        }
        
        // Delete button
        holder.btnDelete.setOnClickListener {
            onDelete(participant)
        }
        
        // Termination/Timeout info - show for both terminated AND timed-out users
        val isTimedOutForLayout = participant.currentStatus == ParticipantStatus.STATUS_TIMEOUT
        if (participant.isTerminated || isTimedOutForLayout) {
            holder.layoutTerminated.visibility = View.VISIBLE
            holder.terminationReason.text = when {
                participant.isTerminated -> "🚫 Terminated: ${participant.terminationReason ?: "Unknown"}"
                isTimedOutForLayout -> "⏰ Timed Out - Needs Extra Time"
                else -> "Status: ${participant.currentStatus}"
            }
            holder.btnResume.setOnClickListener {
                onResume(participant)
            }
        } else {
            holder.layoutTerminated.visibility = View.GONE
        }
        
        // New action buttons - Resume (only for terminated), Add Time, View Details
        holder.card.setOnClickListener {
            onViewDetails(participant)
        }
        
        holder.card.setOnLongClickListener {
            // Long press shows action menu
            val popup = android.widget.PopupMenu(holder.itemView.context, holder.card)
            popup.menu.add("View Details").setOnMenuItemClickListener { onViewDetails(participant); true }
            
            // Add Extra Time - ONLY for timed-out users (not completed/submitted users)
            val isTimedOut = participant.currentStatus == ParticipantStatus.STATUS_TIMEOUT
            if (isTimedOut) {
                popup.menu.add("⏰ Add Extra Time").setOnMenuItemClickListener { onAddTime(participant); true }
            }
            
            // Resume Session - for terminated OR timed-out users
            if (participant.isTerminated || isTimedOut) {
                popup.menu.add("🔄 Resume Session").setOnMenuItemClickListener { onResume(participant); true }
            }
            
            popup.menu.add("🗑️ Delete").setOnMenuItemClickListener { onDelete(participant); true }
            popup.show()
            true
        }
    }
    
    override fun getItemCount() = participants.size
    
    fun updateData(newParticipants: List<ParticipantStatus>) {
        participants = newParticipants.sortedByDescending { it.totalScore }
        notifyDataSetChanged()
    }
}
