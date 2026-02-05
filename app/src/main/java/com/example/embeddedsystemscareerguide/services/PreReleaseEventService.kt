package com.example.embeddedsystemscareerguide.services

import android.util.Log
import com.example.embeddedsystemscareerguide.models.challenge.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Firebase Realtime Database Service for Pre-Release Event Challenge
 * Handles all database operations for the challenge system
 */
class PreReleaseEventService private constructor() {
    
    companion object {
        private const val TAG = "PreReleaseEventService"
        
        @Volatile
        private var INSTANCE: PreReleaseEventService? = null
        
        fun getInstance(): PreReleaseEventService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PreReleaseEventService().also { INSTANCE = it }
            }
        }
    }
    
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    
    // ============== REFERENCE GETTERS ==============
    
    private fun getEventRef(): DatabaseReference = 
        database.getReference(ChallengeConstants.PATH_PRE_RELEASE_EVENT)
    
    private fun getConfigRef(): DatabaseReference = 
        getEventRef().child(ChallengeConstants.PATH_CONFIG)
    
    private fun getParticipantsRef(): DatabaseReference = 
        getEventRef().child(ChallengeConstants.PATH_PARTICIPANTS)
    
    fun getParticipantRef(rollNumber: String): DatabaseReference = 
        getParticipantsRef().child(rollNumber)
    
    private fun getRankingsRef(): DatabaseReference = 
        getEventRef().child(ChallengeConstants.PATH_RANKINGS).child(ChallengeConstants.PATH_UNIVERSAL)
    
    // ============== CONFIG OPERATIONS ==============
    
    suspend fun getEventConfig(): ChallengeConfig? = suspendCancellableCoroutine { cont ->
        getConfigRef().get()
            .addOnSuccessListener { snapshot ->
                val config = snapshot.getValue(ChallengeConfig::class.java)
                cont.resume(config)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to get event config", e)
                cont.resume(null)
            }
    }
    
    suspend fun isEventActive(): Boolean {
        return getEventConfig()?.eventActive ?: false
    }
    
    // ============== PARTICIPANT OPERATIONS ==============
    
    suspend fun registerParticipant(rollNumber: String): Boolean = suspendCancellableCoroutine { cont ->
        val participantRef = getParticipantRef(rollNumber)
        
        // Check if already exists
        participantRef.get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    // Already registered
                    cont.resume(true)
                } else {
                    // Create new participant
                    val now = System.currentTimeMillis()
                    val updates = mapOf(
                        "profile/rollNumber" to rollNumber,
                        "profile/registeredAt" to now,
                        "profile/lastActiveAt" to now,
                        "status/currentStatus" to ParticipantStatus.STATUS_WAITING,
                        "status/warningCount" to 0,
                        "status/canAccessChallenge2" to false,
                        "status/canAccessChallenge3" to false,
                        "status/isTerminated" to false,
                        "universalRanking/challenge1Score" to 0,
                        "universalRanking/challenge2Score" to 0,
                        "universalRanking/challenge3Score" to 0,
                        "universalRanking/totalWeightedScore" to 0,
                        "universalRanking/maxPossibleScore" to ChallengeConstants.MAX_TOTAL_SCORE,
                        "universalRanking/percentage" to 0.0,
                        "universalRanking/rank" to 0
                    )
                    
                    participantRef.updateChildren(updates)
                        .addOnSuccessListener { cont.resume(true) }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Failed to register participant", e)
                            cont.resume(false)
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to check participant", e)
                cont.resume(false)
            }
    }
    
    suspend fun getParticipantStatus(rollNumber: String): ParticipantStatus? = suspendCancellableCoroutine { cont ->
        // Fetch the entire participant to get both status and ranking data
        getParticipantRef(rollNumber).get()
            .addOnSuccessListener { snapshot ->
                val statusSnapshot = snapshot.child("status")
                val status = statusSnapshot.getValue(ParticipantStatus::class.java)
                
                if (status != null) {
                    // Also fetch challenge scores from universalRanking
                    val ranking = snapshot.child("universalRanking")
                    status.rollNumber = rollNumber
                    status.challenge1Score = ranking.child("challenge1Score").getValue(Int::class.java) ?: 0
                    status.challenge2Score = ranking.child("challenge2Score").getValue(Int::class.java) ?: 0
                    status.challenge3Score = ranking.child("challenge3Score").getValue(Int::class.java) ?: 0
                    status.totalScore = ranking.child("totalWeightedScore").getValue(Int::class.java) ?: 0
                }
                
                cont.resume(status)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to get participant status", e)
                cont.resume(null)
            }
    }
    
    suspend fun updateParticipantStatus(rollNumber: String, status: String): Boolean = suspendCancellableCoroutine { cont ->
        getParticipantRef(rollNumber).child("status/currentStatus").setValue(status)
            .addOnSuccessListener { cont.resume(true) }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to update status", e)
                cont.resume(false)
            }
    }
    
    suspend fun isChallengeCompleted(rollNumber: String, challengeNumber: Int): Boolean = suspendCancellableCoroutine { cont ->
        val challengeKey = "challenge$challengeNumber"
        getParticipantRef(rollNumber).child("$challengeKey/status").get()
            .addOnSuccessListener { snapshot ->
                val status = snapshot.getValue(String::class.java)
                cont.resume(status == "completed")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to check challenge $challengeNumber status", e)
                cont.resume(false)
            }
    }
    
    suspend fun updateLastActive(rollNumber: String) {
        getParticipantRef(rollNumber).child("profile/lastActiveAt").setValue(System.currentTimeMillis())
    }
    
    // ============== CHALLENGE 1 OPERATIONS ==============
    
    suspend fun startChallenge1(rollNumber: String): Boolean = suspendCancellableCoroutine { cont ->
        val now = System.currentTimeMillis()
        val updates = mapOf(
            "status/currentStatus" to ParticipantStatus.STATUS_IN_PROGRESS,
            "challenge1/status" to "in_progress",
            "challenge1/startTime" to now
        )
        
        getParticipantRef(rollNumber).updateChildren(updates)
            .addOnSuccessListener { cont.resume(true) }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to start Challenge 1", e)
                cont.resume(false)
            }
    }
    
    suspend fun submitChallenge1(
        rollNumber: String,
        submission: Challenge1Submission,
        evaluation: EvaluationResult
    ): Boolean = suspendCancellableCoroutine { cont ->
        val participantRef = getParticipantRef(rollNumber)
        val now = System.currentTimeMillis()
        
        // Get start time first to calculate duration
        participantRef.child("challenge1/startTime").get()
            .addOnSuccessListener { snapshot ->
                val startTime = snapshot.getValue(Long::class.java) ?: now
                val timeTaken = now - startTime
                
                val weightedScore = (evaluation.totalScore * ChallengeConstants.CHALLENGE_1_WEIGHT).toInt()
                
                val updates = mapOf(
                    "challenge1/status" to "completed",
                    "challenge1/endTime" to now,
                    "challenge1/timeTakenMs" to timeTaken,
                    "challenge1/submission" to submission,
                    "challenge1/evaluation" to evaluation,
                    "challenge1/scores/rawScore" to evaluation.totalScore,
                    "challenge1/scores/weightedScore" to weightedScore,
                    "status/currentStatus" to ParticipantStatus.STATUS_COMPLETED,
                    "universalRanking/challenge1Score" to weightedScore,
                    "universalRanking/totalWeightedScore" to weightedScore,
                    "universalRanking/totalTimeTakenMs" to timeTaken,
                    "universalRanking/lastUpdatedAt" to now
                )
                
                participantRef.updateChildren(updates)
                    .addOnSuccessListener { 
                        // Update rankings
                        updateUniversalRankings()
                        cont.resume(true) 
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to submit Challenge 1", e)
                        cont.resume(false)
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to get start time", e)
                cont.resume(false)
            }
    }
    
    // ============== CHALLENGE 2 OPERATIONS ==============
    
    suspend fun startChallenge2(rollNumber: String): Boolean = suspendCancellableCoroutine { cont ->
        val now = System.currentTimeMillis()
        val updates = mapOf(
            "status/currentStatus" to ParticipantStatus.STATUS_IN_PROGRESS,
            "challenge2/status" to "in_progress",
            "challenge2/startTime" to now
        )
        
        getParticipantRef(rollNumber).updateChildren(updates)
            .addOnSuccessListener { cont.resume(true) }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to start Challenge 2", e)
                cont.resume(false)
            }
    }
    
    suspend fun submitChallenge2(
        rollNumber: String,
        questions: List<Challenge2QuestionInternal>,
        evaluation: EvaluationResult
    ): Boolean = suspendCancellableCoroutine { cont ->
        val participantRef = getParticipantRef(rollNumber)
        val now = System.currentTimeMillis()
        
        participantRef.child("challenge2/startTime").get()
            .addOnSuccessListener { snapshot ->
                val startTime = snapshot.getValue(Long::class.java) ?: now
                val timeTaken = now - startTime
                
                val weightedScore = (evaluation.totalScore * ChallengeConstants.CHALLENGE_2_WEIGHT).toInt()
                
                // Build questions map
                val questionsMap = questions.mapIndexed { index, q -> "q${index + 1}" to q }.toMap()
                
                // Get current Ch1 score for total calculation
                participantRef.child("universalRanking/challenge1Score").get()
                    .addOnSuccessListener { ch1Snapshot ->
                        val ch1Score = ch1Snapshot.getValue(Int::class.java) ?: 0
                        val ch1Time = participantRef.child("challenge1/timeTakenMs")
                        
                        val updates = mapOf(
                            "challenge2/status" to "completed",
                            "challenge2/endTime" to now,
                            "challenge2/timeTakenMs" to timeTaken,
                            "challenge2/questions" to questionsMap,
                            "challenge2/evaluation" to evaluation,
                            "challenge2/scores/rawScore" to evaluation.totalScore,
                            "challenge2/scores/weightedScore" to weightedScore,
                            "status/currentStatus" to ParticipantStatus.STATUS_COMPLETED,
                            "universalRanking/challenge2Score" to weightedScore,
                            "universalRanking/totalWeightedScore" to (ch1Score + weightedScore),
                            "universalRanking/lastUpdatedAt" to now
                        )
                        
                        participantRef.updateChildren(updates)
                            .addOnSuccessListener { 
                                updateUniversalRankings()
                                cont.resume(true) 
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Failed to submit Challenge 2", e)
                                cont.resume(false)
                            }
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to get start time", e)
                cont.resume(false)
            }
    }
    
    // ============== CHALLENGE 3 OPERATIONS ==============
    
    suspend fun startChallenge3(rollNumber: String): Boolean = suspendCancellableCoroutine { cont ->
        val now = System.currentTimeMillis()
        val updates = mapOf(
            "status/currentStatus" to ParticipantStatus.STATUS_IN_PROGRESS,
            "challenge3/status" to "in_progress",
            "challenge3/startTime" to now
        )
        
        getParticipantRef(rollNumber).updateChildren(updates)
            .addOnSuccessListener { cont.resume(true) }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to start Challenge 3", e)
                cont.resume(false)
            }
    }
    
    suspend fun submitChallenge3(
        rollNumber: String,
        questions: List<Challenge3QuestionInternal>,
        evaluation: EvaluationResult
    ): Boolean = suspendCancellableCoroutine { cont ->
        val participantRef = getParticipantRef(rollNumber)
        val now = System.currentTimeMillis()
        
        participantRef.child("challenge3/startTime").get()
            .addOnSuccessListener { snapshot ->
                val startTime = snapshot.getValue(Long::class.java) ?: now
                val timeTaken = now - startTime
                
                val weightedScore = (evaluation.totalScore * ChallengeConstants.CHALLENGE_3_WEIGHT).toInt()
                
                val questionsMap = questions.mapIndexed { index, q -> "q${index + 1}" to q }.toMap()
                
                // Get current scores for total calculation
                participantRef.child("universalRanking").get()
                    .addOnSuccessListener { rankingSnapshot ->
                        val ch1Score = rankingSnapshot.child("challenge1Score").getValue(Int::class.java) ?: 0
                        val ch2Score = rankingSnapshot.child("challenge2Score").getValue(Int::class.java) ?: 0
                        val totalScore = ch1Score + ch2Score + weightedScore
                        
                        val updates = mapOf(
                            "challenge3/status" to "completed",
                            "challenge3/endTime" to now,
                            "challenge3/timeTakenMs" to timeTaken,
                            "challenge3/questions" to questionsMap,
                            "challenge3/evaluation" to evaluation,
                            "challenge3/scores/rawScore" to evaluation.totalScore,
                            "challenge3/scores/weightedScore" to weightedScore,
                            "status/currentStatus" to ParticipantStatus.STATUS_COMPLETED,
                            "universalRanking/challenge3Score" to weightedScore,
                            "universalRanking/totalWeightedScore" to totalScore,
                            "universalRanking/percentage" to (totalScore.toDouble() / ChallengeConstants.MAX_TOTAL_SCORE * 100),
                            "universalRanking/lastUpdatedAt" to now
                        )
                        
                        participantRef.updateChildren(updates)
                            .addOnSuccessListener { 
                                updateUniversalRankings()
                                cont.resume(true) 
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Failed to submit Challenge 3", e)
                                cont.resume(false)
                            }
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to get start time", e)
                cont.resume(false)
            }
    }
    
    // ============== WARNING & TERMINATION ==============
    
    suspend fun addWarning(rollNumber: String): Int = suspendCancellableCoroutine { cont ->
        val statusRef = getParticipantRef(rollNumber).child("status")
        
        statusRef.child("warningCount").get()
            .addOnSuccessListener { snapshot ->
                val currentCount = snapshot.getValue(Int::class.java) ?: 0
                val newCount = currentCount + 1
                
                statusRef.child("warningCount").setValue(newCount)
                    .addOnSuccessListener { cont.resume(newCount) }
                    .addOnFailureListener { cont.resume(currentCount) }
            }
            .addOnFailureListener { cont.resume(0) }
    }
    
    suspend fun terminateParticipant(rollNumber: String, reason: String): Boolean = suspendCancellableCoroutine { cont ->
        val now = System.currentTimeMillis()
        val updates = mapOf(
            "status/currentStatus" to ParticipantStatus.STATUS_TERMINATED,
            "status/terminationReason" to reason,
            "status/isTerminated" to true
        )
        
        getParticipantRef(rollNumber).updateChildren(updates)
            .addOnSuccessListener { cont.resume(true) }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to terminate participant", e)
                cont.resume(false)
            }
    }
    
    suspend fun timeoutChallenge(rollNumber: String, challengeNumber: Int): Boolean = suspendCancellableCoroutine { cont ->
        val now = System.currentTimeMillis()
        val challengeKey = "challenge$challengeNumber"
        
        val updates = mapOf(
            "status/currentStatus" to ParticipantStatus.STATUS_TIMEOUT,
            "$challengeKey/status" to "timeout",
            "$challengeKey/endTime" to now
        )
        
        getParticipantRef(rollNumber).updateChildren(updates)
            .addOnSuccessListener { cont.resume(true) }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to timeout challenge", e)
                cont.resume(false)
            }
    }
    
    // ============== REAL-TIME LISTENERS ==============
    
    fun observeParticipantStatus(rollNumber: String): Flow<ParticipantStatus?> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.getValue(ParticipantStatus::class.java)
                trySend(status)
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Status listener cancelled", error.toException())
            }
        }
        
        val ref = getParticipantRef(rollNumber).child("status")
        ref.addValueEventListener(listener)
        
        awaitClose { ref.removeEventListener(listener) }
    }
    
    fun observeUniversalRankings(): Flow<List<RankingEntry>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val rankings = mutableListOf<RankingEntry>()
                for (child in snapshot.children) {
                    child.getValue(RankingEntry::class.java)?.let { rankings.add(it) }
                }
                trySend(rankings.sortedByDescending { it.totalScore })
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Rankings listener cancelled", error.toException())
            }
        }
        
        val ref = getRankingsRef()
        ref.addValueEventListener(listener)
        
        awaitClose { ref.removeEventListener(listener) }
    }
    
    // ============== ADMIN OPERATIONS ==============
    
    suspend fun toggleChallengeAccess(rollNumber: String, challengeNumber: Int, enabled: Boolean): Boolean = 
        suspendCancellableCoroutine { cont ->
            val field = if (challengeNumber == 2) "canAccessChallenge2" else "canAccessChallenge3"
            
            getParticipantRef(rollNumber).child("status/$field").setValue(enabled)
                .addOnSuccessListener { cont.resume(true) }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to toggle access", e)
                    cont.resume(false)
                }
        }
    
    suspend fun grantExtraTime(rollNumber: String, challengeNumber: Int, extraTimeMs: Long): Boolean = 
        suspendCancellableCoroutine { cont ->
            val challengeKey = "challenge$challengeNumber"
            
            getParticipantRef(rollNumber).child("$challengeKey/extraTimeGrantedMs")
                .setValue(extraTimeMs)
                .addOnSuccessListener { cont.resume(true) }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to grant extra time", e)
                    cont.resume(false)
                }
        }

    // ========== GAP 5: RESUME PARTICIPANT SESSION ==========
    suspend fun resumeParticipantSession(rollNumber: String): Boolean =
        suspendCancellableCoroutine { cont ->
            val updates = mapOf(
                "status/isTerminated" to false,
                "status/terminationReason" to null,
                "status/warningCount" to 0,
                "status/currentStatus" to ParticipantStatus.STATUS_RESUMABLE,
                "status/adminResumeGrantedAt" to System.currentTimeMillis()
            )
            
            getParticipantRef(rollNumber).updateChildren(updates)
                .addOnSuccessListener { 
                    Log.i(TAG, "Resumed session for $rollNumber")
                    cont.resume(true) 
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to resume session", e)
                    cont.resume(false)
                }
        }

    // ========== GAP 6: ADD EXTRA TIME (MINUTES WRAPPER) ==========
    suspend fun addExtraTime(rollNumber: String, minutes: Int): Boolean =
        suspendCancellableCoroutine { cont ->
            val extraTimeMs = minutes * 60 * 1000L
            
            // Get current active or timed-out challenge
            getParticipantRef(rollNumber).get()
                .addOnSuccessListener { snapshot ->
                    // Find which challenge is in progress OR timed out
                    val ch1Status = snapshot.child("challenge1/status").getValue(String::class.java)
                    val ch2Status = snapshot.child("challenge2/status").getValue(String::class.java)
                    val ch3Status = snapshot.child("challenge3/status").getValue(String::class.java)
                    
                    // Check for in_progress first, then timeout
                    val activeChallengeKey = when {
                        ch3Status == "in_progress" || ch3Status == "timeout" -> "challenge3"
                        ch2Status == "in_progress" || ch2Status == "timeout" -> "challenge2"
                        ch1Status == "in_progress" || ch1Status == "timeout" -> "challenge1"
                        else -> null
                    }
                    
                    if (activeChallengeKey != null) {
                        // Get current extra time and add to it
                        val currentExtra = snapshot.child("$activeChallengeKey/extraTimeGrantedMs")
                            .getValue(Long::class.java) ?: 0L
                        val newExtraTime = currentExtra + extraTimeMs
                        
                        // Update extra time AND set status to resumable
                        val updates = mapOf(
                            "$activeChallengeKey/extraTimeGrantedMs" to newExtraTime,
                            "$activeChallengeKey/status" to "resumable",
                            "status/currentStatus" to ParticipantStatus.STATUS_RESUMABLE,
                            "status/extraTimeAddedAt" to System.currentTimeMillis()
                        )
                        
                        getParticipantRef(rollNumber).updateChildren(updates)
                            .addOnSuccessListener { 
                                Log.i(TAG, "Added $minutes minutes to $rollNumber ($activeChallengeKey) and set resumable")
                                cont.resume(true) 
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Failed to add extra time", e)
                                cont.resume(false)
                            }
                    } else {
                        // No active challenge, store as general extra time
                        getParticipantRef(rollNumber)
                            .child("status/pendingExtraTimeMs")
                            .setValue(extraTimeMs)
                            .addOnSuccessListener { cont.resume(true) }
                            .addOnFailureListener { cont.resume(false) }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to get participant data", e)
                    cont.resume(false)
                }
        }

    // ========== GAP 8: VIEW PARTICIPANT DETAILS ==========
    suspend fun getParticipantDetails(rollNumber: String): ParticipantDetails? =
        suspendCancellableCoroutine { cont ->
            getParticipantRef(rollNumber).get()
                .addOnSuccessListener { snapshot ->
                    val ranking = snapshot.child("universalRanking")
                    val details = ParticipantDetails(
                        rollNumber = rollNumber,
                        challenge1Score = ranking.child("challenge1Score").getValue(Int::class.java) ?: 0,
                        challenge2Score = ranking.child("challenge2Score").getValue(Int::class.java) ?: 0,
                        challenge3Score = ranking.child("challenge3Score").getValue(Int::class.java) ?: 0,
                        totalScore = ranking.child("totalWeightedScore").getValue(Int::class.java) ?: 0,
                        rank = ranking.child("rank").getValue(Int::class.java) ?: 0
                    )
                    cont.resume(details)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to get participant details", e)
                    cont.resume(null)
                }
        }

    // ========== OBSERVE ALL PARTICIPANTS (ADMIN) ==========
    fun observeAllParticipants(): Flow<List<ParticipantStatus>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val participants = mutableListOf<ParticipantStatus>()
                for (child in snapshot.children) {
                    val rollNumber = child.key ?: continue
                    val status = child.child("status").getValue(ParticipantStatus::class.java)
                    val ranking = child.child("universalRanking")
                    
                    status?.let {
                        // Enhance with ranking data
                        val enhanced = it.copy(
                            rollNumber = rollNumber,
                            totalScore = ranking.child("totalWeightedScore").getValue(Int::class.java) ?: 0,
                            challenge1Score = ranking.child("challenge1Score").getValue(Int::class.java) ?: 0,
                            challenge2Score = ranking.child("challenge2Score").getValue(Int::class.java) ?: 0,
                            challenge3Score = ranking.child("challenge3Score").getValue(Int::class.java) ?: 0
                        )
                        participants.add(enhanced)
                    }
                }
                trySend(participants.sortedByDescending { it.totalScore })
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Participants listener cancelled", error.toException())
            }
        }
        
        val ref = getParticipantsRef()
        ref.addValueEventListener(listener)
        
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun deleteParticipant(rollNumber: String): Boolean = suspendCancellableCoroutine { cont ->
        getParticipantRef(rollNumber).removeValue()
            .addOnSuccessListener { 
                // Also remove from rankings
                getRankingsRef().child(rollNumber).removeValue()
                cont.resume(true) 
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to delete participant", e)
                cont.resume(false)
            }
    }
    
    suspend fun getAllParticipants(): List<Pair<String, ParticipantStatus>> = suspendCancellableCoroutine { cont ->
        getParticipantsRef().get()
            .addOnSuccessListener { snapshot ->
                val participants = mutableListOf<Pair<String, ParticipantStatus>>()
                for (child in snapshot.children) {
                    val rollNumber = child.key ?: continue
                    val status = child.child("status").getValue(ParticipantStatus::class.java)
                    status?.let { participants.add(rollNumber to it) }
                }
                cont.resume(participants)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to get all participants", e)
                cont.resume(emptyList())
            }
    }
    
    // ============== RANKING UPDATES ==============
    
    private fun updateUniversalRankings() {
        getParticipantsRef().get().addOnSuccessListener { snapshot ->
            val rankings = mutableListOf<RankingEntry>()
            
            for (child in snapshot.children) {
                val rollNumber = child.key ?: continue
                val universalRanking = child.child("universalRanking")
                
                val entry = RankingEntry(
                    rollNumber = rollNumber,
                    totalScore = universalRanking.child("totalWeightedScore").getValue(Int::class.java) ?: 0,
                    ch1 = universalRanking.child("challenge1Score").getValue(Int::class.java) ?: 0,
                    ch2 = universalRanking.child("challenge2Score").getValue(Int::class.java) ?: 0,
                    ch3 = universalRanking.child("challenge3Score").getValue(Int::class.java) ?: 0,
                    totalTimeMs = universalRanking.child("totalTimeTakenMs").getValue(Long::class.java) ?: 0
                )
                rankings.add(entry)
            }
            
            // Sort by score (descending), then by time (ascending)
            rankings.sortWith(compareByDescending<RankingEntry> { it.totalScore }.thenBy { it.totalTimeMs })
            
            // Update rankings in database
            val rankingsRef = getRankingsRef()
            rankings.forEachIndexed { index, entry ->
                val rank = index + 1
                rankingsRef.child(entry.rollNumber).setValue(entry.copy())
                
                // Update participant's rank
                getParticipantRef(entry.rollNumber).child("universalRanking/rank").setValue(rank)
            }
        }
    }
    
    // ============== VALIDATION ==============
    
    fun isValidRollNumber(rollNumber: String): Boolean {
        return ChallengeConstants.ROLL_NUMBER_REGEX.matches(rollNumber)
    }
    
    // ============== QUESTION PERSISTENCE ==============
    
    /**
     * Save generated questions to Firebase for resume functionality
     */
    suspend fun saveGeneratedQuestions(
        rollNumber: String, 
        challengeNumber: Int, 
        questionsJson: String
    ): Boolean = suspendCancellableCoroutine { cont ->
        val challengeKey = "challenge$challengeNumber"
        getParticipantRef(rollNumber).child("$challengeKey/generatedQuestions").setValue(questionsJson)
            .addOnSuccessListener { 
                Log.i(TAG, "Saved generated questions for $rollNumber challenge $challengeNumber")
                cont.resume(true) 
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to save generated questions", e)
                cont.resume(false)
            }
    }
    
    /**
     * Load previously saved questions for resume
     */
    suspend fun loadSavedQuestions(rollNumber: String, challengeNumber: Int): String? = 
        suspendCancellableCoroutine { cont ->
            val challengeKey = "challenge$challengeNumber"
            getParticipantRef(rollNumber).child("$challengeKey/generatedQuestions").get()
                .addOnSuccessListener { snapshot ->
                    val questionsJson = snapshot.getValue(String::class.java)
                    cont.resume(questionsJson)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to load saved questions", e)
                    cont.resume(null)
                }
        }
    
    /**
     * Get which challenge the user timed out on (returns 0 if none)
     */
    suspend fun getTimedOutChallengeNumber(rollNumber: String): Int = 
        suspendCancellableCoroutine { cont ->
            getParticipantRef(rollNumber).get()
                .addOnSuccessListener { snapshot ->
                    // Check each challenge for timeout status
                    for (i in 1..3) {
                        val status = snapshot.child("challenge$i/status").getValue(String::class.java)
                        if (status == "timeout") {
                            cont.resume(i)
                            return@addOnSuccessListener
                        }
                    }
                    cont.resume(0)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to get timed out challenge", e)
                    cont.resume(0)
                }
        }
    
    /**
     * Check if user has extra time granted for resuming a challenge
     */
    suspend fun getExtraTimeInfo(rollNumber: String): Pair<Int, Long>? = 
        suspendCancellableCoroutine { cont ->
            getParticipantRef(rollNumber).get()
                .addOnSuccessListener { snapshot ->
                    // Check each challenge for extra time and resumable status
                    for (i in 1..3) {
                        val status = snapshot.child("challenge$i/status").getValue(String::class.java)
                        val extraTime = snapshot.child("challenge$i/extraTimeGrantedMs").getValue(Long::class.java) ?: 0L
                        if ((status == "timeout" || status == "resumable") && extraTime > 0) {
                            cont.resume(Pair(i, extraTime))
                            return@addOnSuccessListener
                        }
                    }
                    cont.resume(null)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to get extra time info", e)
                    cont.resume(null)
                }
        }
    
    /**
     * Set challenge status to resumable when extra time is added
     */
    suspend fun setChallengeResumable(rollNumber: String, challengeNumber: Int): Boolean = 
        suspendCancellableCoroutine { cont ->
            val challengeKey = "challenge$challengeNumber"
            val updates = mapOf(
                "$challengeKey/status" to "resumable",
                "status/currentStatus" to ParticipantStatus.STATUS_RESUMABLE
            )
            getParticipantRef(rollNumber).updateChildren(updates)
                .addOnSuccessListener { cont.resume(true) }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to set challenge resumable", e)
                    cont.resume(false)
                }
        }
    
    // ============== COMPREHENSIVE STATE SAVE/LOAD FOR RESUME ==============
    
    /**
     * Save complete challenge state before termination/timeout
     * This includes: current answers, selected options, timer remaining, current problem index
     */
    suspend fun saveChallengeState(
        rollNumber: String,
        challengeNumber: Int,
        stateJson: String,
        timeRemainingMs: Long,
        currentProblemIndex: Int
    ): Boolean = suspendCancellableCoroutine { cont ->
        val challengeKey = "challenge$challengeNumber"
        val updates = mapOf(
            "$challengeKey/savedState" to stateJson,
            "$challengeKey/savedTimeRemainingMs" to timeRemainingMs,
            "$challengeKey/savedProblemIndex" to currentProblemIndex,
            "$challengeKey/stateLastSavedAt" to System.currentTimeMillis()
        )
        getParticipantRef(rollNumber).updateChildren(updates)
            .addOnSuccessListener { 
                Log.i(TAG, "Saved challenge $challengeNumber state for $rollNumber (timeRemaining=${timeRemainingMs}ms, problemIndex=$currentProblemIndex)")
                cont.resume(true) 
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to save challenge state", e)
                cont.resume(false)
            }
    }
    
    /**
     * Load saved challenge state for resume
     * Returns: Triple(stateJson, timeRemainingMs, currentProblemIndex) or null if not found
     */
    suspend fun loadChallengeState(rollNumber: String, challengeNumber: Int): Triple<String?, Long, Int>? = 
        suspendCancellableCoroutine { cont ->
            val challengeKey = "challenge$challengeNumber"
            getParticipantRef(rollNumber).child(challengeKey).get()
                .addOnSuccessListener { snapshot ->
                    val savedState = snapshot.child("savedState").getValue(String::class.java)
                    val timeRemainingMs = snapshot.child("savedTimeRemainingMs").getValue(Long::class.java) ?: 0L
                    val problemIndex = snapshot.child("savedProblemIndex").getValue(Int::class.java) ?: 0
                    
                    if (savedState != null) {
                        Log.i(TAG, "Loaded challenge $challengeNumber state for $rollNumber (timeRemaining=${timeRemainingMs}ms, problemIndex=$problemIndex)")
                        cont.resume(Triple(savedState, timeRemainingMs, problemIndex))
                    } else {
                        cont.resume(null)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to load challenge state", e)
                    cont.resume(null)
                }
        }
    
    /**
     * Clear saved state after successful submission
     */
    suspend fun clearChallengeState(rollNumber: String, challengeNumber: Int): Boolean = 
        suspendCancellableCoroutine { cont ->
            val challengeKey = "challenge$challengeNumber"
            val updates = mapOf<String, Any?>(
                "$challengeKey/savedState" to null,
                "$challengeKey/savedTimeRemainingMs" to null,
                "$challengeKey/savedProblemIndex" to null,
                "$challengeKey/stateLastSavedAt" to null
            )
            getParticipantRef(rollNumber).updateChildren(updates)
                .addOnSuccessListener { cont.resume(true) }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to clear challenge state", e)
                    cont.resume(false)
                }
        }
}
