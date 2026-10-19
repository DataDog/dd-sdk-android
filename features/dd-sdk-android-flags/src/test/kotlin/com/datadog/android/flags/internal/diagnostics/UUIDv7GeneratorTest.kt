/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.diagnostics

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class UUIDv7GeneratorTest {
    @Test
    fun `M generate timestamp-bearing random UUIDv7 W generate()`() {
        // Given
        val timestamp = 1_700_000_000_000L
        // When
        val ids = (0 until 100).map { UUIDv7Generator.generate(timestamp) }
        // Then
        assertThat(ids.toSet()).hasSize(100)
        ids.forEach { id ->
            assertThat(id.mostSignificantBits ushr 16).isEqualTo(timestamp)
            assertThat(id.version()).isEqualTo(7)
            assertThat(id.variant()).isEqualTo(2)
        }
    }
}
