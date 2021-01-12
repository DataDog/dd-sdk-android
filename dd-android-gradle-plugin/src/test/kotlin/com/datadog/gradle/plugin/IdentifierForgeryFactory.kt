/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin

import com.datadog.gradle.plugin.internal.DdAppIdentifier
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class IdentifierForgeryFactory : ForgeryFactory<DdAppIdentifier> {
    override fun getForgery(forge: Forge): DdAppIdentifier {
        return DdAppIdentifier(
            serviceName = forge.aStringMatching("[a-z]{3}(\\.[a-z]{5,10}){2,4}"),
            envName = forge.anAlphabeticalString(),
            version = forge.aStringMatching("\\d\\.\\d{1,2}\\.\\d{1,3}"),
            variant = forge.anAlphabeticalString()
        )
    }
}
