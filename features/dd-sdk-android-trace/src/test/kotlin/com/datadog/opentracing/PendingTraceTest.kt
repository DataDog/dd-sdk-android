/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.opentracing

import com.datadog.android.utils.forge.Configurator
import com.datadog.trace.api.sampling.PrioritySampling
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
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

    @Mock
    lateinit var mockTracer: DDTracer

    lateinit var fakeTraceId: BigInteger

    @Test
    fun `PendingTrace is thread safe`(forge: Forge) {
        whenever(mockTracer.partialFlushMinSpans) doReturn 1
        fakeTraceId = BigInteger.valueOf(forge.aLong())
        val pendingTrace = PendingTrace(mockTracer, fakeTraceId)
        val rootSpan = forge.fakeSpan(pendingTrace, mockTracer, fakeTraceId, BigInteger.ZERO, "rootSpan")
        val countDownLatch = CountDownLatch(2)

        val runnables = Array(4) {
            StressTestRunnable(pendingTrace, mockTracer, rootSpan, forge, countDownLatch)
        }
        runnables.forEach { Thread(it).start() }

        countDownLatch.await(10, TimeUnit.SECONDS)
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
                    val span = forge.fakeSpan(pendingTrace,
                        tracer,
                        parentSpan.traceId,
                        parentSpan.spanId,
                        "childSpan_$i")
                    pendingTrace.registerSpan(span)
                    span.finish()
                }
            } catch (e: ConcurrentModificationException) {
                cme = e
            }
            countDownLatch.countDown()
        }
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
