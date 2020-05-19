package dev.csaba.diygpstracker.data

import io.reactivex.Completable
import io.reactivex.Observable

interface IReportRepository {

    fun addReport(report: Report): Completable

    fun setAssetPeriodInterval(periodIntervalProgress: Int): Completable

    fun getChangeObservable(): Observable<Asset>
}
