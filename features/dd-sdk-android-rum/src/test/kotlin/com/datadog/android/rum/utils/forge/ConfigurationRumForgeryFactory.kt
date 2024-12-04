/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.forge

import com.datadog.android.rum.configuration.VitalsUpdateFrequency
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.rum.tracking.FragmentViewTrackingStrategy
import com.datadog.android.rum.tracking.MixedViewTrackingStrategy
import com.datadog.android.rum.tracking.NavigationViewTrackingStrategy
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import org.mockito.kotlin.mock

internal class ConfigurationRumForgeryFactory :
    ForgeryFactory<RumFeature.Configuration> {
    override fun getForgery(forge: Forge): RumFeature.Configuration {
        return RumFeature.Configuration(
            customEndpointUrl = forge.aStringMatching("http(s?)://[a-z]+\\.com/\\w+"),
            sampleRate = forge.aFloat(0f, 100f),
            telemetrySampleRate = forge.aFloat(0f, 100f),
            telemetryConfigurationSampleRate = forge.aFloat(0f, 100f),
            userActionTracking = forge.aBool(),
            touchTargetExtraAttributesProviders = forge.aList { mock() },
            interactionPredicate = mock(),
            viewTrackingStrategy = forge.anElementFrom(
                ActivityViewTrackingStrategy(forge.aBool(), mock()),
                FragmentViewTrackingStrategy(forge.aBool(), mock(), mock()),
                MixedViewTrackingStrategy(forge.aBool(), mock(), mock(), mock()),
                NavigationViewTrackingStrategy(forge.anInt(), forge.aBool(), mock()),
                mock(),
                null
            ),
            viewEventMapper = mock(),
            actionEventMapper = mock(),
            resourceEventMapper = mock(),
            errorEventMapper = mock(),
            longTaskEventMapper = mock(),
            telemetryConfigurationMapper = mock(),
            longTaskTrackingStrategy = mock(),
            backgroundEventTracking = forge.aBool(),
            trackFrustrations = forge.aBool(),
            trackNonFatalAnrs = forge.aBool(),
            vitalsMonitorUpdateFrequency = forge.aValueFrom(VitalsUpdateFrequency::class.java),
            sessionListener = mock(),
            additionalConfig = forge.aMap { aString() to aString() },
            initialResourceIdentifier = mock()
        )
    }
}
