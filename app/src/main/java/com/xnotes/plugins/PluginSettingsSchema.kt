package com.xnotes.plugins

import org.json.JSONObject

/** A platform-neutral description of a native settings form. Values remain strings for API 4–7 compatibility. */
internal object PluginSettingsSchema {
    val tools = linkedMapOf("pen" to "Pen", "dashed" to "Dashed pen", "calligraphy" to "Calligraphy",
        "speed" to "Speed pen", "taper" to "Taper pen", "highlighter" to "Highlighter",
        "eraser" to "Eraser", "pan" to "Pan", "select" to "Select", "lasso" to "Lasso",
        "shape" to "Shape", "markup" to "PDF text markup")
    private val hex = Regex("#[0-9a-fA-F]{6}")
    data class Choice(val value: String, val label: String)
    data class Field(val kind: String, val label: String, val choices: List<Choice> = emptyList(),
        val min: Int = 0, val max: Int = 1, val step: Int = 1, val optional: Boolean = false,
        val preview: Boolean = false) {
        fun values(value: String) = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        fun valid(value: String): Boolean {
            if (value.length > 160 || value.any { it.isISOControl() }) return false
            val items = values(value)
            return when (kind) {
                "text" -> true
                "toggle" -> value == "true" || value == "false"
                "select", "tool-picker" -> choices.any { it.value == value }
                "slider" -> value.toIntOrNull()?.let { it in min..max && (it - min) % step == 0 } == true
                "color" -> value.isEmpty() && optional || hex.matches(value)
                "tool-list", "reorder-list" -> items.size in min..max && items.distinct().size == items.size &&
                    items.all { v -> choices.any { it.value == v } }
                "color-list" -> items.size in min..max && items.map { it.lowercase() }.distinct().size == items.size &&
                    items.all { hex.matches(it) }
                else -> false
            }
        }
    }

    fun read(manifest: JSONObject, defaults: Map<String,String>): Map<String,Field> {
        if (!manifest.has("settingsUi")) return emptyMap()
        val schema = manifest.getJSONObject("settingsUi")
        require(schema.length() <= 8)
        return schema.keys().asSequence().associateWith { key ->
            require(key in defaults) { "Settings UI key has no default" }
            val obj = schema.getJSONObject(key)
            obj.only("type","label","choices","min","max","step","optional","preview")
            val kind = obj.string("type", 24)
            require(kind in setOf("text","toggle","select","slider","tool-picker","tool-list","reorder-list","color","color-list"))
            val choices = when (kind) {
                "tool-list","tool-picker" -> {
                    require(!obj.has("choices")) { "Tool choices use the host tool catalog" }
                    tools.map { Choice(it.key,it.value) }
                }
                "select","reorder-list" -> {
                    val a = obj.getJSONArray("choices")
                    require(a.length() in 1..32)
                    (0 until a.length()).map { i ->
                        val c = a.getJSONObject(i)
                        c.only("value","label")
                        val v = c.string("value", 48)
                        require(',' !in v)
                        Choice(v,c.string("label",80))
                    }.also { require(it.map { c -> c.value }.distinct().size == it.size) }
                }
                else -> { require(!obj.has("choices")); emptyList() }
            }
            val list = kind in setOf("tool-list","reorder-list","color-list")
            val slider = kind == "slider"
            val min = if (slider) obj.int("min",0,-1000,1000) else if (list) obj.int("min",0,0,12) else 0
            val max = if (slider) obj.int("max",100,-1000,1000) else if (list) obj.int("max",8,1,12) else 1
            val step = if (slider) obj.int("step",1,1,100) else 1
            require(max >= min && (!slider || (max > min && (max-min)%step==0 && (max-min)/step<=200)))
            require(kind !in setOf("tool-list","reorder-list") || max <= choices.size)
            val optional = obj.flag("optional")
            val preview = obj.flag("preview")
            require(!preview || kind == "tool-list")
            Field(kind,obj.string("label",80),choices,min,max,step,optional,preview).also {
                require(it.valid(defaults.getValue(key))) { "Invalid default for $key" }
            }
        }
    }
    private fun JSONObject.only(vararg keys: String) {
        require(this.keys().asSequence().all { it in keys }) { "Unknown settings UI field" }
    }
    private fun JSONObject.string(key:String,max:Int): String {
        val v=get(key); require(v is String && v.isNotBlank() && v.length<=max && v.none { it.isISOControl() }); return v
    }
    private fun JSONObject.int(key:String,default:Int,low:Int,high:Int): Int {
        if (!has(key)) return default
        val v=get(key); require(v is Number && v.toDouble() in low.toDouble()..high.toDouble() && v.toDouble()==v.toInt().toDouble()); return v.toInt()
    }
    private fun JSONObject.flag(key:String):Boolean {
        if(!has(key)) return false
        val v=get(key); require(v is Boolean); return v
    }
}
