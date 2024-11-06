/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.Navigation
import com.datadog.benchmark.sample.benchmark.DatadogBenchmark
import com.datadog.benchmark.sample.compose.MainView
import com.datadog.sample.benchmark.R

/**
 * MainActivity of benchmark sample application.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var navController: NavController
    private lateinit var datadogBenchmark: DatadogBenchmark
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        datadogBenchmark = DatadogBenchmark(
            DatadogBenchmark.Config.resolveSyntheticsBundle(intent.extras)
        )
        if (datadogBenchmark.isComposeEnabled) {
            supportActionBar?.hide()
            setContent {
                MainView()
            }
        } else {
            setContentView(R.layout.activity_main)
        }
    }

    override fun onStart() {
        super.onStart()
        datadogBenchmark.start()
    }

    override fun onStop() {
        super.onStop()
        datadogBenchmark.stop()
    }

    override fun onResume() {
        super.onResume()
        if (!datadogBenchmark.isComposeEnabled) {
            navController = Navigation.findNavController(this, R.id.nav_host_fragment)
        }
    }
}
