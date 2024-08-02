/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.integration.tests.forge.factories

import com.datadog.android.DatadogSite
import com.datadog.android._InternalProxy
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.trace.TracingHeaderType
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import java.util.UUID

internal class ConfigurationCoreForgeryFactory :
    ForgeryFactory<Configuration> {
    override fun getForgery(forge: Forge): Configuration {
        return Configuration.Builder(
            UUID.randomUUID().toString(),
            forge.anHexadecimalString(),
            forge.anHexadecimalString(),
            forge.aNullable {
                anAlphaNumericalString()
            }
        )
            .setUseDeveloperModeWhenDebuggable(forge.aBool())
            .setFirstPartyHostsWithHeaderType(
                forge.aMap {
                    val fakeUrl = forge.aStringMatching("https://[a-z0-9]+\\.com")
                    fakeUrl to aList {
                        aValueFrom(
                            TracingHeaderType::class.java
                        )
                    }.toSet()
                }
            )
            .apply {
                _InternalProxy.allowClearTextHttp(this)
            }
            .setBatchSize(forge.getForgery())
            .setUploadFrequency(forge.getForgery())
            .useSite(forge.aValueFrom(DatadogSite::class.java))
            .build()
    }
}
