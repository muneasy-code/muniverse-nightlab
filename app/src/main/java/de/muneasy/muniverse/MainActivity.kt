package de.muneasy.muniverse

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
    @Volatile private var stacking=false
    private var stackStatus:TextView?=null
    private var screen="mission"
    private var target=0
    private var heading:Bearing?=null
    private var status:TextView?=null
    private var preview:TextureView?=null
    private var iso=800
    private var seconds=0.10
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
        listOf("Mission" to "mission","Kamera" to "camera","Stack" to "stack","Sky" to "sky","S23" to "hardware").forEach { (label,code)->
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
        status=null;preview=null;stackStatus=null
        when(page){
            "mission"->{
                title("DEINE FOTONACHT")
                label("Himmelsziele · Magdeburg (offline)",14f,muted)
                label("S23-Test bestätigt: Camera2 erlaubt hier maximal 0,15 s. Für echte lange Sternaufnahmen zunächst Samsung Expert RAW nutzen – mit dem Skyfinder die Ziele anpeilen.",13f,muted)
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
            "stack"->{
                title("✦ STACK LAB · ECHTE PIXEL")
                label("10 echte JPGs → Ausrichten → Mittelwert bilden. Keine generative KI, keine künstlichen Sterne. Alle Originale bleiben unverändert.",14f,muted)
                label("Experimentelle Vorschau: Der PNG-Stack wird auf maximal 1600 px verkleinert, um RAM und Akku zu schonen. Sensor-JPGs werden nicht nachträglich aufgehellt.",13f,muted)
                button("LETZTE BURST-SERIE STACKEN"){
                    if(stacking)return@button
                    Thread {
                        try{
                            val files=StackLab.latestBurst(this)
                            if(files.size<2){
                                runOnUiThread {
                                    stackStatus?.text="Keine zugängliche Burst-Serie gefunden. Bitte JPGs manuell auswählen."
                                }
                            }else startStack(files)
                        }catch(e:Exception){
                            runOnUiThread{stackStatus?.text="Burst-Suche fehlgeschlagen: "+e.message}
                        }
                    }.start()
                }
                button("JPG-DATEIEN MANUELL AUSWÄHLEN"){
                    if(stacking)return@button
                    val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).apply{
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type="image/jpeg"
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true)
                    }
                    startActivityForResult(intent,602)
                }
                stackStatus=label(if(stacking)"Stacking läuft im Hintergrund …" else "Bereit. Bitte nach Möglichkeit nur Bilder derselben Serie wählen.",15f,cyan)
                label("Warum Stacken? Das arithmetische Mittel reduziert unkorreliertes Bildrauschen. Es macht aus 0,15-s-Einzelbildern aber keine echte minutenlange Einzelbelichtung und kann bei Wolken/Bewegung Geisterbilder erzeugen.",13f,muted)
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
                label("v0.1.3 · S23-Lab: maximal 0,15 s gemessen. Hier testen wir RAW, manuelle ISO und kurze Einzelbelichtungen. Für Astro-Langzeit weiter Expert RAW.",14f,muted)
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
                label("Manueller Hardware-Test · echte Sekunden statt unzulässiger 30-s-Regler",13f,muted)
                val isoSeek=SeekBar(this).apply{max=3150;progress=(iso-50).coerceIn(0,3150)}
                content.addView(isoSeek)
                val isoText=label("ISO "+iso)
                isoSeek.setOnSeekBarChangeListener(listener {v->iso=(50+v).coerceIn(50,3200);isoText.text="ISO "+iso})
                // Tested Galaxy S23 Camera2 reports an upper exposure limit of 150 ms.
                val expSeek=SeekBar(this).apply{max=149;progress=((seconds*1000).toInt()-1).coerceIn(0,149)}
                content.addView(expSeek)
                val secText=label("Belichtung "+String.format(Locale.GERMANY,"%.3f",seconds)+" s")
                expSeek.setOnSeekBarChangeListener(listener{v->
                    seconds=(v+1)/1000.0
                    secText.text="Belichtung "+String.format(Locale.GERMANY,"%.3f",seconds)+" s"
                })
                button("MANUELL FOTOGRAFIEREN"){
                    if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)camera.shoot(iso,seconds)
                    else requestPermissions(arrayOf(Manifest.permission.CAMERA),100)
                }
                label("✦ BURST LAB · VERSUCHSREIHE",18f,cyan)
                label("Sammelt mehrere echte JPG-Einzelbilder. Noch kein fertiges Stacking! Originale liegen anschließend in Pictures/MUNIVERSE/Burst_…",13f,muted)
                button("TEST-SERIE · 10 × 0,10 s / ISO 800"){
                    if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)
                        camera.startBurst(10,800,0.10)
                    else requestPermissions(arrayOf(Manifest.permission.CAMERA),100)
                }
                button("ASTRO-SERIE · 20 × 0,15 s / ISO 3200"){
                    if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)
                        camera.startBurst(20,3200,0.15)
                    else requestPermissions(arrayOf(Manifest.permission.CAMERA),100)
                }
                button("SERIE ABBRECHEN"){camera.cancelBurst()}
                if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
                    button("Kameraberechtigung erteilen"){requestPermissions(arrayOf(Manifest.permission.CAMERA),100)}
                }
                label("10 × 0,10 s = 1 s aufsummierte Sensor-Belichtungszeit, 20 × 0,15 s = 3 s; die Gesamtaufnahme dauert länger. Nur JPEG-Testserien, keine RAW-Stacks und keine künstlich ergänzten Sterne.",13f,muted)
            }
        }
    }
    private fun startStack(files:List<Uri>){
        if(stacking)return
        stacking=true
        runOnUiThread{stackStatus?.text="Beginne mit ${files.size} JPGs…"}
        Thread {
            try{
                val result=StackLab.stack(this,files){message->
                    runOnUiThread{stackStatus?.text=message}
                }
                runOnUiThread{
                    stacking=false
                    stackStatus?.text="FERTIG: ${result.count} Bilder kombiniert (PNG ${result.width} × ${result.height})." +
                        "\nGespeichert unter Bilder → MUNIVERSE → Stacks." +
                        "\nErkannte Bildverschiebungen: ${result.shifts}" +
                        "\nOhne generative KI. Original-JPGs nicht verändert."
                    Toast.makeText(this,"MUNIVERSE STACK gespeichert!",Toast.LENGTH_LONG).show()
                }
            }catch(e:Exception){
                runOnUiThread{
                    stacking=false
                    stackStatus?.text="STACK FEHLGESCHLAGEN: ${e.message}"
                }
            }
        }.start()
    }

    @Deprecated("Uses Android file picker for multi-JPEG selection")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode!=602 || resultCode!=RESULT_OK || data==null)return
        val chosen=mutableListOf<Uri>()
        val clip=data.clipData
        if(clip!=null){
            for(i in 0 until clip.itemCount){chosen.add(clip.getItemAt(i).uri)}
        }else data.data?.let{chosen.add(it)}
        if(chosen.size in 2..30)startStack(chosen.distinct())
        else stackStatus?.text="Bitte zwischen 2 und 30 JPGs auswählen (gewählt: ${chosen.size})."
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
