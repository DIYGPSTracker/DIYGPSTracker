package dev.csaba.diygpstracker.data


data class FirebaseProjectConfiguration(
    var projectId: String = "",
    var applicationId: String = "",
    var apiKey: String = "",
    var authType: String = "email",
    var email: String = "",
    var code: String = "",
    var lookBackMinutes: Int = 10
)
