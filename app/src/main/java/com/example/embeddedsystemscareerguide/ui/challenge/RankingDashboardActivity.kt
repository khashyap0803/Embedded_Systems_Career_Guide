package com.example.embeddedsystemscareerguide.ui.challenge

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.embeddedsystemscareerguide.R
import com.example.embeddedsystemscareerguide.databinding.ActivityRankingDashboardBinding
import com.example.embeddedsystemscareerguide.models.challenge.ChallengeConstants
import com.example.embeddedsystemscareerguide.models.challenge.ParticipantStatus
import com.example.embeddedsystemscareerguide.models.challenge.RankingEntry
import com.example.embeddedsystemscareerguide.services.PreReleaseEventService
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Ranking Dashboard Activity
 * Shows universal leaderboard with real-time updates
 * Displays challenge unlock notifications via Firebase listener
 */
class RankingDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRankingDashboardBinding
    private lateinit var eventService: PreReleaseEventService
    private lateinit var auth: FirebaseAuth
    
    private var rollNumber: String = ""
    private var rankingsObserverJob: kotlinx.coroutines.Job? = null
    private var statusObserverJob: kotlinx.coroutines.Job? = null
    
    private lateinit var rankingsAdapter: RankingsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRankingDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        eventService = PreReleaseEventService.getInstance()
        auth = FirebaseAuth.getInstance()
        rollNumber = intent.getStringExtra(RollNumberEntryActivity.EXTRA_ROLL_NUMBER) ?: ""

        setupUI()
        setupRankingsObserver()
        setupStatusObserver()
        loadMyScore()
    }

    private fun setupUI() {
        binding.tvRollNumber.text = "Roll: $rollNumber"
        
        // Rankings RecyclerView
        rankingsAdapter = RankingsAdapter(rollNumber)
        binding.rvRankings.adapter = rankingsAdapter
        binding.rvRankings.layoutManager = LinearLayoutManager(this)
        
        // Swipe to refresh
        binding.swipeRefresh.setOnRefreshListener {
            loadMyScore()
            // M-04: isRefreshing is now dismissed inside loadMyScore() callback
        }
        
        // Logout button
        binding.btnLogout.setOnClickListener {
            // Cancel Firebase observers BEFORE signing out to prevent permission errors
            rankingsObserverJob?.cancel()
            statusObserverJob?.cancel()
            auth.signOut()
            val intent = Intent(this, com.example.embeddedsystemscareerguide.ui.auth.LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finishAffinity()
        }
        
        // Next challenge button (in banner)
        binding.btnStartNextChallenge.setOnClickListener {
            navigateToNextChallenge()
        }
    }

    private fun setupRankingsObserver() {
        rankingsObserverJob = lifecycleScope.launch {
            eventService.observeUniversalRankings().collectLatest { rankings ->
                rankingsAdapter.updateData(rankings)
                binding.tvParticipantCount.text = "${rankings.size} participants"
                
                // Update my rank from rankings
                val myRanking = rankings.find { it.rollNumber == rollNumber }
                myRanking?.let { updateMyScoreFromRanking(it) }
            }
        }
    }

    private fun setupStatusObserver() {
        // Listen for challenge unlock notifications
        statusObserverJob = lifecycleScope.launch {
            eventService.observeParticipantStatus(rollNumber).collectLatest { status ->
                status?.let { handleStatusUpdate(it) }
            }
        }
    }

    private fun handleStatusUpdate(status: ParticipantStatus) {
        // Check for resumable status first (extra time granted)
        if (status.currentStatus == ParticipantStatus.STATUS_RESUMABLE) {
            lifecycleScope.launch {
                val extraTimeInfo = eventService.getExtraTimeInfo(rollNumber)
                if (extraTimeInfo != null) {
                    val (challengeNumber, extraTimeMs) = extraTimeInfo
                    val extraMinutes = extraTimeMs / 60000
                    showExtraTimeGrantedDialog(challengeNumber, extraMinutes.toInt())
                }
            }
            return
        }
        
        // Check if new challenge is available using Firebase completion status
        lifecycleScope.launch {
            when {
                status.canAccessChallenge3 -> {
                    val ch3Complete = eventService.isChallengeCompleted(rollNumber, 3)
                    if (!ch3Complete) {
                        showChallengeBanner("Challenge 3 is now available!", 3)
                    } else {
                        binding.challengeBanner.visibility = View.GONE
                    }
                }
                status.canAccessChallenge2 -> {
                    val ch2Complete = eventService.isChallengeCompleted(rollNumber, 2)
                    if (!ch2Complete) {
                        showChallengeBanner("Challenge 2 is now available!", 2)
                    } else {
                        binding.challengeBanner.visibility = View.GONE
                    }
                }
                else -> {
                    binding.challengeBanner.visibility = View.GONE
                }
            }
        }
    }
    
    private fun showExtraTimeGrantedDialog(challengeNumber: Int, extraMinutes: Int) {
        android.app.AlertDialog.Builder(this)
            .setTitle("⏰ Extra Time Granted!")
            .setMessage("Admin has granted you $extraMinutes extra minutes for Challenge $challengeNumber.\n\nYou can now continue from where you left off.")
            .setPositiveButton("Continue Challenge $challengeNumber") { _, _ ->
                navigateToChallengeResume(challengeNumber)
            }
            .setNegativeButton("Later", null)
            .setCancelable(false)
            .show()
    }
    
    private fun navigateToChallengeResume(challengeNumber: Int) {
        val intent = when (challengeNumber) {
            1 -> Intent(this, Challenge1Activity::class.java)
            2 -> Intent(this, Challenge2Activity::class.java)
            3 -> Intent(this, Challenge3Activity::class.java)
            else -> return
        }
        intent.putExtra(RollNumberEntryActivity.EXTRA_ROLL_NUMBER, rollNumber)
        intent.putExtra(RollNumberEntryActivity.EXTRA_IS_RESUME, true)
        startActivity(intent)
    }

    private fun showChallengeBanner(message: String, challengeNumber: Int) {
        binding.challengeBanner.visibility = View.VISIBLE
        binding.tvBannerMessage.text = message
        binding.btnStartNextChallenge.tag = challengeNumber
    }

    private fun navigateToNextChallenge() {
        val challengeNumber = binding.btnStartNextChallenge.tag as? Int ?: return
        
        val intent = when (challengeNumber) {
            2 -> Intent(this, Challenge2Activity::class.java)
            3 -> Intent(this, Challenge3Activity::class.java)
            else -> return
        }
        
        intent.putExtra(RollNumberEntryActivity.EXTRA_ROLL_NUMBER, rollNumber)
        startActivity(intent)
        finish()
    }

    private fun loadMyScore() {
        binding.loadingOverlay.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            // Get participant's ranking data
            val status = eventService.getParticipantStatus(rollNumber)
            
            status?.let { handleStatusUpdate(it) }
            
            binding.loadingOverlay.visibility = View.GONE
            // M-04: Dismiss refresh spinner after data arrives
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun updateMyScoreFromRanking(ranking: RankingEntry) {
        val rank = rankingsAdapter.getRankForRollNumber(rollNumber)
        
        binding.tvMyRank.text = "#$rank"
        binding.tvMyScore.text = ranking.totalScore.toString()
        binding.tvMyPercentage.text = "${(ranking.totalScore.toDouble() / ChallengeConstants.MAX_TOTAL_SCORE * 100).toInt()}%"
        
        binding.tvCh1Score.text = "Ch.1: ${if (ranking.ch1 > 0) ranking.ch1 else "--"}"
        binding.tvCh2Score.text = "Ch.2: ${if (ranking.ch2 > 0) ranking.ch2 else "--"}"
        binding.tvCh3Score.text = "Ch.3: ${if (ranking.ch3 > 0) ranking.ch3 else "--"}"
    }
}

// ============== ADAPTER ==============

class RankingsAdapter(
    private val currentUserRollNumber: String
) : RecyclerView.Adapter<RankingsAdapter.ViewHolder>() {
    
    private var rankings: List<RankingEntry> = emptyList()
    
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView.findViewById(R.id.cardRanking)
        val rank: TextView = itemView.findViewById(R.id.tvRank)
        val trophy: ImageView = itemView.findViewById(R.id.ivTrophy)
        val rollNumber: TextView = itemView.findViewById(R.id.tvRollNumber)
        val totalScore: TextView = itemView.findViewById(R.id.tvTotalScore)
        val ch1: TextView = itemView.findViewById(R.id.tvCh1)
        val ch2: TextView = itemView.findViewById(R.id.tvCh2)
        val ch3: TextView = itemView.findViewById(R.id.tvCh3)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ranking_entry, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = rankings[position]
        val rank = position + 1
        
        holder.rank.text = "#$rank"
        holder.rollNumber.text = entry.rollNumber
        holder.totalScore.text = "${entry.totalScore} pts"
        
        // Always show numeric scores (0 instead of --)
        holder.ch1.text = entry.ch1.toString()
        holder.ch2.text = entry.ch2.toString()
        holder.ch3.text = entry.ch3.toString()
        
        // Trophy for top 3
        if (rank <= 3) {
            holder.trophy.visibility = View.VISIBLE
            holder.rank.visibility = View.GONE
            when (rank) {
                1 -> holder.trophy.setColorFilter(holder.itemView.context.getColor(android.R.color.holo_orange_light))
                2 -> holder.trophy.setColorFilter(holder.itemView.context.getColor(android.R.color.darker_gray))
                3 -> holder.trophy.setColorFilter(holder.itemView.context.getColor(android.R.color.holo_orange_dark))
            }
        } else {
            holder.trophy.visibility = View.GONE
            holder.rank.visibility = View.VISIBLE
        }
        
        // Highlight current user
        if (entry.rollNumber == currentUserRollNumber) {
            holder.card.strokeWidth = 4
            holder.card.strokeColor = holder.itemView.context.getColor(R.color.indigo_600)
        } else {
            holder.card.strokeWidth = 0
        }
    }
    
    override fun getItemCount() = rankings.size
    
    fun updateData(newRankings: List<RankingEntry>) {
        // L-07: Removed client-side sort — rankings are already sorted by updateUniversalRankings()
        rankings = newRankings
        notifyDataSetChanged()
    }
    
    fun getRankForRollNumber(rollNumber: String): Int {
        return rankings.indexOfFirst { it.rollNumber == rollNumber } + 1
    }
}
