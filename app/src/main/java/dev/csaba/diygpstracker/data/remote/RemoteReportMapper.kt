package dev.csaba.diygpstracker.data.remote

import dev.csaba.diygpstracker.data.Report
import dev.csaba.diygpstracker.data.Notification
import kotlin.collections.HashMap


fun mapToReportData(report: Report): HashMap<String, Any> {
    return hashMapOf(
        "lat" to report.lat,
        "lon" to report.lon,
        "speed" to report.speed,
        "battery" to report.battery,
        "created" to mapDateToTimestamp(report.created)
    )
}

fun mapToNotificationData(notification: Notification): HashMap<String, Any> {
    return hashMapOf(
        "id" to notification.id,
        "title" to notification.title,
        "body" to notification.body,
        "created" to mapDateToTimestamp(notification.created)
    )
}
