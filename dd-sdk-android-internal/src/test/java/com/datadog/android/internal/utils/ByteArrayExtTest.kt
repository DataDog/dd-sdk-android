/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.utils

import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.util.Locale

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
class ByteArrayExtTest {

    @Test
    fun `M return correct hex string W convert {0x00, 0x00}`() {
        // Given
        val byteArray = byteArrayOf(0x00, 0x00) // 0xA is 10 in decimal

        // When
        val result = byteArray.toHexString()

        // Then
        val expectedHex = "0000"
        assertThat(result).isEqualTo(expectedHex)
    }

    @Test
    fun `M return correct hex string W convert {0xFF, 0xFF}`() {
        // Given
        val byteArray = byteArrayOf(0xFF.toByte(), 0xFF.toByte()) // 0xA is 10 in decimal

        // When
        val result = byteArray.toHexString()

        // Then
        val expectedHex = "ffff"
        assertThat(result).isEqualTo(expectedHex)
    }

    @Test
    fun `M return correct hex string W call toHexString()`(@StringForgery fakeInput: String) {
        // Given
        val fakeByteArray = fakeInput.toByteArray()

        // When
        val result = fakeByteArray.toHexString()

        // Then
        val expected = fakeByteArray.joinToString(separator = "") { "%02x".format(Locale.US, it) }
        assertThat(result).isEqualTo(expected)
    }
}
