package dev.csaba.diygpstracker.data

import java.util.Date

data class Notification(
    var id: String = "",
    var title: String = "",
    var body: String = "",
    var processed: Boolean = false,
    var created: Date = Date()
)
