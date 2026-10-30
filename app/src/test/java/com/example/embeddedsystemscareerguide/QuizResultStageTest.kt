package com.example.embeddedsystemscareerguide

import com.example.embeddedsystemscareerguide.models.LearningStage
import com.example.embeddedsystemscareerguide.ui.learningpath.LearningPathFragment
import com.example.embeddedsystemscareerguide.ui.learningpath.LearningPathFragment.QuizAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Learning Path remembered which stage a quiz was for in a field set at launch. When
 * Android recreated the screen while the quiz was open (a font or language change,
 * or the app closed in the background), the field was gone and a passed quiz was
 * silently dropped: no completion, no XP, no message. The stage now comes back with
 * the quiz's result.
 */
class QuizResultStageTest {

    private fun stage(id: String, completed: Boolean = false, stars: Int = 0) =
        LearningStage(id = id, isUnlocked = true, isCompleted = completed, starsEarned = stars)

    @Test
    fun `the result alone says which stage the quiz was for`() {
        val outcome = LearningPathFragment.quizOutcome(stageId = 3, score = 4, total = 5)!!
        // A list loaded after recreation: nothing from before the quiz.
        val reloaded = listOf(stage("1", completed = true, stars = 3), stage("2", completed = true, stars = 2), stage("3"))
        val target = reloaded.single { it.id == outcome.stageId }
        assertEquals(QuizAction.COMPLETE, LearningPathFragment.quizAction(outcome, target))
    }

    @Test
    fun `a result with no stage id is not applied to some stage`() {
        assertNull(LearningPathFragment.quizOutcome(stageId = -1, score = 5, total = 5))
    }

    @Test
    fun `stars follow the score, and an empty quiz is 0 percent`() {
        fun stars(score: Int, total: Int) = LearningPathFragment.quizOutcome(1, score, total)!!.stars
        assertEquals(3, stars(4, 5))
        assertEquals(2, stars(3, 5))
        assertEquals(1, stars(2, 5))
        assertEquals(0, stars(1, 5))
        assertEquals(0, stars(0, 0))
    }

    @Test
    fun `a finished quiz completes, improves, keeps the best, or does not pass`() {
        fun action(score: Int, stage: LearningStage) =
            LearningPathFragment.quizAction(LearningPathFragment.quizOutcome(1, score, 5)!!, stage)
        assertEquals(QuizAction.COMPLETE, action(2, stage("1")))
        assertEquals(QuizAction.IMPROVE_STARS, action(5, stage("1", completed = true, stars = 2)))
        assertEquals(QuizAction.KEEP_BEST, action(3, stage("1", completed = true, stars = 2)))
        assertEquals(QuizAction.NOT_PASSED, action(1, stage("1")))
        assertEquals(QuizAction.NOT_PASSED, action(1, stage("1", completed = true, stars = 3)))
    }

    // Tests run from the module directory.
    private fun source(path: String) =
        File("src/main/java/com/example/embeddedsystemscareerguide/$path").readText(Charsets.UTF_8)

    /** The body of `fun [name](...)` in [text], without its comments. */
    private fun body(text: String, name: String): String {
        val start = text.indexOf("fun $name(")
        val open = text.indexOf('{', start)
        var depth = 0
        for (i in open until text.length) {
            if (text[i] == '{') depth++
            if (text[i] == '}') depth--
            if (depth == 0) return text.substring(open, i + 1).lines().filterNot { it.trim().startsWith("//") }.joinToString("\n")
        }
        error("no body for $name")
    }

    @Test
    fun `launching a quiz keeps nothing the result depends on`() {
        val launch = body(source("ui/learningpath/LearningPathFragment.kt"), "launchQuizForStage")
        assertTrue(
            "launchQuizForStage stores the stage in a field, which recreation loses:\n$launch",
            !Regex("""^\s*\w+\s*=\s*stage\b""", RegexOption.MULTILINE).containsMatchIn(launch)
        )
    }

    @Test
    fun `the quiz sends its launch intent back, stage id included`() {
        val result = body(source("ui/quiz/QuizActivity.kt"), "returnResult")
        assertTrue("returnResult must reply with the launch intent:\n$result", "val resultIntent = intent" in result)
    }
}
