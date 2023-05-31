/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.forge

import com.datadog.android.DatadogSite
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.v2.api.context.DatadogContext
import com.datadog.android.v2.api.context.DeviceInfo
import com.datadog.android.v2.api.context.DeviceType
import com.datadog.android.v2.api.context.NetworkInfo
import com.datadog.android.v2.api.context.ProcessInfo
import com.datadog.android.v2.api.context.TimeInfo
import com.datadog.android.v2.api.context.UserInfo
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import java.util.Locale

// TODO RUMM-2949 Share forgeries/test configurations between modules
internal class DatadogContextForgeryFactory : ForgeryFactory<DatadogContext> {

    override fun getForgery(forge: Forge): DatadogContext {
        return DatadogContext(
            site = forge.aValueFrom(DatadogSite::class.java),
            clientToken = forge.anHexadecimalString().lowercase(Locale.US),
            service = forge.anAlphabeticalString(),
            version = forge.aStringMatching("[0-9](\\.[0-9]{1,3}){2,3}"),
            variant = forge.anAlphabeticalString(),
            env = forge.anAlphabeticalString(),
            source = forge.anAlphabeticalString(),
            sdkVersion = forge.aStringMatching("[0-9](\\.[0-9]{1,2}){1,3}"),
            // inlined custom forgeries below, to avoid copy-pasting even more classes
            time = TimeInfo(
                deviceTimeNs = forge.aLong(min = 0),
                serverTimeNs = forge.aLong(min = 0),
                serverTimeOffsetNs = forge.aLong(),
                serverTimeOffsetMs = forge.aLong()
            ),
            processInfo = ProcessInfo(
                isMainProcess = forge.aBool()
            ),
            networkInfo = NetworkInfo(),
            deviceInfo = DeviceInfo(
                deviceName = forge.anAlphabeticalString(),
                deviceBrand = forge.anAlphabeticalString(),
                deviceModel = forge.anAlphabeticalString(),
                deviceType = forge.aValueFrom(DeviceType::class.java),
                deviceBuildId = forge.anAlphaNumericalString(),
                osName = forge.aString(),
                osVersion = forge.aString(),
                osMajorVersion = forge.aString(),
                architecture = forge.aString()
            ),
            userInfo = UserInfo(
                id = forge.aNullable { anHexadecimalString() },
                email = forge.aNullable { anAlphabeticalString() },
                name = forge.aNullable { anAlphabeticalString() },
                additionalProperties = forge.aMap { anAlphabeticalString() to anAlphabeticalString() }
            ),
            trackingConsent = forge.aValueFrom(TrackingConsent::class.java),
            // building nested maps with default size slows down tests quite a lot, so will use
            // an explicit small size
            featuresContext = forge.aMap(size = 2) {
                forge.anAlphabeticalString() to forge.aMap {
                    forge.anAlphabeticalString() to forge.anAlphabeticalString()
                }
            }
        )
    }
}
