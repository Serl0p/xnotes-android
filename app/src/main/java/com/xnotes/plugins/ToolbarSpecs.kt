package com.xnotes.plugins

import com.xnotes.core.tools.ToolbarItem
import com.xnotes.core.tools.ToolbarLayout
import com.xnotes.settings.ToolbarLook
import com.xnotes.settings.ToolbarPosition
import com.xnotes.settings.ToolbarSize
import org.json.JSONArray
import org.json.JSONObject

internal data class ToolbarStyleSpec(
    val position: String? = null, val size: String? = null, val floating: Boolean? = null,
    val background: String? = null, val radius: Int? = null, val spacing: Int = 0,
) {
    fun apply(base: ToolbarLook) = base.copy(
        position = position?.let(ToolbarPosition::fromId) ?: base.position,
        size = size?.let(ToolbarSize::fromId) ?: base.size,
        floating = floating ?: base.floating,
    )
}
internal sealed interface ToolbarNode {
    data class Native(val item: ToolbarItem) : ToolbarNode
    data class Width(val value: Double) : ToolbarNode
    data class Group(val label: String, val tools: List<ToolbarItem>) : ToolbarNode
    data class Command(val id: String, val label: String) : ToolbarNode
    data object Collapse : ToolbarNode
}
internal data class ToolbarSpec(val style: ToolbarStyleSpec, val rows: List<List<ToolbarNode>>, val collapsible: Boolean)

internal object ToolbarSpecs {
    private val groupable = setOf("pen","dashed","calligraphy","speed","taper","highlighter","eraser","pan","select","lasso","shape","markup")
    fun available(canvas: Boolean, pdf: Boolean): Set<ToolbarItem> =
        (if (canvas) ToolbarLayout.CANVAS_ITEMS else ToolbarLayout.NOTE_ITEMS)
            .filter { it != ToolbarItem.MARKUP || pdf && !canvas }.toSet()

    /** Validates every entry against a small native component vocabulary. No HTML or executable callbacks. */
    fun parse(raw: String, canvas: Boolean, pdf: Boolean): ToolbarSpec {
        require(raw.length <= 32768) { "Toolbar description too large" }
        val root = JSONObject(raw)
        root.only("style","rows","paged","infinite","pdf","collapsible")
        val surface = if (canvas) "infinite" else if (pdf && root.has("pdf")) "pdf" else "paged"
        val obj = if (root.has(surface)) root.getJSONObject(surface) else root
        if (obj !== root) obj.only("style","rows","collapsible")
        val style = obj.optJSONObject("style") ?: root.optJSONObject("style") ?: JSONObject()
        style.only("position","size","floating","background","radius","spacing")
        val pos = style.optionalString("position")?.also { require(it in setOf("top","bottom","left","right")) }
        val size = style.optionalString("size")?.also { require(it in setOf("compact","regular","comfortable")) }
        val bg = style.optionalString("background")?.also { require(Regex("#[0-9a-fA-F]{6}").matches(it)) }
        val radius = if (style.has("radius")) style.int("radius", 0, 32) else null
        val spacing = if (style.has("spacing")) style.int("spacing", 0, 8) else 0
        val floating = if (style.has("floating")) style.strictBool("floating") else null
        val collapse = if (obj.has("collapsible")) obj.strictBool("collapsible") else false
        val allowed = available(canvas, pdf)
        val seen = mutableSetOf<ToolbarItem>()
        val rows = obj.getJSONArray("rows")
        require(rows.length() in 1..3) { "Use one to three toolbar rows" }
        var total = 0
        val result = (0 until rows.length()).mapNotNull { row ->
            val arr = rows.getJSONArray(row)
            require(arr.length() in 1..36)
            val nodes = (0 until arr.length()).mapNotNull { i ->
                require(++total <= 64) { "Too many toolbar controls" }
                val entry = arr.get(i)
                when (entry) {
                    is String -> {
                        val item = requireNotNull(ToolbarItem.fromId(entry)) { "Unknown toolbar item: $entry" }
                        if (item !in allowed) null else {
                            require(seen.add(item)) { "Duplicate native item: $entry" }
                            ToolbarNode.Native(item)
                        }
                    }
                    is JSONObject -> when(entry.get("type")) {
                        "width" -> {
                            entry.only("type","value")
                            val value = entry.get("value")
                            require(value is Number && value.toDouble().isFinite() && value.toDouble() in 0.1..100.0)
                            ToolbarNode.Width(value.toDouble())
                        }
                        "group" -> {
                            entry.only("type","label","tools")
                            val list = entry.getJSONArray("tools")
                            require(list.length() in 1..12)
                            val tools = (0 until list.length()).mapNotNull { n ->
                                val id = list.get(n)
                                require(id is String && id in groupable)
                                ToolbarItem.fromId(id)?.takeIf { it in allowed }
                            }.distinct()
                            if (tools.isEmpty()) null else ToolbarNode.Group(entry.label("label"), tools)
                        }
                        "command" -> {
                            entry.only("type","id","label")
                            val id = entry.label("id")
                            require(DeclarativePackages.isCommand(id)) { "Expected namespaced plugin command" }
                            ToolbarNode.Command(id, entry.label("label"))
                        }
                        else -> error("Unknown toolbar component")
                    }
                    else -> error("Invalid toolbar entry")
                }
            }
            nodes.takeIf { it.isNotEmpty() }
        }.toMutableList()
        // Always retain the real Home control even if a package forgets it or requests collapse.
        val withoutHome = result.map { row -> row.filterNot { it == ToolbarNode.Native(ToolbarItem.HOME) } }.filter { it.isNotEmpty() }.toMutableList()
        if (withoutHome.isEmpty()) withoutHome.add(emptyList())
        withoutHome[0] = listOf(ToolbarNode.Native(ToolbarItem.HOME)) +
            (if (collapse) listOf(ToolbarNode.Collapse) else emptyList()) + withoutHome[0]
        return ToolbarSpec(ToolbarStyleSpec(pos,size,floating,bg,radius,spacing), withoutHome, collapse)
    }
    private fun JSONObject.only(vararg fields: String) {
        require(keys().asSequence().all { it in fields }) { "Unknown toolbar field" }
    }
    private fun JSONObject.label(key: String): String {
        val v = get(key)
        require(v is String && v.isNotBlank() && v.length <= 80 && v.none { it.isISOControl() })
        return v
    }
    private fun JSONObject.optionalString(key: String): String? = if (has(key)) label(key) else null
    private fun JSONObject.int(key: String, min: Int, max: Int): Int {
        val v = get(key)
        require(v is Number && v.toDouble() in min.toDouble()..max.toDouble() && v.toDouble() == v.toInt().toDouble())
        return v.toInt()
    }
    private fun JSONObject.strictBool(key: String): Boolean {
        val v = get(key); require(v is Boolean); return v
    }
}
