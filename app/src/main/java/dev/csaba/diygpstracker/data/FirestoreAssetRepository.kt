package dev.csaba.diygpstracker.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import dev.csaba.diygpstracker.data.remote.mapToAsset
import dev.csaba.diygpstracker.data.remote.RemoteAsset
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import timber.log.Timber


class FirestoreAssetRepository(secondaryDB: FirebaseFirestore) : IAssetRepository {

    companion object {
        private const val ASSET_COLLECTION = "Assets"
    }

    private var remoteDB: FirebaseFirestore = secondaryDB
    private var changeObservable: Observable<List<DocumentSnapshot>>

    init {
        changeObservable = BehaviorSubject.create { emitter: ObservableEmitter<List<DocumentSnapshot>> ->
            val listeningRegistration = remoteDB.collection(ASSET_COLLECTION)
                .addSnapshotListener { value, error ->
                    if (value == null || error != null) {
                        return@addSnapshotListener
                    }

                    if (!emitter.isDisposed) {
                        emitter.onNext(value.documents)
                    }

                    value.documentChanges.forEach {
                        Timber.d("Data changed type ${it.type} document ${it.document.id}")
                    }
                }

            emitter.setCancellable { listeningRegistration.remove() }
        }
    }

    override fun getAllAssets(): Single<List<Asset>> {
        return Single.create<List<DocumentSnapshot>> { emitter ->
            remoteDB.collection(ASSET_COLLECTION)
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
            .map(::mapDocumentToRemoteAsset)
            .map(::mapToAsset)
            .toList()
    }

    private fun mapDocumentToRemoteAsset(document: DocumentSnapshot) = document.toObject(RemoteAsset::class.java)!!.apply { id = document.id }

    override fun getChangeObservable(): Observable<List<Asset>> =
        changeObservable.hide()
            .observeOn(Schedulers.io())
            .map { list -> list.map(::mapDocumentToRemoteAsset).map(::mapToAsset) }

}
