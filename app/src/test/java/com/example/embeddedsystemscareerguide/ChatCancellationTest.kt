package com.example.embeddedsystemscareerguide

import com.example.embeddedsystemscareerguide.ui.chat.ChatFragment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/**
 * Leaving the chat tab while the tutor was still answering cancelled the send,
 * and ChatFragment's catch-all treated that cancellation as a failed request: it
 * queued an apology bubble and fell through to a finally block that read the
 * binding onDestroyView had already cleared - a crash.
 */
class ChatCancellationTest {

    @Test
    fun `a reply becomes a tutor bubble`() = runBlocking {
        val message = ChatFragment.replyFor { "Use volatile for registers." }
        assertEquals("Use volatile for registers.", message.content)
        assertFalse(message.isUser)
    }

    @Test
    fun `a failed request becomes the apology bubble`() = runBlocking {
        val message = ChatFragment.replyFor { throw IOException("connection reset") }
        assertEquals(ChatFragment.APOLOGY, message.content)
        assertFalse(message.isUser)
    }

    @Test
    fun `leaving the tab cancels the send instead of queuing an apology`() = runBlocking {
        try {
            val message = ChatFragment.replyFor { throw CancellationException("left the chat tab") }
            fail("cancellation must propagate, got a bubble: ${message.content}")
        } catch (e: CancellationException) {
            // expected
        }
    }
}
