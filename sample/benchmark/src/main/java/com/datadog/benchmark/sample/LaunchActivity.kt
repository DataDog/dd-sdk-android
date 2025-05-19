/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.datadog.benchmark.sample.config.BenchmarkConfig
import com.datadog.benchmark.sample.config.SyntheticsRun
import com.datadog.benchmark.sample.config.SyntheticsScenario
import javax.inject.Inject

@SuppressLint("CustomSplashScreen")
class LaunchActivity: AppCompatActivity() {
    @Inject
    internal lateinit var benchmarkConfigHolder: BenchmarkConfigHolder

    @Inject
    internal lateinit var benchmarkFeaturesInitializer: DatadogFeaturesInitializer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        application.benchmarkAppComponent.inject(this)

//        val config = BenchmarkConfig.resolveSyntheticsBundle(savedInstanceState)
        val config = BenchmarkConfig(run = SyntheticsRun.Instrumented, scenario = SyntheticsScenario.RumAuto)
        benchmarkConfigHolder.config = config

        benchmarkFeaturesInitializer.initialize(config)

        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
