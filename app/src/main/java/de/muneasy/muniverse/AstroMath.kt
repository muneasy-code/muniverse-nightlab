package de.muneasy.muniverse

import kotlin.math.*

data class Target(val name: String, val ra: Double, val dec: Double)
data class Bearing(val az: Double, val alt: Double)

object AstroMath {
    val targets = listOf(
        Target("Plejaden · M45", 3.7833, 24.1167),
        Target("Doppelsternhaufen · Perseus", 2.3333, 57.1333),
        Target("Kassiopeia · Schedar", 0.675, 56.537),
        Target("Andromedagalaxie · M31", 0.712, 41.269),
        Target("Deneb · Schwan", 20.6905, 45.2803),
        Target("Orionnebel · M42", 5.5881, -5.3911)
    )
    private fun norm(x: Double) = (x % 360 + 360) % 360
    fun position(t: Target, lat: Double, lon: Double, ms: Long): Bearing {
        val jd = ms / 86400000.0 + 2440587.5
        val lst = norm(280.46061837 + 360.98564736629 * (jd - 2451545.0) + lon)
        val ha = Math.toRadians(norm(lst - 15*t.ra))
        val la = Math.toRadians(lat)
        val de = Math.toRadians(t.dec)
        val alt = asin((sin(la)*sin(de)+cos(la)*cos(de)*cos(ha)).coerceIn(-1.0,1.0))
        val az = atan2(-cos(de)*sin(ha), sin(de)*cos(la)-cos(de)*sin(la)*cos(ha))
        return Bearing(norm(Math.toDegrees(az)), Math.toDegrees(alt))
    }
    fun compass(deg: Double) = arrayOf("N","NO","O","SO","S","SW","W","NW")[(norm(deg + 22.5)/45).toInt()%8]
    fun angleDelta(a:Double,b:Double) = ((a-b+540)%360)-180
}
