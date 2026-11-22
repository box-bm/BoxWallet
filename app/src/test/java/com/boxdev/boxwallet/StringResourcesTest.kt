package com.boxdev.boxwallet

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards against English strings leaking into the Spanish build. */
class StringResourcesTest {
    private val resDir = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }

    private fun translatableKeys(file: File): Set<String> =
        Regex("""<(string|plurals|string-array) name="([^"]+)"([^>]*)>""")
            .findAll(file.readText())
            .filterNot { it.groupValues[3].contains("translatable=\"false\"") }
            .map { it.groupValues[2] }
            .toSet()

    @Test fun everyStringHasASpanishTranslation() {
        val missing = translatableKeys(File(resDir, "values/strings.xml")) -
            translatableKeys(File(resDir, "values-es/strings.xml"))
        assertTrue("Missing in values-es/strings.xml: $missing", missing.isEmpty())
    }
}
