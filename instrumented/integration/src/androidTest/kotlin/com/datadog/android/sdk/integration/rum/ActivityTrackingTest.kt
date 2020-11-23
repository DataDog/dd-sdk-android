/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.rum

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.sdk.rules.RumMockServerActivityTestRule

internal abstract class ActivityTrackingTest :
    RumTest<ActivityTrackingPlaygroundActivity,
        RumMockServerActivityTestRule<ActivityTrackingPlaygroundActivity>>() {

    // region RumTest

    override fun runInstrumentationScenario(
        mockServerRule: RumMockServerActivityTestRule<ActivityTrackingPlaygroundActivity>
    ): MutableList<ExpectedEvent> {

        val expectedEvents = mutableListOf<ExpectedEvent>()
        expectedEvents.add(ExpectedApplicationStart())
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = mockServerRule.activity
        val viewUrl = activity.javaClass.canonicalName!!.replace(
            '.',
            '/'
        )

        instrumentation.waitForIdleSync()

        // one for application start update
        expectedEvents.add(
            ExpectedViewEvent(
                viewUrl,
                docVersion = 2,
                viewArguments = expectedViewArguments
            )
        )

        // one for view loading time update
        expectedEvents.add(
            ExpectedViewEvent(
                viewUrl,
                docVersion = 3,
                viewArguments = expectedViewArguments,
                extraViewAttributes = mapOf(
                    "loading_type" to "activity_display"
                ),
                extraViewAttributesWithPredicate = mapOf(
                    "loading_time" to { time ->
                        time.asLong >= 0
                    }
                )
            )
        )

        // one for view stopped
        expectedEvents.add(
            ExpectedViewEvent(
                viewUrl,
                docVersion = 4,
                viewArguments = expectedViewArguments
            )
        )

        // activity on pause
        instrumentation.runOnMainSync {
            instrumentation.callActivityOnPause(activity)
        }

        instrumentation.waitForIdleSync()

        // activity start - resume

        instrumentation.runOnMainSync {
            instrumentation.callActivityOnStart(activity)
            instrumentation.callActivityOnResume(activity)
            // this function is only available from Android Q and above
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // we cannot instrument the onPostResume so we had to improvise this
                mockServerRule.performOnLifecycleCallbacks {
                    it.onActivityPostResumed(activity)
                }
            }
        }
        instrumentation.waitForIdleSync()

        // give time to view id to update
        Thread.sleep(500)

        // one for loading time update
        expectedEvents.add(
            ExpectedViewEvent(
                viewUrl,
                docVersion = 2,
                viewArguments = expectedViewArguments,
                extraViewAttributes = mapOf(
                    "loading_type" to "activity_redisplay"
                ),
                extraViewAttributesWithPredicate = mapOf(
                    "loading_time" to { time ->
                        time.asLong > 0
                    }
                )
            )
        )

        // one for view stopped
        expectedEvents.add(
            ExpectedViewEvent(
                viewUrl,
                docVersion = 3,
                viewArguments = expectedViewArguments
            )
        )

        // activity on pause
        instrumentation.runOnMainSync {
            instrumentation.callActivityOnPause(activity)
        }

        return expectedEvents
    }

    // endregion

    // region Internal

    protected val expectedViewArguments = mapOf<String, Any?>(
        "key1" to "keyValue1",
        "key2" to 1,
        "key3" to 2.0f
    )

    // endregion
}
