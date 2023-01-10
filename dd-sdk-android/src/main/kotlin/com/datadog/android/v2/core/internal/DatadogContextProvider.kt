/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.core.internal

import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.v2.api.context.DatadogContext
import com.datadog.android.v2.api.context.DeviceInfo
import com.datadog.android.v2.api.context.NetworkInfo
import com.datadog.android.v2.api.context.ProcessInfo
import com.datadog.android.v2.api.context.TimeInfo
import com.datadog.android.v2.api.context.UserInfo
import java.util.concurrent.TimeUnit
import com.datadog.android.core.model.NetworkInfo as NetworkInfoV1

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
                    isMainProcess = coreFeature.isMainProcess,
                    processImportance = CoreFeature.processImportance
                ),
                networkInfo = with(coreFeature.networkInfoProvider.getLatestNetworkInfo()) {
                    NetworkInfo(
                        connectivity = connectivity.asV2(),
                        carrierName = carrierName,
                        carrierId = carrierId,
                        upKbps = upKbps,
                        downKbps = downKbps,
                        strength = strength,
                        cellularTechnology = cellularTechnology
                    )
                },
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
                userInfo = with(coreFeature.userInfoProvider.getUserInfo()) {
                    UserInfo(
                        id = id,
                        name = name,
                        email = email,
                        additionalProperties = additionalProperties
                    )
                },
                trackingConsent = coreFeature.trackingConsentProvider.getConsent(),
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

    private fun NetworkInfoV1.Connectivity.asV2(): NetworkInfo.Connectivity {
        return when (this) {
            NetworkInfoV1.Connectivity.NETWORK_NOT_CONNECTED ->
                NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED
            NetworkInfoV1.Connectivity.NETWORK_ETHERNET ->
                NetworkInfo.Connectivity.NETWORK_ETHERNET
            NetworkInfoV1.Connectivity.NETWORK_WIFI -> NetworkInfo.Connectivity.NETWORK_WIFI
            NetworkInfoV1.Connectivity.NETWORK_WIMAX -> NetworkInfo.Connectivity.NETWORK_WIMAX
            NetworkInfoV1.Connectivity.NETWORK_BLUETOOTH ->
                NetworkInfo.Connectivity.NETWORK_BLUETOOTH
            NetworkInfoV1.Connectivity.NETWORK_2G -> NetworkInfo.Connectivity.NETWORK_2G
            NetworkInfoV1.Connectivity.NETWORK_3G -> NetworkInfo.Connectivity.NETWORK_3G
            NetworkInfoV1.Connectivity.NETWORK_4G -> NetworkInfo.Connectivity.NETWORK_4G
            NetworkInfoV1.Connectivity.NETWORK_5G -> NetworkInfo.Connectivity.NETWORK_5G
            NetworkInfoV1.Connectivity.NETWORK_MOBILE_OTHER ->
                NetworkInfo.Connectivity.NETWORK_MOBILE_OTHER
            NetworkInfoV1.Connectivity.NETWORK_CELLULAR ->
                NetworkInfo.Connectivity.NETWORK_CELLULAR
            NetworkInfoV1.Connectivity.NETWORK_OTHER -> NetworkInfo.Connectivity.NETWORK_OTHER
        }
    }
}
