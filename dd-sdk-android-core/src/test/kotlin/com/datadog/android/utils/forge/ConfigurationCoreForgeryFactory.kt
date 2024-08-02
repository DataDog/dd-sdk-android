/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.android.DatadogSite
import com.datadog.android.core.configuration.BackPressureMitigation
import com.datadog.android.core.configuration.BackPressureStrategy
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.persistence.PersistenceStrategy
import com.datadog.android.security.NoOpEncryption
import com.datadog.android.trace.TracingHeaderType
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import okhttp3.Authenticator
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
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
            firstPartyHostsWithHeaderTypes = forge.aMap {
                getForgery<URL>().host to aList {
                    aValueFrom(
                        TracingHeaderType::class.java
                    )
                }.toSet()
            },
            batchSize = forge.getForgery(),
            uploadFrequency = forge.getForgery(),
            proxy = proxy,
            proxyAuth = auth,
            encryption = forge.aNullable { NoOpEncryption() },
            site = forge.aValueFrom(DatadogSite::class.java),
            batchProcessingLevel = forge.getForgery(),
            persistenceStrategyFactory = forge.aNullable {
                mock<PersistenceStrategy.Factory>().apply {
                    whenever(create(any(), any(), any())) doReturn mock()
                }
            },
            backpressureStrategy = BackPressureStrategy(
                forge.aSmallInt(),
                mock(),
                mock(),
                forge.aValueFrom(BackPressureMitigation::class.java)
            )
        )
    }
}
