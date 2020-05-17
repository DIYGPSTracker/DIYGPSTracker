package dev.csaba.diygpstracker

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.google.common.util.concurrent.ListenableFuture

class GPSTrackerWorker(appContext: Context, params: WorkerParameters) :
    ListenableWorker(appContext, params) {

    override fun startWork(): ListenableFuture<ListenableWorker.Result> {
        // Do your work here.
        TODO("Return a ListenableFuture<Result>")
    }

    override fun onStopped() {
        // Cleanup because you are being stopped.
    }
}
