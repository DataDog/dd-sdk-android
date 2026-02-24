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
 * Instrumented test verifying that TTID auto-forwarding works through a chain
 * of non-drawing interstitial activities.
 *
 * The chain is: AuthActivity -> InterstitialSplashActivity -> MainContentActivity.
 * Neither AuthActivity nor InterstitialSplashActivity draw a frame. The auto-forwarding
 * must retarget twice before TTID is reported for MainContentActivity.
 *
 * This catches bugs where forwarding state is incorrectly cleared after the first
 * retarget, preventing the second forward from happening.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
internal class AppStartupChainedAutoForwardingTest :
    RumTest<AuthActivity, AppStartupChainedAutoForwardingTest.ChainedAutoForwardingTestRule>() {

    @get:Rule
    val mockServerRule = ChainedAutoForwardingTestRule()

    override fun runInstrumentationScenario(
        mockServerRule: ChainedAutoForwardingTestRule
    ): MutableList<ExpectedEvent> {
        val expectedEvents = mutableListOf<ExpectedEvent>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        instrumentation.waitForIdleSync()
        waitForPendingRUMEvents()

        expectedEvents.add(
            ExpectedApplicationLaunchViewEvent(
                docVersion = 2
            )
        )

        // TTID should be reported for MainContentActivity after forwarding through
        // AuthActivity -> InterstitialSplashActivity -> MainContentActivity
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
    fun verifyTTIDAutoForwardedThroughChainOfNonDrawingActivities() {
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
     * Test rule that launches AuthActivity without any predicate.
     * The chain AuthActivity -> InterstitialSplashActivity -> MainContentActivity
     * exercises two consecutive auto-forwarding retargets.
     */
    internal class ChainedAutoForwardingTestRule : RumMockServerActivityTestRule<AuthActivity>(
        activityClass = AuthActivity::class.java,
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
                // No predicate — auto-forwarding should handle the entire chain
                .build()
            Rum.enable(rumConfig, sdkCore)
            DdRumContentProvider.processImportance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        }
    }
}
