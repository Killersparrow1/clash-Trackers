package com.killersparrow1.clashtrackers.ui

import com.killersparrow1.clashtrackers.core.SampleExports
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every activity, service and receiver in the manifest, and every widget setup screen, must exist in the source. */
class ManifestClassesTest {
    private val pkg = "com.killersparrow1.clashtrackers"

    private fun sources(): String {
        val root = SampleExports.file("src/main/java", "app/src/main/java")
        return root.walkTopDown().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
    }

    private fun declared(source: String, qualified: String): Boolean {
        val short = qualified.substringAfterLast('.')
        return Regex("""\b(class|object)\s+$short\b""").containsMatchIn(source)
    }

    @Test fun manifestComponentsExist() {
        val manifest = SampleExports.file("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml").readText()
        val src = sources()
        // activity-alias entries point at another activity, so only the real components are checked.
        val names = Regex("""<(?:activity|service|receiver|provider)\b[^>]*?android:name="([^"]+)"""")
            .findAll(manifest).map { it.groupValues[1] }.toList()
        assertTrue("no components found", names.size >= 5)
        for (n in names) {
            val full = if (n.startsWith(".")) pkg + n else n
            if (full.startsWith(pkg)) assertTrue("$full is in the manifest but not in the source", declared(src, full))
        }
    }

    @Test fun widgetSetupScreensExist() {
        val dir = SampleExports.file("src/main/res/xml", "app/src/main/res/xml")
        val src = sources()
        val configured = dir.listFiles().orEmpty().flatMap { f: File ->
            Regex("""android:configure="([^"]+)"""").findAll(f.readText()).map { it.groupValues[1] }.toList()
        }
        assertTrue("no widget setup screens found", configured.isNotEmpty())
        for (c in configured) assertTrue("$c is a widget setup screen that does not exist", declared(src, c))
    }
}
