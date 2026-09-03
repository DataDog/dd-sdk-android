/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.domain.metrics

/**
 * See https://github.com/DataDog/datadog-agent/blob/main/pkg/proto/datadog/trace/stats.proto
 * Field names and order match ClientStatsPayload.EncodeMsg in stats_gen.go.
 */
internal data class ClientStatsPayload(
    internal val hostname: String,
    internal val env: String,
    internal val version: String,
    internal val service: String,
    internal val tracerVersion: String,
    internal val runtimeID: String,
    internal val sequenceNumber: Long,
    internal val stats: List<ClientStatsBucket>
) {
    fun toMsgPackPayload(encoder: MsgPackEncoder) {
        encoder.startMap(PAYLOAD_MAP_FIELD_COUNT)

        encoder.writeRawString(HOSTNAME_FIELD)
        encoder.writeString(hostname)

        encoder.writeRawString(ENV_FIELD)
        encoder.writeString(env)

        encoder.writeRawString(VERSION_FIELD)
        encoder.writeString(version)

        encoder.writeRawString(STATS_FIELD)
        encoder.startArray(stats.size)
        stats.forEach { it.toMsgPackPayload(encoder) }

        encoder.writeRawString(LANG_FIELD)
        encoder.writeRawString(LANG_VALUE)

        encoder.writeRawString(TRACER_VERSION_FIELD)
        encoder.writeString(tracerVersion)

        encoder.writeRawString(RUNTIME_ID_FIELD)
        encoder.writeString(runtimeID)

        encoder.writeRawString(SEQUENCE_FIELD)
        encoder.writeLong(sequenceNumber)

        encoder.writeRawString(SERVICE_FIELD)
        encoder.writeString(service)
    }

    private companion object {
        private const val PAYLOAD_MAP_FIELD_COUNT = 9
        private val HOSTNAME_FIELD = "Hostname".toByteArray(Charsets.UTF_8)
        private val ENV_FIELD = "Env".toByteArray(Charsets.UTF_8)
        private val VERSION_FIELD = "Version".toByteArray(Charsets.UTF_8)
        private val STATS_FIELD = "Stats".toByteArray(Charsets.UTF_8)
        private val LANG_FIELD = "Lang".toByteArray(Charsets.UTF_8)
        private val LANG_VALUE = "android".toByteArray(Charsets.UTF_8)
        private val TRACER_VERSION_FIELD = "TracerVersion".toByteArray(Charsets.UTF_8)
        private val RUNTIME_ID_FIELD = "RuntimeID".toByteArray(Charsets.UTF_8)
        private val SEQUENCE_FIELD = "Sequence".toByteArray(Charsets.UTF_8)
        private val SERVICE_FIELD = "Service".toByteArray(Charsets.UTF_8)
    }
}

/**
 * Field names and order match ClientStatsBucket.EncodeMsg in stats_gen.go.
 */
internal data class ClientStatsBucket(
    internal val start: Long,
    internal val duration: Long,
    internal val stats: List<ClientGroupedStats>
) {

    fun toMsgPackPayload(encoder: MsgPackEncoder) {
        encoder.startMap(BUCKET_MAP_FIELD_COUNT)

        encoder.writeRawString(START_FIELD)
        encoder.writeLong(start)

        encoder.writeRawString(DURATION_FIELD)
        encoder.writeLong(duration)

        encoder.writeRawString(STATS_FIELD)
        encoder.startArray(stats.size)
        stats.forEach { it.toMsgPackPayload(encoder) }
    }

    private companion object {
        private const val BUCKET_MAP_FIELD_COUNT = 3
        private val START_FIELD = "Start".toByteArray(Charsets.UTF_8)
        private val DURATION_FIELD = "Duration".toByteArray(Charsets.UTF_8)
        private val STATS_FIELD = "Stats".toByteArray(Charsets.UTF_8)
    }
}

/**
 * Field names and order match ClientGroupedStats.EncodeMsg in stats_gen.go.
 * Not a data class — ByteArray fields (okSummary, errorSummary) break equals/hashCode semantics.
 */
