/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.context.DeviceInfo
import com.datadog.android.api.context.ProcessInfo
import com.datadog.android.api.context.TimeInfo
import java.util.concurrent.TimeUnit

internal class DatadogContextProvider(val coreFeature: CoreFeature) : ContextProvider {
    override val context: DatadogContext
        get() {
            // IMPORTANT All properties should be immutable and be frozen at the state
            // of the context construction moment
            return DatadogContext(
                site = coreFeature.site,
                clientToken = coreFeature.clientToken,
                service = coreFeature.serviceName,
                env = coreFeature.envName,
                version = coreFeature.packageVersionProvider.version,
                variant = coreFeature.variant,
                sdkVersion = coreFeature.sdkVersion,
                source = coreFeature.sourceName,
                time = with(coreFeature.timeProvider) {
                    val deviceTimeMs = getDeviceTimestamp()
                    val serverTimeMs = getServerTimestamp()
                    TimeInfo(
                        deviceTimeNs = TimeUnit.MILLISECONDS.toNanos(deviceTimeMs),
                        serverTimeNs = TimeUnit.MILLISECONDS.toNanos(serverTimeMs),
                        serverTimeOffsetNs = TimeUnit.MILLISECONDS
                            .toNanos(serverTimeMs - deviceTimeMs),
                        serverTimeOffsetMs = serverTimeMs - deviceTimeMs
                    )
                },
                processInfo = ProcessInfo(
                    isMainProcess = coreFeature.isMainProcess
                ),
                networkInfo = coreFeature.networkInfoProvider.getLatestNetworkInfo(),
                deviceInfo = with(coreFeature.androidInfoProvider) {
                    DeviceInfo(
                        deviceName = deviceName,
                        deviceBrand = deviceBrand,
                        deviceType = deviceType,
                        deviceModel = deviceModel,
                        deviceBuildId = deviceBuildId,
                        osName = osName,
                        osVersion = osVersion,
                        osMajorVersion = osMajorVersion,
                        architecture = architecture
                    )
                },
                userInfo = coreFeature.userInfoProvider.getUserInfo(),
                trackingConsent = coreFeature.trackingConsentProvider.getConsent(),
                appBuildId = coreFeature.appBuildId,
                // toMap call here (and in getFeatureContext) is VERY important - this will make
                // independent snapshot of the features context which is not affected by the
                // changes which can be made later by another thread.
                // Values at the top 2 levels are frozen: feature-name key,
                // and feature-specific-name key.
                featuresContext = mutableMapOf<String, Map<String, Any?>>().apply {
                    val source = coreFeature.featuresContext
                    source.forEach { (key, value) ->
                        this[key] = value.toMap()
                    }
                }
            )
        }

    override fun setFeatureContext(feature: String, context: Map<String, Any?>) {
        coreFeature.featuresContext[feature] = context
    }

    override fun getFeatureContext(feature: String): Map<String, Any?> {
        return coreFeature.featuresContext[feature]?.toMap() ?: emptyMap()
    }
}
