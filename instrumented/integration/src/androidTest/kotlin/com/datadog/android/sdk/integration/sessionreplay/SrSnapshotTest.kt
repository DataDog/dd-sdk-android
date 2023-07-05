/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.sdk.rules.SessionReplayTestRule
import com.datadog.android.sdk.utils.waitFor

internal abstract class SrSnapshotTest<T : SessionReplayPlaygroundActivity> :
    SrTest<T, SessionReplayTestRule<T>>() {

    override fun runInstrumentationScenario(
        mockServerRule: SessionReplayTestRule<T>
    ): ExpectedSrData {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // we need this to avoid the Bitrise flakiness and to force and to wait for
        // SurfaceFlinger to call the onDraw method which will trigger the screen snapshot.
        onView(ViewMatchers.isRoot()).perform(waitFor(UI_THREAD_DELAY_IN_MS))
        instrumentation.waitForIdleSync()
        return mockServerRule.activity.getExpectedSrData()
    }

    companion object {
        const val UI_THREAD_DELAY_IN_MS = 1000L
    }
}
