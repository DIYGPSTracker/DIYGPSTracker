package dev.csaba.diygpstracker.data

import java.util.Date


data class Report(
    var id: String = "",
    var lat: Double = .0,
    var lon: Double = .0,
    var battery: Double = .0,
    var created: Date = Date()
)
