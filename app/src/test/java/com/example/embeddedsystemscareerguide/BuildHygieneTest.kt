package com.example.embeddedsystemscareerguide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Two leftovers. BuildConfig carried a GEMINI_API_KEY field that nothing read,
 * filled from local.properties - a key compiled into every APK built on a machine
 * that had one. And two test harnesses wrote to a hard-coded C:/Users/... path,
 * which is a relative path everywhere but Windows, so a Linux checkout grew an
 * app/C:/ folder.
 */
class BuildHygieneTest {

    @Test
    fun `no API key is compiled into BuildConfig`() {
        val fields = BuildConfig::class.java.declaredFields.map { it.name }
        assertFalse("BuildConfig fields: $fields", fields.any { it.contains("API_KEY") })
    }

    @Test
    fun `no source names a Windows drive path`() {
        // A string literal that opens with a drive letter, a colon and a slash or backslash.
        val drivePath = Regex(""""[A-Za-z]:[/\\]""")
        // Tests run from the module directory.
        val sources = listOf("src/main/java", "src/test/java")
            .flatMap { File(it).walkTopDown().filter { f -> f.extension == "kt" }.toList() }
        assertTrue("no sources found", sources.isNotEmpty())

        val offenders = sources.flatMap { file ->
            file.readLines(Charsets.UTF_8).withIndex()
                .filter { drivePath.containsMatchIn(it.value) }
                .map { "${file.name}:${it.index + 1}: ${it.value.trim()}" }
        }
        assertEquals(offenders.joinToString("\n"), 0, offenders.size)
    }
}
