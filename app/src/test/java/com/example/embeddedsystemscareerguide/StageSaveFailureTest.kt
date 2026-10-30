package com.example.embeddedsystemscareerguide

import com.example.embeddedsystemscareerguide.models.LearningStage
import com.example.embeddedsystemscareerguide.ui.learningpath.LearningPathFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Learning Path shows a result before its save is confirmed. When the save failed -
 * offline, the stage-completion transaction fails outright - the path kept showing
 * the stage done and the next one open, until the next reload quietly took them back.
 * A failed save now puts the path back and says so, with Retry.
 */
class StageSaveFailureTest {

    private fun path() = listOf(
        LearningStage(id = "1", isUnlocked = true, isCompleted = true, starsEarned = 2),
        LearningStage(id = "2", isUnlocked = true),
        LearningStage(id = "3")
    )

    private fun state(stages: List<LearningStage>) = stages.map { Triple(it.isCompleted, it.starsEarned, it.isUnlocked) }

    @Test
    fun `completing a stage shows it done and opens the next one`() {
        val stages = path()
        LearningPathFragment.markCompleted(stages, "2", 3)
        assertEquals(Triple(true, 3, true), state(stages)[1])
        assertEquals(Triple(false, 0, true), state(stages)[2])
    }

    @Test
    fun `undoing a completion puts the path back exactly`() {
        val stages = path()
        val before = state(stages)
        val undo = LearningPathFragment.markCompleted(stages, "2", 3)
        undo()
        assertEquals(before, state(stages))
    }

    @Test
    fun `the last stage has no next one to open or close`() {
        val stages = path()
        val before = state(stages)
        LearningPathFragment.markCompleted(stages, "3", 1)()
        assertEquals(before, state(stages))
    }

    // Tests run from the module directory.
    private val source = File("src/main/java/com/example/embeddedsystemscareerguide/ui/learningpath/LearningPathFragment.kt")
        .readText(Charsets.UTF_8)

    /** The body of `fun [name](...)`, without its comments. */
    private fun body(name: String): String {
        val start = source.indexOf("fun $name(")
        val open = source.indexOf('{', start)
        var depth = 0
        for (i in open until source.length) {
            if (source[i] == '{') depth++
            if (source[i] == '}') depth--
            if (depth == 0) return source.substring(open, i + 1).lines().filterNot { it.trim().startsWith("//") }.joinToString("\n")
        }
        error("no body for $name")
    }

    private fun calls(body: String, name: String) =
        Regex("""^\s*$name\(\)\s*$""", RegexOption.MULTILINE).findAll(body).count()

    @Test
    fun `both ways a stage save can fail put the path back`() {
        // completeStageInCloud returning null, and it throwing.
        assertEquals(2, calls(body("completeStage"), "saveFailed"))
    }

    @Test
    fun `both ways a better star score can fail to save put the old stars back`() {
        val stars = body("updateStageStars")
        // updateStarsInCloud returning null, and it throwing.
        assertEquals(2, calls(stars, "saveFailed"))
        assertTrue("a failed save must restore the old stars", "stage.starsEarned = oldStars" in stars)
    }
}
