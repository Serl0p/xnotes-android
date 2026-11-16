package com.xnotes.plugins

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DeclarativePackagesTest {
    private val manifest = """{"id":"pen-profiles","name":"Pen Profiles","version":"1.0.0","apiVersion":1,"type":"declarative","description":"Test","permissions":["tools.configure","commands.register"]}"""
    private val profiles = """{"profiles":[{"id":"logic","name":"Logic","tool":"pen","width":1.5,"color":"#4189DD","pressure":true}]}"""
    private fun zip(vararg files: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for ((name, data) in files) {
                z.putNextEntry(ZipEntry(name)); z.write(data.toByteArray()); z.closeEntry()
            }
        }
        return out.toByteArray()
    }
    private fun pack(m: String = manifest, p: String = profiles) = zip("manifest.json" to m, "profiles.json" to p)
    private fun rejected(bytes: ByteArray) {
        try { DeclarativePackages.parse(bytes); fail("Invalid package accepted") } catch (_: Exception) {}
    }
    @Test fun validPackage() {
        val pkg = DeclarativePackages.parse(pack())
        assertEquals("pen-profiles", pkg.id)
        assertEquals(1.5, pkg.profiles.single().config.baseWidth, 0.0)
        assertEquals(65, pkg.profiles.single().config.colorOverride!!.r)
        assertTrue(DeclarativePackages.isCommand("pen-profiles:logic"))
    }
    @Test fun rejectsUnsafeOrUnsupportedFiles() {
        rejected(zip("manifest.json" to manifest, "profiles.json" to profiles, "../outside" to "bad"))
        rejected(zip("manifest.json" to manifest, "profiles.json" to profiles, "main.js" to "bad"))
        rejected(zip("manifest.json" to manifest))
        rejected(ByteArray(DeclarativePackages.MAX_ARCHIVE + 1))
        rejected(zip("manifest.json" to manifest, "profiles.json" to " ".repeat(70000)))
    }
    @Test fun rejectsInvalidManifestAndProfiles() {
        rejected(pack(m = manifest.replace("tools.configure", "document.write")))
        rejected(pack(m = manifest.replace("\"apiVersion\":1", "\"apiVersion\":2")))
        rejected(pack(p = profiles.replace("1.5", "900")))
        rejected(pack(p = profiles.replace("1.5", "\"1.5\"")))
        rejected(pack(p = profiles.replace("\"pen\"", "\"eraser\"")))
        rejected(pack(p = profiles.replace("#4189DD", "#WRONG!")))
        rejected(pack(p = """{"profiles":[{"id":"x","name":"A","tool":"pen"},{"id":"x","name":"B","tool":"pen"}]}"""))
        assertFalse(DeclarativePackages.isCommand("core:undo"))
        assertFalse(DeclarativePackages.isCommand("../x:logic"))
    }
}
