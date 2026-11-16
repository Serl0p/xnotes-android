package com.xnotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.core.model.Rgba
import com.xnotes.plugins.DeclarativePackage
import com.xnotes.plugins.PluginSettingsSchema
import com.xnotes.plugins.ScriptMenu
import com.xnotes.plugins.ScriptMenuItem
import com.xnotes.ui.icons.XnotesIcons
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor
import kotlin.math.roundToInt

/** A reusable native form; no plugin-specific screen and no script evaluation while editing. */
@Composable
internal fun PluginVisualSettings(pkg:DeclarativePackage, saved:Map<String,String>, save:(Map<String,String>)->Unit) {
    var draft by remember(pkg,saved){mutableStateOf(saved.toMap())}
    fun change(key:String,value:String){draft=draft+(key to value)}
    val valid=draft.all{(k,v)->pkg.settingsUi[k]?.valid(v)!=false}
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        for((key,default) in pkg.scriptDefaults){
            androidx.compose.runtime.key(key){
                val field=pkg.settingsUi[key]
                val value=draft[key]?:default
                Text(field?.label?:key,fontSize=13.sp)
                when(field?.kind){
                    "tool-list","reorder-list" -> ReorderSetting(field,value){change(key,it)}
                    "tool-picker","select" -> ChoiceSetting(field,value){change(key,it)}
                    "toggle" -> Switch(checked=value=="true",onCheckedChange={change(key,it.toString())})
                    "slider" -> {
                        Text(value,fontSize=12.sp)
                        Slider(value=(value.toFloatOrNull()?:field.min.toFloat()).coerceIn(field.min.toFloat(),field.max.toFloat()),
                            onValueChange={v->val n=field.min+((v-field.min)/field.step).roundToInt()*field.step
                                change(key,n.coerceIn(field.min,field.max).toString())},
                            valueRange=field.min.toFloat()..field.max.toFloat(),steps=((field.max-field.min)/field.step-1).coerceAtLeast(0))
                    }
                    "color" -> ColorSetting(field,value){change(key,it)}
                    "color-list" -> ColorListSetting(field,value){change(key,it)}
                    else -> OutlinedTextField(value,{change(key,it.take(160))},singleLine=true,modifier=Modifier.fillMaxWidth())
                }
            }
        }
        pkg.settingsUi.entries.firstOrNull{it.value.preview}?.let{(key,field)->
            val center=draft["center"]?.takeIf{it in listOf("palette","swatches","close")} ?: "palette"
            val cap=if(center=="close")8 else 10
            val size=draft["size"]?.takeIf{it in listOf("compact","regular","large")} ?: "regular"
            val tools=field.values(draft[key].orEmpty()).take(draft["limit"]?.toIntOrNull()?.coerceIn(1,cap)?:cap)
            if(tools.isNotEmpty()){
                Text("Live menu preview",fontSize=13.sp)
                val baseItems=tools.map{ScriptMenuItem(PluginSettingsSchema.tools[it]?:it,it)}
                var previewTool by remember(baseItems){mutableStateOf(baseItems.first().tool)}
                var previewInk by remember{mutableStateOf(Rgba(65,137,221))}
                val items=if(draft["active-first"]=="true")baseItems.sortedBy{it.tool!=previewTool} else baseItems
                val menu=ScriptMenu("Preview",items,"radial",center,size)
                BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){
                    val extent=minOf(maxWidth.value,radialExtent(center,size))
                    if(extent>=(if(center=="close")226f else 260f)) PaletteRadial(menu,previewTool,extent,previewInk,
                        listOf(Rgba(20,20,20),Rgba(255,255,255),Rgba(65,137,221),Rgba(220,80,91)),
                        draft["colors"].orEmpty().split(',').mapNotNull{Rgba.fromHex(it)}, {},
                        {previewTool=it},{c,_->previewInk=c})
                }
                Text("Preview only; tools and colors here do not change your note.",fontSize=11.sp)
            }
        }
        Row {
            TextButton(enabled=valid && draft!=saved,onClick={save(draft)}){Text("Save settings")}
            TextButton(onClick={draft=pkg.scriptDefaults.toMap()}){Text("Reset defaults")}
        }
        if(!valid)Text("Check settings before saving.",fontSize=12.sp)
    }
}

@Composable
private fun ChoiceSetting(field:PluginSettingsSchema.Field,value:String,change:(String)->Unit){
    var open by remember{mutableStateOf(false)}
    Box{
        TextButton(onClick={open=true}){
            val c=field.choices.firstOrNull{it.value==value}
            if(field.kind=="tool-picker" && c!=null) ScriptToolIcon(ScriptMenuItem(c.label,c.value),false)
            Text(c?.label?:value)
            Icon(XnotesIcons.chevronDown,"Choose")
        }
        DropdownMenu(expanded=open,onDismissRequest={open=false}){
            field.choices.forEach{c->
                DropdownMenuItem(text={Text(c.label)},leadingIcon=if(field.kind=="tool-picker"){
                    {ScriptToolIcon(ScriptMenuItem(c.label,c.value),false)}
                }else null,onClick={open=false;change(c.value)})
            }
        }
    }
}

