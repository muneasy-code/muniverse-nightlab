package de.muneasy.muniverse

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.provider.MediaStore
import android.view.Surface
import android.view.TextureView
import android.media.ToneGenerator
import android.media.AudioManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Camera2 hardware experiments. A successful DNG requires RAW_SENSOR, NOT just a filename change. */
class CameraLab(private val activity:Activity,private val tell:(String)->Unit) {
    private val manager=activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val worker=HandlerThread("MUNIVERSE-Camera").apply{start()}
    private val handler=Handler(worker.looper)
    private var device:CameraDevice?=null
    private var session:CameraCaptureSession?=null
    private var surface:Surface?=null
    private var jpeg:ImageReader?=null
    private var raw:ImageReader?=null
    private var characteristics:CameraCharacteristics?=null
    private var currentId:String?=null
    private var onCapture:TotalCaptureResult?=null
    private var imageJpg:Image?=null
    private var imageRaw:Image?=null
    private var busy=false
    private var requestedSeconds=0.0
    private var requestedIso=0
    private var autoExposure=false
    private var requestedAtMs=0L

    fun exposureSupport():String {
        val c=characteristics ?: return "Camera2: noch nicht geöffnet"
        val caps=c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?:intArrayOf()
        val manual=caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)
        val range=c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val iso=c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        return "Kamera ${currentId ?: "?"} · Manual: ${if(manual) "JA" else "NEIN"}" +
            "\nZulässige Belichtung: ${if(range!=null) String.format(Locale.GERMANY,"%.4f – %.2f s",range.lower/1e9,range.upper/1e9) else "unbekannt"}" +
            "\nISO: ${iso ?: "unbekannt"}"
    }

    private fun notify(s:String)=activity.runOnUiThread{tell(s)}

    fun capabilityReport():String {
        return try {
            manager.cameraIdList.joinToString("\n\n"){id->
                val c=manager.getCameraCharacteristics(id)
                val modes=c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?:intArrayOf()
                val iso=c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
                val exp=c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
                "Camera "+id+" / "+(if(c.get(CameraCharacteristics.LENS_FACING)==CameraCharacteristics.LENS_FACING_BACK)"BACK" else "FRONT")+
                "\nRAW_SENSOR: "+modes.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)+
                "\nMANUAL_SENSOR: "+modes.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)+
                "\nISO: "+(iso?.toString()?:"?")+"\nBelichtung: "+(if(exp!=null) String.format(Locale.GERMANY,"%.4f bis %.2f Sekunden",exp.lower/1e9,exp.upper/1e9) else "unbekannt")
            }
        }catch(e:Exception){"Hardwarebericht: "+e.message}
    }

    fun start(view:TextureView) {
        if(activity.checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){notify("Kameraberechtigung benötigt");return}
        if(!view.isAvailable){notify("Vorschau wird vorbereitet");return}
        close()
        try {
            val id=manager.cameraIdList.firstOrNull {
                manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING)==CameraCharacteristics.LENS_FACING_BACK
            }?:throw IllegalStateException("Keine Rückkamera")
            currentId=id
            val c=manager.getCameraCharacteristics(id)
            characteristics=c
            val config=c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?:error("Keine Output-Konfiguration")
            val modes=c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?:intArrayOf()
            val sizes=config.getOutputSizes(ImageFormat.JPEG)?:error("JPEG nicht unterstützt")
            val jpgSize=sizes.filter{it.width.toLong()*it.height<=16000000L}.maxByOrNull{it.width.toLong()*it.height}?:sizes.first()
            jpeg=ImageReader.newInstance(jpgSize.width,jpgSize.height,ImageFormat.JPEG,2).apply {
                setOnImageAvailableListener({r->storeImage(r.acquireNextImage(),false)},handler)
            }
            if(modes.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)){
                config.getOutputSizes(ImageFormat.RAW_SENSOR)?.maxByOrNull{it.width.toLong()*it.height}?.let{sz->
                    raw=ImageReader.newInstance(sz.width,sz.height,ImageFormat.RAW_SENSOR,2).apply{
                        setOnImageAvailableListener({r->storeImage(r.acquireNextImage(),true)},handler)
                    }
                }
            }
            manager.openCamera(id,object:CameraDevice.StateCallback(){
                override fun onOpened(c:CameraDevice){device=c;configure(view)}
                override fun onDisconnected(c:CameraDevice){c.close();notify("Kamera getrennt")}
                override fun onError(c:CameraDevice,error:Int){c.close();notify("Camera2 Fehler "+error)}
            },handler)
            notify("Kamera "+id+" wird geöffnet")
        }catch(e:Exception){notify("Startfehler: "+e.message);close()}
    }

    private fun configure(view:TextureView){
        val cam=device?:return
        try{
            val tex=view.surfaceTexture?:return
            tex.setDefaultBufferSize(1920,1080)
            surface=Surface(tex)
            val outputs=listOfNotNull(surface,jpeg?.surface,raw?.surface)
            cam.createCaptureSession(outputs,object:CameraCaptureSession.StateCallback(){
                override fun onConfigured(s:CameraCaptureSession){
                    session=s
                    try{
                        val b=cam.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                        b.addTarget(surface!!)
                        b.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON)
                        s.setRepeatingRequest(b.build(),null,handler)
                        notify("Kamera bereit: "+(if(raw!=null)"RAW + JPEG (Test)" else "JPEG") + "\n" + exposureSupport())
                    }catch(e:Exception){notify("Vorschau: "+e.message)}
                }
                override fun onConfigureFailed(s:CameraCaptureSession){
                    s.close()
                    if(raw!=null){
                        raw?.close();raw=null
                        notify("RAW-Kombination verweigert, teste JPEG.")
                        configure(view)
                    }else notify("Kamera-Session fehlgeschlagen")
                }
            },handler)
        }catch(e:Exception){notify("Session: "+e.message)}
    }

    /**
     * Manual exposure requests are deliberately refused if they exceed the reported
     * hardware range. Camera2 may still shorten them; check CaptureResult.
     */
    fun shoot(iso:Int,seconds:Double)=capture(iso,seconds,false)
    fun shootAuto()=capture(400,0.0,true)

    private fun resumePreview(){
        val cam=device?:return
        val sess=session?:return
        val surf=surface?:return
        try{
            val p=cam.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply{
                addTarget(surf)
                set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO)
                set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON)
                set(CaptureRequest.CONTROL_AWB_MODE,CaptureRequest.CONTROL_AWB_MODE_AUTO)
            }
            sess.setRepeatingRequest(p.build(),null,handler)
        }catch(e:Exception){notify("Vorschau-Neustart: "+e.message)}
    }

    private fun capture(iso:Int,seconds:Double,auto:Boolean){
        val cam=device?:return notify("Kamera nicht bereit")
        val sess=session?:return notify("Vorschau noch nicht bereit")
        if(busy)return notify("Noch beschäftigt – bitte auf Abschluss warten.")
        val ch=characteristics?:return notify("Kein Hardwareprofil")
        val caps=ch.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?:intArrayOf()
        val manual=caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)
        val range=ch.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val isoRange=ch.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)

        if(!auto){
            if(!manual || range==null || isoRange==null){
                notify("Diese Kamera bietet keine manuelle Camera2-Belichtung. Verwende AUTO-TEST oder S23-Bericht.")
                return
            }
            val nanos=(seconds*1e9).toLong()
            if(nanos<range.lower || nanos>range.upper){
                notify("NICHT AUSGELÖST: ${seconds.toInt()} s verlangt, Camera2 erlaubt höchstens " +
                    String.format(Locale.GERMANY,"%.3f",range.upper/1e9)+" s. Kein stilles Kürzen!")
                return
            }
            if(iso !in isoRange.lower..isoRange.upper){
                notify("NICHT AUSGELÖST: ISO $iso außerhalb ${isoRange.lower}–${isoRange.upper}")
                return
            }
        }
        busy=true
        requestedSeconds=seconds
        requestedIso=iso
        autoExposure=auto
        requestedAtMs=SystemClock.elapsedRealtime()
        onCapture=null;imageJpg=null;imageRaw=null
        try{
            val request=cam.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            jpeg?.surface?.let{request.addTarget(it)}
            raw?.surface?.let{request.addTarget(it)}
            request.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO)
            request.set(CaptureRequest.CONTROL_AWB_MODE,CaptureRequest.CONTROL_AWB_MODE_AUTO)
            if(auto){
                request.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON)
                request.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                notify("AUTO-TEST läuft (Kamera wählt Belichtung).")
            }else{
                val requested=(seconds*1e9).toLong()
                request.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_OFF)
                request.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_OFF)
                if((ch.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)?:0f)>0f){
                    request.set(CaptureRequest.LENS_FOCUS_DISTANCE,0f)
                }
                request.set(CaptureRequest.SENSOR_EXPOSURE_TIME,requested)
                request.set(CaptureRequest.SENSOR_SENSITIVITY,iso)
                val maxFrame=ch.get(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION)
                if(maxFrame!=null && maxFrame>=requested){
                    request.set(CaptureRequest.SENSOR_FRAME_DURATION,(requested+10000000L).coerceAtMost(maxFrame))
                }
                notify("MANUELL gestartet: angefordert ${seconds} s · ISO ${iso}. Nicht bewegen!")
            }
            sess.stopRepeating()
            sess.capture(request.build(),object:CameraCaptureSession.CaptureCallback(){
                override fun onCaptureCompleted(s:CameraCaptureSession,r:CaptureRequest,result:TotalCaptureResult){
                    onCapture=result
                    resumePreview()
                    trySave()
                    handler.postDelayed({
                        if(busy && onCapture!=null){
                            // Some HALs do not deliver both image streams on mixed captures.
                            if(imageJpg!=null){
                                notify("RAW verzögert/fehlt, sichere vorhandenes JPG.")
                                raw?.let { imageRaw?.close(); imageRaw=null }
                                trySave(true)
                            }else{
                                notify("Kein JPG geliefert. Bitte AUTO-TEST und Hardwarebericht prüfen.")
                                busy=false
                            }
                        }
                    },7000L)
                }
                override fun onCaptureFailed(s:CameraCaptureSession,r:CaptureRequest,f:CaptureFailure){
                    busy=false
                    resumePreview()
                    notify("Camera2-Aufnahme fehlgeschlagen: "+f.reason)
                }
            },handler)
        }catch(e:Exception){
            busy=false
            resumePreview()
            notify("Aufnahmefehler: "+e.message)
        }
    }

    private fun storeImage(image:Image?,isRaw:Boolean){
        if(image==null)return
        if(!busy){image.close();return}
        if(isRaw){imageRaw?.close();imageRaw=image}else{imageJpg?.close();imageJpg=image}
        trySave()
    }

    private fun trySave(allowJpegOnly:Boolean=false){
        val capture=onCapture?:return
        val j=imageJpg?:return
        if(raw!=null&&imageRaw==null&&!allowJpegOnly)return
        val r=imageRaw
        onCapture=null;imageJpg=null;imageRaw=null
        val actualNanos=capture.get(CaptureResult.SENSOR_EXPOSURE_TIME)
        val actualIso=capture.get(CaptureResult.SENSOR_SENSITIVITY)
        val aeMode=capture.get(CaptureResult.CONTROL_AE_MODE)
        val elapsed=(SystemClock.elapsedRealtime()-requestedAtMs)/1000.0
        val actualSec=actualNanos?.div(1e9)
        val measured=if(actualSec!=null) String.format(Locale.GERMANY,"%.4f",actualSec) else "nicht verfügbar"
        val info="Anfrage: " + (if(autoExposure)"AUTO" else String.format(Locale.GERMANY,"%.1f s / ISO %d",requestedSeconds,requestedIso)) +
            "\nSensor meldet: "+measured+" s / ISO "+(actualIso?.toString()?:"?")+
            "\nAE-Modus: "+aeMode+" · Wartezeit: "+String.format(Locale.GERMANY,"%.2f",elapsed)+" s"
        val mismatch=!autoExposure && (actualSec==null || actualSec<requestedSeconds*0.90)
        Thread{
            try{
                val name=SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(Date())
                val buffer=j.planes[0].buffer
                val bytes=ByteArray(buffer.remaining());buffer.get(bytes)
                save(name+".jpg","image/jpeg"){it.write(bytes)}
                if(r!=null){
                    save(name+".dng","image/x-adobe-dng"){stream->
                        DngCreator(characteristics!!,capture).use{it.writeImage(stream,r)}
                    }
                }
                try{ToneGenerator(AudioManager.STREAM_NOTIFICATION,50).apply{startTone(ToneGenerator.TONE_PROP_ACK,200);Thread.sleep(230);release()}}catch(_:Exception){}
                notify("Gespeichert: Pictures/MUNIVERSE ("+(if(r!=null)"JPG + DNG" else "JPG")+")\n"+info+
                    if(mismatch)"\nWARNUNG: Belichtung wurde vom Gerät nicht wie angefordert umgesetzt." else "")
            }catch(e:Exception){notify("Speicherfehler: "+e.message)}
            finally{j.close();r?.close();busy=false}
        }.start()
    }
    private fun save(filename:String,mime:String,write:(java.io.OutputStream)->Unit){
        val values=ContentValues().apply{
            put(MediaStore.Images.Media.DISPLAY_NAME,"MUNIVERSE_"+filename)
            put(MediaStore.Images.Media.MIME_TYPE,mime)
            put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/MUNIVERSE")
            put(MediaStore.Images.Media.IS_PENDING,1)
        }
        val uri=activity.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)?:error("MediaStore insert")
        try{
            activity.contentResolver.openOutputStream(uri)?.use{write(it)}?:error("OutputStream unavailable")
            values.clear();values.put(MediaStore.Images.Media.IS_PENDING,0)
            activity.contentResolver.update(uri,values,null,null)
        }catch(e:Exception){activity.contentResolver.delete(uri,null,null);throw e}
    }
    fun close(){
        try{session?.close()}catch(_:Exception){}
        try{device?.close()}catch(_:Exception){}
        try{surface?.release()}catch(_:Exception){}
        try{jpeg?.close()}catch(_:Exception){}
        try{raw?.close()}catch(_:Exception){}
        session=null;device=null;surface=null;jpeg=null;raw=null
        try{imageJpg?.close();imageRaw?.close()}catch(_:Exception){}
        imageJpg=null;imageRaw=null;onCapture=null;busy=false
    }
    fun shutdown(){close();worker.quitSafely()}
}
