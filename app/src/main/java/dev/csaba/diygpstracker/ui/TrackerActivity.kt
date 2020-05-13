package dev.csaba.diygpstracker.ui

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.view.Menu
import androidx.appcompat.app.AppCompatActivity
import dev.csaba.diygpstracker.R
import dev.csaba.diygpstracker.TrackerJobService
import dev.csaba.diygpstracker.viewmodel.TrackerViewModel


class TrackerActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_LOCATION_PERMISSION = 1
        private const val LOCATION_TRACKER_JOB_ID = 9002
    }

    private lateinit var viewModel: TrackerViewModel
    private var isRestore = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isRestore = savedInstanceState != null
        setContentView(R.layout.activity_tracker)

        val jobScheduler =
            getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        jobScheduler.schedule(
            JobInfo.Builder(
                LOCATION_TRACKER_JOB_ID,
                ComponentName(this, TrackerJobService::class.java)
            )
                .setPeriodic(10000)
                .setBackoffCriteria(5000, JobInfo.BACKOFF_POLICY_LINEAR)
                .setPersisted(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build()
        )
    }

    // Initializes contents of Activity's standard options menu. Only called the first time options
    // menu is displayed.
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.map_options, menu)
        return true
    }
}
