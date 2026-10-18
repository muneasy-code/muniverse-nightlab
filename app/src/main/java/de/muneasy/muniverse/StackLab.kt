package de.muneasy.muniverse

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
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
    data class Output(
        val uri: Uri, val count: Int, val width: Int, val height: Int, val shifts: String,
        val clippedPercent: Double, val unreliableAlignments: Int
    )

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
        val clipped=source.count { p ->
            ((p shr 16) and 255)>=251 && ((p shr 8) and 255)>=251 && (p and 255)>=251
        }
        val clippedPercent=100.0*clipped/length
        if(clippedPercent>5.0)progress("WARNUNG: ${"%.1f".format(Locale.GERMANY,clippedPercent)} % Ausgangspixel sind fast weiß. Bereits überbelichtete Details lassen sich nicht wiederherstellen.")
        var unreliableAlignments=0
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
            val aligned=shift(lumaBase,byteLuma(pix),featurePoints,w,h)
            val dx=aligned.first
            val dy=aligned.second
            if(!aligned.third)unreliableAlignments++
            shifts.add("$dx,$dy"+if(aligned.third)"" else " (nicht zuverlässig)")
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
        return Output(uri,input.size,w,h,shifts.joinToString(" | "),clippedPercent,unreliableAlignments)
    }

    /**
     * BitmapFactory does not honor JPEG EXIF orientation. Normalize captured frames
     * before alignment and export, without altering the source JPEG or its metadata.
     */
    private fun decodeSmall(context:Context,uri:Uri,targetW:Int=0,targetH:Int=0):Bitmap{
        val orientation=try{
            context.contentResolver.openInputStream(uri)?.use { input ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL
                )
            }?:ExifInterface.ORIENTATION_NORMAL
        }catch(_:Exception){ExifInterface.ORIENTATION_NORMAL}
        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it,null,bounds)
        }?:error("JPG-Datei nicht lesbar")
        require(bounds.outWidth>0 && bounds.outHeight>0){"Keine dekodierbaren Bilddaten."}
        val maxSide=1600
        var factor=1
        while(max(bounds.outWidth,bounds.outHeight)/factor>maxSide*1.6)factor*=2
        val settings=BitmapFactory.Options().apply{
            inSampleSize=factor
            inPreferredConfig=Bitmap.Config.ARGB_8888
        }
        val decoded=context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it,null,settings)
        }?:error("JPG-Dekodierung fehlgeschlagen.")
        val matrix=Matrix()
        when(orientation){
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL->matrix.postScale(-1f,1f)
            ExifInterface.ORIENTATION_ROTATE_180->matrix.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL->matrix.postScale(1f,-1f)
            ExifInterface.ORIENTATION_TRANSPOSE->{matrix.postRotate(90f);matrix.postScale(-1f,1f)}
            ExifInterface.ORIENTATION_ROTATE_90->matrix.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE->{matrix.postRotate(270f);matrix.postScale(-1f,1f)}
            ExifInterface.ORIENTATION_ROTATE_270->matrix.postRotate(270f)
        }
        val upright=if(matrix.isIdentity)decoded else {
            val rotated=Bitmap.createBitmap(decoded,0,0,decoded.width,decoded.height,matrix,true)
            if(rotated!==decoded)decoded.recycle()
            rotated
        }
        val scale=minOf(1.0,maxSide.toDouble()/max(upright.width,upright.height))
        val outW=if(targetW>0)targetW else (upright.width*scale).roundToInt().coerceAtLeast(1)
        val outH=if(targetH>0)targetH else (upright.height*scale).roundToInt().coerceAtLeast(1)
        if(targetW>0 && abs(upright.width.toDouble()/upright.height-targetW.toDouble()/targetH)>0.02){
            upright.recycle()
            error("Einzelbilder haben unterschiedliche Formate/Ausrichtungen.")
        }
        if(upright.width==outW && upright.height==outH)return upright
        val resized=Bitmap.createScaledBitmap(upright,outW,outH,true)
        upright.recycle()
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

    private fun shift(reference:ByteArray,other:ByteArray,features:List<Int>,w:Int,h:Int):Triple<Int,Int,Boolean>{
        // Without enough stable image features, the safest honest choice is no shift.
        if(features.size<60)return Triple(0,0,false)
        var bestScore=Double.POSITIVE_INFINITY
        var bestDx=0;var bestDy=0
        var zeroScore=Double.POSITIVE_INFINITY
        val maxShift=8
        for(dy in -maxShift..maxShift){
            for(dx in -maxShift..maxShift){
                var total=0L
                var valid=0
                for(p in features){
                    val x=p%w
                    val y=p/w
                    if(x+dx !in 0 until w || y+dy !in 0 until h)continue
                    val a=reference[p].toInt() and 255
                    val b=other[(y+dy)*w+x+dx].toInt() and 255
                    // Saturated regions are useless for estimating image translation.
                    if(a>=250||b>=250)continue
                    total+=abs(a-b)
                    valid++
                }
                if(valid<features.size/3)continue
                val avg=total.toDouble()/valid
                if(dx==0&&dy==0)zeroScore=avg
                if(avg<bestScore){
                    bestScore=avg;bestDx=dx;bestDy=dy
                }
            }
        }
        // If optimum touches search edge, the true displacement is unknown.
        // Or noise can create an apparent alignment: reject weak improvements.
        val confident=bestDx!=maxShift && bestDx != -maxShift &&
            bestDy!=maxShift && bestDy != -maxShift &&
            bestScore.isFinite() && zeroScore.isFinite() &&
            bestScore<zeroScore*0.83 && (zeroScore-bestScore)>2.0
        return if(confident)Triple(bestDx,bestDy,true) else Triple(0,0,false)
    }
}
