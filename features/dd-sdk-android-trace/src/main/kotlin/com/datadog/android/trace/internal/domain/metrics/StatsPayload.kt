/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.domain.metrics

/**
 * Outer envelope for [ClientStatsPayload].
 * See https://github.com/DataDog/datadog-agent/blob/main/pkg/proto/datadog/trace/stats.proto
 * Field names and order match StatsPayload.EncodeMsg in stats_gen.go in datadog-agent.
 */
internal class StatsPayload(
    private val clientStats: List<ClientStatsPayload>,
    private val splitPayload: Boolean
) {
    fun toMsgPackPayload(): ByteArray {
        val encoder = MsgPackEncoder()

        encoder.startMap(STATS_MAP_FIELD_COUNT)

        encoder.writeRawString(AGENT_HOSTNAME_FIELD)
        encoder.writeRawString(EMPTY_STRING)

        encoder.writeRawString(AGENT_ENV_FIELD)
        encoder.writeRawString(EMPTY_STRING)

        encoder.writeRawString(STATS_FIELD)
        encoder.startArray(clientStats.size)
        clientStats.forEach { it.toMsgPackPayload(encoder) }

        encoder.writeRawString(AGENT_VERSION_FIELD)
        encoder.writeRawString(EMPTY_STRING)

        encoder.writeRawString(CLIENT_COMPUTED_FIELD)
        encoder.writeBoolean(true)

        encoder.writeRawString(SPLIT_PAYLOAD_FIELD)
        encoder.writeBoolean(splitPayload)

        return encoder.getBytes()
    }

    private companion object {
        private const val STATS_MAP_FIELD_COUNT = 6

        private val AGENT_HOSTNAME_FIELD = "AgentHostname".toByteArray(Charsets.UTF_8)
        private val AGENT_ENV_FIELD = "AgentEnv".toByteArray(Charsets.UTF_8)
        private val AGENT_VERSION_FIELD = "AgentVersion".toByteArray(Charsets.UTF_8)
        private val STATS_FIELD = "Stats".toByteArray(Charsets.UTF_8)
        private val CLIENT_COMPUTED_FIELD = "ClientComputed".toByteArray(Charsets.UTF_8)
        private val SPLIT_PAYLOAD_FIELD = "SplitPayload".toByteArray(Charsets.UTF_8)
        private val EMPTY_STRING = "".toByteArray(Charsets.UTF_8)
    }
}
