/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.benchmark.rum

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import com.datadog.android.Datadog
import com.datadog.android.DatadogConfig
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.tracking.ViewAttributesProvider
import com.datadog.android.sdk.benchmark.R
import com.datadog.android.sdk.benchmark.mockResponse
import com.datadog.tools.unit.createInstance
import com.datadog.tools.unit.getStaticValue
import com.datadog.tools.unit.invokeGenericMethod
import com.datadog.tools.unit.invokeMethod
import com.datadog.tools.unit.setStaticValue
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class RumGesturesTrackerBenchmark {

    @get:Rule
    val benchmark = BenchmarkRule()

    @get:Rule
    val activityTestRule: ActivityTestRule<RumTrackedActivity> =
        ActivityTestRule(RumTrackedActivity::class.java)

    lateinit var datadogGesturesTracker: Any
    lateinit var mockWebServer: MockWebServer

    @Before
    fun setUp() {
        // hook the DatadogGesturesTracker
        mockWebServer = MockWebServer()
            .apply {
                start()
            }
        mockWebServer.setDispatcher(
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    return mockResponse(200)
                }
            }
        )
        val fakeEndpoint = mockWebServer.url("/").toString().removeSuffix("/")

        val context = InstrumentationRegistry.getInstrumentation().context
        val config = DatadogConfig
            .Builder("NO_TOKEN", "benchmark")
            .useCustomTracesEndpoint(fakeEndpoint)
            .build()
        Datadog.initialize(context, config)
        val attrsProviderClassName =
            "com.datadog.android.rum.internal.tracking.JetpackViewAttributesProvider"
        val viewAttributesProvider: ViewAttributesProvider =
            createInstance(attrsProviderClassName) as ViewAttributesProvider
        val gesturesTrackerClassName =
            "com.datadog.android.rum.internal.instrumentation.gestures.DatadogGesturesTracker"
        datadogGesturesTracker = createInstance(
            gesturesTrackerClassName,
            arrayOf(viewAttributesProvider)
        )
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        GlobalRum.registerIfAbsent(RumMonitor.Builder().build())
    }

    @After
    fun tearDown() {
        Datadog.invokeMethod("stop")
        mockWebServer.shutdown()
        val noOpMonitor = createInstance(
            "com.datadog.android.rum.NoOpRumMonitor"
        )
        GlobalRum::class.java.setStaticValue("monitor", noOpMonitor)
        val isRegistered: AtomicBoolean = GlobalRum::class.java.getStaticValue("isRegistered")
        isRegistered.set(false)
    }

    @Test
    fun benchmark_clicking_on_recycler_view_item_tracker_not_attached() {
        benchmark.measureRepeated {
            val matcher = runWithTimingDisabled { withId(R.id.recyclerView) }
            val actionOnItemAtPosition = runWithTimingDisabled {
                RecyclerViewActions
                    .actionOnItemAtPosition<RumTrackedActivity.Adapter.ViewHolder>(
                        2,
                        click()
                    )
            }
            onView(matcher)
                .perform(
                    actionOnItemAtPosition
                )
        }
    }

    @Test
    fun benchmark_clicking_on_simple_button_tracker_not_attached() {
        benchmark.measureRepeated {
            val withId = runWithTimingDisabled { withId(R.id.button1) }
            onView(withId)
                .perform(
                    click()
                )
        }
    }

    @Test
    fun benchmark_clicking_on_recycler_view_item_tracker_attached() {
        benchmarkWithTrackerAttached {
            benchmark.measureRepeated {
                val matcher = runWithTimingDisabled { withId(R.id.recyclerView) }
                val actionOnItemAtPosition = runWithTimingDisabled {
                    RecyclerViewActions
                        .actionOnItemAtPosition<RumTrackedActivity.Adapter.ViewHolder>(
                            2,
                            click()
                        )
                }
                onView(matcher)
                    .perform(
                        actionOnItemAtPosition
                    )
            }
        }
    }

    @Test
    fun benchmark_clicking_on_simple_button_tracker_attached() {
        benchmarkWithTrackerAttached {
            benchmark.measureRepeated {
                val withId = runWithTimingDisabled { withId(R.id.button1) }
                onView(withId)
                    .perform(
                        click()
                    )
            }
        }
    }

    // region Internal

    private fun benchmarkWithTrackerAttached(toExecute: () -> Unit) {
        activityTestRule.runOnUiThread {
            datadogGesturesTracker.invokeGenericMethod(
                "startTracking",
                activityTestRule.activity.window,
                activityTestRule.activity
            )
        }
        toExecute()
        activityTestRule.runOnUiThread {
            datadogGesturesTracker.invokeGenericMethod(
                "stopTracking",
                activityTestRule.activity.window,
                activityTestRule.activity
            )
        }
    }
    // endregion
}
