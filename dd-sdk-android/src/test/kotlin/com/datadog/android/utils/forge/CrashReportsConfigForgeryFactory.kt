/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.android.DatadogConfig
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import java.net.URL

internal class CrashReportsConfigForgeryFactory :
    ForgeryFactory<DatadogConfig.CrashReportsConfig> {
    override fun getForgery(forge: Forge): DatadogConfig.CrashReportsConfig {
        return DatadogConfig.CrashReportsConfig(
            clientToken = forge.anHexadecimalString(),
            endpointUrl = forge.getForgery<URL>().toString(),
            envName = forge.anAlphabeticalString()
        )
    }
}
