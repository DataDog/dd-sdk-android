/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.forge

import com.datadog.android.rum.configuration.ResourceHeadersConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class ResourceHeadersConfigurationForgeryFactory :
    ForgeryFactory<ResourceHeadersConfiguration> {
    override fun getForgery(forge: Forge): ResourceHeadersConfiguration {
        return ResourceHeadersConfiguration.Builder()
            .captureRequestHeaders(forge.aList { anAlphabeticalString().lowercase() })
            .captureResponseHeaders(forge.aList { anAlphabeticalString().lowercase() })
            .build()
    }
}
