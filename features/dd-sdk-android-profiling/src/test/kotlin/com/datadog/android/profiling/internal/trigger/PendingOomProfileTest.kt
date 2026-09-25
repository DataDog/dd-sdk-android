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
internal class PendingOomProfileTest {

    @Test
    fun `M preserve every field W toJson() then fromJson()`(
        @StringForgery fakePath: String,
        @LongForgery fakeStartMs: Long,
        @LongForgery fakeEndMs: Long,
        @LongForgery fakeBootNtpNs: Long,
        @StringForgery fakeErrorId: String,
        @Forgery fakeRumContext: ProfilingRumContext
    ) {
        // Given
        val original = PendingOomProfile(
            resultFilePath = fakePath,
            startMs = fakeStartMs,
            endMs = fakeEndMs,
            bootNtpNs = fakeBootNtpNs,
            rumErrorId = fakeErrorId,
            rumContext = fakeRumContext
        )

        // When
        val restored = PendingOomProfile.fromJson(original.toJson())

        // Then
        assertThat(restored).isEqualTo(original)
    }

    @Test
    fun `M read the documented wire format W fromJson()`() {
        // Given
        // The marker is written by the app version that crashed and read by the one that starts
        // next, so these key names are a compatibility contract across an app upgrade.
        val serialized = """
            {
              "path": "/data/user/0/com.example/files/profiling/oom.perfetto",
              "start": 1700000000000,
              "end": 1700000000500,
              "boot_ntp": 1234567890,
              "error_id": "err-1",
              "application_id": "app-1",
              "session_id": "sess-1",
              "view_id": "view-1",
              "view_name": "MainActivity"
            }
        """.trimIndent()

        // When
        val restored = PendingOomProfile.fromJson(serialized)

        // Then
        assertThat(restored).isEqualTo(
            PendingOomProfile(
                resultFilePath = "/data/user/0/com.example/files/profiling/oom.perfetto",
                startMs = 1_700_000_000_000L,
                endMs = 1_700_000_000_500L,
                bootNtpNs = 1_234_567_890L,
                rumErrorId = "err-1",
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
        @StringForgery fakePath: String,
        @LongForgery fakeStartMs: Long,
        @LongForgery fakeEndMs: Long,
        @LongForgery fakeBootNtpNs: Long,
        @StringForgery fakeErrorId: String,
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeSessionId: String
    ) {
        // Given
        val original = PendingOomProfile(
            resultFilePath = fakePath,
            startMs = fakeStartMs,
            endMs = fakeEndMs,
            bootNtpNs = fakeBootNtpNs,
            rumErrorId = fakeErrorId,
            rumContext = ProfilingRumContext(
                applicationId = fakeApplicationId,
                sessionId = fakeSessionId,
                viewId = null,
                viewName = null
            )
        )

        // When
        val restored = PendingOomProfile.fromJson(original.toJson())

        // Then
        assertThat(restored?.rumContext?.viewId).isNull()
        assertThat(restored?.rumContext?.viewName).isNull()
    }

    @Test
    fun `M return null W fromJson() {not json}`(
        @StringForgery fakeRaw: String
    ) {
        // When
        val restored = PendingOomProfile.fromJson(fakeRaw)

        // Then
        assertThat(restored).isNull()
    }

    @Test
    fun `M return null W fromJson() {mandatory field missing}`() {
        // Given
        // A marker written by a future or corrupted version must not resurrect as a profile with
        // an empty path, which would send a profile with no trace attached.
        val serialized = """
            {
              "start": 1700000000000,
              "end": 1700000000500,
              "boot_ntp": 1234567890,
              "error_id": "err-1",
              "application_id": "app-1",
              "session_id": "sess-1"
            }
        """.trimIndent()

        // When
        val restored = PendingOomProfile.fromJson(serialized)

        // Then
        assertThat(restored).isNull()
    }

    @Test
    fun `M return null W fromJson() {timestamp is not a number}`() {
        // Given
        val serialized = """
            {
              "path": "/tmp/oom.perfetto",
              "start": "not-a-number",
              "end": 1700000000500,
              "boot_ntp": 1234567890,
              "error_id": "err-1",
              "application_id": "app-1",
              "session_id": "sess-1"
            }
        """.trimIndent()

        // When
        val restored = PendingOomProfile.fromJson(serialized)

        // Then
        assertThat(restored).isNull()
    }
}
