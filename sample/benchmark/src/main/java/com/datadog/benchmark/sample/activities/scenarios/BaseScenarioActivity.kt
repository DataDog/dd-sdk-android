/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.activities.scenarios

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.datadog.benchmark.sample.observability.ObservabilityMeter
import com.datadog.benchmark.sample.MainActivityViewModel
import com.datadog.benchmark.sample.MainActivityViewModelFactory
import javax.inject.Inject

internal open class BaseScenarioActivity : AppCompatActivity() {
    internal lateinit var viewModel: MainActivityViewModel

    @Inject
    internal lateinit var meter: ObservabilityMeter

    override fun onStart() {
        super.onStart()
        meter.startMeasuring()
    }

    override fun onStop() {
        meter.stopMeasuring()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val factory = MainActivityViewModelFactory(application)

        viewModel = ViewModelProvider(this, factory)[MainActivityViewModel::class]
    }
}
