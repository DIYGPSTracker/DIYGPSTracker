package dev.csaba.diygpstracker.data

import io.reactivex.Completable

interface IReportRepository {

    fun addReport(report: Report): Completable
}
