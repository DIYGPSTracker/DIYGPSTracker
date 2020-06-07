package dev.csaba.diygpstracker.data

import io.reactivex.Completable
import io.reactivex.Observable

interface IReportRepository {

    fun addReport(report: Report): Completable

    fun setAssetLockLocation(lat: Double, lon: Double): Completable

    fun setAssetPeriodInterval(periodIntervalProgress: Int): Completable

    fun setAssetLockAlert(alert: Boolean): Completable

    fun getAssetChangeObservable(): Observable<Asset>

    fun sendNotification(notification: Notification): Completable
}
