package dev.csaba.diygpstracker

import android.app.job.JobParameters
import android.app.job.JobService

class RunnableLocationTracker: Runnable {
    init {
    }

    override fun run() {

    }
}


class TrackerJobService: JobService() {
    override fun onCreate() {
        super.onCreate();
        // Maybe pre-authenticate here
        // Also possibly initialize repository
    }

    override fun onStartJob(params: JobParameters): Boolean {
        val runnableTracker = RunnableLocationTracker()
        runnableTracker.run()

        runnableTracker = Task(this) {
            protected fun onPostExecute(success: Boolean?) {
                jobFinished(params, !success!!)
            }
        }
        mDownloadArtworkTask.execute()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        return !mDownloader.isFinished();
    }
}
