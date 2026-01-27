/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.forge

import com.datadog.benchmark.DatadogExporterConfiguration
import com.datadog.benchmark.EndPoint
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

class DatadogExporterConfigurationForgeryFactory : ForgeryFactory<DatadogExporterConfiguration> {
    override fun getForgery(forge: Forge): DatadogExporterConfiguration {
        return DatadogExporterConfiguration(
            serviceName = forge.aString(),
            resource = forge.anAsciiString(),
            applicationId = forge.aString(),
            applicationVersion = forge.anAsciiString(),
            apiKey = forge.anAsciiString(),
            run = forge.aString(),
            scenario = forge.aString(),
            endPoint = forge.aValueFrom(EndPoint::class.java),
            intervalInSeconds = forge.aLong()
        )
    }
}
