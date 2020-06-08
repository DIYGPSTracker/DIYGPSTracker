package dev.csaba.diygpstracker.data.remote

import com.google.firebase.Timestamp
import dev.csaba.diygpstracker.data.Asset
import java.util.Date


fun mapToAsset(remoteAsset: RemoteAsset): Asset {
    return Asset(
        remoteAsset.id,
        remoteAsset.title,
        remoteAsset.lock,
        remoteAsset.lockLat,
        remoteAsset.lockLon,
        remoteAsset.lockRadius,
        remoteAsset.lockAlert,
        remoteAsset.lockManualAlert,
        remoteAsset.periodInterval,
        remoteAsset.created.toDate(),
        remoteAsset.updated.toDate()
    )
}

fun mapDateToTimestamp(date: Date): Timestamp {
    return Timestamp(date.time / 1000, (date.time % 1000 * 1000).toInt())
}

fun mapPeriodIntervalToProgress(periodInterval: Int): Int {
    val intervals = intArrayOf(0, 10, 60, 600, 3600, 86400)

    for ((index, interval) in intervals.withIndex()) {
        if (periodInterval <= interval)
            return index
    }

    return intervals.size - 1
}

fun mapToGeoFenceExitedUpdate(periodInterval: Int, alert: Boolean, native: Boolean): HashMap<String, Any> {
    val updateMap: HashMap<String, Any> = hashMapOf(
        "periodInterval" to periodInterval,
        "lockAlert" to alert,
        "updated" to mapDateToTimestamp(Date())
    )
    if (native) {
        updateMap["lockManualAlert"] = true
    }

    return updateMap
}

fun mapToLockLocationUpdate(lat: Double, lon: Double): HashMap<String, Any> {
    return hashMapOf(
        "lockLat" to lat,
        "lockLon" to lon,
        "updated" to mapDateToTimestamp(Date())
    )
}
