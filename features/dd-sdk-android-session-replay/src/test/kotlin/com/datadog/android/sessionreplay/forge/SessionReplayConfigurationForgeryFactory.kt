/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.forge

import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.NoOpSessionReplayInternalCallback
import com.datadog.android.sessionreplay.SessionReplayConfiguration
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.SystemRequirementsConfiguration
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.TouchPrivacy
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import org.mockito.kotlin.mock

class SessionReplayConfigurationForgeryFactory : ForgeryFactory<SessionReplayConfiguration> {
    override fun getForgery(forge: Forge): SessionReplayConfiguration {
        return SessionReplayConfiguration(
            customEndpointUrl = forge.aNullable { aStringMatching("https://[a-z]+\\.com") },
            privacy = forge.aValueFrom(SessionReplayPrivacy::class.java),
            textAndInputPrivacy = forge.aValueFrom(TextAndInputPrivacy::class.java),
            imagePrivacy = forge.aValueFrom(ImagePrivacy::class.java),
            touchPrivacy = forge.aValueFrom(TouchPrivacy::class.java),
            customMappers = forge.aList { mock() },
            customOptionSelectorDetectors = forge.aList { mock() },
            customDrawableMappers = forge.aList { mock() },
            startRecordingImmediately = forge.aBool(),
            sampleRate = forge.aFloat(min = 0f, max = 100f),
            dynamicOptimizationEnabled = forge.aBool(),
            internalCallback = NoOpSessionReplayInternalCallback(),
            systemRequirementsConfiguration = SystemRequirementsConfiguration.Builder()
                .setMinRAMSizeMb(forge.aSmallInt())
                .setMinCPUCoreNumber(forge.aSmallInt())
                .build()
        )
    }
}
