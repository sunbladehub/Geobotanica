package com.geobotanica.geobotanica.android.location

import org.mapsforge.core.model.LatLong
import org.threeten.bp.OffsetDateTime
import java.io.Serializable
import kotlin.math.max

data class Location(
        val latitude: Double? = null,
        val longitude: Double? = null,
        val altitude: Double? = null,
        val precision: Float? = null,
        val satellitesInUse: Int? = null,
        val satellitesVisible: Int, // Before GPS fix, visible satellites are always available
        val timestamp: OffsetDateTime = OffsetDateTime.now()
): Serializable {
    fun isRecent(): Boolean = OffsetDateTime.now().minusSeconds(1).isBefore(this.timestamp)

    fun mergeWith(location: Location): Location {
        return Location(
                latitude ?: location.latitude,
                longitude ?: location.longitude,
                altitude ?: location.altitude,
                precision ?: location.precision,
                satellitesInUse ?: location.satellitesInUse,
                satellitesVisible = max(satellitesVisible, location.satellitesVisible)
        )
    }

    fun toLatLong() = LatLong(latitude!!, longitude!!)
}