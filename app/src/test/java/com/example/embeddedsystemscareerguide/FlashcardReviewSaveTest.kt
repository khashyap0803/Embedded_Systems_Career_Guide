package com.example.embeddedsystemscareerguide

import android.content.SharedPreferences
import com.example.embeddedsystemscareerguide.services.Flashcard
import com.example.embeddedsystemscareerguide.services.FirestoreManager
import com.example.embeddedsystemscareerguide.services.FlashcardService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * Marking a flashcard read the stage's whole list, changed one card and wrote the
 * list back in two separate steps, so a second quick mark could overwrite the first.
 * And a failed save was reported as saved, so the screen said "Marked for review"
 * either way. The mark is now one transaction, and a failure comes back as one.
 */
class FlashcardReviewSaveTest {

    private val cards = listOf(
        Flashcard(id = 1, front = "a", back = "b"),
        Flashcard(id = 2, front = "c", back = "d", needsReview = true)
    )

    @Test
    fun `marking changes only that card`() {
        assertEquals(
            listOf(cards[0].copy(needsReview = true), cards[1]),
            FirestoreManager.withReview(cards, 1, needsReview = true)
        )
        assertEquals(
            listOf(cards[0], cards[1].copy(needsReview = false)),
            FirestoreManager.withReview(cards, 2, needsReview = false)
        )
    }

    @Test
    fun `marking a card that is not there writes nothing`() {
        assertNull(FirestoreManager.withReview(cards, 9, needsReview = true))
    }

    @Test
    fun `the stored list reads back as it was saved`() {
        val stored = listOf(
            mapOf("id" to 1L, "front" to "a", "back" to "b", "difficulty" to "hard", "category" to "code", "needsReview" to true),
            "not a card",
            mapOf("id" to 2L)
        )
        assertEquals(
            listOf(
                Flashcard(id = 1, front = "a", back = "b", difficulty = "hard", category = "code", needsReview = true),
                Flashcard(id = 2)
            ),
            FirestoreManager.flashcardsFrom(stored)
        )
        assertEquals(emptyList<Flashcard>(), FirestoreManager.flashcardsFrom(null))
    }

    /** SharedPreferences whose every read fails, as an unreadable store would. */
    private val failing = Proxy.newProxyInstance(
        SharedPreferences::class.java.classLoader,
        arrayOf(SharedPreferences::class.java)
    ) { _, _, _ -> throw IllegalStateException("store unavailable") } as SharedPreferences

    @Test
    fun `a failed save is reported as failed`() {
        val firestoreManager = withoutConstructor(FirestoreManager::class.java).also { set(it, "prefs", failing) }
        val service = withoutConstructor(FlashcardService::class.java).also { set(it, "firestoreManager", firestoreManager) }
        val result = runBlocking { service.updateFlashcardReview(stageId = 1, flashcardId = 1, needsReview = true) }
        assertTrue("a save that failed came back as $result", result.isFailure)
    }

    private fun set(target: Any, field: String, value: Any) {
        target.javaClass.getDeclaredField(field).apply { isAccessible = true }.set(target, value)
    }

    private fun <T> withoutConstructor(type: Class<T>): T {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        @Suppress("UNCHECKED_CAST")
        return unsafeClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, type) as T
    }
}
