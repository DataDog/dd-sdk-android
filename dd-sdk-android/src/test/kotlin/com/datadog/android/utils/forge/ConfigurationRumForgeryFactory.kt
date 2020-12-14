/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.android.Configuration
import com.datadog.android.plugin.DatadogPlugin
import com.nhaarman.mockitokotlin2.mock
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class ConfigurationRumForgeryFactory :
    ForgeryFactory<Configuration.Feature.RUM> {
    override fun getForgery(forge: Forge): Configuration.Feature.RUM {
        return Configuration.Feature.RUM(
            endpointUrl = forge.aStringMatching("http(s?)://[a-z]+\\.com/\\w+"),
            plugins = forge.aList { mock<DatadogPlugin>() },
            samplingRate = forge.aFloat(0f, 100f),
            gesturesTracker = mock(),
            userActionTrackingStrategy = mock(),
            viewTrackingStrategy = mock(),
            rumEventMapper = mock()
        )
    }
}
