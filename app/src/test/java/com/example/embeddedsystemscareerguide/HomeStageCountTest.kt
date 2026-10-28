package com.example.embeddedsystemscareerguide

import com.example.embeddedsystemscareerguide.ui.home.HomeFragment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/**
 * Navigating away from Home while its dashboard was loading cancelled the load.
 * The stage-count read caught that cancellation as an ordinary failure and
 * returned the cached count, so the load went on to draw into a view that
 * onDestroyView had already released, and its finally block crashed on the
 * cleared binding.
 */
class HomeStageCountTest {

    @Test
    fun `the stored path length wins`() = runBlocking {
        assertEquals(3, HomeFragment.stageCountOrCached(cached = 40) { listOf("a", "b", "c") })
    }

    @Test
    fun `an empty or unreadable path falls back to the cached length`() = runBlocking {
        assertEquals(40, HomeFragment.stageCountOrCached(cached = 40) { emptyList<Any>() })
        assertEquals(40, HomeFragment.stageCountOrCached(cached = 40) { null })
        assertEquals(40, HomeFragment.stageCountOrCached(cached = 40) { throw IllegalStateException("offline") })
    }

    @Test
    fun `leaving Home ends the load instead of returning the cached length`() = runBlocking {
        try {
            val count = HomeFragment.stageCountOrCached(cached = 40) {
                throw CancellationException("left Home")
            }
            fail("cancellation must propagate, got $count")
        } catch (e: CancellationException) {
            // expected
        }
    }
}
