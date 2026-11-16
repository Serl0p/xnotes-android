package com.xnotes.plugins

import com.xnotes.core.model.Rgba
import com.xnotes.core.tools.Tool
import com.xnotes.core.tools.ToolConfig
import com.xnotes.core.tools.ToolConversions
import com.xnotes.core.tools.ToolDefaults
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

internal data class PenProfile(val id: String, val name: String, val tool: Tool, val config: ToolConfig)
internal data class DeclarativePackage(
    val id: String, val name: String, val version: String, val description: String,
    val profiles: List<PenProfile>, val bytes: ByteArray,
    val feature: String? = null,
    val script: String? = null,
    val scriptDefaults: Map<String, String> = emptyMap(),
    val toolbar: String? = null,
    val toolbarScript: Boolean = false,
    val settingsUi: Map<String, PluginSettingsSchema.Field> = emptyMap(),
    val paletteAccess: Boolean = false,
)

/** API 1 accepts only two root JSON files. Nothing is extracted, evaluated or executed. */
internal object DeclarativePackages {
    const val MAX_ARCHIVE = 128 * 1024
    private const val MAX_ENTRY = 64 * 1024
    private val idPattern = Regex("[a-z][a-z0-9.-]{0,63}")
    private val commandPattern = Regex("[a-z][a-z0-9_.-]{0,63}")
    private val versionPattern = Regex("[0-9]{1,9}\\.[0-9]{1,9}\\.[0-9]{1,9}")
    private val permissions = setOf("tools.configure", "commands.register")
    private val allowedFiles = setOf("manifest.json", "profiles.json", "main.js", "toolbar.json")
    private val profilesFiles = setOf("manifest.json", "profiles.json")

    fun readLimited(input: InputStream, limit: Int = MAX_ARCHIVE): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            require(out.size() + n <= limit) { "Plugin exceeds size limit" }
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    fun isCommand(id: String): Boolean {
        val parts = id.split(':')
        return parts.size == 2 && parts[0] != "core" &&
            idPattern.matches(parts[0]) && commandPattern.matches(parts[1])
    }

