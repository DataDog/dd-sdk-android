/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.android.core.configuration.Configuration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class ConfigurationFeatureForgeryFactory :
    ForgeryFactory<Configuration.Feature> {
    override fun getForgery(forge: Forge): Configuration.Feature {
        return forge.anElementFrom(
            forge.getForgery<Configuration.Feature.Logs>(),
            forge.getForgery<Configuration.Feature.CrashReport>(),
            forge.getForgery<Configuration.Feature.Tracing>(),
            forge.getForgery<Configuration.Feature.RUM>()
        )
    }
}
