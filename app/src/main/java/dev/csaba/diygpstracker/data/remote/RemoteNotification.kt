package dev.csaba.diygpstracker.data.remote

import com.google.firebase.Timestamp

data class RemoteNotification(
    var id: String = "",
    var title: String = "",
    var body: String = "",
    var processed: Boolean = false,
    var created: Timestamp = Timestamp(0, 0)
)
