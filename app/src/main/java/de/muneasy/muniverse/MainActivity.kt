package de.muneasy.muniverse

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.net.Uri
import android.os.Bundle
import android.view.TextureView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.util.Locale
import kotlin.math.abs

/**
 * Kotlin lesson: Activity owns hardware + state; @Composable NightLabUi renders state.
 * CameraLab.kt, SkySensors.kt and StackLab.kt remain independently testable engines.
 */
class MainActivity : ComponentActivity() {
    private lateinit var camera:CameraLab
    private lateinit var sky:SkySensors
    private var activePage by mutableStateOf(NightPage.MISSION)
    private var selectedTarget by mutableIntStateOf(0)
    private var cameraMessage by mutableStateOf("Kamera noch nicht gestartet.")
    private var stackMessage by mutableStateOf("Bereit. Eine Burst-Serie auswählen.")
    private var skyBearing by mutableStateOf<Bearing?>(null)
    private var skyAccuracy by mutableIntStateOf(0)
    private var hardwareReport by mutableStateOf("Öffne den Bereich S23 für den Hardwarebericht.")
    private var cameraAllowed by mutableStateOf(false)
    private var iso by mutableIntStateOf(800)
    private var exposureMs by mutableIntStateOf(100)
    private var stackBusy by mutableStateOf(false)
    private var cameraTexture:TextureView?=null

