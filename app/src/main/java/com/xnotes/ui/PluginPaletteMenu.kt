package com.xnotes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.core.model.Rgba
import com.xnotes.plugins.ScriptMenu
import com.xnotes.plugins.ScriptMenuRequest
import com.xnotes.ui.icons.XnotesIcons
import com.xnotes.ui.theme.ColorMath
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor
import kotlin.math.cos
import kotlin.math.sin

/** All palette UI lives behind the plugin adapter; native ink/picker implementations are reused. */
@Composable
internal fun PluginPaletteOverlay(request: ScriptMenuRequest, menu: ScriptMenu, host: ToolPopupHost,
    dismiss: () -> Unit, choose: (String) -> Unit, apply: (Rgba,Int?) -> Unit) {
    val active = host.hostToolbarColors.getOrNull(host.hostActiveColorIndex) ?: Rgba(0,0,0)
    val swatches = host.shownSwatches.take(6)
    val custom = menu.colors.mapNotNull { Rgba.fromHex(it) }
    val desired = radialExtent(menu.center,menu.size).dp
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val extent = minOf(desired,maxWidth-8.dp,maxHeight-8.dp)
        if (extent >= (if(menu.center=="close") 226.dp else 280.dp)) {
            BackHandler(onBack = dismiss)
            val density = LocalDensity.current
            val half = extent.value / 2
            val ax = with(density) { request.anchor.x.toFloat().toDp().value }
            val ay = with(density) { request.anchor.y.toFloat().toDp().value }
            val cx = ax.coerceIn(half,maxWidth.value-half)
            val cy = ay.coerceIn(half,maxHeight.value-half)
            Box(Modifier.fillMaxSize().clickable(onClickLabel="Dismiss tool menu",onClick=dismiss))
            Box(Modifier.offset((cx-half).dp,(cy-half).dp).size(extent)) {
                PaletteRadial(menu, request.activeTool, extent.value, active, swatches, custom,
                    dismiss, choose, apply)
            }
        } else {
            // Small pane fallback uses the same native palette and explicit commit actions.
            AlertDialog(onDismissRequest=dismiss,title={Text(menu.title)},text={
                Column(Modifier.verticalScroll(rememberScrollState()),horizontalAlignment=Alignment.CenterHorizontally) {
                    if(menu.center!="close") PaletteCenter(menu.center, active, swatches, custom, 150f, dismiss, apply)
                    menu.items.forEach { item ->
                        TextButton(onClick={choose(item.tool)},modifier=Modifier.fillMaxWidth()) {
                            ScriptToolIcon(item,item.tool==request.activeTool)
                            Text(item.label)
                        }
                    }
                }
            },confirmButton={TextButton(onClick=dismiss){Text("Close")}})
        }
    }
}

/** Shared by the real overlay and the visual settings preview. */
@Composable
internal fun PaletteRadial(menu:ScriptMenu, selected:String, extent:Float, active:Rgba,
    swatches:List<Rgba>, custom:List<Rgba>, dismiss:()->Unit, choose:(String)->Unit,
    apply:(Rgba,Int?)->Unit) {
    val palette=LocalPalette.current
    val half=extent/2
    val button=(extent*(if(menu.center=="close") 0.23f else 0.17f)).coerceIn(46f,64f)
    val radius=half-button/2-5
    Box(Modifier.size(extent.dp)) {
        Box(Modifier.fillMaxSize().clip(CircleShape).background(palette.menuBg.toComposeColor())
            .border(1.dp,palette.border.toComposeColor(),CircleShape).clickable(onClick=dismiss))
        Box(Modifier.align(Alignment.Center).size((extent*0.58f).dp).clip(CircleShape)
            .background(palette.panel.toComposeColor()).border(1.dp,palette.border.toComposeColor(),CircleShape))
        menu.items.forEachIndexed { i,item ->
            val a=-Math.PI/2+i*2*Math.PI/menu.items.size
            val x=half+(radius*cos(a)).toFloat()-button/2
            val y=half+(radius*sin(a)).toFloat()-button/2
            val armed=item.tool==selected
            Box(Modifier.offset(x.dp,y.dp).size(button.dp).clip(CircleShape)
                .background(if(armed) palette.accent.toComposeColor().copy(alpha=0.24f) else palette.panel.toComposeColor())
                .border(1.dp,if(armed) palette.accent.toComposeColor() else palette.border.toComposeColor(),CircleShape)
                .clickable(onClickLabel=item.label){choose(item.tool)},contentAlignment=Alignment.Center) {
                ScriptToolIcon(item,armed)
            }
        }
        Box(Modifier.align(Alignment.Center)) {
            if(menu.center=="close"){
                Box(Modifier.size(48.dp).clip(CircleShape).background(palette.menuBg.toComposeColor())
                    .clickable(onClickLabel="Close tool menu",onClick=dismiss),contentAlignment=Alignment.Center){
                    Icon(XnotesIcons.close,"Close tool menu",tint=palette.text.toComposeColor())
                }
            } else PaletteCenter(menu.center,active,swatches,custom,extent*0.43f,dismiss,apply)
        }
    }
}

