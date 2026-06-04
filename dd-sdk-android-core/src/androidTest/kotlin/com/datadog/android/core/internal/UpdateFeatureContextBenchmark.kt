/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal

import android.content.Context
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.Datadog
import com.datadog.android.DatadogSite
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureContextUpdateReceiver
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.privacy.TrackingConsent
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Benchmarks the real [DatadogCore.updateFeatureContext] code path, measuring
 * lock acquisition + map operations + receiver notification.
 *
 * The pre-optimization code used `.toMap().toMutableMap()`, `.filter { }.forEach { }`,
 * and extra `.toMap()` calls for notification, creating multiple intermediate collections
 * per call. The optimized path mutates in place and passes the map directly.
 *
 * Toggle via instrumentation argument:
 * ```
 * -Pandroid.testInstrumentationRunnerArguments.useSlowMapCopy=false  # baseline (optimized)
 * -Pandroid.testInstrumentationRunnerArguments.useSlowMapCopy=true   # regression (slow copies)
 * ```
 */
@RunWith(AndroidJUnit4::class)
class UpdateFeatureContextBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private lateinit var sdkCore: FeatureSdkCore

    @Before
    fun setUp() {
        val args = InstrumentationRegistry.getArguments()
        useSlowMapCopy = args.getString("useSlowMapCopy", "false").toBoolean()

        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val config = Configuration.Builder(
            clientToken = "pub00000000000000000000000000000000",
            env = "benchmark",
            variant = "",
            service = null
        )
            .useSite(DatadogSite.US1)
            .build()

        sdkCore = Datadog.initialize(context, config, TrackingConsent.GRANTED) as FeatureSdkCore

        sdkCore.registerFeature(object : Feature {
            override val name: String = FEATURE_NAME
            override fun onInitialize(appContext: Context) {}
            override fun onStop() {}
        })

        sdkCore.updateFeatureContext(FEATURE_NAME, useContextThread = false) { ctx ->
            ctx["view_id"] = "abc-123-def-456-ghi-789"
            ctx["view_name"] = "HomeActivity"
            ctx["view_url"] = "com.example.app/HomeActivity"
            ctx["view_referrer"] = "com.example.app/SplashActivity"
            ctx["action_id"] = "tap-001-abc-def"
            ctx["action_type"] = "tap"
            ctx["action_target_name"] = "Submit Button"
            ctx["session_id"] = "sess-001-xyz-789"
            ctx["session_type"] = "user"
            ctx["application_id"] = "app-xyz-123-456"
            ctx["has_replay"] = true
            ctx["view_active"] = true
            ctx["view_loading_time"] = 1_234_567_890L
            ctx["view_loading_type"] = "activity_display"
            ctx["view_time_spent"] = 5_000_000_000L
            ctx["view_resource_count"] = 12
            ctx["view_action_count"] = 3
            ctx["view_error_count"] = 0
            ctx["view_long_task_count"] = 1
            ctx["view_frozen_frame_count"] = 0
        }

        repeat(RECEIVER_COUNT) {
            sdkCore.setContextUpdateReceiver(FeatureContextUpdateReceiver { _, _ -> })
        }
    }

    @After
    fun tearDown() {
        useSlowMapCopy = false
        Datadog.stopInstance()
    }

    @Test
    fun updateFeatureContext() {
        benchmarkRule.measureRepeated {
            sdkCore.updateFeatureContext(FEATURE_NAME, useContextThread = false) {
                it["action_id"] = "new-action-id"
            }
        }
    }

    companion object {
        private const val FEATURE_NAME = "rum"
        private const val RECEIVER_COUNT = 5
    }
}
