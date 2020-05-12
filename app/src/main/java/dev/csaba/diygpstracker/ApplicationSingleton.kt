package dev.csaba.diygpstracker

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore

class ApplicationSingleton: Application() {
    var _firebaseApp: FirebaseApp? = null
    var _firestore: FirebaseFirestore? = null

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
