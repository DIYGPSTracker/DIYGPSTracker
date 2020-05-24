package dev.csaba.diygpstracker.data

import java.util.Date


data class Asset(
    var id: String = "",
    var title: String = "",
    var lock: Boolean = false,  // Manager sets it
    var lockLat: Double = .0,  // Tracker sets it as a response to lock
    var lockLon: Double = .0,  // Tracker sets it as a response to lock
    var lockRadius: Int = 0,
    var lockAlert: Boolean = false,  // Tracker trips it
    var periodInterval: Int = 3600,
    var created: Date = Date(),
    var updated: Date = Date()
)
