package com.killersparrow1.clashtrackers.locale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Guards translations: every `values-xx/strings.xml` may only use known string names, must keep the same
 * `%1$s` / `%1$d` placeholders as English, and must be listed in `res/xml/locales_config.xml`.
 */
class StringsConsistencyTest {
    private val res = File("src/main/res")
    private val entry = Regex("""<string\s+name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
    private val placeholder = Regex("""%\d+\$[sd]""")

    private fun load(file: File): Map<String, String> =
        entry.findAll(file.readText()).associate { it.groupValues[1] to it.groupValues[2] }

    private fun translations(): List<File> =
        res.listFiles().orEmpty().filter { it.isDirectory && it.name.startsWith("values-") && File(it, "strings.xml").isFile }

    @Test fun translationsUseKnownNamesAndSamePlaceholders() {
        assumeTrue(res.isDirectory)
        val english = load(File(res, "values/strings.xml"))
        for (dir in translations()) {
            for ((name, text) in load(File(dir, "strings.xml"))) {
                val en = english[name]
                assertTrue("${dir.name}: unknown string name '$name'", en != null)
                assertEquals(
                    "${dir.name}: placeholders of '$name' differ from English",
                    placeholder.findAll(en!!).map { it.value }.sorted().toList(),
                    placeholder.findAll(text).map { it.value }.sorted().toList(),
                )
            }
        }
    }

    @Test fun everyTranslationIsListedInLocalesConfig() {
        assumeTrue(res.isDirectory)
        val config = File(res, "xml/locales_config.xml").readText()
        val listed = Regex("""android:name="([^"]+)"""").findAll(config).map { it.groupValues[1] }.toSet()
        for (dir in translations()) {
            val tag = dir.name.removePrefix("values-").replace("-r", "-")
            assertTrue("$tag has res/${dir.name}/strings.xml but is missing in res/xml/locales_config.xml", tag in listed)
        }
    }
}
