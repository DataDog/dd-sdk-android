/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.android.core.configuration.Configuration
import com.nhaarman.mockitokotlin2.mock
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import okhttp3.Authenticator
import java.net.Proxy
import java.net.URL

internal class ConfigurationCoreForgeryFactory :
    ForgeryFactory<Configuration.Core> {
    override fun getForgery(forge: Forge): Configuration.Core {
        val (proxy, auth) = if (forge.aBool()) {
            mock<Proxy>() to mock()
        } else {
            null to Authenticator.NONE
        }

        return Configuration.Core(
            needsClearTextHttp = forge.aBool(),
            enableDeveloperModeWhenDebuggable = forge.aBool(),
            firstPartyHosts = forge.aList { getForgery<URL>().host },
            batchSize = forge.getForgery(),
            uploadFrequency = forge.getForgery(),
            proxy = proxy,
            proxyAuth = auth,
            securityConfig = forge.getForgery(),
            webViewTrackingHosts = forge.aList { getForgery<URL>().host }
        )
    }
}
