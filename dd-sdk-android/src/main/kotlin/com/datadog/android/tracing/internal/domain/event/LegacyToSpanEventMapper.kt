/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tracing.internal.domain.event

import com.datadog.android.BuildConfig
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.Mapper
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.core.internal.utils.toHexString
import com.datadog.android.log.internal.user.UserInfoProvider
import com.datadog.android.tracing.model.SpanEvent
import com.datadog.opentracing.DDSpan

internal class LegacyToSpanEventMapper(
    private val timeProvider: TimeProvider,
    private val networkInfoProvider: NetworkInfoProvider,
    private val userInfoProvider: UserInfoProvider
) : Mapper<DDSpan, SpanEvent> {

    // region Mapper

    override fun map(model: DDSpan): SpanEvent {
        val serverOffset = timeProvider.getServerOffsetNanos()
        val metrics = resolveMetrics(model)
        val metadata = resolveMeta(model)
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

    private fun resolveMeta(event: DDSpan): SpanEvent.Meta {
        val networkInfo = networkInfoProvider.getLatestNetworkInfo()
        val networkInfoClient = SpanEvent.Client(
            simCarrierId = networkInfo.carrierId.toStringOrNull(),
            simCarrierName = networkInfo.carrierName,
            signalStrength = networkInfo.strength.toStringOrNull(),
            downlinkKbps = networkInfo.downKbps.toStringOrNull(),
            uplinkKbps = networkInfo.upKbps.toStringOrNull(),
            connectivity = networkInfo.connectivity.toString()
        )
        val networkInfoMeta = SpanEvent.Network(networkInfoClient)
        val userInfo = userInfoProvider.getUserInfo()
        val usrMeta = SpanEvent.Usr(
            id = userInfo.id,
            name = userInfo.name,
            email = userInfo.email,
            additionalProperties = userInfo.additionalProperties
        )
        return SpanEvent.Meta(
            version = CoreFeature.packageVersion,
            dd = SpanEvent.Dd(),
            span = SpanEvent.Span(),
            tracer = SpanEvent.Tracer(
                version = BuildConfig.SDK_VERSION_NAME
            ),
            usr = usrMeta,
            network = networkInfoMeta,
            additionalProperties = event.meta
        )
    }

    private fun Long?.toStringOrNull(): String? {
        return if (this != null && this > 0) this.toString() else null
    }

    // endregion
}
