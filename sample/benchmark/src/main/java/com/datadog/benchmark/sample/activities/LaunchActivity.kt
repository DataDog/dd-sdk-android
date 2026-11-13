/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import coil.Coil
import coil.ImageLoader
import com.datadog.benchmark.sample.BenchmarkConfigHolder
import com.datadog.benchmark.sample.DatadogFeaturesInitializer
import com.datadog.benchmark.sample.activities.scenarios.DefaultScenarioActivity
import com.datadog.benchmark.sample.activities.scenarios.RumAutoScenarioActivity
import com.datadog.benchmark.sample.activities.scenarios.SessionReplayComposeScenarioActivity
import com.datadog.benchmark.sample.activities.scenarios.SessionReplayScenarioActivity
import com.datadog.benchmark.sample.benchmarkAppComponent
import com.datadog.benchmark.sample.config.BenchmarkConfig
import com.datadog.benchmark.sample.config.SyntheticsScenario
import com.datadog.benchmark.sample.network.rickandmorty.fallback.RickAndMortyFallbackListener
import com.datadog.benchmark.sample.network.rickandmorty.fallback.RickAndMortyLocalServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.IOException
import javax.inject.Inject

@SuppressLint("CustomSplashScreen")
internal class LaunchActivity : BaseBenchmarkActivity() {
    @Inject
    internal lateinit var benchmarkConfigHolder: BenchmarkConfigHolder

    @Inject
    internal lateinit var benchmarkFeaturesInitializer: DatadogFeaturesInitializer

    @Inject
    internal lateinit var okHttpClient: dagger.Lazy<OkHttpClient>

    @Inject
    internal lateinit var rickAndMortyLocalServer: dagger.Lazy<RickAndMortyLocalServer>

    @Inject
    internal lateinit var rickAndMortyFallbackListener: dagger.Lazy<RickAndMortyFallbackListener>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        application.benchmarkAppComponent.inject(this)

        val config = BenchmarkConfig.resolveSyntheticsBundle(intent.extras)
        benchmarkConfigHolder.config = config

        /*
         * The general recommendation is to initialize Datadog SDK at the Application.onCreate
         * to have all the observability as early as possible. However in the Benchmark app we know what kind of run we
         * have [SyntheticsRun.Instrumented] or [SyntheticsRun.Baseline] only in [LaunchActivity.onCreate].
         * It is derived from intent extras.
         */
        benchmarkFeaturesInitializer.initialize(config = config, intent = intent)

        initializeCoil()

        if (config.scenario == SyntheticsScenario.Timeseries) {
            startRickAndMortyLocalServer()
        }

        openScenarioActivity(config)

        finish()
    }

    private fun initializeCoil() {
        Coil.setImageLoader(ImageLoader(applicationContext).newBuilder().okHttpClient(okHttpClient.get()).build())
    }

    // Not tied to the activity lifecycle: this activity finishes right away, while the server lives for the process.
    private fun startRickAndMortyLocalServer() {
        val server = rickAndMortyLocalServer.get()
        val listener = rickAndMortyFallbackListener.get()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                server.start()
            } catch (e: IOException) {
                listener.onFallbackFailed(e)
            }
        }
    }

    private fun openScenarioActivity(config: BenchmarkConfig) {
        val activity = when (config.scenario) {
            SyntheticsScenario.Timeseries,
            SyntheticsScenario.RumAuto -> RumAutoScenarioActivity::class.java

            SyntheticsScenario.SessionReplayCompose -> SessionReplayComposeScenarioActivity::class.java
            SyntheticsScenario.SessionReplay -> SessionReplayScenarioActivity::class.java
            else -> DefaultScenarioActivity::class.java
        }

        val intent = Intent(this, activity)
        startActivity(intent)
    }
}
