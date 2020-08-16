package dev.csaba.diygpstracker.data

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

// https://stackoverflow.com/questions/58515475/is-there-a-way-to-integrate-encryptedsharedpreference-with-preferencescreen
// https://stackoverflow.com/questions/40398072/singleton-with-parameter-in-kotlin
class EncryptedPreferenceDataStore private constructor(context: Context) : PreferenceDataStore() {
    companion object {
        private const val SHARED_PREFERENCES_NAME = "secret_shared_preferences"

        @Volatile private var INSTANCE: EncryptedPreferenceDataStore? = null

        fun getInstance(context: Context): EncryptedPreferenceDataStore =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: EncryptedPreferenceDataStore(context).also { INSTANCE = it }
            }
    }

    private var mSharedPreferences: SharedPreferences
    private lateinit var mContext: Context

    init {
        try {
            mContext = context
            val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            mSharedPreferences = EncryptedSharedPreferences.create(
                context,
                SHARED_PREFERENCES_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback, default mode is Context.MODE_PRIVATE!
            mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        }
    }

    override fun putString(key: String, value: String?) {
        mSharedPreferences.edit().putString(key, value).apply()
    }

    override fun putStringSet(key: String, values: Set<String>?) {
        mSharedPreferences.edit().putStringSet(key, values).apply()
    }

    override fun putInt(key: String, value: Int) {
        mSharedPreferences.edit().putInt(key, value).apply()
    }

    override fun putLong(key: String, value: Long) {
        mSharedPreferences.edit().putLong(key, value).apply()
    }

    override fun putFloat(key: String, value: Float) {
        mSharedPreferences.edit().putFloat(key, value).apply()
    }

    override fun putBoolean(key: String, value: Boolean) {
        mSharedPreferences.edit().putBoolean(key, value).apply()
    }

    override fun getString(key: String, defValue: String?): String? {
        return mSharedPreferences.getString(key, defValue)
    }

    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? {
        return mSharedPreferences.getStringSet(key, defValues)
    }

    override fun getInt(key: String, defValue: Int): Int {
        return mSharedPreferences.getInt(key, defValue)
    }

    override fun getLong(key: String, defValue: Long): Long {
        return mSharedPreferences.getLong(key, defValue)
    }

    override fun getFloat(key: String, defValue: Float): Float {
        return mSharedPreferences.getFloat(key, defValue)
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        return mSharedPreferences.getBoolean(key, defValue)
    }
}