@Composable
private fun ReorderSetting(field:PluginSettingsSchema.Field,value:String,change:(String)->Unit){
    val items=field.values(value)
    val latest by rememberUpdatedState(items)
    val update by rememberUpdatedState(change)
    val palette=LocalPalette.current
    val threshold=with(LocalDensity.current){48.dp.toPx()}
    fun move(id:String,delta:Int){
        val list=latest.toMutableList()
        val from=list.indexOf(id);if(from<0)return
        val to=(from+delta).coerceIn(0,list.lastIndex)
        if(from!=to){list.add(to,list.removeAt(from));update(list.joinToString(","))}
    }
    Column {
        Text("Hold the handle to reorder, or use arrows.",fontSize=11.sp)
        for(id in items){
            key(id){
                val c=field.choices.firstOrNull{it.value==id}
                val label=c?.label?:id
                Row(Modifier.fillMaxWidth().height(48.dp),verticalAlignment=Alignment.CenterVertically){
                    Box(Modifier.size(36.dp).pointerInput(id,threshold){
                        var distance=0f
                        detectDragGesturesAfterLongPress(onDragStart={distance=0f},onDragEnd={distance=0f},
                            onDragCancel={distance=0f}){event,amount->
                            event.consume();distance+=amount.y
                            if(distance>=threshold){move(id,1);distance-=threshold}
                            else if(distance<=-threshold){move(id,-1);distance+=threshold}
                        }
                    },contentAlignment=Alignment.Center){Icon(XnotesIcons.grip,"Drag $label")}
                    if(field.kind=="tool-list")ScriptToolIcon(ScriptMenuItem(label,id),false)
                    Text(label,Modifier.weight(1f),fontSize=13.sp)
                    IconButton(onClick={move(id,-1)},enabled=items.indexOf(id)>0,modifier=Modifier.size(36.dp)){
                        Icon(XnotesIcons.chevronUp,"Move $label up")
                    }
                    IconButton(onClick={move(id,1)},enabled=items.indexOf(id)<items.lastIndex,modifier=Modifier.size(36.dp)){
                        Icon(XnotesIcons.chevronDown,"Move $label down")
                    }
                    IconButton(onClick={change(items.filterNot{it==id}.joinToString(","))},enabled=items.size>field.min,modifier=Modifier.size(36.dp)){
                        Icon(XnotesIcons.close,"Remove $label")
                    }
                }
                HorizontalDivider(color=palette.border.toComposeColor())
            }
        }
        var add by remember{mutableStateOf(false)}
        Box{
            TextButton(enabled=items.size<field.max && field.choices.any{it.value !in items},onClick={add=true}){
                Icon(XnotesIcons.plus,"Add"); Text("Add tool or item")
            }
            DropdownMenu(expanded=add,onDismissRequest={add=false}){
                field.choices.filter{it.value !in items}.forEach{c->
                    DropdownMenuItem(text={Text(c.label)},leadingIcon=if(field.kind=="tool-list"){
                        {ScriptToolIcon(ScriptMenuItem(c.label,c.value),false)}
                    }else null,onClick={add=false;change((items+c.value).joinToString(","))})
                }
            }
        }
    }
}

@Composable
private fun ColorSetting(field:PluginSettingsSchema.Field,value:String,change:(String)->Unit) {
    var open by remember{mutableStateOf(false)}
    Row(verticalAlignment=Alignment.CenterVertically){
        Box{
            TextButton(onClick={open=true}){
                Rgba.fromHex(value)?.let{c->Box(Modifier.size(24.dp).background(c.toComposeColor(),RoundedCornerShape(6.dp)))}
                Text(value.ifEmpty{"Choose color"})
            }
            if(open)ColorPickerPopup(Rgba.fromHex(value),emptyList(),{open=false}){change(Rgba.toHex(it))}
        }
        if(field.optional)TextButton(onClick={change("")}){Text("Default")}
    }
}

@Composable
private fun ColorListSetting(field:PluginSettingsSchema.Field,value:String,change:(String)->Unit) {
    val items=field.values(value)
    Column{
        items.chunked(4).forEachIndexed{row,colors->
            Row{
                colors.forEachIndexed{i,hex->
                    val index=row*4+i
                    key(index){
                        var open by remember{mutableStateOf(false)}
                        Column(horizontalAlignment=Alignment.CenterHorizontally){
                            Box{
                                PaletteChip(Rgba.fromHex(hex)?:Rgba(0,0,0),34f,"Edit $hex"){open=true}
                                if(open)ColorPickerPopup(Rgba.fromHex(hex),emptyList(),{open=false}){c->
                                    val next=items.toMutableList();next[index]=Rgba.toHex(c)
                                    if(next.map{it.lowercase()}.distinct().size==next.size)change(next.joinToString(","))
                                }
                            }
                            IconButton(onClick={change(items.filterIndexed{n,_->n!=index}.joinToString(","))},
                                enabled=items.size>field.min,modifier=Modifier.size(32.dp)){Icon(XnotesIcons.close,"Remove $hex")}
                        }
                    }
                }
            }
        }
        var add by remember{mutableStateOf(false)}
        Box{
            TextButton(enabled=items.size<field.max,onClick={add=true}){Text("Add color")}
            if(add)ColorPickerPopup(null,emptyList(),{add=false}){c->
                val hex=Rgba.toHex(c)
                if(items.none{it.equals(hex,true)})change((items+hex).joinToString(","))
                add=false
            }
        }
    }
}
