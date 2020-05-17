package dev.csaba.diygpstracker

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import timber.log.Timber
import timber.log.Timber.DebugTree

class ApplicationSingleton: Application() {
    var _firebaseApp: FirebaseApp? = null
    var _firestore: FirebaseFirestore? = null

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(DebugTree())
        }
    }

    var firebaseApp: FirebaseApp?
        get() = _firebaseApp
        set(value) {
            _firebaseApp = value
        }

    var firestore: FirebaseFirestore?
        get() = _firestore
        set(value) {
            _firestore = value
        }
}
