/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.forge

import com.datadog.android.telemetry.internal.TelemetryCoreConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class TelemetryCoreConfigurationForgeryFactory :
    ForgeryFactory<TelemetryCoreConfiguration> {
    override fun getForgery(forge: Forge): TelemetryCoreConfiguration {
        return TelemetryCoreConfiguration(
            trackErrors = forge.aBool(),
            batchSize = forge.aPositiveLong(),
            batchUploadFrequency = forge.aPositiveLong(),
            useProxy = forge.aBool(),
            useLocalEncryption = forge.aBool(),
            batchProcessingLevel = forge.aPositiveLong()
        )
    }
}
