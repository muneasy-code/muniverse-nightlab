package de.muneasy.muniverse

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.SurfaceTexture
import android.view.TextureView
import android.view.View
import android.widget.*
import android.hardware.SensorManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity:Activity(){
    private val bg=Color.rgb(5,17,30)
    private val fg=Color.WHITE
    private val cyan=Color.rgb(0,217,245)
    private val muted=Color.rgb(160,184,200)
    private lateinit var root:LinearLayout
    private lateinit var content:LinearLayout
    private lateinit var camera:CameraLab
    private lateinit var sky:SkySensors
    private var screen="mission"
    private var target=0
    private var heading:Bearing?=null
    private var status:TextView?=null
    private var preview:TextureView?=null
    private var iso=800
    private var seconds=5.0
    private val lat=52.12
    private val lon=11.63
    override fun onCreate(saved:Bundle?){
        super.onCreate(saved)
        window.statusBarColor=bg
        window.navigationBarColor=bg
        camera=CameraLab(this){status?.text=it}
        sky=SkySensors(this){bearing,accuracy->runOnUiThread{
            heading=bearing
            if(screen=="sky")status?.text=skyText(accuracy)
        }}
        root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(bg)}
        setContentView(root)
        val head=TextView(this).apply{
            text="✦  M U N I V E R S E\nNIGHTLAB  /  ASTROCAMERA"
            setTextColor(cyan);textSize=19f
            setPadding(20,36,18,25)
        }
        root.addView(head)
        val frame=ScrollView(this).apply{isFillViewport=true}
        content=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(22,20,22,24)
        }
        frame.addView(content)
        root.addView(frame,LinearLayout.LayoutParams(-1,0,1f))
        val nav=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        listOf("Mission" to "mission","Kamera" to "camera","Sky" to "sky","S23" to "hardware").forEach { (label,code)->
            val b=Button(this).apply{
                text=label;textSize=11f;isAllCaps=false
                setOnClickListener{show(code)}
            }
            nav.addView(b,LinearLayout.LayoutParams(0,-2,1f))
        }
        root.addView(nav)
        show("mission")
    }
    private fun title(t:String){label(t,22f,fg)}
    private fun label(t:String,sz:Float=15f,color:Int=muted):TextView{
        val v=TextView(this).apply{text=t;textSize=sz;setTextColor(color);setPadding(0,10,0,12)}
        content.addView(v)
        return v
    }
    private fun button(text:String,fn:()->Unit){
        content.addView(Button(this).apply{this.text=text;isAllCaps=false;setOnClickListener{fn()}})
    }
    private fun show(page:String){
        if(screen=="camera"&&page!="camera")camera.close()
        if(screen=="sky"&&page!="sky")sky.stop()
        screen=page
        content.removeAllViews()
        status=null;preview=null
        when(page){
            "mission"->{
                title("DEINE NÄCHSTE FOTONACHT")
                label("Sterne finden. Echt fotografieren. Keine erfundenen Bilddetails.")
                label("Offline-Himmelskarte · Standort für v0.1: Magdeburg")
                AstroMath.targets.forEachIndexed{index,t->
                    val p=AstroMath.position(t,lat,lon,System.currentTimeMillis())
                    label(t.name+"\n"+p.az.toInt()+"° "+AstroMath.compass(p.az)+
                        " · Höhe "+p.alt.toInt()+"°",16f,if(p.alt>10)cyan else muted)
                    button("Im Skyfinder öffnen"){target=index;show("sky")}
                }
                label("Positionen sind Näherungen, ohne Wetter und Sternbild-Erkennung.")
            }
            "hardware"->{
                title("S23 · HARDWARE LAB")
                label("Das S23 entscheidet, welche Camera2-Funktionen wirklich freigegeben werden.")
                val report=camera.capabilityReport()
                label(report,14f,fg)
                button("Hardwarebericht kopieren"){
                    val cm=getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("S23 Camera2",report))
                    Toast.makeText(this,"Kopiert",Toast.LENGTH_SHORT).show()
                }
            }
            "sky"->{
                title("SKYFINDER")
                label("Hintere Kamera zeigt auf das Ziel. Display nach unten = Blick zum Zenit.")
                label("Ziel: "+AstroMath.targets[target].name,18f,cyan)
                status=label("Berechne Richtung…",17f,fg)
                sky.start()
            }
            "camera"->{
                title("ASTROCAM · CAMERA2")
                label("Alpha-Test: feste Unterlage nutzen. Erst 1–2 Sekunden testen!")
                val pv=TextureView(this)
                preview=pv
                content.addView(pv,LinearLayout.LayoutParams(-1,480))
                pv.surfaceTextureListener=object:TextureView.SurfaceTextureListener{
                    override fun onSurfaceTextureAvailable(s:SurfaceTexture,w:Int,h:Int){camera.start(pv)}
                    override fun onSurfaceTextureSizeChanged(s:SurfaceTexture,w:Int,h:Int){}
                    override fun onSurfaceTextureDestroyed(s:SurfaceTexture):Boolean{camera.close();return true}
                    override fun onSurfaceTextureUpdated(s:SurfaceTexture){}
                }
                status=label("Starte Kamera…",14f,cyan)
                label("ISO: "+iso+" | Sekunden: "+seconds)
                val isoSeek=SeekBar(this).apply{max=3100;progress=700}
                content.addView(isoSeek)
                val isoText=label("ISO "+iso)
                isoSeek.setOnSeekBarChangeListener(listener {v->iso=(100+v).coerceAtLeast(100);isoText.text="ISO "+iso})
                val expSeek=SeekBar(this).apply{max=29;progress=4}
                content.addView(expSeek)
                val secText=label("Belichtung "+seconds+" s")
                expSeek.setOnSeekBarChangeListener(listener{v->seconds=v+1.0;secText.text="Belichtung "+seconds+" s"})
                button("FOTO AUFNEHMEN"){if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)camera.shoot(iso,seconds) else requestPermissions(arrayOf(Manifest.permission.CAMERA),100)}
                if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
                    button("Kameraberechtigung erteilen"){requestPermissions(arrayOf(Manifest.permission.CAMERA),100)}
                }
                label("DNG nur bei RAW_SENSOR und unterstützter Streamkombination. Proprietäres Expert-RAW-Stacking nicht enthalten.")
            }
        }
    }
    private fun listener(action:(Int)->Unit)=object:SeekBar.OnSeekBarChangeListener{
        override fun onProgressChanged(s:SeekBar?,v:Int,user:Boolean){action(v)}
        override fun onStartTrackingTouch(s:SeekBar?){}
        override fun onStopTrackingTouch(s:SeekBar?){}
    }
    private fun skyText(accuracy:Int):String{
        val aim=heading?:return "Warte auf Sensoren"
        val t=AstroMath.targets[target]
        val p=AstroMath.position(t,lat,lon,System.currentTimeMillis())
        val delta=AstroMath.angleDelta(p.az,aim.az)
        return "Kamerarichtung: "+aim.az.toInt()+"° / Höhe "+aim.alt.toInt()+"°"+
            "\nZiel: "+p.az.toInt()+"° "+AstroMath.compass(p.az)+
            " / Höhe "+p.alt.toInt()+"°"+
            "\nSchwenken: "+(if(delta>0)"rechts " else "links ")+kotlin.math.abs(delta).toInt()+"°"+
            "\nKompass-Genauigkeit: "+accuracy+
            "\nBei senkrechter Ausrichtung ist Azimut unbestimmt."
    }
    override fun onRequestPermissionsResult(req:Int,p:Array<out String>,g:IntArray){
        super.onRequestPermissionsResult(req,p,g)
        if(req==100&&g.isNotEmpty()&&g[0]==PackageManager.PERMISSION_GRANTED&&screen=="camera")show("camera")
    }
    override fun onPause(){super.onPause();camera.close();sky.stop()}
    override fun onDestroy(){camera.shutdown();super.onDestroy()}
}
