/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.android.core.configuration.Credentials
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class CredentialsForgeryFactory :
    ForgeryFactory<Credentials> {
    override fun getForgery(forge: Forge): Credentials {
        return Credentials(
            clientToken = forge.anHexadecimalString(),
            envName = forge.aStringMatching("[a-zA-Z0-9_:./-]{0,195}[a-zA-Z0-9_./-]"),
            variant = forge.anElementFrom(forge.anAlphabeticalString(), ""),
            serviceName = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")
        )
    }
}
