/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.rum.startup

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.os.Bundle
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Instrumented test verifying that TTID auto-forwarding works when the interstitial
 * activity navigates asynchronously (via Handler.post).
 *
 * This exercises the deferred retarget path: the interstitial's onDestroy fires
 * before the next activity's onCreate because startActivity + finish are posted
 * to the message queue. At destroy time, no next candidate exists, so the detector
 * must hold the startup scenario and retarget when the next activity is created.
 *
 * This test:
 * 1. Launches AsyncInterstitialSplashActivity (posts startActivity + finish via Handler)
 * 2. Does NOT configure any predicate (default behavior)
 * 3. Verifies TTID is reported for MainContentActivity through deferred retarget
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
internal class AppStartupAsyncAutoForwardingTest :
    RumTest<
        AsyncInterstitialSplashActivity,
        AppStartupAsyncAutoForwardingTest.AsyncAutoForwardingTestRule
        >() {

    @get:Rule
    val mockServerRule = AsyncAutoForwardingTestRule()

    override fun runInstrumentationScenario(
        mockServerRule: AsyncAutoForwardingTestRule
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

        // Expect application launch view for MainContentActivity
        expectedEvents.add(
            ExpectedApplicationLaunchViewEvent(
                docVersion = 2
            )
        )

        // TTID should be reported for MainContentActivity via deferred retarget
        // (AsyncInterstitialSplashActivity was destroyed before MainContentActivity was created)
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
    fun verifyTTIDAutoForwardedToMainActivityWhenSplashNavigatesAsynchronously() {
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

    companion object {
        private const val MAIN_ACTIVITY_WAIT_SECONDS = 30L
    }

    /**
     * Test rule that launches AsyncInterstitialSplashActivity WITHOUT any predicate.
     * This verifies the deferred retarget mechanism: the interstitial posts
     * startActivity + finish via Handler, so its onDestroy fires before
     * MainContentActivity.onCreate, and the detector must hold the startup
     * scenario until the next tracked activity arrives.
     *
     * The [mainActivityResumed] latch is registered in [beforeActivityLaunched], before the
     * activity is launched, to avoid a race where MainContentActivity reaches RESUMED before
     * [runInstrumentationScenario] has a chance to register the callback.
     */
    internal class AsyncAutoForwardingTestRule :
        RumMockServerActivityTestRule<AsyncInterstitialSplashActivity>(
            activityClass = AsyncInterstitialSplashActivity::class.java,
            keepRequests = true,
            trackingConsent = TrackingConsent.GRANTED
        ) {

        val mainActivityResumed = CountDownLatch(1)
        private lateinit var mainActivityResumedCallback: Application.ActivityLifecycleCallbacks

        override fun beforeActivityLaunched() {
            super.beforeActivityLaunched()
            val appContext = InstrumentationRegistry.getInstrumentation()
                .targetContext.applicationContext

            // Register before the activity launches so we can't miss the RESUMED callback.
            mainActivityResumedCallback = object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    if (activity is MainContentActivity) mainActivityResumed.countDown()
                }
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
                override fun onActivityStarted(activity: Activity) {}
                override fun onActivityPaused(activity: Activity) {}
                override fun onActivityStopped(activity: Activity) {}
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
                override fun onActivityDestroyed(activity: Activity) {}
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
                    // No predicate configured — deferred retarget should handle this
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
