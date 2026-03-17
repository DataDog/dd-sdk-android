/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.sampling

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class SessionSamplingIdProviderTest {

    @Test
    fun `M return last segment as ULong W provideId() {valid UUID}`() {
        // Given
        val sessionId = "aaaaaaaa-bbbb-cccc-dddd-1234567890ab"

        // When
        val result = SessionSamplingIdProvider.provideId(sessionId)

        // Then
        assertThat(result).isEqualTo(0x1234567890abUL)
    }

    @Test
    fun `M return 0 W provideId() {malformed UUID, no dashes}`() {
        // Given
        val sessionId = "notauuid"

        // When
        val result = SessionSamplingIdProvider.provideId(sessionId)

        // Then
        assertThat(result).isEqualTo(0uL)
    }

    @Test
    fun `M return 0 W provideId() {empty string}`() {
        // When
        val result = SessionSamplingIdProvider.provideId("")

        // Then
        assertThat(result).isEqualTo(0uL)
    }

    @Test
    fun `M return 0 W provideId() {last segment is not valid hex}`() {
        // Given
        val sessionId = "aaaaaaaa-bbbb-cccc-dddd-nothex"

        // When
        val result = SessionSamplingIdProvider.provideId(sessionId)

        // Then
        assertThat(result).isEqualTo(0uL)
    }
}
