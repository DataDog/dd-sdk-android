/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.domain.event

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.context.NetworkInfo
import com.datadog.android.core.internal.utils.toHexString
import com.datadog.android.trace.model.SpanEvent
import com.datadog.opentracing.DDSpan

internal class DdSpanToSpanEventMapper : ContextAwareMapper<DDSpan, SpanEvent> {

    // region Mapper

    override fun map(datadogContext: DatadogContext, model: DDSpan): SpanEvent {
        val serverOffset = datadogContext.time.serverTimeOffsetNs
        val metrics = resolveMetrics(model)
        val metadata = resolveMeta(datadogContext, model)
        return SpanEvent(
            traceId = model.traceId.toHexString(),
            spanId = model.spanId.toHexString(),
            parentId = model.parentId.toHexString(),
            resource = model.resourceName,
            name = model.operationName,
            service = model.serviceName,
            duration = model.durationNano,
            start = model.startTime + serverOffset,
            error = if (model.isError) 1 else 0,
            meta = metadata,
            metrics = metrics
        )
    }

    // endregion

    // region internal

    private fun resolveMetrics(event: DDSpan) = SpanEvent.Metrics(
        topLevel = if (event.parentId.toLong() == 0L) 1 else null,
        additionalProperties = event.metrics
    )

    private fun resolveMeta(datadogContext: DatadogContext, event: DDSpan): SpanEvent.Meta {
        val networkInfo = datadogContext.networkInfo
        val simCarrier = resolveSimCarrier(networkInfo)
        val networkInfoClient = SpanEvent.Client(
            simCarrier = simCarrier,
            signalStrength = networkInfo.strength?.toString(),
            downlinkKbps = networkInfo.downKbps?.toString(),
            uplinkKbps = networkInfo.upKbps?.toString(),
            connectivity = networkInfo.connectivity.toString()
        )
        val networkInfoMeta = SpanEvent.Network(networkInfoClient)
        val userInfo = datadogContext.userInfo
        val usrMeta = SpanEvent.Usr(
            id = userInfo.id,
            name = userInfo.name,
            email = userInfo.email,
            additionalProperties = userInfo.additionalProperties.toMutableMap()
        )
        return SpanEvent.Meta(
            version = datadogContext.version,
            dd = SpanEvent.Dd(source = datadogContext.source),
            span = SpanEvent.Span(),
            tracer = SpanEvent.Tracer(
                version = datadogContext.sdkVersion
            ),
            usr = usrMeta,
            network = networkInfoMeta,
            additionalProperties = event.meta
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

    // endregion
}
