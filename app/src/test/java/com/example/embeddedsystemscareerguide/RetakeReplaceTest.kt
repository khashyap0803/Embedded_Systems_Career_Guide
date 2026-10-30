package com.example.embeddedsystemscareerguide

import com.example.embeddedsystemscareerguide.services.FirestoreManager
import com.example.embeddedsystemscareerguide.services.PersonalizedStage
import com.example.embeddedsystemscareerguide.services.RetakeWrite
import com.example.embeddedsystemscareerguide.services.StageGeneratorService
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A retake reset the stage progress in one write and saved the new stages in
 * another. When the reset went through and the save failed, the student kept
 * the old path with its progress wiped. Everything now goes into one WriteBatch,
 * which Firestore applies whole or not at all - and which holds 500 writes.
 */
class RetakeReplaceTest {

    private fun stages(ids: IntRange) = ids.map { PersonalizedStage(id = it, title = "Stage $it") }

    @Test
    fun `the reset, the deletes and the saves are one batch`() {
        val writes = FirestoreManager.retakeWrites(listOf("1", "2", "3"), stages(1..2), now = 42L)

        assertTrue("the reset must be in the batch, was $writes", writes.first() is RetakeWrite.ResetProgress)
        assertEquals(
            listOf(RetakeWrite.DeleteStage("3"), RetakeWrite.SaveStage(stages(1..2)[0]), RetakeWrite.SaveStage(stages(1..2)[1])),
            writes.drop(1)
        )
    }

    @Test
    fun `the reset clears stage progress and leaves XP and the streak alone`() {
        val reset = FirestoreManager.retakeWrites(emptyList(), stages(1..1), now = 42L).first() as RetakeWrite.ResetProgress

        assertEquals(
            mapOf("currentStage" to 1, "completedStages" to emptyList<String>(), "stageStars" to emptyMap<String, Int>(), "lastUpdated" to 42L),
            reset.fields
        )
    }

    @Test
    fun `the largest path the generator keeps fits in one batch`() {
        val max = StageGeneratorService.MAX_STAGES
        // Worst case: an old path of the same size whose ids the new one reuses none of.
        val oldIds = (1..max).map { (it + 1000).toString() }
        val writes = FirestoreManager.retakeWrites(oldIds, stages(1..max), now = 42L)

        assertEquals(1 + max + max, writes.size)
        assertTrue("${writes.size} writes", writes.size <= FirestoreManager.MAX_BATCH_WRITES)
    }

    @Test
    fun `the generator keeps at most MAX_STAGES stages`() {
        val response = (1..150).joinToString(",", """{"stages":[""", "]}") { """{"id":$it,"title":"Stage $it"}""" }

        assertEquals(StageGeneratorService.MAX_STAGES, generator().parseStagesFromResponse(response).size)
    }

    /** The constructor needs a live FirebaseApp; parsing needs only Gson. */
    private fun generator(): StageGeneratorService {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        val service = unsafeClass.getMethod("allocateInstance", Class::class.java)
            .invoke(unsafe, StageGeneratorService::class.java) as StageGeneratorService
        StageGeneratorService::class.java.getDeclaredField("gson").apply { isAccessible = true }.set(service, Gson())
        return service
    }
}
