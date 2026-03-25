/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.integration.tests.rum

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.datadog.android.core.integration.tests.utils.poll
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.RumResourceMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RumViewUpdateTest : BaseRumViewTest() {

    @Test
    fun must_report2Actions_when_2ClickActionsAdded() {
        runBlocking {
            val viewKey = UUID.randomUUID().toString()
            val rumMonitor = GlobalRumMonitor.get(sdkCore)

            // When
            rumMonitor.startView(key = viewKey, name = VIEW_NAME)
            delay(1000)

            rumMonitor.addAttribute("custom_attr_1", "attr_value_1") // no view update triggered

            rumMonitor.addAction(RumActionType.CUSTOM, "click1", emptyMap())
            delay(5000) // wait for action to be finalized → view index 1

            rumMonitor.addFeatureFlagEvaluation("flag_bool", true)  // view index 2
            delay(1000)
            rumMonitor.addFeatureFlagEvaluation("flag_int", 42)     // view index 3
            delay(1000)

            val resource1Key = UUID.randomUUID().toString()
            rumMonitor.startResource(resource1Key, RumResourceMethod.GET, "https://httpbin.org/get")
            delay(1000)
            rumMonitor.stopResource(resource1Key, 200, null, RumResourceKind.FETCH) // view index 4
            delay(5000)

            rumMonitor.addFeatureFlagEvaluation("flag_bool", false) // view index 5
            delay(1000)
            rumMonitor.addFeatureFlagEvaluation("flag_int", 100)    // view index 6
            delay(1000)

            rumMonitor.addAttribute("custom_attr_2", "attr_value_2") // no view update triggered

            rumMonitor.addAction(RumActionType.CUSTOM, "click2", emptyMap())
            delay(5000) // wait for action to be finalized → view index 7

            rumMonitor.addError("test error 1", RumErrorSource.SOURCE, null) // view index 8
            delay(1000)
            rumMonitor.addError("test error 2", RumErrorSource.SOURCE, RuntimeException("boom")) // view index 9
            delay(1000)

            val resource2Key = UUID.randomUUID().toString()
            rumMonitor.startResource(resource2Key, RumResourceMethod.GET, "https://httpbin.org/get")
            delay(1000)
            rumMonitor.stopResourceWithError(                        // view index 10
                resource2Key,
                statusCode = 500,
                message = "server error",
                source = RumErrorSource.NETWORK,
                throwable = RuntimeException("500")
            )
            delay(1000)

            withContext(Dispatchers.Main) { Thread.sleep(300) }       // blocks main thread → long task detected
            delay(1000)                                              // wait for long task event → view index 11

            rumMonitor.addTiming("screen_loaded")                    // view index 12
            delay(1000)

            @OptIn(ExperimentalRumApi::class)
            rumMonitor.addViewLoadingTime(overwrite = false)         // view index 13
            delay(1000)

            rumMonitor.stopView(viewKey)                             // view index 14
            delay(5000)

            val localLastViewEvent = synchronized(viewEventsList) { viewEventsList.find { it.context?.additionalProperties?.get("test_view_index") == 14 } }!!
            val viewId = localLastViewEvent.view.id

            // Then
            val response = poll(
                block = {
                    datadogApiClient.getRumViewEventById(
                        viewId = viewId,
                        contextAttributes = mapOf("test_view_index" to 14)
                    )
                },
                predicate = {
                    val found = it.optionalResult?.data?.firstOrNull() != null
                    android.util.Log.w("POLL_DEBUG", "predicate: found=$found")
                    found
                },
                interval = POLLING_INTERVAL_MS.milliseconds,
                timeout = POLLING_TIMEOUT_MS.milliseconds
            )

            val viewEvent = checkNotNull(response?.optionalResult?.data?.firstOrNull())

            RumSearchResponseViewEventAssert.assertThat(viewEvent).apply {
                // Counts
                hasActionCount(2)
                hasResourceCount(1)    // only successful resources
                hasErrorCount(3)       // 2× addError + 1× stopResourceWithError
                hasLongTaskCount(1)    // 1× main thread block > 250ms

                // View state
                isNotActive()
                hasViewName(VIEW_NAME)

                // Feature flags — final values after overrides
                hasFeatureFlagBoolean("flag_bool", false)
                hasFeatureFlagInt("flag_int", 100)

                // Custom timing
                hasCustomTiming("screen_loaded")

                // Time and performance — cross-checked against the local SDK event
                hasTimeSpent(localLastViewEvent.view.timeSpent)
                hasCpuTicksCount(localLastViewEvent.view.cpuTicksCount)
                hasCpuTicksPerSecond(localLastViewEvent.view.cpuTicksPerSecond)
                hasMemoryAverage(localLastViewEvent.view.memoryAverage)
                hasMemoryMax(localLastViewEvent.view.memoryMax)
                hasRefreshRateAverage(localLastViewEvent.view.refreshRateAverage)
                hasRefreshRateMin(localLastViewEvent.view.refreshRateMin)

                // View url — cross-checked against the local SDK event
                hasViewUrl(localLastViewEvent.view.url)

                // OS — cross-checked against the local SDK event
                hasOsName(localLastViewEvent.os?.name)
                hasOsVersion(localLastViewEvent.os?.version)
                hasOsVersionMajor(localLastViewEvent.os?.versionMajor)

                // Device — cross-checked against the local SDK event
                hasDeviceName(localLastViewEvent.device?.name)
                hasDeviceModel(localLastViewEvent.device?.model)
                hasDeviceBrand(localLastViewEvent.device?.brand)
                hasDeviceArchitecture(localLastViewEvent.device?.architecture)
                hasDeviceLocale(localLastViewEvent.device?.locale)
                hasDeviceTimeZone(localLastViewEvent.device?.timeZone)

                // Connectivity
                hasConnectivityStatusNonNull()

                // Session
                hasSessionType("user")

                // Application
                hasApplicationCurrentLocale(localLastViewEvent.application.currentLocale)

                // Version
                hasBuildVersion(localLastViewEvent.buildVersion)

                // Identity — verify backend matches local SDK state
                hasSessionId(localLastViewEvent.session.id)
                hasApplicationId(localLastViewEvent.application.id)

                // Anonymous user (trackAnonymousUser = true in config)
                hasAnonymousUserIdNonNull()

                // User info — set via Datadog.setUserInfo in base setUp
                hasUserId("test-user-id")
                hasUserName("Test User")
                hasUserEmail("test@example.com")

                // Service — set in Configuration.Builder in base setUp
                hasService("test-service")

                // Date — cross-checked against the local SDK event
                hasDate(localLastViewEvent.date)

                // Loading time — set via addViewLoadingTime
                hasLoadingTimeNonNull()

                // Network settled time — auto-computed after resources settle
                hasNetworkSettledTime(localLastViewEvent.view.networkSettledTime)

                // Accessibility — collectAccessibility(true) is configured; cross-check vs local
                hasAccessibility(localLastViewEvent.view.accessibility)

                // Global attribute added via addAttribute
                hasContextAttribute("custom_attr_1", "attr_value_1")
                hasContextAttribute("custom_attr_2", "attr_value_2")
            }
        }
    }

    companion object {
        private const val VIEW_NAME = "rum-view-update-test"
        private const val POLLING_TIMEOUT_MS = 60_000L
        private const val POLLING_INTERVAL_MS = 5_000L
    }
}