    fun parse(bytes: ByteArray): DeclarativePackage {
        require(bytes.size in 1..MAX_ARCHIVE) { "Invalid plugin size" }
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory && entry.name in allowedFiles && entry.name !in entries) {
                    "Unsupported or duplicate package entry"
                }
                entries[entry.name] = readLimited(zip, MAX_ENTRY)
                zip.closeEntry()
            }
        }
        require("manifest.json" in entries) { "Expected manifest.json" }
        fun json(name: String) = JSONObject(entries.getValue(name).toString(Charsets.UTF_8))
        val manifest = json("manifest.json")
        manifest.only("id", "name", "version", "description", "apiVersion", "type", "permissions", "feature", "settings", "settingsUi")
        val id = manifest.string("id", 64)
        require(idPattern.matches(id) && id != "core") { "Invalid plugin ID" }
        val name = manifest.string("name", 80)
        val version = manifest.string("version", 32)
        require(versionPattern.matches(version)) { "Expected a version such as 1.0.0" }
        val api = manifest.get("apiVersion")
        require(!manifest.has("settingsUi") || (api == 8 && manifest.get("type") in setOf("javascript","toolbar","toolbar-javascript"))) { "Visual settings require API 8" }
        if (manifest.get("type") in setOf("toolbar", "toolbar-javascript")) {
            val scripted = manifest.get("type") == "toolbar-javascript"
            val required = if (scripted) setOf("scripts.execute", "ui.toolbar", "ui.controls") else setOf("ui.toolbar", "ui.controls")
            require(api in setOf(if (scripted) 7 else 6, 8)) { "Unsupported toolbar API" }
            require(!manifest.has("feature") && NativeFeature.entries.none { it.owner == id })
            val file = if (scripted) "main.js" else "toolbar.json"
            require(entries.keys == setOf("manifest.json", file)) { "Unexpected toolbar package entries" }
            val declared = manifest.getJSONArray("permissions")
            require(declared.length() == required.size &&
                (0 until declared.length()).map { declared.get(it) }.toSet() == required) { "Unsupported toolbar permissions" }
            val source = entries.getValue(file).toString(Charsets.UTF_8)
            require(source.isNotBlank() && source.length <= 32768) { "Invalid toolbar source size" }
            val defaults = readSettings(manifest)
            if (!scripted) {
                ToolbarSpecs.parse(source, false, false)
                ToolbarSpecs.parse(source, false, true)
                ToolbarSpecs.parse(source, true, false)
            }
            return DeclarativePackage(id, name, version, manifest.string("description", 300), emptyList(),
                bytes.copyOf(), scriptDefaults = defaults, toolbar = source, toolbarScript = scripted,
                settingsUi = PluginSettingsSchema.read(manifest, defaults))
        }
        if (manifest.get("type") == "javascript") {
            require(api in setOf(4, 5, 8) && entries.keys == setOf("manifest.json", "main.js") &&
                !manifest.has("feature") && NativeFeature.entries.none { it.owner == id }) { "Incompatible script package" }
            val declared = manifest.getJSONArray("permissions")
            val baseline = setOf("scripts.execute", "ui.popup", "tools.select", "commands.register")
            val granted = (0 until declared.length()).map { declared.get(it) }.toSet()
            val palette = api == 8 && granted == baseline + "tools.color"
            val required = if (palette) baseline + "tools.color" else baseline
            require(declared.length() == required.size && granted == required) {
                "Unsupported script permissions"
            }
            val source = entries.getValue("main.js").toString(Charsets.UTF_8)
            require(source.isNotBlank() && source.length <= 32768) { "Invalid script size" }
            val defaults = readSettings(manifest)
            return DeclarativePackage(id, name, version, manifest.string("description", 300),
                emptyList(), bytes.copyOf(), script = source, scriptDefaults = defaults,
                settingsUi = PluginSettingsSchema.read(manifest, defaults), paletteAccess = palette)
        }
        require(manifest.get("type") == "declarative" && !manifest.has("settings")) { "Unsupported plugin type" }
        val feature = if (manifest.has("feature")) manifest.string("feature", 64) else null
        val required = if (feature == null) {
            require(manifest.get("apiVersion") == 1 && entries.keys == profilesFiles)
            require(NativeFeature.entries.none { it.owner == id }) { "Reserved feature ID" }
            permissions
        } else {
            val native = requireNotNull(NativeFeature.find(feature)) { "Unsupported native feature" }
            require((manifest.get("apiVersion") == native.api || (native == NativeFeature.STYLUS && manifest.get("apiVersion") == 5)) && id == native.owner &&
                entries.keys == setOf("manifest.json")) { "Incompatible feature package" }
            native.permissions
        }
        val declared = manifest.getJSONArray("permissions")
        require(declared.length() == required.size &&
            (0 until declared.length()).map { declared.get(it) }.toSet() == required) {
            "Unsupported or missing permissions"
        }
        if (feature != null) return DeclarativePackage(id, name, version,
            manifest.string("description", 300), emptyList(), bytes.copyOf(), feature)
        val root = json("profiles.json")
        root.only("profiles")
        val array = root.getJSONArray("profiles")
        require(array.length() in 1..24) { "Expected 1 to 24 profiles" }
        val profiles = (0 until array.length()).map { profile(array.getJSONObject(it)) }
        require(profiles.map { it.id }.toSet().size == profiles.size) { "Duplicate profile IDs" }
        return DeclarativePackage(id, name, version, manifest.string("description", 300), profiles, bytes.copyOf())
    }

    private fun readSettings(manifest: JSONObject): Map<String, String> {
        val defaults = linkedMapOf<String, String>()
        if (manifest.has("settings")) {
            val settings = manifest.getJSONObject("settings")
            require(settings.length() <= 8) { "Too many settings" }
            for (key in settings.keys()) {
                require(commandPattern.matches(key)) { "Invalid setting key" }
                val value = settings.get(key)
                require(value is String && value.length <= 160 && value.none { it.isISOControl() }) { "Invalid setting" }
                defaults[key] = value
            }
        }
        return defaults
    }

    private fun profile(o: JSONObject): PenProfile {
        o.only("id", "name", "tool", "width", "color", "pressure", "minPressure", "neon",
            "neonStrength", "alpha", "scale", "stabilize", "directionStrength", "speedStrength",
            "taperMin", "dashLength", "dashGap")
        val id = o.string("id", 64)
        require(commandPattern.matches(id)) { "Invalid profile ID" }
        val tool = Tool.fromId(o.string("tool", 32))
        require(tool != null && tool.isStroke) { "Unsupported pen type" }
        val d = ToolDefaults.configFor(tool)
        val range = ToolConversions.widthRange(tool)
        val color = if (o.has("color")) {
            val hex = o.string("color", 7)
            require(Regex("#[0-9a-fA-F]{6}").matches(hex)) { "Expected #RRGGBB color" }
            requireNotNull(Rgba.fromHex(hex))
        } else null
        val config = d.copy(
            baseWidth = o.number("width", d.baseWidth, range.start, range.endInclusive),
            pressureEnabled = o.bool("pressure", d.pressureEnabled),
            pressureMinFactor = o.number("minPressure", d.pressureMinFactor, 0.1, 1.0),
            colorOverride = color,
            neon = o.bool("neon", d.neon),
            neonStrength = o.number("neonStrength", d.neonStrength, 0.0, 1.0),
            highlighterAlpha = o.number("alpha", d.highlighterAlpha, 0.1, 1.0),
            scale = o.bool("scale", d.scale),
            stabilize = o.bool("stabilize", d.stabilize),
            directionStrength = o.number("directionStrength", d.directionStrength, 0.0, 0.95),
            speedStrength = o.number("speedStrength", d.speedStrength, 0.0, 0.92),
            taperMinFactor = o.number("taperMin", d.taperMinFactor, 0.0, 1.0),
            dashLength = o.number("dashLength", d.dashLength, 1.0, 100.0),
            dashGap = o.number("dashGap", d.dashGap, 1.0, 100.0),
        )
        return PenProfile(id, o.string("name", 80), tool, config)
    }

    private fun JSONObject.only(vararg allowed: String) {
        require(keys().asSequence().all { it in allowed }) { "Unknown package field" }
    }
    private fun JSONObject.string(key: String, max: Int): String {
        val s = get(key)
        require(s is String && s.isNotBlank() && s.length <= max &&
            s.none { it.isISOControl() }) { "Invalid $key" }
        return s
    }
    private fun JSONObject.number(key: String, default: Double, low: Double, high: Double): Double {
        if (!has(key)) return default
        val value = get(key)
        require(value is Number) { "Expected number for $key" }
        return value.toDouble().also { require(it.isFinite() && it in low..high) { "$key out of range" } }
    }
    private fun JSONObject.bool(key: String, default: Boolean): Boolean {
        if (!has(key)) return default
        val value = get(key)
        require(value is Boolean) { "Expected boolean for $key" }
        return value
    }
}
