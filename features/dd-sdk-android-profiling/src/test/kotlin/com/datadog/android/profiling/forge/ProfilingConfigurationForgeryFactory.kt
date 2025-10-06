/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.forge

import com.datadog.android.profiling.ProfilingConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

class ProfilingConfigurationForgeryFactory :
    ForgeryFactory<ProfilingConfiguration> {
    override fun getForgery(forge: Forge): ProfilingConfiguration {
        return ProfilingConfiguration.Builder().useCustomEndpoint(
            endpoint = forge.aStringMatching("http(s?)://[a-z]+\\.com/\\w+")
        ).build()
    }
}
