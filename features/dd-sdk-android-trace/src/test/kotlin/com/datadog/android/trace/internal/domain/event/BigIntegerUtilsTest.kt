/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.domain.event

import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import java.math.BigInteger

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
internal class BigIntegerUtilsTest {

    private val testedUtils = BigIntegerUtils

    @StringForgery(regex = "[a-f0-9]{32}")
    lateinit var fakeTraceIdAsHexaString: String

    @StringForgery(regex = "[a-f0-9]{16}")
    lateinit var fakeHalfTraceIdHexaString: String

    // region lessSignificantUnsignedLongAsHexa

    @Test
    fun `M use unsigned padded bits W extracting LSB from a max low trace id { hexa }`() {
        // Given
        val traceId = BigInteger(fakeHalfTraceIdHexaString + MAX_UNSIGNED_LONG_HEXA_STRING, 16)

        // When
        val lessSignificantAsHexString = testedUtils.lessSignificantUnsignedLongAsHexa(traceId)

        // Then
        assertThat(lessSignificantAsHexString).isEqualTo(MAX_UNSIGNED_LONG_HEXA_STRING)
    }

    @Test
    fun `M use unsigned padded bits W extracting LSB from a min low trace id { hexa }`() {
        // Given
        val traceId = BigInteger(fakeHalfTraceIdHexaString + MIN_UNSIGNED_LONG_HEXA_STRING, 16)

        // When
        val lessSignificantAsHexString = testedUtils.lessSignificantUnsignedLongAsHexa(traceId)

        // Then
        assertThat(lessSignificantAsHexString).isEqualTo(MIN_UNSIGNED_LONG_HEXA_STRING)
    }

    @Test
    fun `M extract unsigned less and most significant bits W extract { hexa }`() {
        // Given
        val traceId = BigInteger(fakeTraceIdAsHexaString, 16)

        // When
        val lessSignificantAsHexString = testedUtils.lessSignificantUnsignedLongAsHexa(traceId)
        val mostSignificantAsHexString = testedUtils.mostSignificantUnsignedLongAsHexa(traceId)

        // Then
        assertThat(mostSignificantAsHexString + lessSignificantAsHexString).isEqualTo(fakeTraceIdAsHexaString)
    }

    // endregion

    // region mostSignificantUnsignedLongAsHexa

    @Test
    fun `M use unsigned padded bits W extracting MSB from a max high trace id { hexa }`() {
        // Given
        val traceId = BigInteger(MAX_UNSIGNED_LONG_HEXA_STRING + fakeHalfTraceIdHexaString, 16)

        // When
        val mostSignificantAsHexString = testedUtils.mostSignificantUnsignedLongAsHexa(traceId)

        // Then
        assertThat(mostSignificantAsHexString).isEqualTo(MAX_UNSIGNED_LONG_HEXA_STRING)
    }

    @Test
    fun `M use unsigned padded bits W extracting MSB from a min high id { hexa }`() {
        // Given
        val traceId = BigInteger(MIN_UNSIGNED_LONG_HEXA_STRING + fakeHalfTraceIdHexaString, 16)

        // When
        val mostSignificantAsHexString = testedUtils.mostSignificantUnsignedLongAsHexa(traceId)

        // Then
        assertThat(mostSignificantAsHexString).isEqualTo(MIN_UNSIGNED_LONG_HEXA_STRING)
    }

    // endregion

    // region lessSignificantUnsignedLongAsDecimal

    @Test
    fun `M use unsigned bits W extracting LSB from a max low trace id { decimal }`() {
        // Given
        val traceId = BigInteger(fakeHalfTraceIdHexaString + MAX_UNSIGNED_LONG_HEXA_STRING, 16)

        // When
        val lessSignificantAsDecimalString = testedUtils.lessSignificantUnsignedLongAsDecimal(traceId)

        // Then
        assertThat(
            BigInteger(
                lessSignificantAsDecimalString,
                10
            ).toString(16).padStart(16, '0')
        ).isEqualTo(MAX_UNSIGNED_LONG_HEXA_STRING)
    }

    @Test
    fun `M use unsigned bits W extracting LSB from a min low trace id { decimal }`() {
        // Given
        val traceId = BigInteger(fakeHalfTraceIdHexaString + MIN_UNSIGNED_LONG_HEXA_STRING, 16)

        // When
        val lessSignificantAsDecimalString = testedUtils.lessSignificantUnsignedLongAsDecimal(traceId)

        // Then
        assertThat(
            BigInteger(
                lessSignificantAsDecimalString,
                10
            ).toString(16).padStart(16, '0')
        ).isEqualTo(MIN_UNSIGNED_LONG_HEXA_STRING)
    }

    @Test
    fun `M extract unsigned less significant bits W extract { decimals }`() {
        // Given
        val traceId = BigInteger(fakeTraceIdAsHexaString, 16)

        // When
        val lessSignificantAsDecimalString = testedUtils.lessSignificantUnsignedLongAsDecimal(traceId)

        // Then
        assertThat(
            BigInteger(
                lessSignificantAsDecimalString,
                10
            ).toString(16).padStart(16, '0')
        ).isEqualTo(fakeTraceIdAsHexaString.takeLast(16))
    }

    // endregion

    companion object {
        const val MAX_UNSIGNED_LONG_HEXA_STRING = "ffffffffffffffff"
        const val MIN_UNSIGNED_LONG_HEXA_STRING = "0000000000000000"
    }
}