@Composable
private fun PaletteCenter(mode:String,active:Rgba,swatches:List<Rgba>,custom:List<Rgba>,width:Float,
    dismiss:()->Unit,apply:(Rgba,Int?)->Unit) {
    var draft by remember(active){mutableStateOf(active)}
    val start=remember(active){ColorMath.rgbToHsv(active)}
    var h by remember(active){mutableStateOf(start[0])}
    var s by remember(active){mutableStateOf(start[1])}
    var v by remember(active){mutableStateOf(start[2])}
    val palette=LocalPalette.current
    Column(Modifier.width(width.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        if(mode=="palette"){
            SpectrumWheel(h,s,v,onPreview={hh,ss,vv->
                h=hh;s=ss;v=vv;draft=ColorMath.hsvToRgb(h,s,v)
            },onCommit={},modifier=Modifier.size((width*0.79f).dp))
        } else {
            // More existing/favorite swatches instead of a hue wheel.
            val all=swatches.mapIndexed { i,c->c to i }+custom.map { it to null }
            all.take(12).chunked(4).forEach { row ->
                Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){
                    row.forEach { (c,index)->PaletteChip(c,26f,"Choose ${Rgba.toHex(c)}"){apply(c,index)} }
                }
            }
        }
        if(mode=="palette"){
            Row(horizontalArrangement=Arrangement.spacedBy(3.dp)){
                val favorites=custom.map{it to null}
                (favorites+swatches.mapIndexed { i,c->c to i }).take(6).forEach{(c,index)->
                    PaletteChip(c,(width/7).coerceIn(18f,24f),"Choose ${Rgba.toHex(c)}"){apply(c,index)}
                }
            }
        }
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
            // Dragging a spectrum only previews. A deliberate check commits to the active swatch.
            PaletteChip(draft,30f,"Apply ink ${Rgba.toHex(draft)}"){apply(draft,null)}
            Box(Modifier.size(30.dp).clip(CircleShape).clickable(onClickLabel="Apply ink"){apply(draft,null)},
                contentAlignment=Alignment.Center){
                Icon(XnotesIcons.check,"Apply ink",tint=palette.text.toComposeColor(),modifier=Modifier.size(18.dp))
            }
            Box(Modifier.size(30.dp).clip(CircleShape).clickable(onClickLabel="Close tool menu",onClick=dismiss),
                contentAlignment=Alignment.Center){
                Icon(XnotesIcons.close,"Close tool menu",tint=palette.text.toComposeColor(),modifier=Modifier.size(18.dp))
            }
        }
    }
}

@Composable
internal fun PaletteChip(color:Rgba,size:Float,label:String,action:()->Unit){
    val palette=LocalPalette.current
    Box(Modifier.size(size.dp).clip(CircleShape).background(color.toComposeColor())
        .border(1.dp,palette.border.toComposeColor(),CircleShape).clickable(onClickLabel=label,onClick=action))
}

internal fun radialExtent(center:String,size:String):Float =
    if(center=="close") when(size){"compact"->226f;"large"->300f;else->260f}
    else when(size){"compact"->300f;"large"->380f;else->342f}
