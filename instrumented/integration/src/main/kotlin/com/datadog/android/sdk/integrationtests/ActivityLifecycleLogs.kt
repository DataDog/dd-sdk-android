package com.datadog.android.sdk.integrationtests

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.datadog.android.log.Logger

internal class ActivityLifecycleLogs : AppCompatActivity() {

    lateinit var logger: Logger

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logger = Runtime.logger(this)
        setContentView(R.layout.main_activity_layout)
        logger.i("MainActivity/onCreate")
    }

    override fun onStart() {
        super.onStart()
        logger.i("MainActivity/onStart")
    }

    override fun onResume() {
        super.onResume()
        logger.i("MainActivity/onResume")
    }
}
