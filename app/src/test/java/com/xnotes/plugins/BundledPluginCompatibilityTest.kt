package com.xnotes.plugins

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Regression guard for the actual examples and legacy packages shipped in plugins/.
 * A refactor must not silently change what the production .xplugin parser accepts.
 */
class BundledPluginCompatibilityTest {
    @Test fun existingPluginSourcesStillLoadWithoutManifestMigration() {
        val root = generateSequence(File(System.getProperty("user.dir")).canonicalFile) { it.parentFile }
            .firstOrNull { File(it, "plugins").isDirectory && File(it, "sdk/pack.py").isFile }
            ?: error("Repository root containing plugins/ and sdk/pack.py not found")
        val sources = requireNotNull(File(root, "plugins").listFiles())
            .filter { it.isDirectory && File(it, "manifest.json").isFile }.sortedBy { it.name }
        assertTrue("Expected bundled API 1–8 plugin examples", sources.size >= 10)

        val identifiers = mutableSetOf<String>()
        for (source in sources) {
            val manifest = JSONObject(File(source, "manifest.json").readText(Charsets.UTF_8))
            val files = when (manifest.getString("type")) {
                "javascript", "toolbar-javascript" -> listOf("manifest.json", "main.js")
                "toolbar" -> listOf("manifest.json", "toolbar.json")
                "declarative" -> if (manifest.has("feature")) listOf("manifest.json") else listOf("manifest.json", "profiles.json")
                else -> error("Unknown plugin type in ${source.name}")
            }
            val bytes = ByteArrayOutputStream().also { memory ->
                ZipOutputStream(memory).use { zip ->
                    for (name in files) {
                        zip.putNextEntry(ZipEntry(name))
                        zip.write(File(source, name).readBytes())
                        zip.closeEntry()
                    }
                }
            }.toByteArray()

            val pkg = try {
                DeclarativePackages.parse(bytes)
            } catch (e: Exception) {
                throw AssertionError("Cannot load bundled plugin ${source.name}: ${e.message}", e)
            }
            assertEquals(source.name, manifest.getString("id"), pkg.id)
            assertEquals(manifest.getString("version"), pkg.version)
            assertTrue("Duplicate bundled plugin ID ${pkg.id}", identifiers.add(pkg.id))
            when (manifest.getString("type")) {
                "javascript" -> assertNotNull(pkg.script)
                "toolbar", "toolbar-javascript" -> assertNotNull(pkg.toolbar)
                "declarative" -> if (manifest.has("feature")) {
                    assertEquals(manifest.getString("feature"), pkg.feature)
                } else {
                    assertTrue(pkg.profiles.isNotEmpty())
                }
            }
        }
    }
}
