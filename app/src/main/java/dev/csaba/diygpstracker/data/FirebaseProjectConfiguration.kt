package dev.csaba.diygpstracker.data


data class FirebaseProjectConfiguration(
    var projectId: String = "",
    var applicationId: String = "",
    var apiKey: String = "",
    var googleAuth: Boolean = false,
    var lookBackMinutes: Int = 10
)
