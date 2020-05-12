package dev.csaba.diygpstracker.data.remote

import com.google.firebase.Timestamp


data class RemoteAsset(
    var id: String = "",
    var title: String = "",
    var lock: Boolean = false,
    var lockLat: Double = .0,
    var lockLon: Double = .0,
    var lockRadius: Int = 0,
    var periodInterval: Int = 3600,
    var created: Timestamp = Timestamp(0, 0),
    var updated: Timestamp = Timestamp(0, 0)
)
