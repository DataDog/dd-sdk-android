/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.trigger

import com.datadog.android.internal.profiling.ProfilingRumContext
import com.datadog.android.profiling.forge.Configurator
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class PendingOomGatingEventTest {

    @Test
    fun `M preserve every field W toJson() then fromJson()`(
        @StringForgery fakeRumErrorId: String,
        @LongForgery fakeTimestampMs: Long,
        @Forgery fakeRumContext: ProfilingRumContext
    ) {
        // Given
        val original = PendingOomGatingEvent(
            rumErrorId = fakeRumErrorId,
            timestampMs = fakeTimestampMs,
            rumContext = fakeRumContext
        )

        // When
        val restored = PendingOomGatingEvent.fromJson(original.toJson())

        // Then
        assertThat(restored).isEqualTo(original)
    }

    @Test
    fun `M read the documented wire format W fromJson()`() {
        // Given
        // The marker is written by the process that received the RUM gating event and read by
        // the one that eventually receives the deferred trigger result, so these key names are a
        // compatibility contract across an app launch.
        val serialized = """
            {
              "error_id": "err-1",
              "timestamp": 1700000000000,
              "application_id": "app-1",
              "session_id": "sess-1",
              "view_id": "view-1",
              "view_name": "MainActivity"
            }
        """.trimIndent()

        // When
        val restored = PendingOomGatingEvent.fromJson(serialized)

        // Then
        assertThat(restored).isEqualTo(
            PendingOomGatingEvent(
                rumErrorId = "err-1",
                timestampMs = 1_700_000_000_000L,
                rumContext = ProfilingRumContext(
                    applicationId = "app-1",
                    sessionId = "sess-1",
                    viewId = "view-1",
                    viewName = "MainActivity"
                )
            )
        )
    }

    @Test
    fun `M keep the view absent W toJson() then fromJson() {no view in rum context}`(
        @StringForgery fakeRumErrorId: String,
        @LongForgery fakeTimestampMs: Long,
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeSessionId: String
    ) {
        // Given
        val original = PendingOomGatingEvent(
            rumErrorId = fakeRumErrorId,
            timestampMs = fakeTimestampMs,
            rumContext = ProfilingRumContext(
                applicationId = fakeApplicationId,
                sessionId = fakeSessionId,
                viewId = null,
                viewName = null
            )
        )

        // When
        val restored = PendingOomGatingEvent.fromJson(original.toJson())

        // Then
        assertThat(restored?.rumContext?.viewId).isNull()
        assertThat(restored?.rumContext?.viewName).isNull()
    }

    @Test
    fun `M return null W fromJson() {not json}`(
        @StringForgery fakeRaw: String
    ) {
        // When
        val restored = PendingOomGatingEvent.fromJson(fakeRaw)

        // Then
        assertThat(restored).isNull()
    }

    @Test
    fun `M return null W fromJson() {mandatory field missing}`() {
        // Given
        val serialized = """
            {
              "timestamp": 1700000000000,
              "application_id": "app-1",
              "session_id": "sess-1"
            }
        """.trimIndent()

        // When
        val restored = PendingOomGatingEvent.fromJson(serialized)

        // Then
        assertThat(restored).isNull()
    }

    @Test
    fun `M return null W fromJson() {timestamp is not a number}`() {
        // Given
        val serialized = """
            {
              "error_id": "err-1",
              "timestamp": "not-a-number",
              "application_id": "app-1",
              "session_id": "sess-1"
            }
        """.trimIndent()

        // When
        val restored = PendingOomGatingEvent.fromJson(serialized)

        // Then
        assertThat(restored).isNull()
    }
}
