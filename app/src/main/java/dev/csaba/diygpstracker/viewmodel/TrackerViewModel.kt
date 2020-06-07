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
import dev.csaba.diygpstracker.data.Notification
import dev.csaba.diygpstracker.data.Report
import java.time.LocalDateTime


class TrackerViewModel(firestore: FirebaseFirestore, assetId: String) : ViewModel() {

    var asset = MutableLiveData<Asset>()
    private var repository: IReportRepository = FirestoreReportRepository(firestore, assetId)
    private val disposable = CompositeDisposable()

    // Saved Instance variables
    var remoteAssetId = ""
    var lastLock = false
    var lockLat = .0
    var lockLon = .0
    var lockRadius = 0
    var lastPeriodInterval = 0
    @Volatile var geoFenceLatch = false
    var geoFenceIndex = 0

    init {
        repository.getAssetChangeObservable()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe (
                {
                    asset.value = it
                },
                {
                    it.printStackTrace()
                }
            )
            .addTo(disposable)
    }

    fun updateState(asset: Asset) {
        remoteAssetId = asset.id
        lastLock = asset.lock
        lockLat = asset.lockLat
        lockLon = asset.lockLon
        lockRadius = asset.lockRadius
        lastPeriodInterval = asset.periodInterval
    }

    fun addReport(lat: Double, lon: Double, speed: Float, battery: Int) {
        repository.addReport(
            Report("${System.currentTimeMillis()}", lat, lon, speed, battery)
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

    fun setAssetLockAlert(alert: Boolean) {
        repository.setAssetLockAlert(alert)
            .subscribeOn(Schedulers.io())
            .subscribe(
                {},
                {
                    it.printStackTrace()
                })
            .addTo(disposable)
    }

    fun sendGeoFencingNotification(native: Boolean) {
        val title = "Asset $remoteAssetId moved!"
        val geoFenceType = if (native) "Native" else "Manual"
        val dateTimeString = LocalDateTime.now()
        val body = "Asset exited $geoFenceType at $dateTimeString " +
                "(${lockLat}, ${lockLon} radius ${lockRadius}m)"
        repository.sendNotification(
            Notification("${System.currentTimeMillis()}", title, body)
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
