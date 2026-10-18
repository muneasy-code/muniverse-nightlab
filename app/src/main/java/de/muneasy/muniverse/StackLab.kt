package de.muneasy.muniverse

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Local, non-generative image alignment and noise averaging.
 * Alpha: JPEG input only, conservatively downsized to <=1600 pixels on the long edge.
 * Never adds points of light and never changes or deletes its source images.
 */
object StackLab {
    data class Output(val uri: Uri, val count: Int, val width: Int, val height: Int, val shifts: String)

    /**
     * Discover the most recent camera-generated BURST folder.
     * Android permits querying MediaStore media owned by this application.
     * SAF manual selection is available if ownership/indexing prevents discovery.
     */
    fun latestBurst(context: Context): List<Uri> {
        val columns=arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH
        )
        val folderLike="Pictures/MUNIVERSE/Burst_%"
        val items=mutableListOf<Triple<String,String,Uri>>()
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, columns,
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?",
            arrayOf(folderLike,"frame_%.jpg"),
            "${MediaStore.Images.Media._ID} DESC"
        )?.use { c ->
            val iId=c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val iName=c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val iFolder=c.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
            while(c.moveToNext()){
                val name=c.getString(iName)?:continue
                val folder=c.getString(iFolder)?:continue
                if(!folder.contains("/Burst_")||!name.startsWith("frame_"))continue
                val uri=ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,c.getLong(iId))
                items.add(Triple(folder,name,uri))
                if(items.size>=120)break
            }
        }
        val folder=items.firstOrNull()?.first?:return emptyList()
        return items.filter{it.first==folder}.sortedBy{it.second}.take(30).map{it.third}
    }

    fun stack(context:Context, input:List<Uri>, progress:(String)->Unit):Output {
        require(input.size in 2..30){"Bitte 2 bis 30 JPGs auswählen."}
        progress("Lese Originalbilder…")
        val base=decodeSmall(context,input.first())
        val w=base.width
        val h=base.height
        val length=w*h
        require(length in 1000..4000000){"Ungültige Bildauflösung."}
        val source=IntArray(length)
        base.getPixels(source,0,w,0,0,w,h)
        base.recycle()
        val red=IntArray(length)
        val green=IntArray(length)
        val blue=IntArray(length)
        val counts=ByteArray(length)
        val lumaBase=byteLuma(source)
        for(i in 0 until length){
            val p=source[i]
            red[i]=(p shr 16) and 255
            green[i]=(p shr 8) and 255
            blue[i]=p and 255
            counts[i]=1
        }
        val featurePoints=features(lumaBase,w,h)
        val shifts=mutableListOf("0,0")
        for(frame in 1 until input.size){
            progress("Richte Bild ${frame+1}/${input.size} aus…")
            val bmp=decodeSmall(context,input[frame],w,h)
            require(bmp.width==w && bmp.height==h){"Bildgrößen unterscheiden sich. Bitte nur eine Burst-Serie auswählen."}
            val pix=IntArray(length)
            bmp.getPixels(pix,0,w,0,0,w,h)
            bmp.recycle()
            val (dx,dy)=shift(lumaBase,byteLuma(pix),featurePoints,w,h)
            shifts.add("$dx,$dy")
            // Translate source into reference coordinates. Never interpolate / generate detail.
            for(y in max(0,-dy) until minOf(h,h-dy)){
                val sy=y+dy
                val offset=y*w
                val sourceOffset=sy*w
                for(x in max(0,-dx) until minOf(w,w-dx)){
                    val pos=offset+x
                    val p=pix[sourceOffset+x+dx]
                    red[pos]+=(p shr 16) and 255
                    green[pos]+=(p shr 8) and 255
                    blue[pos]+=p and 255
                    counts[pos]=(counts[pos].toInt()+1).toByte()
                }
            }
        }
        progress("Berechne Mittelwert aus echten Pixeln…")
        val output=IntArray(length)
        for(i in 0 until length){
            val c=counts[i].toInt().coerceAtLeast(1)
            output[i]= -0x1000000 or
                ((red[i]/c) shl 16) or
                ((green[i]/c) shl 8) or
                (blue[i]/c)
        }
        val bitmap=Bitmap.createBitmap(output,w,h,Bitmap.Config.ARGB_8888)
        val filename="MUNIVERSE_STACK_"+SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(Date())+".png"
        val data=ContentValues().apply{
            put(MediaStore.Images.Media.DISPLAY_NAME,filename)
            put(MediaStore.Images.Media.MIME_TYPE,"image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/MUNIVERSE/Stacks")
            put(MediaStore.Images.Media.IS_PENDING,1)
        }
        val uri=context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,data)
            ?:error("Mediathek konnte keine Datei anlegen.")
        try{
            context.contentResolver.openOutputStream(uri)?.use{
                check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)){"PNG-Export fehlgeschlagen."}
            }?:error("Ausgabedatei nicht geöffnet.")
            val ready=ContentValues().apply{put(MediaStore.Images.Media.IS_PENDING,0)}
            context.contentResolver.update(uri,ready,null,null)
        }catch(e:Exception){
            context.contentResolver.delete(uri,null,null)
            throw e
        }finally{bitmap.recycle()}
        return Output(uri,input.size,w,h,shifts.joinToString(" | "))
    }

    private fun decodeSmall(context:Context, uri:Uri, targetW:Int=0,targetH:Int=0):Bitmap {
        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
        context.contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,bounds)}
            ?:error("JPG-Datei nicht lesbar")
        require(bounds.outWidth>0 && bounds.outHeight>0){"Datei ist kein dekodierbares Foto."}
        val maxSide=1600
        var factor=1
        while(max(bounds.outWidth,bounds.outHeight)/factor>maxSide*1.6)factor*=2
        val settings=BitmapFactory.Options().apply{
            inSampleSize=factor
            inPreferredConfig=Bitmap.Config.ARGB_8888
        }
        val decoded=context.contentResolver.openInputStream(uri)?.use{
            BitmapFactory.decodeStream(it,null,settings)
        }?:error("JPG-Dekodierung fehlgeschlagen")
        val outW=if(targetW>0)targetW else (decoded.width*minOf(1.0,maxSide.toDouble()/max(decoded.width,decoded.height))).roundToInt().coerceAtLeast(1)
        val outH=if(targetH>0)targetH else (decoded.height*minOf(1.0,maxSide.toDouble()/max(decoded.width,decoded.height))).roundToInt().coerceAtLeast(1)
        // Reject differently oriented frames, do not geometrically warp a scene to fit.
        if(targetW>0 && abs(decoded.width.toDouble()/decoded.height-targetW.toDouble()/targetH)>0.02){
            decoded.recycle()
            error("Bilder haben unterschiedliche Formate/Ausrichtung.")
        }
        if(decoded.width==outW && decoded.height==outH)return decoded
        val resized=Bitmap.createScaledBitmap(decoded,outW,outH,true)
        decoded.recycle()
        return resized
    }

    private fun byteLuma(pixels:IntArray):ByteArray {
        val out=ByteArray(pixels.size)
        for(i in pixels.indices){
            val p=pixels[i]
            out[i]=((((p shr 16 and 255)*77 +(p shr 8 and 255)*150 + (p and 255)*29) shr 8)).toByte()
        }
        return out
    }

    private fun features(gray:ByteArray,w:Int,h:Int):List<Int> {
        val selected=ArrayList<Pair<Int,Int>>()
        val xmin=max(10,w/10); val ymin=max(10,h/10)
        val xmax=w-xmin;val ymax=h-ymin
        for(y in ymin until ymax step 12){
            for(x in xmin until xmax step 12){
                val p=y*w+x
                val contrast=abs((gray[p+1].toInt() and 255)-(gray[p-1].toInt() and 255))+
                    abs((gray[p+w].toInt() and 255)-(gray[p-w].toInt() and 255))
                if(contrast>=18)selected.add(p to contrast)
            }
        }
        return selected.sortedByDescending{it.second}.take(1100).map{it.first}
    }

    private fun shift(reference:ByteArray,other:ByteArray,features:List<Int>,w:Int,h:Int):Pair<Int,Int> {
        // No reliable features: do not invent an alignment.
        if(features.size<35)return 0 to 0
        var bestScore=Double.POSITIVE_INFINITY
        var bestDx=0;var bestDy=0
        var zeroScore=Double.POSITIVE_INFINITY
        for(dy in -8..8){
            for(dx in -8..8){
                var total=0L
                var valid=0
                for(p in features){
                    val x=p%w
                    val y=p/w
                    if(x+dx !in 0 until w || y+dy !in 0 until h)continue
                    val ref=reference[p].toInt() and 255
                    val obs=other[(y+dy)*w+x+dx].toInt() and 255
                    total+=abs(ref-obs)
                    valid++
                }
                if(valid==0)continue
                val avg=total.toDouble()/valid
                if(dx==0&&dy==0)zeroScore=avg
                if(avg<bestScore){bestScore=avg;bestDx=dx;bestDy=dy}
            }
        }
        // Only shift for a meaningful improvement; avoids aligning random sensor noise.
        if(bestScore>=zeroScore*0.94)return 0 to 0
        return bestDx to bestDy
    }
}
