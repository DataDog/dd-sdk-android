/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.rum.startup

import android.app.ActivityManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.Datadog
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.DdRumContentProvider
import com.datadog.android.rum.Rum
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.integration.rum.AppLaunchMetric
import com.datadog.android.sdk.integration.rum.ExpectedApplicationLaunchViewEvent
import com.datadog.android.sdk.integration.rum.ExpectedEvent
import com.datadog.android.sdk.integration.rum.ExpectedVitalAppLaunchEvent
import com.datadog.android.sdk.integration.rum.RumTest
import com.datadog.android.sdk.rules.RumMockServerActivityTestRule
import com.datadog.tools.unit.ConditionWatcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test verifying that TTID auto-forwarding works without any predicate
 * configuration.
 *
 * This is the "just works" scenario: an interstitial activity that never draws a frame
 * (calls startActivity + finish in onCreate without setContentView) is automatically
 * detected, and the TTID measurement is forwarded to the next activity.
 *
 * This test:
 * 1. Launches InterstitialSplashActivity (which never draws, starts MainContentActivity and finishes)
 * 2. Does NOT configure any predicate (default behavior)
 * 3. Verifies TTID is reported for MainContentActivity through auto-forwarding
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
internal class AppStartupAutoForwardingTest :
    RumTest<InterstitialSplashActivity, AppStartupAutoForwardingTest.AutoForwardingTestRule>() {

    @get:Rule
    val mockServerRule = AutoForwardingTestRule()

    override fun runInstrumentationScenario(
        mockServerRule: AutoForwardingTestRule
    ): MutableList<ExpectedEvent> {
        val expectedEvents = mutableListOf<ExpectedEvent>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        instrumentation.waitForIdleSync()
        waitForPendingRUMEvents()

        // Expect application launch view for MainContentActivity
        expectedEvents.add(
            ExpectedApplicationLaunchViewEvent(
                docVersion = 2
            )
        )

        // TTID should be reported for MainContentActivity via auto-forwarding
        // (InterstitialSplashActivity was detected as non-drawing and skipped automatically)
        expectedEvents.add(
            ExpectedVitalAppLaunchEvent(
                appLaunchMetric = AppLaunchMetric.TTID,
                viewArguments = mapOf("name" to MainContentActivity::class.java.canonicalName)
            )
        )

        expectedEvents.add(
            ExpectedVitalAppLaunchEvent(
                appLaunchMetric = AppLaunchMetric.TTFD,
                viewArguments = mapOf("name" to MainContentActivity::class.java.canonicalName)
            )
        )

        instrumentation.waitForIdleSync()

        return expectedEvents
    }

    @Test
    fun verifyTTIDAutoForwardedToMainActivityWhenSplashDoesNotDraw() {
        val expectedEvents = runInstrumentationScenario(mockServerRule)

        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        ConditionWatcher {
            verifyExpectedEvents(
                mockServerRule.getRequests(RuntimeConfig.rumEndpointUrl),
                expectedEvents
            )
            true
        }.doWait(timeoutMs = FINAL_WAIT_MS)
    }

    /**
     * Test rule that launches InterstitialSplashActivity WITHOUT any predicate.
     * This verifies the auto-forwarding mechanism detects the non-drawing activity
     * and forwards TTID measurement to MainContentActivity automatically.
     */
    internal class AutoForwardingTestRule : RumMockServerActivityTestRule<InterstitialSplashActivity>(
        activityClass = InterstitialSplashActivity::class.java,
        keepRequests = true,
        trackingConsent = TrackingConsent.GRANTED
    ) {
        override fun beforeActivityLaunched() {
            super.beforeActivityLaunched()
            val config = RuntimeConfig.configBuilder()
                .build()

            val sdkCore = Datadog.initialize(
                InstrumentationRegistry.getInstrumentation().targetContext.applicationContext,
                config,
                trackingConsent
            )
            checkNotNull(sdkCore)

            val rumConfig = RuntimeConfig.rumConfigBuilder()
                .trackUserInteractions()
                .trackLongTasks(RuntimeConfig.LONG_TASK_LARGE_THRESHOLD)
                .useViewTrackingStrategy(ActivityViewTrackingStrategy(false))
                // No predicate configured — auto-forwarding should handle this
                .build()
            Rum.enable(rumConfig, sdkCore)
            DdRumContentProvider.processImportance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        }
    }
}
