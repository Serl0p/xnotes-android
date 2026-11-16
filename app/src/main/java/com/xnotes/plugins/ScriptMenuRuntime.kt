package com.xnotes.plugins

import android.content.Context
import androidx.javascriptengine.IsolateStartupParameters
import androidx.javascriptengine.JavaScriptSandbox
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class ScriptMenuItem(val label: String, val tool: String)
internal data class ScriptMenu(val title: String, val items: List<ScriptMenuItem>, val layout: String = "list",
    val center: String = "close", val size: String = "regular", val colors: List<String> = emptyList())
internal data class ScriptMenuRequest(
    val pkg: DeclarativePackage, val activeTool: String, val canvas: Boolean,
    val settings: Map<String, String>, val tools: List<String>,
    val anchor: com.xnotes.core.geometry.Pt,
)

/** A fresh isolated process per invocation. Only JSON data crosses the boundary.
 * No Android objects, documents, callback bridge, filesystem or network access is exposed. */
internal object ScriptMenuRuntime {
    private val lock = Any()
    const val MAX_RETURN = 16 * 1024

    fun parseResult(raw: String, tools: Collection<String>, paletteAccess: Boolean = false): ScriptMenu {
        require(raw.length <= MAX_RETURN) { "Script result too large" }
        val obj = JSONObject(raw)
        require(obj.keys().asSequence().all { it in setOf("title", "items", "layout", "center", "size", "colors") }) { "Unsupported menu field" }
        fun label(value: Any?, max: Int): String {
            require(value is String && value.isNotBlank() && value.length <= max && value.none { it.isISOControl() }) { "Invalid menu label" }
            return value
        }
        val title = label(obj.get("title"), 80)
        val list = obj.getJSONArray("items")
        require(list.length() in 1..16) { "Menu must contain 1–16 tools" }
        val items = (0 until list.length()).map { i ->
            val item = list.getJSONObject(i)
            require(item.keys().asSequence().all { it in setOf("label", "tool") }) { "Unsupported action" }
            val tool = item.get("tool")
            require(tool is String && tool in tools) { "Unsupported tool action" }
            ScriptMenuItem(label(item.get("label"), 60), tool)
        }
        val layout = if (obj.has("layout")) obj.get("layout") else "list"
        require(layout is String && layout in setOf("list", "radial")) { "Unsupported menu layout" }
        val center = if (obj.has("center")) obj.get("center") else "close"
        require(center is String && center in setOf("close","palette","swatches"))
        require(center == "close" || (paletteAccess && layout == "radial")) { "Palette permission required" }
        val size = if (obj.has("size")) obj.get("size") else "regular"
        require(size is String && size in setOf("compact","regular","large"))
        val colors = if (obj.has("colors")) {
            require(paletteAccess && center != "close")
            val a = obj.getJSONArray("colors")
            require(a.length() <= 6)
            (0 until a.length()).map { i ->
                val c = a.get(i); require(c is String && Regex("#[0-9a-fA-F]{6}").matches(c)); c
            }.distinct()
        } else emptyList()
        require(layout != "radial" || items.size <= if (center == "close") 8 else 10) { "Too many radial entries" }
        require(items.map { it.tool }.distinct().size == items.size) { "Duplicate tools" }
        return ScriptMenu(title, items, layout, center, size, colors)
    }

    suspend fun run(context: Context, request: ScriptMenuRequest): ScriptMenu {
        val input = JSONObject().put("activeTool", request.activeTool)
            .put("surface", if (request.canvas) "infinite" else "paged")
            .put("tools", JSONArray(request.tools)).put("settings", JSONObject(request.settings)).toString()
        return parseResult(evaluate(context, requireNotNull(request.pkg.script), input), request.tools, request.pkg.paletteAccess)
    }

    /** Shared bounded evaluator; toolbar and menu calls cannot run concurrently. */
    suspend fun evaluate(context: Context, source: String, input: String): String = withContext(Dispatchers.IO) {
        synchronized(lock) {
            check(JavaScriptSandbox.isSupported()) { "Android JavaScript sandbox unavailable; update Android System WebView" }
            val connection = JavaScriptSandbox.createConnectedInstanceAsync(context.applicationContext)
            val sandbox = try { connection.get(4, TimeUnit.SECONDS) } catch (e: Exception) {
                connection.addListener({ runCatching { connection.get().close() } }, Executor { it.run() })
                throw IllegalStateException("Could not connect to JavaScript sandbox", e)
            }
            try {
                check(sandbox.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_ISOLATE_MAX_HEAP_SIZE) &&
                    sandbox.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_ISOLATE_TERMINATION)) {
                    "Device sandbox lacks required memory/termination safeguards"
                }
                val limits = IsolateStartupParameters()
                limits.setMaxHeapSizeBytes(16L * 1024 * 1024)
                if (sandbox.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_EVALUATE_WITHOUT_TRANSACTION_LIMIT))
                    limits.setMaxEvaluationReturnSizeBytes(MAX_RETURN)
                val isolate = sandbox.createIsolate(limits)
                try {
                    val code = "JSON.stringify((function(context) { 'use strict';\n" + source + "\n})(" + input + "))"
                    val result = isolate.evaluateJavaScriptAsync(code).get(2, TimeUnit.SECONDS)
                    require(result.length <= MAX_RETURN) { "Script result too large" }
                    result
                } finally { isolate.close() }
            } finally { sandbox.close() }
        }
    }
}
