/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:Suppress("DEPRECATION")

package com.datadog.android.utils.forge

import com.datadog.android.DatadogConfig
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import java.net.URL

internal class RumFeatureConfigForgeryFactory :
    ForgeryFactory<DatadogConfig.RumConfig> {
    override fun getForgery(forge: Forge): DatadogConfig.RumConfig {
        return DatadogConfig.RumConfig(
            clientToken = forge.anHexadecimalString(),
            applicationId = forge.getForgery(),
            endpointUrl = forge.getForgery<URL>().toString(),
            envName = forge.anAlphabeticalString(),
            samplingRate = forge.aFloat(0f, 100f)
        )
    }
}
