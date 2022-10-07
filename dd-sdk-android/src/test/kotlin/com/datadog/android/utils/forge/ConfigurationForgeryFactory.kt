/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.android.core.configuration.Configuration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class ConfigurationForgeryFactory : ForgeryFactory<Configuration> {
    override fun getForgery(forge: Forge): Configuration {
        return Configuration(
            coreConfig = forge.getForgery(),
            logsConfig = forge.getForgery(),
            tracesConfig = forge.getForgery(),
            crashReportConfig = forge.getForgery(),
            rumConfig = forge.getForgery(),
            sessionReplayConfig = forge.getForgery(),
            additionalConfig = emptyMap()
        )
    }
}
