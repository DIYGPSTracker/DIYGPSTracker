package dev.csaba.diygpstracker.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.FirebaseFirestore
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import dev.csaba.diygpstracker.addTo
import dev.csaba.diygpstracker.data.FirestoreReportRepository
import dev.csaba.diygpstracker.data.IReportRepository
import dev.csaba.diygpstracker.data.Report


class TrackerViewModel(firestore: FirebaseFirestore, assetId: String, lookBackMinutes: Int) : ViewModel() {

    private val _reportList = MutableLiveData<List<Report>>()
    val reportList: LiveData<List<Report>>
        get() = _reportList

    private var repository: IReportRepository = FirestoreReportRepository(firestore, assetId, lookBackMinutes)

    private val disposable = CompositeDisposable()

    init {
        repository.getChangeObservable()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe (
                {
                    _reportList.value = it
                },
                {
                    it.printStackTrace()
                }
            )
            .addTo(disposable)
    }

    fun addReport(position: LatLng, battery: Double) {
        repository.addReport(
            Report("${System.currentTimeMillis()}", position.latitude, position.longitude, battery)
        )
            .subscribeOn(Schedulers.io())
            .subscribe(
                {},
                {
                    it.printStackTrace()
                })
            .addTo(disposable)
    }

    override fun onCleared() {
        super.onCleared()
        disposable.clear()
    }
}
