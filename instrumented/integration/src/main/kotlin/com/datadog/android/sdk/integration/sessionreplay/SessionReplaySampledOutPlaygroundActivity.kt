/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay

import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sessionreplay.SessionReplayConfiguration
import com.datadog.android.sessionreplay.SessionReplayPrivacy

internal class SessionReplaySampledOutPlaygroundActivity : SessionReplayPlaygroundActivity() {

    override fun sessionReplayConfiguration(): SessionReplayConfiguration =
        RuntimeConfig.sessionReplayConfigBuilder(0f)
            .setPrivacy(SessionReplayPrivacy.ALLOW)
            .build()

    @Suppress("LongMethod")
    override fun getExpectedSrData(): ExpectedSrData {
        return ExpectedSrData(
            "",
            "",
            "",
            emptyList()
        )
    }
}
