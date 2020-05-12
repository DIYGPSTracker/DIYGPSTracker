package dev.csaba.diygpstracker.data

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import dev.csaba.diygpstracker.data.remote.RemoteReport
import dev.csaba.diygpstracker.data.remote.mapToReport
import dev.csaba.diygpstracker.data.remote.mapToReportData
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import java.time.LocalDateTime


class FirestoreReportRepository(secondaryDB: FirebaseFirestore, assetId: String, lookBackMinutes: Int) : IReportRepository {

    companion object {
        private const val TAG = "FirestoreReportRepo"
        private const val ASSET_COLLECTION = "Assets"
        private const val REPORT_COLLECTION = "Reports"
    }

    private val remoteDB: FirebaseFirestore = secondaryDB
    private var changeObservable: Observable<List<DocumentSnapshot>>
    private val _assetId: String = assetId
    private var _lookBackMinutes: Int = lookBackMinutes

    init {
        changeObservable = BehaviorSubject.create { emitter: ObservableEmitter<List<DocumentSnapshot>> ->
            val listeningRegistration = remoteDB.collection(ASSET_COLLECTION)
                .document(_assetId).collection(REPORT_COLLECTION)
                .whereGreaterThan("created",
                    LocalDateTime.now().minusMinutes(_lookBackMinutes.toLong()))
                .addSnapshotListener { value, error ->
                    if (value == null || error != null) {
                        return@addSnapshotListener
                    }

                    if (!emitter.isDisposed) {
                        emitter.onNext(value.documents)
                    }

                    value.documentChanges.forEach {
                        Log.d(TAG, "Data changed type ${it.type} document ${it.document.id}")
                    }
                }

            emitter.setCancellable { listeningRegistration.remove() }
        }
    }

    private fun mapDocumentToRemoteReport(document: DocumentSnapshot) = document.toObject(RemoteReport::class.java)!!.apply { id = document.id }

    override fun getAllReports(): Single<List<Report>> {
        return Single.create<List<DocumentSnapshot>> { emitter ->
            remoteDB.collection(ASSET_COLLECTION)
                .document(_assetId).collection(REPORT_COLLECTION)
                .orderBy("created", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener {
                    if (!emitter.isDisposed) {
                        emitter.onSuccess(it.documents)
                    }
                }
                .addOnFailureListener {
                    if (!emitter.isDisposed) {
                        emitter.onError(it)
                    }
                }
        }
            .observeOn(Schedulers.io())
            .flatMapObservable { Observable.fromIterable(it) }
            .map(::mapDocumentToRemoteReport)
            .map(::mapToReport)
            .toList()
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

    override fun getChangeObservable(): Observable<List<Report>> =
        changeObservable.hide()
            .observeOn(Schedulers.io())
            .map { list -> list.map(::mapDocumentToRemoteReport).map(::mapToReport) }

}
