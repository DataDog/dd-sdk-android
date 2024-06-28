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
import java.util.concurrent.TimeUnit
import kotlin.system.measureNanoTime

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
internal class BigIntegerUtilsTest {

    @StringForgery(regex = "[a-f0-9]{32}")
    lateinit var fakeTraceIdAsHexString: String

    @StringForgery(regex = "[a-f0-9]{16}")
    lateinit var fakeHalfTraceIdHexString: String

    // region leastSignificant64BitsAsHex

    @Test
    fun `M use unsigned padded bits W leastSignificant64BitsAsHex { max low order trace id }`() {
        // Given
        val traceId = BigInteger(fakeHalfTraceIdHexString + MAX_UNSIGNED_LONG_HEX_STRING, 16)

        // When
        var leastSignificantAsHexString: String
        val executionTime = measureNanoTime {
            leastSignificantAsHexString = BigIntegerUtils.leastSignificant64BitsAsHex(traceId)
        }

        // Then
        assertThat(executionTime).isLessThan(MAX_EXEC_TIME_IN_NANOS)
        assertThat(leastSignificantAsHexString).isEqualTo(MAX_UNSIGNED_LONG_HEX_STRING)
    }

    @Test
    fun `M use unsigned padded bits W leastSignificant64BitsAsHex { from min low order trace id }`() {
        // Given
        val traceId = BigInteger(fakeHalfTraceIdHexString + MIN_UNSIGNED_LONG_HEX_STRING, 16)

        // When
        var leastSignificantAsHexString: String
        val executionTime = measureNanoTime {
            leastSignificantAsHexString = BigIntegerUtils.leastSignificant64BitsAsHex(traceId)
        }

        // Then
        assertThat(executionTime).isLessThan(MAX_EXEC_TIME_IN_NANOS)
        assertThat(leastSignificantAsHexString).isEqualTo(MIN_UNSIGNED_LONG_HEX_STRING)
    }

    @Test
    fun `M extract unsigned least and most significant bits W extract LSB and MSB`() {
        // Given
        val traceId = BigInteger(fakeTraceIdAsHexString, 16)

        // When

        var leastSignificantAsHexString: String
        var mostSignificantAsHexString: String
        val executionTime = measureNanoTime {
            leastSignificantAsHexString = BigIntegerUtils.leastSignificant64BitsAsHex(traceId)
            mostSignificantAsHexString = BigIntegerUtils.mostSignificant64BitsAsHex(traceId)
        }

        // Then
        assertThat(mostSignificantAsHexString + leastSignificantAsHexString).isEqualTo(fakeTraceIdAsHexString)
        assertThat(executionTime).isLessThan(MAX_EXEC_TIME_IN_NANOS)
    }

    // endregion

    // region mostSignificant64BitsAsHex

    @Test
    fun `M use unsigned padded bits W mostSignificant64BitsAsHex { max high order trace id }`() {
        // Given
        val traceId = BigInteger(MAX_UNSIGNED_LONG_HEX_STRING + fakeHalfTraceIdHexString, 16)

        // When
        var mostSignificantAsHexString: String
        val executionTime = measureNanoTime {
            mostSignificantAsHexString = BigIntegerUtils.mostSignificant64BitsAsHex(traceId)
        }

        // Then
        assertThat(mostSignificantAsHexString).isEqualTo(MAX_UNSIGNED_LONG_HEX_STRING)
        assertThat(executionTime).isLessThan(MAX_EXEC_TIME_IN_NANOS)
    }

    @Test
    fun `M use unsigned padded bits W mostSignificant64BitsAsHex { min high order trace id }`() {
        // Given
        val traceId = BigInteger(MIN_UNSIGNED_LONG_HEX_STRING + fakeHalfTraceIdHexString, 16)

        // When
        var mostSignificantAsHexString: String
        val executionTime = measureNanoTime {
            mostSignificantAsHexString = BigIntegerUtils.mostSignificant64BitsAsHex(traceId)
        }

        // Then
        assertThat(mostSignificantAsHexString).isEqualTo(MIN_UNSIGNED_LONG_HEX_STRING)
        assertThat(executionTime).isLessThan(MAX_EXEC_TIME_IN_NANOS)
    }

    // endregion

    // region leastSignificant64BitsAsDecimal

    @Test
    fun `M use unsigned bits W leastSignificant64BitsAsDecimal { max low order trace id }`() {
        // Given
        val traceId = BigInteger(fakeHalfTraceIdHexString + MAX_UNSIGNED_LONG_HEX_STRING, 16)

        // When
        var leastSignificantAsDecimalString: String
        val executionTime = measureNanoTime {
            leastSignificantAsDecimalString = BigIntegerUtils.leastSignificant64BitsAsDecimal(traceId)
        }

        // Then
        val leastSignificant64BitTraceIdAsBigInt = BigInteger(leastSignificantAsDecimalString, 10)
        assertThat(
            leastSignificant64BitTraceIdAsBigInt.toString(16).padStart(16, '0')
        ).isEqualTo(MAX_UNSIGNED_LONG_HEX_STRING)
        assertThat(executionTime).isLessThan(MAX_EXEC_TIME_IN_NANOS)
    }

    @Test
    fun `M use unsigned bits W leastSignificant64BitsAsDecimal { min low order trace id }`() {
        // Given
        val traceId = BigInteger(fakeHalfTraceIdHexString + MIN_UNSIGNED_LONG_HEX_STRING, 16)

        // When
        var leastSignificantAsDecimalString: String
        val executionTime = measureNanoTime {
            leastSignificantAsDecimalString = BigIntegerUtils.leastSignificant64BitsAsDecimal(traceId)
        }

        // Then
        assertThat(
            BigInteger(
                leastSignificantAsDecimalString,
                10
            ).toString(16).padStart(16, '0')
        ).isEqualTo(MIN_UNSIGNED_LONG_HEX_STRING)
        assertThat(executionTime).isLessThan(MAX_EXEC_TIME_IN_NANOS)
    }

    @Test
    fun `M extract unsigned least significant bits W leastSignificant64BitsAsDecimal`() {
        // Given
        val traceId = BigInteger(fakeTraceIdAsHexString, 16)

        // When
        var leastSignificantAsDecimalString: String
        val executionTime = measureNanoTime {
            leastSignificantAsDecimalString = BigIntegerUtils.leastSignificant64BitsAsDecimal(traceId)
        }

        // Then
        assertThat(
            BigInteger(
                leastSignificantAsDecimalString,
                10
            ).toString(16).padStart(16, '0')
        ).isEqualTo(fakeTraceIdAsHexString.takeLast(16))
        assertThat(executionTime).isLessThan(MAX_EXEC_TIME_IN_NANOS)
    }

    // endregion

    companion object {
        private const val MAX_UNSIGNED_LONG_HEX_STRING = "ffffffffffffffff"
        private const val MIN_UNSIGNED_LONG_HEX_STRING = "0000000000000000"
        private val MAX_EXEC_TIME_IN_NANOS = TimeUnit.MILLISECONDS.toNanos(8)
    }
}
