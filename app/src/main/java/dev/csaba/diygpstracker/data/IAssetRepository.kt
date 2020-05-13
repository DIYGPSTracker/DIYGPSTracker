package dev.csaba.diygpstracker.data

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single


interface IAssetRepository {

    fun getAllAssets(): Single<List<Asset>>

    fun setAssetLockRadius(assetId: String, lockRadius: Int): Completable
    fun setAssetPeriodInterval(assetId: String, periodIntervalProgress: Int): Completable

    fun getChangeObservable(): Observable<List<Asset>>
}
