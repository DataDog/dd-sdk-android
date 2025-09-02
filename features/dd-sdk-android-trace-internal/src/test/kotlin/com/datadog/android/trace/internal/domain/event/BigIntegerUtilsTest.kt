/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.domain.event

import com.datadog.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.RepeatedTest
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

    @StringForgery(regex = REGEX_16_CHAR_HEX_NUMBER)
    lateinit var fakeIrrelevantHexString: String

    // region leastSignificant64BitsAsHex

    @Test
    fun `M use unsigned padded bits W leastSignificant64BitsAsHex { max value }`() {
        // Given
        val traceId = BigInteger(fakeIrrelevantHexString + MAX_UNSIGNED_LONG_HEX_STRING, HEX_RADIX)

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
    fun `M use unsigned padded bits W leastSignificant64BitsAsHex { min value }`() {
        // Given
        val traceId = BigInteger(fakeIrrelevantHexString + MIN_UNSIGNED_LONG_HEX_STRING, HEX_RADIX)

        // When
        var leastSignificantAsHexString: String
        val executionTime = measureNanoTime {
            leastSignificantAsHexString = BigIntegerUtils.leastSignificant64BitsAsHex(traceId)
        }

        // Then
        assertThat(executionTime).isLessThan(MAX_EXEC_TIME_IN_NANOS)
        assertThat(leastSignificantAsHexString).isEqualTo(MIN_UNSIGNED_LONG_HEX_STRING)
    }

    @RepeatedTest(8)
    fun `M extract unsigned padded bits W leastSignificant64BitsAsHex`(
        @StringForgery(regex = REGEX_16_CHAR_HEX_NUMBER) fakeLSB: String
    ) {
        // Given
        val traceId = BigInteger(fakeIrrelevantHexString + fakeLSB, HEX_RADIX)

        // When
        var leastSignificantAsHexString: String
        val executionTime = measureNanoTime {
            leastSignificantAsHexString = BigIntegerUtils.leastSignificant64BitsAsHex(traceId)
        }

        // Then
        assertThat(leastSignificantAsHexString).isEqualTo(fakeLSB)
        assertThat(executionTime).isLessThan(MAX_EXEC_TIME_IN_NANOS)
    }

    // endregion

    // region mostSignificant64BitsAsHex

    @Test
    fun `M use unsigned padded bits W mostSignificant64BitsAsHex { max value }`() {
        // Given
        val traceId = BigInteger(MAX_UNSIGNED_LONG_HEX_STRING + fakeIrrelevantHexString, HEX_RADIX)

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
    fun `M use unsigned padded bits W mostSignificant64BitsAsHex { min value }`() {
        // Given
        val traceId = BigInteger(MIN_UNSIGNED_LONG_HEX_STRING + fakeIrrelevantHexString, HEX_RADIX)

        // When
        var mostSignificantAsHexString: String
        val executionTime = measureNanoTime {
            mostSignificantAsHexString = BigIntegerUtils.mostSignificant64BitsAsHex(traceId)
        }

        // Then
        assertThat(mostSignificantAsHexString).isEqualTo(MIN_UNSIGNED_LONG_HEX_STRING)
        assertThat(executionTime).isLessThan(MAX_EXEC_TIME_IN_NANOS)
    }

    @RepeatedTest(8)
    fun `M extract unsigned padded bits W mostSignificant64BitsAsHex`(
        @StringForgery(regex = REGEX_16_CHAR_HEX_NUMBER) fakeMSB: String
    ) {
        // Given
        val traceId = BigInteger(fakeMSB + fakeIrrelevantHexString, HEX_RADIX)

        // When
        var mostSignificantAsHexString: String
        val executionTime = measureNanoTime {
            mostSignificantAsHexString = BigIntegerUtils.mostSignificant64BitsAsHex(traceId)
        }

        // Then
        assertThat(mostSignificantAsHexString).isEqualTo(fakeMSB)
        assertThat(executionTime).isLessThan(MAX_EXEC_TIME_IN_NANOS)
    }

    // endregion

    // region leastSignificant64BitsAsDecimal

    @Test
    fun `M extract unsigned bits W leastSignificant64BitsAsDecimal { max value }`() {
        // Given
        val traceId = BigInteger(fakeIrrelevantHexString + MAX_UNSIGNED_LONG_HEX_STRING, HEX_RADIX)

        // When
        var leastSignificant64BitsAsDecimal: String
        val executionTime = measureNanoTime {
            leastSignificant64BitsAsDecimal =
                BigIntegerUtils.leastSignificant64BitsAsDecimal(traceId)
        }

        // Then
        assertThat(leastSignificant64BitsAsDecimal).isEqualTo(MAX_UNSIGNED_LONG_DEC_STRING)
        assertThat(executionTime).isLessThan(MAX_EXEC_TIME_IN_NANOS)
    }

    @Test
    fun `M use unsigned bits W leastSignificant64BitsAsDecimal { min low order trace id }`() {
        // Given
        val traceId = BigInteger(fakeIrrelevantHexString + MIN_UNSIGNED_LONG_HEX_STRING, HEX_RADIX)

        // When
        var leastSignificant64BitsAsDecimal: String
        val executionTime = measureNanoTime {
            leastSignificant64BitsAsDecimal =
                BigIntegerUtils.leastSignificant64BitsAsDecimal(traceId)
        }

        // Then
        assertThat(leastSignificant64BitsAsDecimal).isEqualTo(MIN_UNSIGNED_LONG_DEC_STRING)
        assertThat(executionTime).isLessThan(MAX_EXEC_TIME_IN_NANOS)
    }

    @RepeatedTest(8)
    fun `M extract unsigned bits W leastSignificant64BitsAsDecimal`(
        @LongForgery(min = 0) fakeLSB: Long
    ) {
        // Given
        val traceId = BigInteger(fakeIrrelevantHexString + fakeLSB.toString(HEX_RADIX).padStart(16, '0'), HEX_RADIX)

        // When
        var leastSignificant64BitsAsDecimal: String
        val executionTime = measureNanoTime {
            leastSignificant64BitsAsDecimal = BigIntegerUtils.leastSignificant64BitsAsDecimal(traceId)
        }

        // Then
        assertThat(leastSignificant64BitsAsDecimal).isEqualTo(fakeLSB.toString())
        assertThat(executionTime).isLessThan(MAX_EXEC_TIME_IN_NANOS)
    }

    // endregion

    companion object {

        private const val REGEX_16_CHAR_HEX_NUMBER = "[a-f0-9]{16}"
        private const val HEX_RADIX = 16

        private const val MAX_UNSIGNED_LONG_HEX_STRING = "ffffffffffffffff"
        private const val MIN_UNSIGNED_LONG_HEX_STRING = "0000000000000000"
        private const val MAX_UNSIGNED_LONG_DEC_STRING = "18446744073709551615"
        private const val MIN_UNSIGNED_LONG_DEC_STRING = "0"

        private val MAX_EXEC_TIME_IN_NANOS = TimeUnit.MILLISECONDS.toNanos(8)
    }
}
