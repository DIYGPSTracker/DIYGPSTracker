package dev.csaba.diygpstracker.data.remote

import com.google.firebase.Timestamp


data class RemoteAsset(
    var id: String = "",
    var title: String = "",
    var lock: Boolean = false,  // Manager sets it
    var lockLat: Double = .0,  // Tracker sets it as a response to lock
    var lockLon: Double = .0,  // Tracker sets it as a response to lock
    var lockRadius: Int = 0,
    var lockAlert: Boolean = false,  // Tracker geo fence exit trips it
    var lockManualAlert: Boolean = false,  // Tracker manual geo fence exit trips it
    var periodInterval: Int = 3600,
    var created: Timestamp = Timestamp(0, 0),
    var updated: Timestamp = Timestamp(0, 0)
)
