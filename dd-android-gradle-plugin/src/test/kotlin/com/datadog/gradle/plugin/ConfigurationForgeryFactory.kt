/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin

import com.datadog.gradle.plugin.internal.DdConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class ConfigurationForgeryFactory : ForgeryFactory<DdConfiguration> {
    override fun getForgery(forge: Forge): DdConfiguration {
        return DdConfiguration(
            apiKey = forge.anHexadecimalString(),
            site = forge.getForgery()
        )
    }
}
