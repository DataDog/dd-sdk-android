/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.domain.event

import com.datadog.android.api.context.AccountInfo
import com.datadog.android.api.context.DeviceInfo
import com.datadog.android.api.context.DeviceType
import com.datadog.android.api.context.NetworkInfo
import com.datadog.android.api.context.UserInfo
import com.datadog.android.trace.model.SpanEvent

internal abstract class BaseSpanEventMapper<T> : ContextAwareMapper<T, SpanEvent> {

    protected fun resolveUserInfo(userInfo: UserInfo) = SpanEvent.Usr(
        id = userInfo.id,
        name = userInfo.name,
        email = userInfo.email,
        additionalProperties = userInfo.additionalProperties.toMutableMap()
    )

    protected fun resolveAccountInfo(accountInfo: AccountInfo) = SpanEvent.Account(
        id = accountInfo.id,
        name = accountInfo.name,
        additionalProperties = accountInfo.extraInfo.toMutableMap()
    )

    protected fun resolveDeviceInfo(deviceInfo: DeviceInfo): SpanEvent.Device {
        return SpanEvent.Device(
            type = resolveDeviceType(deviceInfo.deviceType),
            name = deviceInfo.deviceName,
            model = deviceInfo.deviceModel,
            brand = deviceInfo.deviceBrand,
            architecture = deviceInfo.architecture
        )
    }

    protected fun resolveNetworkInfo(networkInfo: NetworkInfo): SpanEvent.Network {
        val simCarrier = resolveSimCarrier(networkInfo)
        val networkInfoClient = SpanEvent.Client(
            simCarrier = simCarrier,
            signalStrength = networkInfo.strength?.toString(),
            downlinkKbps = networkInfo.downKbps?.toString(),
            uplinkKbps = networkInfo.upKbps?.toString(),
            connectivity = networkInfo.connectivity.toString()
        )
        return SpanEvent.Network(networkInfoClient)
    }

    protected fun resolveOsInfo(deviceInfo: DeviceInfo): SpanEvent.Os {
        return SpanEvent.Os(
            name = deviceInfo.osName,
            version = deviceInfo.osVersion,
            versionMajor = deviceInfo.osMajorVersion
        )
    }

    private fun resolveSimCarrier(networkInfo: NetworkInfo): SpanEvent.SimCarrier? {
        return if (networkInfo.carrierId != null || networkInfo.carrierName != null) {
            SpanEvent.SimCarrier(
                id = networkInfo.carrierId?.toString(),
                name = networkInfo.carrierName
            )
        } else {
            null
        }
    }

    private fun resolveDeviceType(deviceType: DeviceType): SpanEvent.Type {
        return when (deviceType) {
            DeviceType.MOBILE -> SpanEvent.Type.MOBILE
            DeviceType.TABLET -> SpanEvent.Type.TABLET
            DeviceType.TV -> SpanEvent.Type.TV
            DeviceType.DESKTOP -> SpanEvent.Type.DESKTOP
            DeviceType.GAMING_CONSOLE -> SpanEvent.Type.GAMING_CONSOLE
            DeviceType.BOT -> SpanEvent.Type.BOT
            DeviceType.OTHER -> SpanEvent.Type.OTHER
        }
    }
}
