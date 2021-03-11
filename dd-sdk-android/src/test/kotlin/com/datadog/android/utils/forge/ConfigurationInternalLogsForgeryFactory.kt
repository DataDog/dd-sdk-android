/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.android.core.configuration.Configuration
import com.datadog.android.plugin.DatadogPlugin
import com.nhaarman.mockitokotlin2.mock
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class ConfigurationInternalLogsForgeryFactory :
    ForgeryFactory<Configuration.Feature.InternalLogs> {
    override fun getForgery(forge: Forge): Configuration.Feature.InternalLogs {
        return Configuration.Feature.InternalLogs(
            internalClientToken = forge.anHexadecimalString(),
            endpointUrl = forge.aStringMatching("http(s?)://[a-z]+\\.com/\\w+"),
            plugins = forge.aList { mock<DatadogPlugin>() }
        )
    }
}
