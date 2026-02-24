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
 * Instrumented test verifying that TTID is auto-forwarded from an interstitial
 * activity to the next activity without needing a custom AppStartupActivityPredicate.
 *
 * This test:
 * 1. Launches InterstitialSplashActivity (which immediately starts MainContentActivity and finishes)
 * 2. Uses default behavior (no predicate configured) — the auto-subscribe mechanism detects
 *    the interstitial and forwards TTID to MainContentActivity
 * 3. Verifies TTID is reported for MainContentActivity, not the splash
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
internal class AppStartupAutoForwardTest :
    RumTest<InterstitialSplashActivity, AppStartupAutoForwardTest.AutoForwardTestRule>() {

    @get:Rule
    val mockServerRule = AutoForwardTestRule()

    override fun runInstrumentationScenario(
        mockServerRule: AutoForwardTestRule
    ): MutableList<ExpectedEvent> {
        val expectedEvents = mutableListOf<ExpectedEvent>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        instrumentation.waitForIdleSync()
        waitForPendingRUMEvents()

        // Expect application launch view for MainContentActivity (not InterstitialSplashActivity)
        expectedEvents.add(
            ExpectedApplicationLaunchViewEvent(
                docVersion = 2
            )
        )

        // Expect TTID to be reported for MainContentActivity
        // (interstitial was auto-detected and TTID forwarded)
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
    fun verifyTTIDAutoForwardedFromInterstitialToMainActivity() {
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
     * Test rule that configures RUM without a custom predicate,
     * relying on the default auto-forwarding behavior.
     */
    internal class AutoForwardTestRule : RumMockServerActivityTestRule<InterstitialSplashActivity>(
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
                // No predicate configured — rely on default auto-forwarding behavior
                .build()
            Rum.enable(rumConfig, sdkCore)
            DdRumContentProvider.processImportance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        }
    }
}
