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
    private val testedTelemetry = ResponseSigningTelemetry(mockLogger, mutableSetOf(), SITE, SDK_VERSION)

    @Test
    fun `M send one event W report() {two identical failures}`() {
        // When
        repeat(2) { testedTelemetry.report(ResponseSigningMode.REQUIRED, ResponseSigningFailureReason.MISSING_PROOF) }

        // Then
        assertThat(events()).containsExactly(
            mapOf(
                "mode" to "required",
                "result" to "unverified",
                "reason" to "missing-proof",
                "sdk_version" to SDK_VERSION,
                "site" to SITE
            )
        )
    }

    @Test
    fun `M send two events W report() {two different reasons}`() {
        // When
        testedTelemetry.report(ResponseSigningMode.OBSERVE, ResponseSigningFailureReason.MISSING_PROOF)
        testedTelemetry.report(ResponseSigningMode.OBSERVE, ResponseSigningFailureReason.INVALID_SIGNATURE)

        // Then
        assertThat(events().map { it["reason"] }).containsExactly("missing-proof", "invalid-signature")
    }

    @Test
    fun `M send verified once without a reason W report() {observe}`() {
        // When
        repeat(2) { testedTelemetry.report(ResponseSigningMode.OBSERVE, null) }

        // Then
        assertThat(events()).containsExactly(
            mapOf("mode" to "observe", "result" to "verified", "sdk_version" to SDK_VERSION, "site" to SITE)
        )
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
    fun `M share sent keys W report() {two clients of one feature}`() {
        // Given
        val sent = mutableSetOf<String>()
        val first = ResponseSigningTelemetry(mockLogger, sent, SITE, SDK_VERSION)
        val second = ResponseSigningTelemetry(mockLogger, sent, SITE, SDK_VERSION)

        // When
        first.report(ResponseSigningMode.REQUIRED, ResponseSigningFailureReason.EXPIRED_PROOF)
        second.report(ResponseSigningMode.REQUIRED, ResponseSigningFailureReason.EXPIRED_PROOF)

        // Then
        assertThat(events()).hasSize(1)
    }

    @Test
    fun `M name the site from the intake host W siteName()`() {
        assertThat(ResponseSigningTelemetry.siteName("https://browser-intake-datad0g.com")).isEqualTo("datad0g.com")
        assertThat(ResponseSigningTelemetry.siteName("https://browser-intake-datadoghq.com")).isEqualTo("datadoghq.com")
    }

    private fun events(): List<Map<String, Any?>> {
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
        assertThat(messages.allValues.map { it() }).allMatch { it == ResponseSigningTelemetry.EVENT }
        return properties.allValues
    }

    private companion object {
        const val SITE = "datad0g.com"
        const val SDK_VERSION = "3.9.0"
    }
}
