package dev.csaba.diygpstracker.data.remote

import com.google.firebase.Timestamp


data class RemoteReport(
    var id: String = "",
    var lat: Double = .0,
    var lon: Double = .0,
    var battery: Double = .0,
    var created: Timestamp = Timestamp(0, 0)
)
