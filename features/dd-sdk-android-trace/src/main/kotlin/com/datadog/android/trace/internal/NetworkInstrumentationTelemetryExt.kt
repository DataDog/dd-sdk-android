/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal

import com.datadog.android._InternalProxy
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.api.logToUser
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.internal.telemetry.InternalTelemetryEvent
import com.datadog.android.lint.InternalApi

/** Retains first-party host usage without replacing configuration reported by other integrations. */
@InternalApi
fun SdkCore.reportFirstPartyHostsConfiguration(
    hasFirstPartyHosts: Boolean,
    hasEffectiveLocalHosts: Boolean,
    missingHostsWarning: () -> String
) {
    if (hasFirstPartyHosts) {
        _InternalProxy.updateNetworkInstrumentationConfiguration(this, InternalTelemetryEvent.FirstPartyHostsConfigured)
    }
    val sdkCore = this as? InternalSdkCore ?: return
    if (!hasEffectiveLocalHosts && sdkCore.firstPartyHostResolver.isEmpty()) {
        sdkCore.internalLogger.logToUser(
            InternalLogger.Level.WARN,
            onlyOnce = true,
            messageBuilder = missingHostsWarning
        )
    }
}
