/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay

import android.os.Bundle
import com.datadog.android.sdk.integration.R
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.utils.getTextAndInputPrivacy
import com.datadog.android.sessionreplay.SessionReplayConfiguration
import com.datadog.android.sessionreplay.SessionReplayPrivacy

internal class SessionReplayTextAndInputPrivacyActivity : BaseSessionReplayActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sr_text_and_input_privacy_layout)
    }

    @Suppress("DEPRECATION")
    override fun sessionReplayConfiguration(privacy: SessionReplayPrivacy, sampleRate: Float): SessionReplayConfiguration {
        val textAndInputPrivacy = intent.getTextAndInputPrivacy()
        return if (textAndInputPrivacy != null) {
            RuntimeConfig.sessionReplayConfigBuilder(sampleRate)
                .setPrivacy(privacy)
                .setTextAndInputPrivacy(textAndInputPrivacy)
                .build()
        } else {
            super.sessionReplayConfiguration(privacy, sampleRate)
        }
    }
}

