package com.example.embeddedsystemscareerguide

import com.example.embeddedsystemscareerguide.services.StageGeneratorService
import com.example.embeddedsystemscareerguide.services.StageGeneratorService.RetakeReplace
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * A retake saved its new stages even when resetting the old progress was refused
 * (progress unreadable or the write failed). The old completedStages ids then
 * marked the new stages - which reuse ids 1, 2, 3... - as done.
 */
class RetakeReplaceTest {

    private val calls = mutableListOf<String>()

    private fun replace(reset: Boolean, save: Result<Unit> = Result.success(Unit)) = runBlocking {
        StageGeneratorService.replacePathForRetake(
            resetProgress = { calls += "reset"; reset },
            deleteOldStages = { calls += "delete" },
            saveStages = { calls += "save"; save }
        )
    }

    @Test
    fun `a refused reset keeps the old path and progress`() {
        assertEquals(RetakeReplace.NotStarted, replace(reset = false))
        assertEquals(listOf("reset"), calls)
    }

    @Test
    fun `progress is reset before the old stages are touched`() {
        assertEquals(RetakeReplace.Replaced, replace(reset = true))
        assertEquals(listOf("reset", "delete", "save"), calls)
    }

    @Test
    fun `a failed save after the reset is reported as a failed save`() {
        val result = replace(reset = true, save = Result.failure(IOException("offline")))
        assertTrue("was $result", result is RetakeReplace.SaveFailed)
    }
}
