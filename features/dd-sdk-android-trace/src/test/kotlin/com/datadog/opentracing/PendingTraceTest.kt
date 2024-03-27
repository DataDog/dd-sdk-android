/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.opentracing

import com.datadog.android.utils.forge.Configurator
import com.datadog.legacy.trace.api.sampling.PrioritySampling
import com.datadog.tools.unit.createInstance
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.math.BigInteger
import java.util.ConcurrentModificationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(value = Configurator::class)
class PendingTraceTest {

    @Test
    fun `PendingTrace is thread safe`(forge: Forge) {
        // Given
        val mockTracer: DDTracer = mock()
        whenever(mockTracer.partialFlushMinSpans) doReturn 1
        val fakeTraceId = BigInteger.valueOf(forge.aLong())
        val pendingTrace =
            createInstance(PendingTrace::class.java, mockTracer, fakeTraceId)
        val rootSpan = forge.fakeSpan(pendingTrace, mockTracer, fakeTraceId, BigInteger.ZERO, "rootSpan")
        val countDownLatch = CountDownLatch(CONCURRENT_THREAD)

        // When
        val runnables = Array(CONCURRENT_THREAD) {
            StressTestRunnable(pendingTrace, mockTracer, rootSpan, forge, countDownLatch)
        }
        runnables.forEach { Thread(it).start() }
        countDownLatch.await(20, TimeUnit.SECONDS)

        // Then
        assertThat(countDownLatch.count).isZero()
        runnables.forEach {
            assertThat(it.cme).isNull()
        }
    }

    private class StressTestRunnable(
        val pendingTrace: PendingTrace,
        val tracer: DDTracer,
        val parentSpan: DDSpan,
        val forge: Forge,
        val countDownLatch: CountDownLatch
    ) : Runnable {

        var cme: ConcurrentModificationException? = null

        override fun run() {
            try {
                for (i in 0..10000) {
                    val span = forge.fakeSpan(
                        pendingTrace,
                        tracer,
                        parentSpan.traceId,
                        parentSpan.spanId,
                        "childSpan_$i"
                    )
                    pendingTrace.registerSpan(span)
                    span.finish()
                }
            } catch (e: ConcurrentModificationException) {
                cme = e
            }
            countDownLatch.countDown()
        }
    }

    companion object {
        const val CONCURRENT_THREAD = 4
    }
}

private fun Forge.fakeSpan(
    pendingTrace: PendingTrace,
    tracer: DDTracer,
    traceId: BigInteger,
    parentSpanId: BigInteger,
    operationName: String
): DDSpan {
    val ddSpanContext = DDSpanContext(
        traceId,
        BigInteger.valueOf(aLong()),
        parentSpanId,
        anAlphabeticalString(), // service
        operationName,
        anAlphabeticalString(), // resourceName,
        PrioritySampling.SAMPLER_KEEP,
        anAlphabeticalString(), // origin
        emptyMap(), // baggageItems
        aBool(), // errorFlag
        anAlphabeticalString(), // spanType
        emptyMap(), // tags
        pendingTrace,
        tracer,
        emptyMap() // serviceNameMappings
    )

    return DDSpan(aLong(), ddSpanContext)
}
