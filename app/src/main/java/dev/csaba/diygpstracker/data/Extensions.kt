package dev.csaba.diygpstracker.data

import android.content.SharedPreferences
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager


fun getPreferenceString(preferences: SharedPreferences, name: String): String {
    return preferences.getString(name, "") ?: return ""
}

fun mapValueToInterval(intervals: IntArray, value: Int): Int {
    if (value < 0)
        return intervals.first()

    if (value >= intervals.size)
        return intervals.last()

    return intervals[value]
}

fun FragmentActivity.getSecondaryFirebaseConfiguration(): FirebaseProjectConfiguration {
    val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
    return FirebaseProjectConfiguration(
        getPreferenceString(preferences, "project_id"),
        getPreferenceString(preferences, "application_id"),
        getPreferenceString(preferences, "api_key"),
        preferences.getBoolean("auth_type", false),
        preferences.getString("look_back_minutes", "10")!!.toInt()
    )
}

fun FragmentActivity.hasAuthConfiguration(): Boolean {
    val configuration = this.getSecondaryFirebaseConfiguration()
    return configuration.projectId.isNotBlank() &&
            configuration.applicationId.isNotBlank() &&
            configuration.apiKey.isNotBlank()
}
