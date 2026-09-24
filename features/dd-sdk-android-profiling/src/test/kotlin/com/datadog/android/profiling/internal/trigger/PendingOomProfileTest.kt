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
        @LongForgery fakeBootNtpOffsetNs: Long,
        @StringForgery fakeErrorId: String,
        @Forgery fakeRumContext: ProfilingRumContext
    ) {
        // Given
        val original = PendingOomProfile(
            resultFilePath = fakePath,
            startMs = fakeStartMs,
            endMs = fakeEndMs,
            bootNtpOffsetNs = fakeBootNtpOffsetNs,
            rumErrorId = fakeErrorId,
            rumContext = fakeRumContext
        )

        // When
        val restored = PendingOomProfile.fromJson(original.toJson())

        // Then
        assertThat(restored).isEqualTo(original)
    }

    @Test
    fun `M read the documented wire format W fromJson()`(
        @StringForgery fakePath: String,
        @LongForgery fakeStartMs: Long,
        @LongForgery fakeEndMs: Long,
        @LongForgery fakeBootNtpOffsetNs: Long,
        @StringForgery fakeErrorId: String,
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeSessionId: String,
        @StringForgery fakeViewId: String,
        @StringForgery fakeViewName: String
    ) {
        // Given
        // The pending profile is written by the app version that crashed and read by the one that starts
        // next, so these key names are a compatibility contract across an app upgrade.
        val serialized = """
            {
              "path": "$fakePath",
              "start": $fakeStartMs,
              "end": $fakeEndMs,
              "boot_ntp": $fakeBootNtpOffsetNs,
              "error_id": "$fakeErrorId",
              "application_id": "$fakeApplicationId",
              "session_id": "$fakeSessionId",
              "view_id": "$fakeViewId",
              "view_name": "$fakeViewName"
            }
        """.trimIndent()

        // When
        val restored = PendingOomProfile.fromJson(serialized)

        // Then
        assertThat(restored).isEqualTo(
            PendingOomProfile(
                resultFilePath = fakePath,
                startMs = fakeStartMs,
                endMs = fakeEndMs,
                bootNtpOffsetNs = fakeBootNtpOffsetNs,
                rumErrorId = fakeErrorId,
                rumContext = ProfilingRumContext(
                    applicationId = fakeApplicationId,
                    sessionId = fakeSessionId,
                    viewId = fakeViewId,
                    viewName = fakeViewName
                )
            )
        )
    }

    @Test
    fun `M keep the view absent W toJson() then fromJson() {no view in rum context}`(
        @StringForgery fakePath: String,
        @LongForgery fakeStartMs: Long,
        @LongForgery fakeEndMs: Long,
        @LongForgery fakeBootNtpOffsetNs: Long,
        @StringForgery fakeErrorId: String,
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeSessionId: String
    ) {
        // Given
        val original = PendingOomProfile(
            resultFilePath = fakePath,
            startMs = fakeStartMs,
            endMs = fakeEndMs,
            bootNtpOffsetNs = fakeBootNtpOffsetNs,
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
    fun `M return null W fromJson() {mandatory field missing}`(
        @LongForgery fakeStartMs: Long,
        @LongForgery fakeEndMs: Long,
        @LongForgery fakeBootNtpOffsetNs: Long,
        @StringForgery fakeErrorId: String,
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeSessionId: String
    ) {
        // Given
        // A pending profile written by a future or corrupted version must not resurrect as one with
        // an empty path, which would send a profile with no trace attached.
        val serialized = """
            {
              "start": $fakeStartMs,
              "end": $fakeEndMs,
              "boot_ntp": $fakeBootNtpOffsetNs,
              "error_id": "$fakeErrorId",
              "application_id": "$fakeApplicationId",
              "session_id": "$fakeSessionId"
            }
        """.trimIndent()

        // When
        val restored = PendingOomProfile.fromJson(serialized)

        // Then
        assertThat(restored).isNull()
    }

    @Test
    fun `M return null W fromJson() {timestamp is not a number}`(
        @StringForgery fakePath: String,
        @LongForgery fakeEndMs: Long,
        @LongForgery fakeBootNtpOffsetNs: Long,
        @StringForgery fakeErrorId: String,
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeSessionId: String
    ) {
        // Given
        val serialized = """
            {
              "path": "$fakePath",
              "start": "not-a-number",
              "end": $fakeEndMs,
              "boot_ntp": $fakeBootNtpOffsetNs,
              "error_id": "$fakeErrorId",
              "application_id": "$fakeApplicationId",
              "session_id": "$fakeSessionId"
            }
        """.trimIndent()

        // When
        val restored = PendingOomProfile.fromJson(serialized)

        // Then
        assertThat(restored).isNull()
    }
}
