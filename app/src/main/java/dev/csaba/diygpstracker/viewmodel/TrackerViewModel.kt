package dev.csaba.diygpstracker.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import dev.csaba.diygpstracker.addTo
import dev.csaba.diygpstracker.data.Asset
import dev.csaba.diygpstracker.data.FirestoreReportRepository
import dev.csaba.diygpstracker.data.IReportRepository
import dev.csaba.diygpstracker.data.Report


class TrackerViewModel(firestore: FirebaseFirestore, assetId: String) : ViewModel() {

    var asset = MutableLiveData<Asset>()

    private var repository: IReportRepository = FirestoreReportRepository(firestore, assetId)

    private val disposable = CompositeDisposable()

    init {
        repository.getAssetChangeObservable()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe (
                {
                    asset = MutableLiveData(it)
                },
                {
                    it.printStackTrace()
                }
            )
            .addTo(disposable)
    }

    fun addReport(lat: Double, lon: Double, battery: Double) {
        repository.addReport(
            Report("${System.currentTimeMillis()}", lat, lon, battery)
        )
            .subscribeOn(Schedulers.io())
            .subscribe(
                {},
                {
                    it.printStackTrace()
                })
            .addTo(disposable)
    }

    fun setAssetLockLocation(lat: Double, lon: Double) {
        repository.setAssetLockLocation(lat, lon)
            .subscribeOn(Schedulers.io())
            .subscribe(
                {},
                {
                    it.printStackTrace()
                })
            .addTo(disposable)
    }

    fun setAssetPeriodInterval(periodIntervalProgress: Int) {
        repository.setAssetPeriodInterval(periodIntervalProgress)
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
