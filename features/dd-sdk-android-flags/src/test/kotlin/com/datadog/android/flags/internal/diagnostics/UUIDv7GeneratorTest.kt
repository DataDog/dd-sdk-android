/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.diagnostics

import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
internal class UUIDv7GeneratorTest {
    @Test
    fun `M generate timestamp-bearing UUIDv7 W generate()`(
        @LongForgery(min = 0, max = 0xffffffffffffL) fakeTimestamp: Long
    ) {
        // When
        val id = UUIDv7Generator.generate(fakeTimestamp)

        // Then
        assertThat(id.mostSignificantBits ushr 16).isEqualTo(fakeTimestamp)
        assertThat(id.version()).isEqualTo(7)
        assertThat(id.variant()).isEqualTo(2)
    }
}
