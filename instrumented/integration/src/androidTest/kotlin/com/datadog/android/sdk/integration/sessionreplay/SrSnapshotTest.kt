/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay

import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.sdk.rules.SessionReplayTestRule

internal abstract class SrSnapshotTest :
    SrTest<SessionReplayPlaygroundActivity,
        SessionReplayTestRule<SessionReplayPlaygroundActivity>>() {

    override fun runInstrumentationScenario(
        mockServerRule: SessionReplayTestRule<SessionReplayPlaygroundActivity>
    ): ExpectedSrData {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        return mockServerRule.activity.getExpectedSrData()
    }
}
