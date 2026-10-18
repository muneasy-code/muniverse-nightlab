package de.muneasy.muniverse

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.content.Context
import android.graphics.Color
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.graphics.SurfaceTexture
import android.view.TextureView
import android.view.View
import android.view.WindowInsets
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
    private val navButtons=mutableMapOf<String,Button>()
    private fun dp(value:Int)=(value*resources.displayMetrics.density+0.5f).toInt()
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
        root=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setBackgroundColor(bg)
            // Android 15/16: keep the entire UI clear of system status/navigation bars.
            setOnApplyWindowInsetsListener { view, insets ->
                val safe=insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(0,safe.top,0,safe.bottom)
                insets
            }
        }
        setContentView(root)
        root.post { root.requestApplyInsets() }
        val head=TextView(this).apply{
            text="✦  MUNIVERSE\nNIGHTLAB  /  ASTROCAMERA"
            setTextColor(cyan);textSize=17f
            setPadding(dp(16),dp(13),dp(16),dp(10))
        }
        root.addView(head)
        val frame=ScrollView(this).apply{isFillViewport=true}
        content=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(16),dp(6),dp(16),dp(18))
        }
        frame.addView(content)
        root.addView(frame,LinearLayout.LayoutParams(-1,0,1f))
        // Fixed bottom navigation, outside the scrolling mission list and above Android's gesture bar.
        val nav=LinearLayout(this).apply{
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER
            setBackgroundColor(Color.rgb(9,29,43))
            setPadding(dp(6),dp(6),dp(6),dp(6))
        }
        listOf("Mission" to "mission","Kamera" to "camera","Sky" to "sky","S23" to "hardware").forEach { (label,code)->
            val b=Button(this).apply{
                text=label
                textSize=12f
                isAllCaps=false
                minHeight=dp(52)
                setPadding(dp(3),0,dp(3),0)
                setTextColor(fg)
                setOnClickListener{show(code)}
            }
            navButtons[code]=b
            nav.addView(b,LinearLayout.LayoutParams(0,dp(52),1f).apply{
                marginStart=dp(2)
                marginEnd=dp(2)
            })
        }
        root.addView(nav,LinearLayout.LayoutParams(-1,-2))
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
        navButtons.forEach { (code, b) ->
            val selected=code==page
            b.backgroundTintList=ColorStateList.valueOf(
                if(selected) Color.rgb(0,91,112) else Color.rgb(24,48,64))
            b.setTextColor(if(selected) Color.WHITE else Color.rgb(210,228,236))
        }
        content.removeAllViews()
        status=null;preview=null
        when(page){
            "mission"->{
                title("DEINE FOTONACHT")
                label("Himmelsziele · Magdeburg (offline)",14f,muted)
                AstroMath.targets.forEachIndexed{index,t->
                    val p=AstroMath.position(t,lat,lon,System.currentTimeMillis())
                    val card=LinearLayout(this).apply{
                        orientation=LinearLayout.HORIZONTAL
                        gravity=Gravity.CENTER_VERTICAL
                        setPadding(dp(12),dp(9),dp(9),dp(9))
                        background=GradientDrawable().apply{
                            setColor(Color.rgb(13,34,50))
                            cornerRadius=dp(12).toFloat()
                        }
                    }
                    val detail=TextView(this).apply{
                        text=t.name+"\n"+p.az.toInt()+"° "+AstroMath.compass(p.az)+
                            " · Höhe "+p.alt.toInt()+"°"
                        setTextColor(if(p.alt>10) fg else muted)
                        textSize=15f
                        setLineSpacing(dp(3).toFloat(),1f)
                    }
                    card.addView(detail,LinearLayout.LayoutParams(0,-2,1f))
                    val aim=Button(this).apply{
                        text="ZIELEN"
                        textSize=12f
                        isAllCaps=false
                        setTextColor(bg)
                        backgroundTintList=ColorStateList.valueOf(cyan)
                        setOnClickListener{target=index;show("sky")}
                    }
                    card.addView(aim,LinearLayout.LayoutParams(dp(94),dp(48)))
                    content.addView(card,LinearLayout.LayoutParams(-1,-2).apply{
                        bottomMargin=dp(9)
                    })
                }
                label("Positionen sind Näherungen; Kompass kalibrieren. Wetter nicht berücksichtigt.",13f,muted)
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
                label("v0.1.2: Wir messen jetzt die ECHTE Belichtungszeit des Sensors. Erst AUTO-TEST machen!",14f,muted)
                button("AUTO-TEST · helles Zimmer fotografieren"){
                    if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)camera.shootAuto()
                    else requestPermissions(arrayOf(Manifest.permission.CAMERA),100)
                }
                label("Bei manueller Aufnahme werden Sekunden/ISO nur verwendet, wenn die Kamera sie unterstützt.",13f,muted)
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
                label("Wähle manuelle Belichtung. Die Camera2-Maximalzeit steht nach Kamerastart im Status.",13f,muted)
                val isoSeek=SeekBar(this).apply{max=3100;progress=(iso-100).coerceIn(0,3100)}
                content.addView(isoSeek)
                val isoText=label("ISO "+iso)
                isoSeek.setOnSeekBarChangeListener(listener {v->iso=(100+v).coerceAtLeast(100);isoText.text="ISO "+iso})
                val expSeek=SeekBar(this).apply{max=29;progress=(seconds.toInt()-1).coerceIn(0,29)}
                content.addView(expSeek)
                val secText=label("Belichtung "+seconds+" s")
                expSeek.setOnSeekBarChangeListener(listener{v->seconds=v+1.0;secText.text="Belichtung "+seconds+" s"})
                button("MANUELL FOTOGRAFIEREN"){
                    if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)camera.shoot(iso,seconds)
                    else requestPermissions(arrayOf(Manifest.permission.CAMERA),100)
                }
                if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
                    button("Kameraberechtigung erteilen"){requestPermissions(arrayOf(Manifest.permission.CAMERA),100)}
                }
                label("Nach der Aufnahme erscheinen die zurückgemeldeten Sensorwerte direkt im Status. Falls Camera2 deine Zeit nicht erlaubt, lösen wir absichtlich nicht aus. DNG nur mit RAW_SENSOR. Kein Expert-RAW-Stacking.",13f,muted)
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
