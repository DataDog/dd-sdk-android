/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.domain.event

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.context.NetworkInfo
import com.datadog.android.core.internal.utils.toHexString
import com.datadog.android.log.LogAttributes
import com.datadog.android.trace.model.SpanEvent
import com.datadog.opentracing.DDSpan

internal class DdSpanToSpanEventMapper(
    internal val networkInfoEnabled: Boolean,
    private val bigIntegerUtils: BigIntegerUtils = BigIntegerUtils
) : ContextAwareMapper<DDSpan, SpanEvent> {

    // region Mapper

    override fun map(datadogContext: DatadogContext, model: DDSpan): SpanEvent {
        val serverOffset = datadogContext.time.serverTimeOffsetNs
        val metrics = resolveMetrics(model)
        val metadata = resolveMeta(datadogContext, model)
        val lessSignificantTraceId = bigIntegerUtils.lessSignificantUnsignedLongAsHexa(model.traceId)
        return SpanEvent(
            traceId = lessSignificantTraceId,
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
        val networkInfoMeta = if (networkInfoEnabled) {
            val networkInfo = datadogContext.networkInfo
            val simCarrier = resolveSimCarrier(networkInfo)
            val networkInfoClient = SpanEvent.Client(
                simCarrier = simCarrier,
                signalStrength = networkInfo.strength?.toString(),
                downlinkKbps = networkInfo.downKbps?.toString(),
                uplinkKbps = networkInfo.upKbps?.toString(),
                connectivity = networkInfo.connectivity.toString()
            )
            SpanEvent.Network(networkInfoClient)
        } else {
            null
        }
        val userInfo = datadogContext.userInfo
        val mostSignificantTraceId = bigIntegerUtils.mostSignificantUnsignedLongAsHexa(event.traceId)
        val additionalProperties = mutableMapOf<String, String>()
        additionalProperties[TRACE_ID_META_KEY] = mostSignificantTraceId
        additionalProperties += event.meta
        val usrMeta = SpanEvent.Usr(
            id = userInfo.id,
            name = userInfo.name,
            email = userInfo.email,
            additionalProperties = userInfo.additionalProperties.toMutableMap()
        )
        val dd = SpanEvent.Dd(
            source = datadogContext.source,
            application = event.tags[LogAttributes.RUM_APPLICATION_ID]?.let { SpanEvent.Application(it as? String) },
            session = event.tags[LogAttributes.RUM_SESSION_ID]?.let { SpanEvent.Session(it as? String) },
            view = event.tags[LogAttributes.RUM_VIEW_ID]?.let { SpanEvent.View(it as? String) }
        )
        return SpanEvent.Meta(
            version = datadogContext.version,
            dd = dd,
            span = SpanEvent.Span(),
            tracer = SpanEvent.Tracer(
                version = datadogContext.sdkVersion
            ),
            usr = usrMeta,
            network = networkInfoMeta,
            additionalProperties = additionalProperties
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
