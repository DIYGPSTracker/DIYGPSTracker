package dev.csaba.diygpstracker.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import dev.csaba.diygpstracker.data.remote.RemoteReport
import dev.csaba.diygpstracker.data.remote.mapToReport
import dev.csaba.diygpstracker.data.remote.mapToReportData
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers


class FirestoreReportRepository(secondaryDB: FirebaseFirestore, assetId: String) : IReportRepository {

    companion object {
        private const val TAG = "FirestoreReportRepo"
        private const val ASSET_COLLECTION = "Assets"
        private const val REPORT_COLLECTION = "Reports"
    }

    private val remoteDB: FirebaseFirestore = secondaryDB
    private val _assetId: String = assetId

    override fun addReport(report: Report): Completable {
        return Completable.create { emitter ->
            remoteDB.collection(ASSET_COLLECTION)
                .document(_assetId).collection(REPORT_COLLECTION)
                .add(mapToReportData(report))
                .addOnSuccessListener {
                    it.collection(REPORT_COLLECTION)
                    if (!emitter.isDisposed) {
                        emitter.onComplete()
                    }
                }
                .addOnFailureListener {
                    if (!emitter.isDisposed) {
                        emitter.onError(it)
                    }
                }
        }
    }
}
