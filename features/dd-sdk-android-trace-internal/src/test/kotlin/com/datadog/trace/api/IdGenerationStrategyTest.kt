/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.trace.api

import com.datadog.utils.forge.Configurator
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.quality.Strictness
import java.security.SecureRandom

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class IdGenerationStrategyTest {

    // Please note that these tests were just ported from the Groovy CoreTracer tests in APM java code

    @ParameterizedTest
    @ValueSource(strings = ["RANDOM", "SEQUENTIAL", "SECURE_RANDOM"])
    fun `M generate id with strategyName and tIdSize bits`(strategyName: String) {
        val isTid128 = listOf(false, true)
        val highOrderLowerBound = (System.currentTimeMillis() / 1000).shl(32)
        isTid128.forEach { tId128b ->
            val strategy = IdGenerationStrategy.fromName(strategyName, tId128b)
            val traceIds = (0..4096).map { strategy.generateTraceId() }
            val checked = HashSet<DDTraceId>()

            traceIds.forEach { traceId ->
                assertThat(traceId).isNotNull
                assertThat(traceId).isNotEqualTo("foo")
                assertThat(traceId).isNotEqualTo(DDTraceId.ZERO)
                assertThat(traceId).isEqualTo(traceId)
                val hashCode =
                    (
                        traceId.toHighOrderLong() xor (traceId.toHighOrderLong() ushr 32)
                            xor traceId.toLong() xor (traceId.toLong() ushr 32)
                        ).toInt()
                assertThat(hashCode).isEqualTo(traceId.hashCode())
                assertThat(checked).doesNotContain(traceId)
                if (tId128b && strategyName != "SEQUENTIAL") {
                    val highOrderUpperBound = (System.currentTimeMillis() / 1000).shl(32)
                    val lowOrderUpperBound = Long.MAX_VALUE
                    assertThat(traceId.toLong()).isLessThanOrEqualTo(lowOrderUpperBound)
                    assertThat(traceId.toHighOrderLong()).isLessThanOrEqualTo(highOrderUpperBound)
                    assertThat(traceId.toLong()).isGreaterThan(0)
                    assertThat(traceId.toHighOrderLong()).isGreaterThanOrEqualTo(highOrderLowerBound)
                }
                checked.add(traceId)
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["SOME", "UNKNOWN", "STRATEGIES"])
    fun `M return null for non existing strategy strategyName`(strategyName: String) {
        val strategy = IdGenerationStrategy.fromName(strategyName)
        assertThat(strategy).isNull()
    }

    @Test
    fun `exception created on SecureRandom strategy`() {
        // Given
        val illegalArgumentException = IllegalArgumentException("SecureRandom init exception")
        val provider: IdGenerationStrategy.ThrowingSupplier<SecureRandom> = mock {
            on { get() }.thenThrow(illegalArgumentException)
        }

        // Then
        assertThatThrownBy { IdGenerationStrategy.SRandom(true, provider) }
            .isInstanceOf(ExceptionInInitializerError::class.java)
            .hasCause(illegalArgumentException)
    }
}
