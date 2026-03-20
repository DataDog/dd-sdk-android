/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.rum

import android.app.ActivityManager
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.Datadog
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.configuration.BatchSize
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.UploadFrequency
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.DdRumContentProvider
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.RumResourceMethod
import com.datadog.android.sdk.integration.BuildConfig
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Reproduces the bug described in RUM-9413.
 *
 * In [com.datadog.android.rum.internal.domain.scope.RumViewManagerScope.handleOrphanEvent],
 * the condition `applicationDisplayed || !isForegroundProcess` incorrectly routes orphaned
 * events to handleBackgroundEvent() even when the app is actively in the foreground between
 * two screen transitions.
 *
 * When backgroundTrackingEnabled = true, this causes a spurious Background view to be
 * created for any valid background event type (error, action, resource) that arrives
 * between views.
 *
 * Scenario:
 *   1. Start and stop ScreenA — sets applicationDisplayed = true, leaves no active view scope.
 *   2. Fire startResource while no view is active and process is in the foreground.
 *   3. Bug: applicationDisplayed = true satisfies the condition → handleBackgroundEvent()
 *      is called → a spurious Background view scope is created to hold the resource.
 *   4. Start and stop ScreenB — making the Background view visible as an intruder
 *      between two legitimate views: ScreenA → Background → ScreenB.
 *
 * After running this test, find the session in the RUM explorer using the SESSION_ID
 * printed to logcat (tag: RUM_BUG_REPRO). Look for the spurious "Background" view
 * sandwiched between ScreenA and ScreenB.
 *
 * Prerequisites: config/us1.json must contain a valid token and rumApplicationId.
 * The test is automatically skipped if credentials are absent (e.g. in CI).
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
internal class RumBackgroundViewBugReproductionTest {

    private lateinit var sdkCore: SdkCore

    @Before
    fun setUp() {
        Assume.assumeTrue(
            "config/us1.json credentials required — skipping on this machine",
            BuildConfig.DD_BUG_REPRO_TOKEN.isNotEmpty()
        )

        InstrumentationRegistry.getInstrumentation()
            .targetContext
            .cacheDir
            .deleteRecursively()

        val config = Configuration.Builder(
            clientToken = BuildConfig.DD_BUG_REPRO_TOKEN,
            env = BUG_REPRO_ENV
        )
            .setBatchSize(BatchSize.SMALL)
            .setUploadFrequency(UploadFrequency.FREQUENT)
            .build()

        sdkCore = checkNotNull(
            Datadog.initialize(
                InstrumentationRegistry.getInstrumentation().targetContext.applicationContext,
                config,
                TrackingConsent.GRANTED
            )
        )

        val rumConfig = RumConfiguration.Builder(BuildConfig.DD_BUG_REPRO_RUM_APP_ID)
            .trackBackgroundEvents(true)
            .build()

        Rum.enable(rumConfig, sdkCore)

        // Simulate a foreground process — same as all other integration tests.
        // Without this, the test runner process has IMPORTANCE_FOREGROUND_SERVICE which
        // would make !isForegroundProcess = true, masking the applicationDisplayed bug path.
        DdRumContentProvider.processImportance =
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    @After
    fun tearDown() {
        Datadog.stopInstance()
    }

    @Test
    fun reproduceSpuriousBackgroundViewOnOrphanedResourceBetweenForegroundViews() {
        val rumMonitor = GlobalRumMonitor.get(sdkCore)

        // Step 1: Start and stop a view.
        // This sets applicationDisplayed = true in RumViewManagerScope and leaves
        // no active RumViewScope behind.
        rumMonitor.startView(VIEW_KEY, VIEW_NAME)
        rumMonitor.stopView(VIEW_KEY)

        // Step 2: Fire a resource event while no view is active.
        // The process importance is IMPORTANCE_FOREGROUND so this is a foreground orphan.
        // Bug: applicationDisplayed = true satisfies the condition
        //   `else if (applicationDisplayed || !isForegroundProcess)`
        // in RumViewManagerScope.handleOrphanEvent(), causing handleBackgroundEvent() to be
        // called. Since trackBackgroundEvents = true and StartResource is a validBackgroundEventType,
        // a spurious Background RumViewScope is created.
        rumMonitor.startResource(RESOURCE_KEY, RumResourceMethod.GET, RESOURCE_URL)
        rumMonitor.stopResource(RESOURCE_KEY, 200, null, RumResourceKind.OTHER)

        Thread.sleep(300)

        // Step 3: Start a second real view to illustrate that the spurious Background view
        // was created in the middle of two legitimate foreground views.
        // Expected (after fix): ScreenA → ScreenB, no Background view in between.
        // Actual (bug):         ScreenA → Background → ScreenB.
        rumMonitor.startView(VIEW_KEY_2, VIEW_NAME_2)
        rumMonitor.stopView(VIEW_KEY_2)

        // Step 4: Wait for the SDK to write and upload the batch.
        // BatchSize.SMALL + UploadFrequency.FREQUENT means uploads happen every ~500ms.
        Thread.sleep(UPLOAD_WAIT_MS)

        // Retrieve the session ID for lookup in the RUM explorer.
        val sessionId = (sdkCore as? FeatureSdkCore)
            ?.getFeatureContext("rum")
            ?.get("session_id") as? String
            ?: "unknown"

        Log.w(TAG, "========================================")
        Log.w(TAG, "BUG REPRODUCTION COMPLETE")
        Log.w(TAG, "SESSION_ID: $sessionId")
        Log.w(TAG, "Find the spurious Background view at:")
        Log.w(TAG, "https://app.datadoghq.com/rum/sessions?query=%40session.id%3A$sessionId")
        Log.w(TAG, "========================================")
    }

    companion object {
        private const val TAG = "RUM_BUG_REPRO"
        private const val BUG_REPRO_ENV = "bug-repro"
        private const val VIEW_KEY = "bug-repro-view-key-1"
        private const val VIEW_NAME = "BugReproScreenA"
        private const val VIEW_KEY_2 = "bug-repro-view-key-2"
        private const val VIEW_NAME_2 = "BugReproScreenB"
        private const val RESOURCE_KEY = "bug-repro-resource-key"
        private const val RESOURCE_URL = "https://httpbin.org/get"
        private const val UPLOAD_WAIT_MS = 15_000L
    }
}
