package dev.csaba.diygpstracker.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import dev.csaba.diygpstracker.data.remote.*
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import timber.log.Timber


class FirestoreReportRepository(secondaryDB: FirebaseFirestore, assetId: String) : IReportRepository {

    companion object {
        private const val ASSET_COLLECTION = "Assets"
        private const val REPORT_COLLECTION = "Reports"
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

    override fun setAssetPeriodInterval(periodIntervalProgress: Int): Completable {
        return Completable.create { emitter ->
            remoteDB.collection(ASSET_COLLECTION)
                .document(_assetId)
                .update(mapToPeriodIntervalUpdate(periodIntervalProgress))
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

    private fun mapDocumentToRemoteAsset(document: DocumentSnapshot) = document.toObject(RemoteAsset::class.java)!!.apply { id = document.id }

    override fun getChangeObservable(): Observable<Asset> =
        changeObservable.hide()
            .observeOn(Schedulers.io())
            .map(::mapDocumentToRemoteAsset).map(::mapToAsset)

}
