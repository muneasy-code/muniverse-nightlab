package de.muneasy.muniverse

import android.content.Context
import android.hardware.*
import kotlin.math.*

class SkySensors(context:Context, private val onHeading:(Bearing,Int)->Unit):SensorEventListener {
    private val manager=context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accel=manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnet=manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private var a:FloatArray?=null
    private var m:FloatArray?=null
    private var accuracy=SensorManager.SENSOR_STATUS_UNRELIABLE
    var available:Boolean = accel!=null && magnet!=null
    fun start() {
        accel?.let{manager.registerListener(this,it,SensorManager.SENSOR_DELAY_UI)}
        magnet?.let{manager.registerListener(this,it,SensorManager.SENSOR_DELAY_UI)}
    }
    fun stop()=manager.unregisterListener(this)
    override fun onAccuracyChanged(sensor:Sensor?,value:Int) { if(sensor?.type==Sensor.TYPE_MAGNETIC_FIELD)accuracy=value }
    override fun onSensorChanged(event:SensorEvent) {
        if(event.sensor.type==Sensor.TYPE_ACCELEROMETER) a=event.values.clone()
        if(event.sensor.type==Sensor.TYPE_MAGNETIC_FIELD)m=event.values.clone()
        val aa=a?:return
        val mm=m?:return
        val rot=FloatArray(9)
        val inc=FloatArray(9)
        if(!SensorManager.getRotationMatrix(rot,inc,aa,mm))return
        val east=-rot[2].toDouble()
        val north=-rot[5].toDouble()
        val up=-rot[8].toDouble()
        onHeading(Bearing((Math.toDegrees(atan2(east,north))+360)%360,Math.toDegrees(asin(up.coerceIn(-1.0,1.0)))),accuracy)
    }
}
