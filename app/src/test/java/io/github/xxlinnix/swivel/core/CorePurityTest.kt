package io.github.xxlinnix.swivel.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/** The core package must stay free of Android so it can be tested without a device. */
class CorePurityTest {
    @Test
    fun coreHasNoAndroidImports() {
        val coreDir = listOf(
            File("src/main/java/io/github/xxlinnix/swivel/core"),
            File("app/src/main/java/io/github/xxlinnix/swivel/core"),
        ).firstOrNull { it.isDirectory } ?: fail("core sources not found from ${File(".").absolutePath}")

        val sources = coreDir.walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue(sources.isNotEmpty())
        val offenders = sources.filter { file ->
            file.readLines().any { it.startsWith("import android.") || it.startsWith("import androidx.") }
        }
        assertTrue(offenders.isEmpty(), "Android imports in core: ${offenders.map { it.name }}")
    }
}
