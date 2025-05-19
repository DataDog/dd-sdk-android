/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.opentracing

import com.datadog.android.api.InternalLogger
import com.datadog.opentracing.PendingTrace.SpanCleaner
import com.datadog.tools.unit.createInstance
import com.datadog.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.quality.Strictness
import java.math.BigInteger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(value = Configurator::class)
internal class SpanCleanerTest {

    private var testedCleaner: SpanCleaner = SpanCleaner()

    // region cleanup

    @Test
    fun `M remove random traces W add and remove`(forge: Forge) {
        // Given
        val pendingTraces = forge.aList(size = forge.anInt(min = 10, max = 50)) { mock<PendingTrace>() }
        pendingTraces.forEach {
            testedCleaner.addPendingTrace(it)
        }
        Assumptions.assumeTrue(testedCleaner.pendingTraces.values.size == pendingTraces.size)
        Assumptions.assumeTrue(testedCleaner.pendingTraces.values.containsAll(pendingTraces))

        // When
        pendingTraces.shuffled().forEach {
            testedCleaner.removePendingTrace(it)
        }

        // Then
        assertThat(testedCleaner.pendingTraces).isEmpty()
    }

    @Test
    fun `M cleanup random traces W add and close`(forge: Forge) {
        // Given
        val pendingTraces = forge.aList(size = forge.anInt(min = 10, max = 50)) { mock<PendingTrace>() }
        pendingTraces.forEach {
            testedCleaner.addPendingTrace(it)
        }
        assertThat(testedCleaner.pendingTraces.values).containsExactlyInAnyOrderElementsOf(pendingTraces)

        // When
        testedCleaner.close()

        // Then
        pendingTraces.forEach {
            verify(it).clean()
        }
    }

    // endregion

    // region `equals` contract

    @Test
    fun `M contain only one entry W add same PendingTrace twice`(forge: Forge) {
        // Given
        val pendingTrace = mockPendingTrace(forge)
        testedCleaner.addPendingTrace(pendingTrace)
        testedCleaner.addPendingTrace(pendingTrace)

        // Then
        assertThat(testedCleaner.pendingTraces).hasSize(1)
        assertThat(testedCleaner.pendingTraces.values).containsExactly(pendingTrace)
    }

    @Test
    fun `M contain only one entry W add {same PendingTrace mutated, twice}`(forge: Forge) {
        // Given
        val pendingTrace = mockPendingTrace(forge)
        testedCleaner.addPendingTrace(pendingTrace)
        pendingTrace.addSpan(mock())
        pendingTrace.addSpan(mock())
        testedCleaner.addPendingTrace(pendingTrace)

        // Then
        assertThat(testedCleaner.pendingTraces).hasSize(1)
        assertThat(testedCleaner.pendingTraces.values).containsExactly(pendingTrace)

        // When
        testedCleaner.removePendingTrace(pendingTrace)

        // Then
        assertThat(testedCleaner.pendingTraces).isEmpty()
    }

    @Test
    fun `M contain 2 entries W add {2 PendingTrace instances with same root span}`(forge: Forge) {
        // Given
        val pendingTrace1 = mockPendingTrace(forge)
        val pendingTrace2 = mockPendingTrace(forge)
        val mock = mock<DDSpan>()
        pendingTrace1.addSpan(mock)
        pendingTrace2.addSpan(mock)
        testedCleaner.addPendingTrace(pendingTrace1)
        testedCleaner.addPendingTrace(pendingTrace2)

        // Then
        assertThat(testedCleaner.pendingTraces).hasSize(2)
        assertThat(testedCleaner.pendingTraces.values).containsExactlyInAnyOrder(pendingTrace1, pendingTrace2)

        // When
        testedCleaner.removePendingTrace(pendingTrace1)
        testedCleaner.removePendingTrace(pendingTrace2)

        // Then
        assertThat(testedCleaner.pendingTraces).isEmpty()
    }

    // endregion

    // region concurrency

    @Test
    fun `M not leak any PendingTrace W add, mutate and remove { different threads }`(forge: Forge) {
        // Given
        val pendingTraces = forge.aList(size = forge.anInt(min = 10, max = 50)) { mockPendingTrace(forge) }
        val addCountDownLatch = CountDownLatch(pendingTraces.size)
        val removeCountDownLatch = CountDownLatch(pendingTraces.size)
        val half = pendingTraces.size / 2
        pendingTraces.take(half).forEach { pendingTrace ->
            Thread {
                testedCleaner.addPendingTrace(pendingTrace)
                repeat(forge.anInt(min = 1, max = 10)) {
                    pendingTrace.addSpan(mock())
                }
                addCountDownLatch.countDown()
            }.start()
        }
        pendingTraces.subList(half, pendingTraces.size).forEach { pendingTrace ->
            Thread {
                if (forge.aBool()) {
                    repeat(forge.anInt(min = 1, max = 10)) {
                        pendingTrace.addSpan(mock())
                    }
                }
                testedCleaner.addPendingTrace(pendingTrace)
                repeat(forge.anInt(min = 1, max = 10)) {
                    pendingTrace.addSpan(mock())
                }
                addCountDownLatch.countDown()
            }.start()
        }
        addCountDownLatch.await(3, TimeUnit.SECONDS)

        // When
        pendingTraces.forEach {
            Thread {
                testedCleaner.removePendingTrace(it)
                removeCountDownLatch.countDown()
            }.start()
        }
        removeCountDownLatch.await(3, TimeUnit.SECONDS)

        // Then
        assertThat(testedCleaner.pendingTraces).isEmpty()
    }

    // endregion

    // region Internal

    private fun mockPendingTrace(forge: Forge): PendingTrace {
        val mockTracer: DDTracer = mock()
        val mockInternalLogger: InternalLogger = mock()
        val fakeTraceId = BigInteger.valueOf(forge.aLong())
        return createInstance(
            PendingTrace::class.java,
            mockTracer,
            fakeTraceId,
            mockInternalLogger
        )
    }

    // endregion
}
