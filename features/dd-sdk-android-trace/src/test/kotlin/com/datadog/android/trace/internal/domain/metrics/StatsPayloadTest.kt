/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.domain.metrics

import com.datadog.android.trace.assertj.MsgPackAssert
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(Configurator::class)
internal class StatsPayloadTest {

    @Test
    fun `M encode all 6 fields W toMsgPackPayload()`(
        @BoolForgery fakeSplitPayload: Boolean
    ) {
        // Given
        val fakeClientStatsBytes = ClientStatsPayload(
            hostname = "test-host",
            env = "prod",
            version = "1.0.0",
            service = "test-service",
            tracerVersion = "1.60.0",
            runtimeID = "abc-123-runtime",
            sequenceNumber = 7L,
            stats = emptyList()
        ).toMsgPackPayload()
        val testedPayload = StatsPayload(
            clientStats = listOf(fakeClientStatsBytes),
            splitPayload = fakeSplitPayload
        )

        // When
        val bytes = testedPayload.toMsgPackPayload()

        // Then
        MsgPackAssert.assertThat(bytes)
            .hasField("AgentHostname", "")
            .hasField("AgentEnv", "")
            .hasField("AgentVersion", "")
            .hasField("ClientComputed", true)
            .hasField("SplitPayload", fakeSplitPayload)
            .hasField("Stats[0].Hostname", "test-host")
            .hasField("Stats[0].Lang", "android")
    }
}
