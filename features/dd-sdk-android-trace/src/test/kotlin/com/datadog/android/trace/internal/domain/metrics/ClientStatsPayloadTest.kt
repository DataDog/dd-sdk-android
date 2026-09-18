/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.domain.metrics

import com.datadog.android.trace.assertj.MsgPackAssert
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(Configurator::class)
internal class ClientStatsPayloadTest {

    @Test
    fun `M encode all fields in wire format W toMsgPackPayload()`(
        @StringForgery(regex = "[a-z][a-z0-9-]{1,15}") fakeHostname: String,
        @StringForgery(regex = "[a-z][a-z0-9-]{1,15}") fakeEnv: String,
        @StringForgery(regex = "[a-z][a-z0-9-]{1,15}") fakeVersion: String,
        @StringForgery(regex = "[a-z][a-z0-9-]{1,15}") fakeService: String,
        @StringForgery(regex = "[a-z][a-z0-9-]{1,15}") fakeTracerVersion: String,
        @StringForgery(regex = "[a-z][a-z0-9-]{1,15}") fakeRuntimeID: String,
        @LongForgery(min = 0L, max = 1000L) fakeSequenceNumber: Long
    ) {
        // Given
        val fakeGroupedStats = ClientGroupedStats(
            service = fakeService,
            name = "test-op",
            resource = "GET /test",
            httpStatusCode = 200,
            type = "web",
            spanKind = "server",
            isTraceRoot = Trilean.TRUE,
            hits = 10L,
            errors = 2L,
            duration = 500_000L,
            topLevelHits = 3L,
            okSummary = byteArrayOf(0x61, 0x62),
            errorSummary = byteArrayOf(0x63),
            isSynthetic = false,
            peerTags = listOf("env:prod", "version:1.0"),
            serviceSource = "inferred"
        )
        val fakeBucket = ClientStatsBucket(
            start = 1_700_000_000_000_000_000L,
            duration = 10_000_000_000L,
            stats = listOf(fakeGroupedStats)
        )
        val testedPayload = ClientStatsPayload(
            hostname = fakeHostname,
            env = fakeEnv,
            version = fakeVersion,
            service = fakeService,
            tracerVersion = fakeTracerVersion,
            runtimeID = fakeRuntimeID,
            sequenceNumber = fakeSequenceNumber,
            stats = listOf(fakeBucket)
        )

        // When
        val bytes = testedPayload.toMsgPackPayload()

        // Then
        MsgPackAssert.assertThat(bytes)
            .hasField("Hostname", fakeHostname)
            .hasField("Env", fakeEnv)
            .hasField("Version", fakeVersion)
            .hasField("Lang", "android")
            .hasField("TracerVersion", fakeTracerVersion)
            .hasField("RuntimeID", fakeRuntimeID)
            .hasField("Sequence", fakeSequenceNumber)
            .hasField("Service", fakeService)
            .hasField("Stats[0]") {
                hasField("Start", 1_700_000_000_000_000_000L)
                hasField("Duration", 10_000_000_000L)
            }
            .hasField("Stats[0].Stats[0]") {
                hasField("Service", fakeService)
                hasField("Name", "test-op")
                hasField("Resource", "GET /test")
                hasField("HTTPStatusCode", 200)
                hasField("Type", "web")
                hasField("Hits", 10L)
                hasField("Errors", 2L)
                hasField("Duration", 500_000L)
                hasField("OkSummary", "ab")
                hasField("ErrorSummary", "c")
                hasField("Synthetics", false)
                hasField("TopLevelHits", 3L)
                hasField("SpanKind", "server")
                hasField("PeerTags", listOf("env:prod", "version:1.0"))
                hasField("IsTraceRoot", Trilean.TRUE.value)
                hasField("GRPCStatusCode", "")
                hasField("srv_src", "inferred")
            }
    }
}
