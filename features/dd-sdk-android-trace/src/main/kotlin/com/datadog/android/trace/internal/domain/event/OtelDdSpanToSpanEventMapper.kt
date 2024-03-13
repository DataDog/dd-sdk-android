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
import com.datadog.trace.core.DDSpan
import com.datadog.trace.core.DDSpanContext

internal class OtelDdSpanToSpanEventMapper(
    internal val networkInfoEnabled: Boolean
) : ContextAwareMapper<DDSpan, SpanEvent> {

    // region Mapper

    override fun map(datadogContext: DatadogContext, model: DDSpan): SpanEvent {
        val serverOffset = datadogContext.time.serverTimeOffsetNs
        val metrics = resolveMetrics(model)
        val metadata = resolveMeta(datadogContext, model)
        return SpanEvent(
            // we remove the first part as we specifically set an IdGeneratorStrategy with
            // 64bits length. Our current endpoint does not accept trace ids longer than 64bits
            traceId = resolveTraceId(model),
            spanId = model.spanId.toHexString(),
            parentId = model.parentId.toHexString(),
            resource = model.resourceName.toString(), //
            name = model.operationName.toString(), // GET, POST, etc
            service = model.serviceName,
            duration = model.durationNano,
            start = model.startTime + serverOffset,
            error = model.error.toLong(),
            meta = metadata,
            metrics = metrics
        )
    }

    // endregion

    // region internal

    private fun resolveTraceId(model: DDSpan): String {
        // the argument will never be negative
        @Suppress("UnsafeThirdPartyFunctionCall")
        return model.traceId.toHexString().takeLast(RUM_ENDPOINT_REQUIRED_TRACE_ID_LENGTH)
    }

    private fun resolveMetrics(event: DDSpan): SpanEvent.Metrics {
        val metrics = resolveMetricsFromSpanContext(event).apply {
            this[DDSpanContext.PRIORITY_SAMPLING_KEY] = event.samplingPriority()
        }
        return SpanEvent.Metrics(
            topLevel = if (event.parentId == 0L) 1 else null,
            additionalProperties = metrics
        )
    }

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
        val tags = event.tags.mapValues { it.value.toString() }
        val meta = (event.baggage + tags).toMutableMap()
        return SpanEvent.Meta(
            version = datadogContext.version,
            dd = dd,
            span = SpanEvent.Span(),
            tracer = SpanEvent.Tracer(
                version = datadogContext.sdkVersion
            ),
            usr = usrMeta,
            network = networkInfoMeta,
            additionalProperties = meta
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

    private fun resolveMetricsFromSpanContext(span: DDSpan): MutableMap<String, Number> {
        return span.tags
            .filterValues { it is Number }
            .mapValues { it.value as Number }
            .toMutableMap()
    }

    // endregion

    companion object {
        internal const val RUM_ENDPOINT_REQUIRED_TRACE_ID_LENGTH = 16
    }
}
