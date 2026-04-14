/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.rum.startup

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.Datadog
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.DdRumContentProvider
import com.datadog.android.rum.ExperimentalRumApi
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
import leakcanary.LeakAssertions
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Instrumented test verifying that AppStartupActivityPredicate correctly filters
 * interstitial activities from TTID measurement.
 *
 * This test:
 * 1. Launches InterstitialSplashActivity (which immediately starts MainContentActivity and finishes)
 * 2. Configures predicate to exclude InterstitialSplashActivity
 * 3. Verifies TTID is reported for MainContentActivity, not the splash
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
internal class AppStartupActivityPredicateTest :
    RumTest<InterstitialSplashActivity, AppStartupActivityPredicateTest.AppStartupPredicateTestRule>() {

    @get:Rule
    val mockServerRule = AppStartupPredicateTestRule()

    override fun runInstrumentationScenario(
        mockServerRule: AppStartupPredicateTestRule
    ): MutableList<ExpectedEvent> {
        val expectedEvents = mutableListOf<ExpectedEvent>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        // The lifecycle callback was registered in beforeActivityLaunched() (before the activity
        // was launched), so the latch is guaranteed to fire even on fast devices where
        // MainContentActivity reaches RESUMED before runInstrumentationScenario is called.
        check(mockServerRule.mainActivityResumed.await(MAIN_ACTIVITY_WAIT_SECONDS, TimeUnit.SECONDS)) {
            "MainContentActivity did not reach RESUMED state within $MAIN_ACTIVITY_WAIT_SECONDS seconds"
        }
        instrumentation.waitForIdleSync()
        waitForPendingRUMEvents()

        // Expect application launch view for MainContentActivity (not InterstitialSplashActivity)
        expectedEvents.add(
            ExpectedApplicationLaunchViewEvent(
                docVersion = 2
            )
        )

        // Expect TTID to be reported for MainContentActivity
        // (interstitial was excluded by predicate)
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

    companion object {
        private const val MAIN_ACTIVITY_WAIT_SECONDS = 30L
    }

    @Test
    fun verifyTTIDReportedForMainActivityWhenInterstitialExcluded() {
        val expectedEvents = runInstrumentationScenario(mockServerRule)

        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        ConditionWatcher {
            verifyExpectedEvents(
                mockServerRule.getRequests(RuntimeConfig.rumEndpointUrl),
                expectedEvents
            )
            true
        }.doWait(timeoutMs = FINAL_WAIT_MS)

        LeakAssertions.assertNoLeaks()
    }

    /**
     * Test rule that configures RUM with a predicate excluding InterstitialSplashActivity.
     *
     * The [mainActivityResumed] latch is registered in [beforeActivityLaunched], before the
     * activity is launched, to avoid a race where MainContentActivity reaches RESUMED before
     * [runInstrumentationScenario] has a chance to register the callback.
     */
    internal class AppStartupPredicateTestRule : RumMockServerActivityTestRule<InterstitialSplashActivity>(
        activityClass = InterstitialSplashActivity::class.java,
        keepRequests = true,
        trackingConsent = TrackingConsent.GRANTED
    ) {
        val mainActivityResumed = CountDownLatch(1)
        private var mainActivityResumedCallback: Application.ActivityLifecycleCallbacks =
            NoOpActivityLifecycleCallbacks()

        @OptIn(ExperimentalRumApi::class)
        override fun beforeActivityLaunched() {
            super.beforeActivityLaunched()
            val appContext = InstrumentationRegistry.getInstrumentation()
                .targetContext.applicationContext

            // Register before the activity launches so we can't miss the RESUMED callback.
            mainActivityResumedCallback = object : NoOpActivityLifecycleCallbacks() {
                override fun onActivityResumed(activity: Activity) {
                    if (activity is MainContentActivity) mainActivityResumed.countDown()
                }
            }
            (appContext as Application).registerActivityLifecycleCallbacks(mainActivityResumedCallback)

            try {
                val config = RuntimeConfig.configBuilder()
                    .build()

                val sdkCore = Datadog.initialize(appContext, config, trackingConsent)
                checkNotNull(sdkCore)

                val rumConfig = RuntimeConfig.rumConfigBuilder()
                    .trackUserInteractions()
                    .trackLongTasks(RuntimeConfig.LONG_TASK_LARGE_THRESHOLD)
                    .useViewTrackingStrategy(ActivityViewTrackingStrategy(false))
                    // Exclude InterstitialSplashActivity from TTID measurement
                    .setAppStartupActivityPredicate { activity ->
                        activity !is InterstitialSplashActivity
                    }
                    .build()
                Rum.enable(rumConfig, sdkCore)
                DdRumContentProvider.processImportance =
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
            } catch (e: Throwable) {
                appContext.unregisterActivityLifecycleCallbacks(mainActivityResumedCallback)
                throw e
            }
        }

        override fun afterActivityFinished() {
            super.afterActivityFinished()
            val appContext = InstrumentationRegistry.getInstrumentation()
                .targetContext.applicationContext as Application
            appContext.unregisterActivityLifecycleCallbacks(mainActivityResumedCallback)
        }
    }
}
