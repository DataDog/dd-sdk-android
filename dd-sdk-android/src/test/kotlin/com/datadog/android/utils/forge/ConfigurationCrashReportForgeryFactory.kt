/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.android.core.configuration.Configuration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class ConfigurationCrashReportForgeryFactory :
    ForgeryFactory<Configuration.Feature.CrashReport> {
    override fun getForgery(forge: Forge): Configuration.Feature.CrashReport {
        return Configuration.Feature.CrashReport(
            endpointUrl = forge.aStringMatching("http(s?)://[a-z]+\\.com/\\w+")
        )
    }
}
