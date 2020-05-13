package dev.csaba.diygpstracker

import android.app.job.JobParameters
import android.app.job.JobService

class TrackerJobService: JobService() {
    override fun onCreate() {
        super.onCreate();
        mDownloader = ArtworkDownloader.getSequencialDownloader();
    }

    override fun onStartJob(params: JobParameters): Boolean {
        return mDownloader.hasPendingArtworkDownload();
    }

    override fun onStopJob(params: JobParameters): Boolean {
        return !mDownloader.isFinished();
    }
}