@Suppress("LongParameterList")
internal class ClientGroupedStats(
    internal val service: String,
    internal val name: String,
    internal val resource: String,
    internal val httpStatusCode: Int,
    internal val type: String,
    internal val spanKind: String,
    internal val isTraceRoot: Trilean,
    internal val hits: Long,
    internal val errors: Long,
    internal val duration: Long,
    internal val topLevelHits: Long,
    internal val okSummary: ByteArray,
    internal val errorSummary: ByteArray,
    internal val isSynthetic: Boolean,
    internal val peerTags: List<String>,
    internal val serviceSource: String
) {

    fun toMsgPackPayload(encoder: MsgPackEncoder) {
        encoder.startMap(GROUPED_STATS_MAP_FIELD_COUNT)

        encoder.writeRawString(SERVICE_FIELD)
        encoder.writeString(service)

        encoder.writeRawString(NAME_FIELD)
        encoder.writeString(name)

        encoder.writeRawString(RESOURCE_FIELD)
        encoder.writeString(resource)

        encoder.writeRawString(HTTP_STATUS_CODE_FIELD)
        encoder.writeInt(httpStatusCode)

        encoder.writeRawString(TYPE_FIELD)
        encoder.writeString(type)

        encoder.writeRawString(HITS_FIELD)
        encoder.writeLong(hits)

        encoder.writeRawString(ERRORS_FIELD)
        encoder.writeLong(errors)

        encoder.writeRawString(DURATION_FIELD)
        encoder.writeLong(duration)

        encoder.writeRawString(OK_SUMMARY_FIELD)
        encoder.writeBinary(okSummary)

        encoder.writeRawString(ERROR_SUMMARY_FIELD)
        encoder.writeBinary(errorSummary)

        encoder.writeRawString(SYNTHETICS_FIELD)
        encoder.writeBoolean(isSynthetic)

        encoder.writeRawString(TOP_LEVEL_HITS_FIELD)
        encoder.writeLong(topLevelHits)

        encoder.writeRawString(SPAN_KIND_FIELD)
        encoder.writeString(spanKind)

        encoder.writeRawString(PEER_TAGS_FIELD)
        encoder.startArray(peerTags.size)
        peerTags.forEach { encoder.writeString(it) }

        encoder.writeRawString(IS_TRACE_ROOT_FIELD)
        encoder.writeInt(isTraceRoot.value)

        encoder.writeRawString(GRPC_STATUS_CODE_FIELD)
        encoder.writeRawString(EMPTY_STRING)

        encoder.writeRawString(SERVICE_SOURCE_FIELD)
        encoder.writeString(serviceSource)
    }

    private companion object {
        private const val GROUPED_STATS_MAP_FIELD_COUNT = 17

        private val SERVICE_FIELD = "Service".toByteArray(Charsets.UTF_8)
        private val NAME_FIELD = "Name".toByteArray(Charsets.UTF_8)
        private val RESOURCE_FIELD = "Resource".toByteArray(Charsets.UTF_8)
        private val HTTP_STATUS_CODE_FIELD = "HTTPStatusCode".toByteArray(Charsets.UTF_8)
        private val TYPE_FIELD = "Type".toByteArray(Charsets.UTF_8)
        private val HITS_FIELD = "Hits".toByteArray(Charsets.UTF_8)
        private val ERRORS_FIELD = "Errors".toByteArray(Charsets.UTF_8)
        private val DURATION_FIELD = "Duration".toByteArray(Charsets.UTF_8)
        private val OK_SUMMARY_FIELD = "OkSummary".toByteArray(Charsets.UTF_8)
        private val ERROR_SUMMARY_FIELD = "ErrorSummary".toByteArray(Charsets.UTF_8)
        private val SYNTHETICS_FIELD = "Synthetics".toByteArray(Charsets.UTF_8)
        private val TOP_LEVEL_HITS_FIELD = "TopLevelHits".toByteArray(Charsets.UTF_8)
        private val SPAN_KIND_FIELD = "SpanKind".toByteArray(Charsets.UTF_8)
        private val PEER_TAGS_FIELD = "PeerTags".toByteArray(Charsets.UTF_8)
        private val IS_TRACE_ROOT_FIELD = "IsTraceRoot".toByteArray(Charsets.UTF_8)
        private val GRPC_STATUS_CODE_FIELD = "GRPCStatusCode".toByteArray(Charsets.UTF_8)
        private val SERVICE_SOURCE_FIELD = "srv_src".toByteArray(Charsets.UTF_8)
        private val EMPTY_STRING = "".toByteArray(Charsets.UTF_8)
    }
}

internal enum class Trilean(val value: Int) {
    NOT_SET(0),
    TRUE(1),
    FALSE(2)
}
