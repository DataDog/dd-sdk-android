/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:OptIn(ExperimentalFlagsApi::class)

package com.datadog.android.flags.internal.signing

import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.ExperimentalFlagsApi
import com.datadog.android.flags.ResponseSigningFailureReason
import com.datadog.android.flags.ResponseSigningMode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

internal class ResponseSigningTelemetryTest {
    private val mockLogger = mock<InternalLogger>()
    private val testedTelemetry = ResponseSigningTelemetry(mockLogger, SITE, SDK_VERSION)

    @Test
    fun `M give the outcome its own identity W report() {required failure}`() {
        // When
        testedTelemetry.report(ResponseSigningMode.REQUIRED, ResponseSigningFailureReason.MISSING_PROOF)

        // Then
        assertThat(events()).containsExactly(
            "flags.response_signing required unverified missing-proof" to mapOf(
                "mode" to "required",
                "result" to "unverified",
                "reason" to "missing-proof",
                "sdk_version" to SDK_VERSION,
                "site" to SITE
            )
        )
    }

    @Test
    fun `M send each report with the same identity W report() {two identical failures}`() {
        // When
        repeat(2) { testedTelemetry.report(ResponseSigningMode.REQUIRED, ResponseSigningFailureReason.MISSING_PROOF) }

        // Then: the SDK keeps no dedupe set; the RUM telemetry handler deduplicates by identity for each session.
        assertThat(events().map { it.first }.distinct()).containsExactly(
            "flags.response_signing required unverified missing-proof"
        )
        assertThat(events()).hasSize(2)
    }

    @Test
    fun `M use distinct identities W report() {different outcomes}`() {
        // When
        testedTelemetry.report(ResponseSigningMode.OBSERVE, null)
        testedTelemetry.report(ResponseSigningMode.OBSERVE, ResponseSigningFailureReason.MISSING_PROOF)
        testedTelemetry.report(ResponseSigningMode.OBSERVE, ResponseSigningFailureReason.INVALID_SIGNATURE)

        // Then
        assertThat(events().map { it.first }).containsExactly(
            "flags.response_signing observe verified",
            "flags.response_signing observe unverified missing-proof",
            "flags.response_signing observe unverified invalid-signature"
        )
        assertThat(events().first().second).doesNotContainKey("reason")
    }

    @Test
    fun `M send nothing W report() {required verified, or off}`() {
        // When
        testedTelemetry.report(ResponseSigningMode.REQUIRED, null)
        testedTelemetry.report(ResponseSigningMode.OFF, null)
        testedTelemetry.report(ResponseSigningMode.OFF, ResponseSigningFailureReason.MISSING_PROOF)

        // Then
        assertThat(events()).isEmpty()
    }

    @Test
    fun `M send no field outside the contract W report()`() {
        // When
        testedTelemetry.report(ResponseSigningMode.OBSERVE, ResponseSigningFailureReason.EXPIRED_PROOF)

        // Then
        assertThat(events().single().second.keys)
            .containsExactlyInAnyOrder("mode", "result", "reason", "sdk_version", "site")
    }

    @Test
    fun `M name the site from the intake host W siteName()`() {
        assertThat(ResponseSigningTelemetry.siteName("https://browser-intake-datad0g.com")).isEqualTo("datad0g.com")
        assertThat(ResponseSigningTelemetry.siteName("https://browser-intake-datadoghq.com")).isEqualTo("datadoghq.com")
    }

    private fun events(): List<Pair<String, Map<String, Any?>>> {
        val messages = argumentCaptor<() -> String>()
        val properties = argumentCaptor<Map<String, Any?>>()
        verify(mockLogger, atLeast(0)).log(
            eq(InternalLogger.Level.INFO),
            eq(InternalLogger.Target.TELEMETRY),
            messages.capture(),
            eq(null),
            eq(false),
            properties.capture()
        )
        return messages.allValues.map { it() }.zip(properties.allValues)
    }

    private companion object {
        const val SITE = "datad0g.com"
        const val SDK_VERSION = "3.9.0"
    }
}
