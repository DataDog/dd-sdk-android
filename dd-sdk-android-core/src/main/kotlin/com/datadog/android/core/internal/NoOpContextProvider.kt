/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal

import com.datadog.android.DatadogSite
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.context.DeviceInfo
import com.datadog.android.api.context.DeviceType
import com.datadog.android.api.context.LocaleInfo
import com.datadog.android.api.context.NetworkInfo
import com.datadog.android.api.context.ProcessInfo
import com.datadog.android.api.context.TimeInfo
import com.datadog.android.api.context.UserInfo
import com.datadog.android.privacy.TrackingConsent

internal class NoOpContextProvider : ContextProvider {
    // TODO RUM-3784 this one is quite ugly. Should return type be nullable?
    override fun getContext(withFeatureContexts: Set<String>) = DatadogContext(
        site = DatadogSite.US1,
        clientToken = "",
        service = "",
        env = "",
        version = "",
        versionCode = "0",
        variant = "",
        source = "",
        sdkVersion = "",
        time = TimeInfo(
            deviceTimeNs = 0L,
            serverTimeNs = 0L,
            serverTimeOffsetMs = 0L,
            serverTimeOffsetNs = 0L
        ),
        processInfo = ProcessInfo(isMainProcess = true),
        networkInfo = NetworkInfo(
            connectivity = NetworkInfo.Connectivity.NETWORK_OTHER,
            carrierName = null,
            carrierId = null,
            upKbps = null,
            downKbps = null,
            strength = null,
            cellularTechnology = null
        ),
        deviceInfo = DeviceInfo(
            deviceName = "",
            deviceBrand = "",
            deviceModel = "",
            deviceType = DeviceType.OTHER,
            deviceBuildId = "",
            osName = "",
            osMajorVersion = "",
            osVersion = "",
            architecture = "",
            numberOfDisplays = null,
            localeInfo = LocaleInfo(
                locales = emptyList(),
                currentLocale = "",
                timeZone = ""
            )
        ),
        userInfo = UserInfo(null, null, null, null, emptyMap()),
        accountInfo = null,
        trackingConsent = TrackingConsent.NOT_GRANTED,
        appBuildId = null,
        featuresContext = emptyMap()
    )
}
