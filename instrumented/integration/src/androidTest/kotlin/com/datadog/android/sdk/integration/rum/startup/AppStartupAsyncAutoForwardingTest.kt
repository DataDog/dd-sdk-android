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

        // AsyncInterstitialSplashActivity navigates via Handler.postDelayed(100ms),
        // which means startActivity(MainContentActivity) fires asynchronously after
        // the splash finishes. We register a lifecycle callback to deterministically
        // wait until MainContentActivity reaches RESUMED state before capturing
        // the RUM context (which resolvedRumContext() snapshots at ExpectedEvent
        // construction time).
        val mainActivityResumed = CountDownLatch(1)
        val application = instrumentation.targetContext.applicationContext as Application
        val callback = onActivityResumedCallback<MainContentActivity>(mainActivityResumed)
        application.registerActivityLifecycleCallbacks(callback)
        try {
            instrumentation.waitForIdleSync()
            check(mainActivityResumed.await(MAIN_ACTIVITY_WAIT_SECONDS, TimeUnit.SECONDS)) {
                "MainContentActivity did not reach RESUMED state within $MAIN_ACTIVITY_WAIT_SECONDS seconds"
            }
            instrumentation.waitForIdleSync()
            waitForPendingRUMEvents()
        } finally {
            application.unregisterActivityLifecycleCallbacks(callback)
        }

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

    private inline fun <reified T : Activity> onActivityResumedCallback(
        latch: CountDownLatch
    ): Application.ActivityLifecycleCallbacks {
        return object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if (activity is T) latch.countDown()
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        }
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
     */
    internal class AsyncAutoForwardingTestRule :
        RumMockServerActivityTestRule<AsyncInterstitialSplashActivity>(
            activityClass = AsyncInterstitialSplashActivity::class.java,
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
                // No predicate configured — deferred retarget should handle this
                .build()
            Rum.enable(rumConfig, sdkCore)
            DdRumContentProvider.processImportance =
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        }
    }
}
