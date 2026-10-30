package com.example.embeddedsystemscareerguide

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Home loaded its progress in both onViewCreated and onResume, and Android always
 * runs onResume straight after onViewCreated, so every time Home appeared it read
 * everything twice. Which callbacks run is Android's, not a decision this code makes,
 * so this checks the wiring itself: onResume starts the load, onViewCreated doesn't.
 */
class HomeLoadOnceTest {

    // Tests run from the module directory.
    private val source = File("src/main/java/com/example/embeddedsystemscareerguide/ui/home/HomeFragment.kt")
        .readText(Charsets.UTF_8)

    /** The body of `override fun [name](...)`, without its comments. */
    private fun body(name: String): String {
        val start = source.indexOf("override fun $name(")
        val open = source.indexOf('{', start)
        var depth = 0
        for (i in open until source.length) {
            if (source[i] == '{') depth++
            if (source[i] == '}') depth--
            if (depth == 0) return source.substring(open, i + 1).lines().filterNot { it.trim().startsWith("//") }.joinToString("\n")
        }
        error("no body for $name")
    }

    private fun loads(name: String) = Regex("""\bloadProgressFromCloud\(\)""").findAll(body(name)).count()

    @Test
    fun `Home starts its progress load once when it appears`() {
        assertEquals("loads started in onViewCreated", 0, loads("onViewCreated"))
        assertEquals("loads started in onResume", 1, loads("onResume"))
    }
}
