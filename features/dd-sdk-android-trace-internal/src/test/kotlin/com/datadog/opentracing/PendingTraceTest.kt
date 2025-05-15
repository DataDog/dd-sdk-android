/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.opentracing

import com.datadog.android.api.InternalLogger
import com.datadog.legacy.trace.api.sampling.PrioritySampling
import com.datadog.opentracing.scopemanager.ContinuableScope.Continuation
import com.datadog.tools.unit.createInstance
import com.datadog.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
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

    @BeforeEach
    fun `set up`() {
        PendingTrace.initialize()
    }

    @AfterEach
    fun `tear down`() {
        PendingTrace.close()
    }

    @Test
    fun `PendingTrace is thread safe`(forge: Forge) {
        // Given
        val mockTracer: DDTracer = mock()
        val mockInternalLogger: InternalLogger = mock()
        whenever(mockTracer.partialFlushMinSpans) doReturn 1
        val fakeTraceId = BigInteger.valueOf(forge.aLong())
        val pendingTrace = createInstance(
            PendingTrace::class.java,
            mockTracer,
            fakeTraceId,
            mockInternalLogger
        )
        val rootSpan =
            forge.fakeSpan(pendingTrace, mockTracer, fakeTraceId, BigInteger.ZERO, "rootSpan", mockInternalLogger)
        val countDownLatch = CountDownLatch(CONCURRENT_THREAD)

        // When
        val runnables = Array(CONCURRENT_THREAD) {
            StressTestRunnable(pendingTrace, mockTracer, rootSpan, forge, countDownLatch, mockInternalLogger)
        }
        runnables.forEach { Thread(it).start() }
        countDownLatch.await(20, TimeUnit.SECONDS)

        // Then
        assertThat(countDownLatch.count).isZero()
        runnables.forEach {
            assertThat(it.cme).isNull()
        }
    }

    @Test
    fun `M not leak the PendingTraces W finish`(
        forge: Forge
    ) {
        // Given
        val mockTracer: DDTracer = mock()
        val mockInternalLogger: InternalLogger = mock()
        whenever(mockTracer.partialFlushMinSpans) doReturn 1

        repeat(forge.anInt(min = 10, max = 30)) {
            val fakeTraceId = BigInteger.valueOf(forge.aLong())

            val pendingTrace = createInstance(
                PendingTrace::class.java,
                mockTracer,
                fakeTraceId,
                mockInternalLogger
            )

            val rootSpan =
                forge.fakeSpan(
                    pendingTrace,
                    mockTracer,
                    fakeTraceId,
                    BigInteger.ZERO,
                    forge.anAlphabeticalString(),
                    mockInternalLogger
                )
            Thread.sleep(1)
            rootSpan.finish()
        }

        // When
        PendingTrace.getSpanCleaner().run()

        // Then
        assertThat(PendingTrace.getPendingTracesSize()).isZero()
    }

    @Test
    fun `M not leak the PendingTraces W finishSpan from different threads`(
        forge: Forge
    ) {
        // Given
        val mockTracer: DDTracer = mock()
        val mockInternalLogger: InternalLogger = mock()
        whenever(mockTracer.partialFlushMinSpans) doReturn 1
        val repeatCount = forge.anInt(min = 10, max = 30)
        val countDownLatch = CountDownLatch(repeatCount)
        repeat(repeatCount) {
            Thread {
                val fakeTraceId = BigInteger.valueOf(forge.aLong())

                val pendingTrace = createInstance(
                    PendingTrace::class.java,
                    mockTracer,
                    fakeTraceId,
                    mockInternalLogger
                )

                val rootSpan =
                    forge.fakeSpan(
                        pendingTrace,
                        mockTracer,
                        fakeTraceId,
                        BigInteger.ZERO,
                        forge.anAlphabeticalString(),
                        mockInternalLogger
                    )
                Thread.sleep(1)
                rootSpan.finish()
                countDownLatch.countDown()
            }.start()
        }
        countDownLatch.await(2, TimeUnit.SECONDS)

        // When
        Thread {
            PendingTrace.getSpanCleaner().run()
        }.apply {
            start()
            join(3)
        }

        // Then
        assertThat(PendingTrace.getPendingTracesSize()).isEqualTo(0)
    }

    @Test
    fun `M not leak the PendingTraces W drop`(
        forge: Forge
    ) {
        // Given
        val mockTracer: DDTracer = mock()
        val mockInternalLogger: InternalLogger = mock()
        whenever(mockTracer.partialFlushMinSpans) doReturn 1
        val repeatTimes = forge.anInt(min = 10, max = 30)
        repeat(repeatTimes) {
            val fakeTraceId = BigInteger.valueOf(forge.aLong())

            val pendingTrace = createInstance(
                PendingTrace::class.java,
                mockTracer,
                fakeTraceId,
                mockInternalLogger
            )

            val rootSpan =
                forge.fakeSpan(
                    pendingTrace,
                    mockTracer,
                    fakeTraceId,
                    BigInteger.ZERO,
                    forge.anAlphabeticalString(),
                    mockInternalLogger
                )
            Thread.sleep(1)
            rootSpan.drop()
        }

        // When
        PendingTrace.getSpanCleaner().run()

        // Then
        assertThat(PendingTrace.getPendingTracesSize()).isZero()
    }

    @Test
    fun `M write the PendingTrace W addParentSpan and drop some spans`(
        forge: Forge
    ) {
        // Given
        val mockTracer: DDTracer = mock()
        val mockInternalLogger: InternalLogger = mock()
        whenever(mockTracer.partialFlushMinSpans) doReturn 1
        val fakeParentTraceId = BigInteger.valueOf(forge.aLong())
        val pendingTrace = createInstance(
            PendingTrace::class.java,
            mockTracer,
            fakeParentTraceId,
            mockInternalLogger
        )
        val rootSpan =
            forge.fakeSpan(
                pendingTrace,
                mockTracer,
                fakeParentTraceId,
                BigInteger.ZERO,
                forge.anAlphabeticalString(),
                mockInternalLogger
            )
        val childSpan1 = DDSpan(forge.aPositiveLong(), rootSpan.context())
        val childSpan2 = DDSpan(forge.aPositiveLong(), rootSpan.context())

        // When
        childSpan1.drop()
        childSpan2.finish()
        rootSpan.finish()
        PendingTrace.getSpanCleaner().run()

        // Then
        assertThat(PendingTrace.getPendingTracesSize()).isZero()
        argumentCaptor<Collection<DDSpan>>().apply {
            verify(mockTracer).write(capture())
            assertThat(firstValue).hasSize(2)
            assertThat(firstValue).containsExactlyInAnyOrder(rootSpan, childSpan2)
        }
    }

    @Test
    fun `M write the PendingTrace W addParentSpan and drop the only child`(
        forge: Forge
    ) {
        // Given
        val mockTracer: DDTracer = mock()
        val mockInternalLogger: InternalLogger = mock()
        whenever(mockTracer.partialFlushMinSpans) doReturn 1
        val fakeParentTraceId = BigInteger.valueOf(forge.aLong())
        val pendingTrace = createInstance(
            PendingTrace::class.java,
            mockTracer,
            fakeParentTraceId,
            mockInternalLogger
        )
        val rootSpan =
            forge.fakeSpan(
                pendingTrace,
                mockTracer,
                fakeParentTraceId,
                BigInteger.ZERO,
                forge.anAlphabeticalString(),
                mockInternalLogger
            )
        val childSpan1 = DDSpan(forge.aPositiveLong(), rootSpan.context())

        // When
        childSpan1.drop()
        // add intermediary cleaner just to make sure we're testing this case
        PendingTrace.getSpanCleaner().run()
        rootSpan.finish()
        PendingTrace.getSpanCleaner().run()

        // Then
        assertThat(PendingTrace.getPendingTracesSize()).isZero()
        argumentCaptor<Collection<DDSpan>>().apply {
            verify(mockTracer).write(capture())
            assertThat(firstValue).hasSize(1)
            assertThat(firstValue).containsExactlyInAnyOrder(rootSpan)
        }
    }

    @Test
    fun `M not drop the PendingTrace W active continuable scope and dropSpan`(
        forge: Forge
    ) {
        // Given
        val mockTracer: DDTracer = mock()
        val mockInternalLogger: InternalLogger = mock()
        whenever(mockTracer.partialFlushMinSpans) doReturn 1
        val fakeParentTraceId = BigInteger.valueOf(forge.aLong())
        val pendingTrace = createInstance(
            PendingTrace::class.java,
            mockTracer,
            fakeParentTraceId,
            mockInternalLogger
        )
        val rootSpan =
            forge.fakeSpan(
                pendingTrace,
                mockTracer,
                fakeParentTraceId,
                BigInteger.ZERO,
                forge.anAlphabeticalString(),
                mockInternalLogger
            )
        val continuation: Continuation = mock()
        pendingTrace.registerContinuation(continuation)

        // When
        rootSpan.drop()
        PendingTrace.getSpanCleaner().run()

        // Then
        assertThat(PendingTrace.getPendingTracesSize()).isEqualTo(1)
    }

    @Test
    fun `M not drop the PendingTrace W continuable scope stopped and dropSpan`(
        forge: Forge
    ) {
        // Given
        val mockTracer: DDTracer = mock()
        val mockInternalLogger: InternalLogger = mock()
        whenever(mockTracer.partialFlushMinSpans) doReturn 1
        val fakeParentTraceId = BigInteger.valueOf(forge.aLong())
        val pendingTrace = createInstance(
            PendingTrace::class.java,
            mockTracer,
            fakeParentTraceId,
            mockInternalLogger
        )
        val rootSpan =
            forge.fakeSpan(
                pendingTrace,
                mockTracer,
                fakeParentTraceId,
                BigInteger.ZERO,
                forge.anAlphabeticalString(),
                mockInternalLogger
            )
        val continuation: Continuation = mock()
        pendingTrace.registerContinuation(continuation)

        // When
        pendingTrace.cancelContinuation(continuation)
        rootSpan.drop()

        // Then
        assertThat(PendingTrace.getPendingTracesSize()).isZero
    }

    private class StressTestRunnable(
        val pendingTrace: PendingTrace,
        val tracer: DDTracer,
        val parentSpan: DDSpan,
        val forge: Forge,
        val countDownLatch: CountDownLatch,
        val internalLogger: InternalLogger
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
                        "childSpan_$i",
                        internalLogger
                    )
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
    operationName: String,
    internalLogger: InternalLogger
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
        emptyMap(), // serviceNameMappings
        internalLogger
    )

    return DDSpan(aPositiveLong(), ddSpanContext)
}