    private val latitude=52.12
    private val longitude=11.63

    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        window.statusBarColor=Color.rgb(5,17,30)
        window.navigationBarColor=Color.rgb(5,17,30)
        cameraAllowed=hasCameraPermission()
        camera=CameraLab(this){message->cameraMessage=message}
        sky=SkySensors(this){bearing,accuracy->
            runOnUiThread{
                skyBearing=bearing
                skyAccuracy=accuracy
            }
        }
        setContent {
            val targetRows=AstroMath.targets.map{item->
                val position=AstroMath.position(item,latitude,longitude,System.currentTimeMillis())
                MissionRow(
                    name=item.name,
                    position=position.az.toInt().toString()+"° "+AstroMath.compass(position.az)+
                        "  ·  Höhe "+position.alt.toInt()+"°",
                    visible=position.alt>10
                )
            }
            NightLabUi(
                page=activePage,
                onPage={navigate(it)},
                missions=targetRows,
                onTarget={index->
                    selectedTarget=index
                    navigate(NightPage.SKY)
                },
                targetName=AstroMath.targets[selectedTarget].name,
                skyStatus=skyDetails(),
                cameraPreview={ CameraTexture() },
                cameraStatus=cameraMessage,
                cameraPermission=cameraAllowed,
                requestCameraPermission={requestCameraPermission()},
                iso=iso,
                setIso={iso=it.coerceIn(50,3200)},
                exposureMs=exposureMs,
                setExposureMs={exposureMs=it.coerceIn(1,150)},
                captureAuto={requireCamera{camera.shootAuto()}},
                captureManual={requireCamera{camera.shoot(iso,exposureMs/1000.0)}},
                captureDay={requireCamera{camera.startBurst(10,100,0.005)}},
                captureDark={requireCamera{camera.startBurst(10,800,0.100)}},
                captureAstro={requireCamera{camera.startBurst(20,3200,0.150)}},
                cancelBurst={camera.cancelBurst()},
                stackStatus=stackMessage,
                stackBusy=stackBusy,
                stackLast={stackLatest()},
                stackPick={pickStackFiles()},
                hardwareReport=hardwareReport,
                copyHardware={copyHardwareReport()}
            )
        }
    }

    private fun hasCameraPermission()=
        checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED

    private fun requestCameraPermission(){
        requestPermissions(arrayOf(Manifest.permission.CAMERA),100)
    }

    private fun requireCamera(action:()->Unit){
        if(hasCameraPermission())action() else requestCameraPermission()
    }

    private fun navigate(next:NightPage){
        if(activePage==next)return
        if(activePage==NightPage.CAMERA)camera.close()
        if(activePage==NightPage.SKY)sky.stop()
        activePage=next
        if(next==NightPage.HARDWARE)hardwareReport=camera.capabilityReport()
        if(next==NightPage.SKY)sky.start()
    }

    @Composable
    private fun CameraTexture(){
        AndroidView(
            factory={ctx->
                TextureView(ctx).apply{
                    cameraTexture=this
                    surfaceTextureListener=object:TextureView.SurfaceTextureListener{
                        override fun onSurfaceTextureAvailable(surface:SurfaceTexture,width:Int,height:Int){
                            if(hasCameraPermission())camera.start(this@apply)
                            else cameraMessage="Kamera-Berechtigung fehlt."
                        }
                        override fun onSurfaceTextureSizeChanged(surface:SurfaceTexture,width:Int,height:Int){}
                        override fun onSurfaceTextureDestroyed(surface:SurfaceTexture):Boolean{
                            camera.close()
                            if(cameraTexture===this@apply)cameraTexture=null
                            return true
                        }
                        override fun onSurfaceTextureUpdated(surface:SurfaceTexture){}
                    }
                }
            },
            modifier=Modifier.fillMaxSize()
        )
    }

    private fun skyDetails():String {
        val aim=skyBearing?:return if(sky.available)"Warte auf Kompassdaten…" else "Keine Magnet-/Lagesensoren verfügbar."
        val target=AstroMath.position(
            AstroMath.targets[selectedTarget],latitude,longitude,System.currentTimeMillis())
        val delta=AstroMath.angleDelta(target.az,aim.az)
        return "Gerät: "+aim.az.toInt()+"° / Höhe "+aim.alt.toInt()+"°"+
            "\nZiel: "+target.az.toInt()+"° "+AstroMath.compass(target.az)+
            " / Höhe "+target.alt.toInt()+"°"+
            "\nSchwenken: "+(if(delta>0)"rechts " else "links ")+abs(delta).toInt()+"°"+
            "\nSensor-Genauigkeit: "+skyAccuracy+
            "\nBei senkrechtem Zielen ist der Azimut nicht eindeutig."
    }

    private fun copyHardwareReport(){
        val clipboard=getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("MUNIVERSE S23",hardwareReport))
        Toast.makeText(this,"Bericht kopiert",Toast.LENGTH_SHORT).show()
    }

    private fun stackLatest(){
        if(stackBusy)return
        stackBusy=true
        stackMessage="Suche zuletzt gespeicherte Burst-Serie…"
        Thread{
            try{
                val files=StackLab.latestBurst(this)
                runOnUiThread{
                    if(files.size<2){
                        stackBusy=false
                        stackMessage="Keine passende Serie gefunden. JPGs bitte manuell auswählen."
                    }else stackSelected(files,true)
                }
            }catch(e:Exception){
                runOnUiThread{
                    stackBusy=false
                    stackMessage="Suche fehlgeschlagen: "+e.message
                }
            }
        }.start()
    }

    private fun pickStackFiles(){
        if(stackBusy)return
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{
            addCategory(Intent.CATEGORY_OPENABLE)
            type="image/jpeg"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true)
        },602)
    }

    private fun stackSelected(files:List<Uri>,alreadyBusy:Boolean=false){
        if(stackBusy && !alreadyBusy)return
        stackBusy=true
        stackMessage="Beginne mit "+files.size+" JPG-Dateien…"
        Thread{
            try{
                val result=StackLab.stack(this,files){progress->
                    runOnUiThread{stackMessage=progress}
                }
                runOnUiThread{
                    stackBusy=false
                    stackMessage="FERTIG: "+result.count+" Fotos → PNG "+result.width+" × "+result.height+
                        "\nBilder/MUNIVERSE/Stacks"+
                        "\nVerschiebungen: "+result.shifts+
                        (if(result.unreliableAlignments>0)
                            "\n"+result.unreliableAlignments+" unsichere Ausrichtungen verworfen." else "")+
                        (if(result.clippedPercent>5.0)
                            "\nACHTUNG: "+String.format(Locale.GERMANY,"%.1f",result.clippedPercent)+
                                " % im ersten Bild fast weiß, Details überbelichtet." else "")+
                        "\nNur echte Pixel, Original-JPGs unverändert."
                    Toast.makeText(this,"PNG-Stack gespeichert",Toast.LENGTH_LONG).show()
                }
            }catch(e:Exception){
                runOnUiThread{
                    stackBusy=false
                    stackMessage="Stacking fehlgeschlagen: "+e.message
                }
            }
        }.start()
    }

    @Deprecated("Android file picker callback for alpha app")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode!=602||resultCode!=RESULT_OK||data==null)return
        val images=mutableListOf<Uri>()
        data.clipData?.let{clip->
            for(i in 0 until clip.itemCount)images.add(clip.getItemAt(i).uri)
        }?:data.data?.let{images.add(it)}
        val chosen=images.distinct()
        if(chosen.size in 2..30)stackSelected(chosen)
        else stackMessage="Bitte 2–30 JPG-Dateien auswählen."
    }

    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==100){
            cameraAllowed=hasCameraPermission()
            if(cameraAllowed && activePage==NightPage.CAMERA){
                cameraTexture?.let{if(it.isAvailable)camera.start(it)}
            }
        }
    }

    override fun onPause(){
        super.onPause()
        camera.close()
        sky.stop()
    }
    override fun onResume(){
        super.onResume()
        if(::sky.isInitialized && activePage==NightPage.SKY)sky.start()
        if(::camera.isInitialized && activePage==NightPage.CAMERA && hasCameraPermission()){
            cameraTexture?.let{if(it.isAvailable)camera.start(it)}
        }
    }
    override fun onDestroy(){
        if(::camera.isInitialized)camera.shutdown()
        super.onDestroy()
    }
}
