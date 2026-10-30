package com.example.embeddedsystemscareerguide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Log.w, Log.e and Log.i survive R8 in release builds, and some of them carried
 * the student's roll number. Log.d and Log.v are not checked: they are allowed
 * to carry diagnostics.
 */
class ReleaseLogIdentifierTest {

    /** A release-level Log call, from "Log.x(" to its closing parenthesis. */
    private val logCall = Regex("""\bLog\.(w|e|i|wtf)\(""")

    /** An identifier interpolated (${'$'}name, ${'$'}{...name...}) or concatenated (+ name) into the message. */
    private val identifier = Regex(
        """\$\{[^}]*\b(rollNumber|username|userName|email|userEmail|uid|userId|displayName)\b[^}]*\}""" +
            """|\$(rollNumber|username|userName|email|userEmail|uid|userId|displayName)\b""" +
            """|\+\s*(rollNumber|username|userName|email|userEmail|uid|userId|displayName)\b"""
    )

    private fun releaseLogCalls(): List<String> {
        // Tests run from the module directory.
        val sources = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue("no sources found under ${File("src/main/java").absolutePath}", sources.isNotEmpty())
        return sources.flatMap { file ->
            val text = file.readText(Charsets.UTF_8)
            logCall.findAll(text).map { match ->
                var depth = 0
                var end = match.range.last
                for (i in match.range.last until text.length) {
                    if (text[i] == '(') depth++
                    if (text[i] == ')') depth--
                    if (depth == 0) { end = i; break }
                }
                "${file.name}: " + text.substring(match.range.first, end + 1).replace(Regex("\\s+"), " ")
            }.toList()
        }
    }

    @Test
    fun `release logs carry no student identifiers`() {
        val offenders = releaseLogCalls().filter { identifier.containsMatchIn(it) }
        assertEquals(offenders.joinToString("\n"), 0, offenders.size)
    }

    @Test
    fun `the check sees an identifier when there is one`() {
        assertTrue(identifier.containsMatchIn("""Log.i(TAG, "Resumed session for ${'$'}rollNumber")"""))
        assertTrue(identifier.containsMatchIn("""Log.w(TAG, "Signed in as ${'$'}{user.email}")"""))
        assertTrue(identifier.containsMatchIn("""Log.e(TAG, "Failed for " + username, e)"""))
    }
}
