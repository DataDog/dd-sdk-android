/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.forge

import com.datadog.android.rum.RumConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import java.util.UUID

class RumConfigurationForgeryFactory : ForgeryFactory<RumConfiguration> {
    override fun getForgery(forge: Forge): RumConfiguration {
        return RumConfiguration(
            applicationId = forge.getForgery<UUID>().toString(),
            featureConfiguration = forge.getForgery()
        )
    }
}
