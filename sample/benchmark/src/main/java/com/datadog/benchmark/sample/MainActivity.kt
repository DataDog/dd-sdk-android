/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample

import android.app.Activity
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import com.datadog.benchmark.DatadogBaseMeter
import com.datadog.benchmark.sample.config.BenchmarkConfig
import com.datadog.benchmark.sample.di.activity.BenchmarkActivityComponent
import com.datadog.benchmark.sample.navigation.FragmentsNavigationManager
import com.datadog.benchmark.sample.ui.sessionreplaycompose.MainView
import com.datadog.sample.benchmark.R
import javax.inject.Inject

/**
 * MainActivity of benchmark sample application.
 */
class MainActivity : AppCompatActivity() {

    @Inject
    internal lateinit var datadogMeter: DatadogBaseMeter

    @Inject
    internal lateinit var fragmentsNavigationManager: FragmentsNavigationManager

    @Inject
    internal lateinit var datadogFeaturesInitializer: DatadogFeaturesInitializer

    @Inject
    internal lateinit var config: BenchmarkConfig

    internal lateinit var viewModel: MainActivityViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val factory = MainActivityViewModelFactory(application, this)

        viewModel = ViewModelProvider(this, factory)[MainActivityViewModel::class]

        benchmarkActivityComponent.inject(this)

        if (config.isComposeEnabled) {
            supportActionBar?.hide()
            setContent {
                MainView()
            }
        } else {
            setContentView(R.layout.activity_main)
        }

        datadogFeaturesInitializer.initialize(config)
    }

    override fun onStart() {
        super.onStart()
//        datadogMeter.startMeasuring()
    }

    override fun onStop() {
//        datadogMeter.stopMeasuring()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (!config.isComposeEnabled) {
            fragmentsNavigationManager.setNavController(findNavController(R.id.nav_host_fragment))
        }
    }
}

internal val Activity.benchmarkActivityComponent: BenchmarkActivityComponent
    get() = (this as MainActivity).viewModel.component
