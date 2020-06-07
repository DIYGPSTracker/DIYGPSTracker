package dev.csaba.diygpstracker.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import dev.csaba.diygpstracker.data.remote.mapToAsset
import dev.csaba.diygpstracker.data.remote.mapToGeoFenceExitedUpdate
import dev.csaba.diygpstracker.data.remote.mapToLockLocationUpdate
import dev.csaba.diygpstracker.data.remote.mapToNotificationData
import dev.csaba.diygpstracker.data.remote.mapToReportData
import dev.csaba.diygpstracker.data.remote.RemoteAsset
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import timber.log.Timber


class FirestoreReportRepository(secondaryDB: FirebaseFirestore, assetId: String) : IReportRepository {

    companion object {
        private const val ASSET_COLLECTION = "Assets"
        private const val REPORT_COLLECTION = "Reports"
        private const val NOTIFICATION_COLLECTION = "Notifications"
    }

    private val remoteDB: FirebaseFirestore = secondaryDB
    private val _assetId: String = assetId
    private var changeObservable: Observable<DocumentSnapshot>

    init {
        changeObservable = BehaviorSubject.create { emitter: ObservableEmitter<DocumentSnapshot> ->
            val listeningRegistration = remoteDB.collection(ASSET_COLLECTION).document(_assetId)
                .addSnapshotListener { value, error ->
                    if (value == null || error != null) {
                        return@addSnapshotListener
                    }

                    if (!emitter.isDisposed) {
                        emitter.onNext(value)
                    }

                    Timber.d("Data changed of asset ${value.id}")
                }

            emitter.setCancellable { listeningRegistration.remove() }
        }
    }

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

    override fun setAssetLockLocation(lat: Double, lon: Double): Completable {
        return Completable.create { emitter ->
            remoteDB.collection(ASSET_COLLECTION)
                .document(_assetId)
                .update(mapToLockLocationUpdate(lat, lon))
                .addOnSuccessListener {
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

    override fun setAssetPeriodIntervalAndLockAlert(periodInterval: Int, lockAlert: Boolean): Completable {
        return Completable.create { emitter ->
            remoteDB.collection(ASSET_COLLECTION)
                .document(_assetId)
                .update(mapToGeoFenceExitedUpdate(periodInterval, lockAlert))
                .addOnSuccessListener {
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

    override fun sendNotification(notification: Notification): Completable {
        return Completable.create { emitter ->
            remoteDB.collection(ASSET_COLLECTION)
                .document(_assetId).collection(NOTIFICATION_COLLECTION)
                .add(mapToNotificationData(notification))
                .addOnSuccessListener {
                    it.collection(NOTIFICATION_COLLECTION)
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

    private fun mapDocumentToRemoteAsset(document: DocumentSnapshot) =
        document.toObject(RemoteAsset::class.java)!!.apply { id = document.id }

    override fun getAssetChangeObservable(): Observable<Asset> =
        changeObservable.hide()
            .observeOn(Schedulers.io())
            .map(::mapDocumentToRemoteAsset).map(::mapToAsset)

}
