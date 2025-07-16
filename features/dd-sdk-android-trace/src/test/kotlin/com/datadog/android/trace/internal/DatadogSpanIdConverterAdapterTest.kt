/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.internal

import com.datadog.trace.api.DDSpanId.toHexStringPadded
import fr.xgouchet.elmyr.annotation.LongForgery
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DatadogSpanIdConverterAdapterTest {

    @LongForgery
    private var fakeLong: Long = 0L

    @Test
    fun `M serialize to String and deserialize valid W fromHeX(toHexStringPadded(Long))`() {
        // Given
        val converter = DatadogSpanIdConverter()

        // When
        val actual = converter.fromHex(toHexStringPadded(fakeLong))

        // Then
        assertThat(actual).isEqualTo(fakeLong)
    }
}
