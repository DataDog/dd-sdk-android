package com.datadog.android.sdk.integrationtests

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.datadog.android.log.Logger
import java.io.IOException

class ActivityProfiling : AppCompatActivity() {

    lateinit var logger: Logger

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logger = Runtime.logger(this)
        setContentView(R.layout.profiling_activity_layout)
    }

    override fun onStart() {
        super.onStart()
        val crash = IOException()
        val attributes = mutableMapOf<String, String>()
        for (i in 0..100) {
            attributes["key$i"] = "value$i"
        }

        repeat(50) {
            logger.d(
                "Test Crash",
                crash,
                attributes = attributes
            )
        }
    }
}
