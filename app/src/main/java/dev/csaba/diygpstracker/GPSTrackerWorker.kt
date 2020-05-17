package dev.csaba.diygpstracker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters


class GPSTrackerWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    companion object {
        private val TAG = GPSTrackerWorker::class.java.simpleName
    }

//    val data = workDataOf(
//        KEY_MY_INT to myIntVar,
//        KEY_MY_INT_ARRAY to myIntArray,
//        KEY_MY_STRING to myString
//    )

    override suspend fun doWork(): Result {
        val serverUrl = inputData.getString("SERVER_URL")

        return try {
            // Do something with the URL
            Result.success()
        } catch (error: Throwable) {
            if (runAttemptCount <3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}