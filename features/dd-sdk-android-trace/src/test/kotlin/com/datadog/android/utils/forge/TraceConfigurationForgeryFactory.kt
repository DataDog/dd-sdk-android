/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.android.trace.TraceConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import org.mockito.kotlin.mock

class TraceConfigurationForgeryFactory : ForgeryFactory<TraceConfiguration> {
    override fun getForgery(forge: Forge): TraceConfiguration {
        return TraceConfiguration(
            customEndpointUrl = forge.aNullable { aStringMatching("https://[a-z]+\\.com") },
            eventMapper = mock(),
            networkInfoEnabled = forge.aBool()
        )
    }
}
