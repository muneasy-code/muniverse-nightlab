package de.muneasy.muniverse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** MUNIVERSE Compose UI: layout only. Camera, sensors and stacking remain native Kotlin. */
enum class NightPage(val title:String) {
    MISSION("Mission"), CAMERA("Kamera"), STACK("Stack"), SKY("Sky"), HARDWARE("S23")
}
data class MissionRow(val name:String,val position:String,val visible:Boolean)

private val NightNavy=Color(0xFF05111E)
private val NightPanel=Color(0xFF0D293B)
private val NightLine=Color(0xFF19374B)
private val NightCyan=Color(0xFF00D9F5)
private val NightMuted=Color(0xFFA2B8C9)
private val NightWhite=Color(0xFFF4FAFF)

@Composable
fun NightLabUi(
    page:NightPage,
    onPage:(NightPage)->Unit,
    missions:List<MissionRow>,
    onTarget:(Int)->Unit,
    targetName:String,
    skyStatus:String,
    cameraPreview:@Composable ()->Unit,
    cameraStatus:String,
    cameraPermission:Boolean,
    requestCameraPermission:()->Unit,
    iso:Int,
    setIso:(Int)->Unit,
    exposureMs:Int,
    setExposureMs:(Int)->Unit,
    captureAuto:()->Unit,
    captureManual:()->Unit,
    captureDay:()->Unit,
    captureDark:()->Unit,
    captureAstro:()->Unit,
    cancelBurst:()->Unit,
    stackStatus:String,
    stackBusy:Boolean,
    stackLast:()->Unit,
    stackPick:()->Unit,
    hardwareReport:String,
    copyHardware:()->Unit
) {
    MaterialTheme(colorScheme=darkColorScheme(
        primary=NightCyan,
        onPrimary=NightNavy,
        background=NightNavy,
        onBackground=NightWhite,
        surface=NightPanel,
        onSurface=NightWhite
    )) {
        Column(
            Modifier.fillMaxSize().background(NightNavy).safeDrawingPadding()
        ) {
            Column(Modifier.fillMaxWidth().padding(start=20.dp,end=20.dp,top=14.dp,bottom=12.dp)){
                Text("✦  MUNIVERSE",color=NightCyan,fontSize=22.sp,fontWeight=FontWeight.SemiBold,
                    letterSpacing=2.sp)
                Text("NIGHTLAB  /  ASTROCAMERA",color=NightMuted,fontSize=12.sp,
                    letterSpacing=1.5.sp)
            }
            HorizontalDivider(color=NightLine)
            Column(
                modifier=Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal=18.dp,vertical=16.dp),
                verticalArrangement=Arrangement.spacedBy(11.dp)
            ) {
                when(page){
                    NightPage.MISSION -> {
                        Heading("DEINE FOTONACHT","Sternenziele · Magdeburg (offline)")
                        InfoCard("Echte Kamera · Echte Pixel",
                            "S23-Hardwaretest: Camera2 liefert höchstens 0,15 s. Für längere Aufnahmen weiterhin Samsung Expert RAW nutzen.")
                        missions.forEachIndexed{index,target->
                            Row(
                                modifier=Modifier.fillMaxWidth().background(NightPanel,RoundedCornerShape(15.dp))
                                    .clickable {onTarget(index)}.padding(14.dp),
                                horizontalArrangement=Arrangement.spacedBy(10.dp),
                                verticalAlignment=Alignment.CenterVertically
                            ){
                                Column(Modifier.weight(1f)){
                                    Text(target.name,color=NightWhite,fontSize=16.sp,fontWeight=FontWeight.Medium)
                                    Text(target.position,color=if(target.visible)NightCyan else NightMuted,fontSize=13.sp)
                                }
                                Text("ZIELEN →",color=NightCyan,fontSize=12.sp,fontWeight=FontWeight.SemiBold)
                            }
                        }
                        Note("Richtungen sind Näherungen. Magnetkompass bei Bedarf kalibrieren; Wolken und Hindernisse werden nicht berücksichtigt.")
                    }
                    NightPage.CAMERA -> {
                        Heading("ASTROCAM · S23","Camera2-Hardwarelabor")
                        InfoCard("0,15 s pro Einzelbild",
                            "Die gemessene Android-Grenze ist kein Fehler des Reglers. Der Expert-RAW-Nachtmodus wird nicht kopiert.")
                        if(!cameraPermission) {
                            ActionButton("KAMERA ERLAUBEN",requestCameraPermission,primary=true)
                        }
                        Box(Modifier.fillMaxWidth().height(240.dp)
                            .background(Color.Black,RoundedCornerShape(12.dp))){
                            cameraPreview()
                        }
                        InfoCard("SENSORSTATUS",cameraStatus)
                        Text("ISO  " + iso,color=NightWhite,fontWeight=FontWeight.SemiBold)
                        Slider(value=iso.toFloat(),onValueChange={setIso(it.roundToInt())},
                            valueRange=50f..3200f)
                        Text("Belichtung  " + exposureMs + " ms",color=NightWhite,fontWeight=FontWeight.SemiBold)
                        Slider(value=exposureMs.toFloat(),onValueChange={setExposureMs(it.roundToInt())},
                            valueRange=1f..150f)
                        ActionButton("AUTO-TEST",captureAuto)
                        ActionButton("MANUELL FOTOGRAFIEREN",captureManual,primary=true)
                        HorizontalDivider(color=NightLine)
                        Heading("BURST LAB","Mehrere echte kurze Einzelbilder")
                        ActionButton("TAG · 10 × 5 ms · ISO 100",captureDay)
                        ActionButton("DUNKEL · 10 × 100 ms · ISO 800",captureDark)
                        ActionButton("ASTRO · 20 × 150 ms · ISO 3200",captureAstro)
                        ActionButton("SERIE ABBRECHEN",cancelBurst)
                        Note("JPG-Serien werden einzeln gesichert. Erst der Stack-Tab verarbeitet sie; keine synthetischen Sterne.")
                    }
                    NightPage.STACK -> {
                        Heading("STACK LAB","Echte Pixel statt generativer KI")
                        InfoCard("Pixel für Pixel",
                            "2–30 JPGs: konservative Ausrichtung und Mittelwert. Originale bleiben unverändert. Experimenteller Export: maximal 1600 px.")
                        ActionButton("LETZTE BURST-SERIE STACKEN",stackLast,primary=true,enabled=!stackBusy)
                        ActionButton("JPG-DATEIEN AUSWÄHLEN",stackPick,enabled=!stackBusy)
                        InfoCard("ERGEBNIS",stackStatus)
                        Note("Unzuverlässige Verschiebungen werden verworfen. Bereits überbelichtete Bereiche lassen sich nicht rekonstruieren.")
                    }
                    NightPage.SKY -> {
                        Heading("SKYFINDER","Sternenkompass · aktuelle Zeit")
                        InfoCard("ZIEL",targetName)
                        InfoCard("KOMPASS",skyStatus)
                        Note("Display nach unten: Kamera zeigt annähernd zum Zenit. In Zenitnähe ist Azimut nicht stabil. Keine Live-Sternkarten-Grafik in dieser Version.")
                    }
                    NightPage.HARDWARE -> {
                        Heading("S23 HARDWARE LAB","Was gibt Samsung über Android frei?")
                        InfoCard("CAMERA2-BERICHT",hardwareReport)
                        ActionButton("BERICHT KOPIEREN",copyHardware,primary=true)
                        Note("Die Night-Extension ist auf dem getesteten S23 nicht freigegeben. Die Software kann keine versteckten Herstellermodi erzwingen.")
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            HorizontalDivider(color=NightLine)
            Row(Modifier.fillMaxWidth().background(Color(0xFF091C2C)).padding(horizontal=7.dp,vertical=7.dp),
                horizontalArrangement=Arrangement.spacedBy(4.dp)){
                NightPage.entries.forEach{item->
                    val selected=page==item
                    Box(
                        modifier=Modifier.weight(1f).height(52.dp)
                            .background(if(selected)Color(0xFF006A83) else NightPanel,RoundedCornerShape(10.dp))
                            .clickable {onPage(item)},
                        contentAlignment=Alignment.Center
                    ){
                        Text(item.title,color=if(selected)Color.White else NightMuted,
                            fontSize=12.sp,fontWeight=if(selected)FontWeight.Bold else FontWeight.Medium,
                            textAlign=TextAlign.Center,maxLines=1)
                    }
                }
            }
        }
    }
}
@Composable
private fun Heading(title:String,subtitle:String){
    Text(title,color=NightWhite,fontSize=21.sp,fontWeight=FontWeight.SemiBold)
    Text(subtitle,color=NightMuted,fontSize=13.sp)
}
@Composable
private fun InfoCard(label:String,body:String){
    Column(Modifier.fillMaxWidth().background(NightPanel,RoundedCornerShape(14.dp))
        .padding(15.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
        Text(label,color=NightCyan,fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp)
        Text(body,color=NightWhite,fontSize=14.sp,lineHeight=21.sp)
    }
}
@Composable
private fun Note(message:String){
    Text(message,color=NightMuted,fontSize=12.sp,lineHeight=18.sp)
}
@Composable
private fun ActionButton(text:String,onClick:()->Unit,primary:Boolean=false,enabled:Boolean=true){
    Button(
        onClick=onClick,enabled=enabled,
        modifier=Modifier.fillMaxWidth().heightIn(min=51.dp),
        shape=RoundedCornerShape(11.dp),
        colors=ButtonDefaults.buttonColors(
            containerColor=if(primary)NightCyan else NightPanel,
            contentColor=if(primary)NightNavy else NightWhite
        )
    ){
        Text(text,fontSize=13.sp,fontWeight=FontWeight.SemiBold,
            textAlign=TextAlign.Center)
    }
}
