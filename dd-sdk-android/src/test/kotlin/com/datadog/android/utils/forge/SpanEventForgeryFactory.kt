/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.android.core.model.NetworkInfo
import com.datadog.android.core.model.UserInfo
import com.datadog.android.tracing.model.SpanEvent
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import java.util.concurrent.TimeUnit

internal class SpanEventForgeryFactory : ForgeryFactory<SpanEvent> {

    override fun getForgery(forge: Forge): SpanEvent {
        val operationName = forge.anAlphabeticalString()
        val resourceName = forge.anAlphabeticalString()
        val serviceName = forge.anAlphabeticalString()
        val errorFlag = if (forge.aBool()) 1L else 0L
        val isTopLevel = if (forge.aBool()) 1L else 0L
        val metrics = forge.exhaustiveMetrics()
        val meta = forge.exhaustiveMeta()
        val traceId = forge.aLong(min = 1).toString(16)
        val spanId = forge.aLong(min = 1).toString(16)
        val parentId = forge.aLong(min = 1).toString(16)
        val duration = forge.aLong(min = 0)
        val startTime = TimeUnit.SECONDS.toNanos(System.currentTimeMillis())
        val appPackageVersion = forge.aStringMatching("[0-9]\\.[0-9]\\.[0-9]")
        val tracerVersion = forge.aStringMatching("[0-9]\\.[0-9]\\.[0-9]")
        val userInfo: UserInfo = forge.getForgery()
        val networkInfo: NetworkInfo = forge.getForgery()

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
                dd = SpanEvent.Dd(),
                span = SpanEvent.Span(),
                tracer = SpanEvent.Tracer(tracerVersion),
                usr = SpanEvent.Usr(
                    id = userInfo.id,
                    name = userInfo.name,
                    email = userInfo.email,
                    additionalProperties = userInfo.additionalProperties
                ),
                network = SpanEvent.Network(
                    SpanEvent.Client(
                        simCarrier = SpanEvent.SimCarrier(
                            id = networkInfo.carrierId.toString(),
                            name = networkInfo.carrierName
                        ),
                        signalStrength = networkInfo.strength.toString(),
                        uplinkKbps = networkInfo.upKbps.toString(),
                        downlinkKbps = networkInfo.downKbps.toString(),
                        connectivity = networkInfo.connectivity.toString()
                    )
                ),
                additionalProperties = meta
            ),
            metrics = SpanEvent.Metrics(topLevel = isTopLevel, additionalProperties = metrics)
        )
    }

    fun Forge.exhaustiveMetrics(): Map<String, Number> {
        return listOf(
            aLong(),
            anInt(),
            aFloat(),
            aDouble()
        ).map { anAlphabeticalString() to it as Number }
            .toMap()
    }

    fun Forge.exhaustiveMeta(): Map<String, String> {
        return listOf(
            aString()
        ).map { anAlphabeticalString() to it }
            .toMap()
    }
}
