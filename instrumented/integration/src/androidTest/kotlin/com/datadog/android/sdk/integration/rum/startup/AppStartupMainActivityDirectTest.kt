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
 * Instrumented test verifying that TTID is reported correctly when
 * MainContentActivity is launched directly without any interstitial.
 *
 * This test:
 * 1. Launches MainContentActivity directly (no interstitial)
 * 2. Does NOT configure any predicate (not needed)
 * 3. Verifies TTID IS reported correctly for the main activity
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
internal class AppStartupMainActivityDirectTest :
    RumTest<MainContentActivity, AppStartupMainActivityDirectTest.MainActivityDirectTestRule>() {

    @get:Rule
    val mockServerRule = MainActivityDirectTestRule()

    override fun runInstrumentationScenario(
        mockServerRule: MainActivityDirectTestRule
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

        // TTID should be reported when MainContentActivity draws
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
    fun verifyTTIDReportedWhenMainActivityLaunchedDirectly() {
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
     * Test rule that launches MainContentActivity directly without predicate.
     * This verifies the normal case where TTID works correctly.
     */
    internal class MainActivityDirectTestRule : RumMockServerActivityTestRule<MainContentActivity>(
        activityClass = MainContentActivity::class.java,
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
                // No predicate needed - MainContentActivity draws normally
                .build()
            Rum.enable(rumConfig, sdkCore)
            DdRumContentProvider.processImportance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        }
    }
}
