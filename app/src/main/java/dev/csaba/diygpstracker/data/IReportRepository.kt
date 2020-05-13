package dev.csaba.diygpstracker.data

import io.reactivex.Completable
import io.reactivex.Single


interface IReportRepository {

    fun getAllReports(): Single<List<Report>>

    fun addReport(report: Report): Completable
}
