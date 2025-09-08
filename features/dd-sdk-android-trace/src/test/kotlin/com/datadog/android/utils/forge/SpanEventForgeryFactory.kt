/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.android.api.context.AccountInfo
import com.datadog.android.api.context.DeviceInfo
import com.datadog.android.api.context.DeviceType
import com.datadog.android.api.context.NetworkInfo
import com.datadog.android.api.context.UserInfo
import com.datadog.android.internal.utils.toHexString
import com.datadog.android.trace.model.SpanEvent
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import java.util.concurrent.TimeUnit

internal class SpanEventForgeryFactory : ForgeryFactory<SpanEvent> {

    override fun getForgery(forge: Forge): SpanEvent {
        val operationName = forge.anAlphabeticalString()
        val resourceName = forge.anAlphabeticalString()
        val serviceName = forge.anAlphabeticalString()
        val errorFlag = if (forge.aBool()) 1L else 0L
        val isTopLevel = forge.aNullable { if (forge.aBool()) 1L else 0L }
        val metrics = forge.exhaustiveMetrics()
        val meta = forge.exhaustiveMeta()
        val traceId = forge.aLong(min = 1).toHexString()
        val spanId = forge.aLong(min = 1).toHexString()
        val parentId = forge.aLong(min = 1).toHexString()
        val duration = forge.aLong(min = 0)
        val startTime = TimeUnit.SECONDS.toNanos(System.currentTimeMillis())
        val appPackageVersion = forge.aStringMatching("[0-9]\\.[0-9]\\.[0-9]")
        val tracerVersion = forge.aStringMatching("[0-9]\\.[0-9]\\.[0-9]")
        val userInfo = forge.aNullable<UserInfo>()
        val accountInfo = forge.aNullable<AccountInfo>()
        val networkInfo = forge.aNullable<NetworkInfo>()
        val deviceInfo = forge.getForgery<DeviceInfo>()

        return SpanEvent(
            spanId = spanId,
            traceId = traceId,
            parentId = parentId,
            name = operationName,
            resource = resourceName,
            service = serviceName,
            error = errorFlag,
            duration = duration,
            start = startTime,
            meta = SpanEvent.Meta(
                version = appPackageVersion,
                dd = SpanEvent.Dd(source = forge.aNullable { anAlphabeticalString() }),
                span = SpanEvent.Span(),
                tracer = SpanEvent.Tracer(tracerVersion),
                usr = SpanEvent.Usr(
                    id = userInfo?.id,
                    name = userInfo?.name,
                    email = userInfo?.email,
                    additionalProperties = userInfo?.additionalProperties?.toMutableMap()
                        ?: mutableMapOf()
                ),
                account = accountInfo?.let {
                    SpanEvent.Account(
                        id = it.id,
                        name = it.name,
                        additionalProperties = it.extraInfo.toMutableMap()
                    )
                },
                network = SpanEvent.Network(
                    SpanEvent.Client(
                        simCarrier = forge.aNullable {
                            SpanEvent.SimCarrier(
                                id = networkInfo?.carrierId?.toString(),
                                name = networkInfo?.carrierName
                            )
                        },
                        signalStrength = networkInfo?.strength?.toString(),
                        uplinkKbps = networkInfo?.upKbps?.toString(),
                        downlinkKbps = networkInfo?.downKbps?.toString(),
                        connectivity = networkInfo?.connectivity?.toString().orEmpty()
                    )
                ),
                device = SpanEvent.Device(
                    type = resolveDeviceType(deviceInfo.deviceType),
                    name = deviceInfo.deviceName,
                    model = deviceInfo.deviceModel,
                    brand = deviceInfo.deviceBrand,
                    architecture = deviceInfo.architecture,
                    locale = forge.aNullable { anAlphabeticalString() },
                    locales = forge.aNullable { aList { anAlphabeticalString() } },
                    timeZone = forge.aNullable { anAlphabeticalString() },
                    powerSavingMode = forge.aNullable { aBool() }
                ),
                os = SpanEvent.Os(
                    name = deviceInfo.osName,
                    version = deviceInfo.osVersion,
                    versionMajor = deviceInfo.osMajorVersion,
                    build = forge.aNullable { anAlphabeticalString() }
                ),
                additionalProperties = meta
            ),
            metrics = SpanEvent.Metrics(topLevel = isTopLevel, additionalProperties = metrics)
        )
    }

    private fun resolveDeviceType(deviceType: DeviceType): SpanEvent.Type {
        return when (deviceType) {
            DeviceType.MOBILE -> SpanEvent.Type.MOBILE
            DeviceType.TABLET -> SpanEvent.Type.TABLET
            DeviceType.TV -> SpanEvent.Type.TV
            DeviceType.DESKTOP -> SpanEvent.Type.DESKTOP
            else -> SpanEvent.Type.OTHER
        }
    }

    private fun Forge.exhaustiveMetrics(): MutableMap<String, Number> {
        return listOf(
            aLong(),
            anInt(),
            aFloat(),
            aDouble()
        ).map { anAlphabeticalString() to it as Number }
            .toMap(mutableMapOf())
    }

    private fun Forge.exhaustiveMeta(): MutableMap<String, String> {
        return listOf(
            aString()
        ).associateByTo(mutableMapOf()) { anAlphabeticalString() }
    }
}
