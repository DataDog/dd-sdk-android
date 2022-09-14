/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.core.internal

import com.datadog.android.DatadogSite
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.v2.api.context.DatadogContext
import com.datadog.android.v2.api.context.DeviceInfo
import com.datadog.android.v2.api.context.DeviceType
import com.datadog.android.v2.api.context.NetworkInfo
import com.datadog.android.v2.api.context.ProcessInfo
import com.datadog.android.v2.api.context.TimeInfo
import com.datadog.android.v2.api.context.UserInfo

internal class NoOpContextProvider : ContextProvider {
    // TODO RUMM-0000 this one is quite ugly. Should return type be nullable?
    override val context: DatadogContext
        get() = DatadogContext(
            site = DatadogSite.US1,
            clientToken = "",
            service = "",
            env = "",
            version = "",
            variant = "",
            source = "",
            sdkVersion = "",
            time = TimeInfo(
                deviceTimeNs = 0L,
                serverTimeNs = 0L,
                serverTimeOffsetMs = 0L,
                serverTimeOffsetNs = 0L
            ),
            processInfo = ProcessInfo(isMainProcess = true, processImportance = 0),
            networkInfo = NetworkInfo(
                connectivity = NetworkInfo.Connectivity.NETWORK_OTHER,
                carrier = null
            ),
            deviceInfo = DeviceInfo("", "", "", DeviceType.OTHER, "", "", "", "", ""),
            userInfo = UserInfo(null, null, null, emptyMap()),
            trackingConsent = TrackingConsent.NOT_GRANTED,
            featuresContext = emptyMap()
        )

    override fun setFeatureContext(feature: String, context: Map<String, Any?>) {
        // no-op
    }
}
