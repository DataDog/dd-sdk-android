/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.flags.internal.aggregation.EvaluationAggregationStats
import com.datadog.android.flags.internal.aggregation.EvaluationAggregator
import com.datadog.android.flags.internal.storage.EvaluationEventRecordWriter
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.internal.time.TimeProvider
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@Tag("FormalMethods")
internal class FormalMethodsEvaluationDiagnosticsTest {

    @Test
    fun `known counterexample - concurrent flush returns while preceding write is active`() {
        val writeStarted = CountDownLatch(1)
        val releaseWrite = CountDownLatch(1)
        val writeActive = AtomicBoolean(false)
        val writer = object : EvaluationEventWriter {
            override fun writeAll(events: List<EvaluationAggregationStats>) {
                writeActive.set(true)
                writeStarted.countDown()
                releaseWrite.await(5, TimeUnit.SECONDS)
                writeActive.set(false)
            }
        }
        val processor = processor(writer)
        recordOne(processor)
        val firstFlush = Thread(processor::flush)

        firstFlush.start()
        assertThat(writeStarted.await(5, TimeUnit.SECONDS)).isTrue()

        processor.flush()

        assertThat(writeActive.get())
            .describedAs("the second flush returned before the preceding write completed")
            .isTrue()

        releaseWrite.countDown()
        firstFlush.join(5_000)
        assertThat(firstFlush.isAlive).isFalse()
    }

    @Test
    fun `known counterexample - failed write leaves drained evaluations without retry ownership`() {
        val writeAttempts = AtomicInteger()
        val writer = object : EvaluationEventWriter {
            override fun writeAll(events: List<EvaluationAggregationStats>) {
                writeAttempts.incrementAndGet()
                throw IOException("persistence failure")
            }
        }
        val processor = processor(writer)
        recordOne(processor)

        assertThatThrownBy(processor::flush).isInstanceOf(IOException::class.java)

        processor.flush()
        assertThat(writeAttempts.get())
            .describedAs("a later flush has no ownership of the batch drained before the failure")
            .isEqualTo(1)
    }

    @Test
    fun `known counterexample - stop returns while threshold-triggered write is active`() {
        val writeStarted = CountDownLatch(1)
        val releaseWrite = CountDownLatch(1)
        val writeActive = AtomicBoolean(false)
        val writer = object : EvaluationEventWriter {
            override fun writeAll(events: List<EvaluationAggregationStats>) {
                writeActive.set(true)
                writeStarted.countDown()
                releaseWrite.await(5, TimeUnit.SECONDS)
                writeActive.set(false)
            }
        }
        val processor = processor(writer, maxAggregations = 1)
        val producer = Thread { recordOne(processor) }

        producer.start()
        assertThat(writeStarted.await(5, TimeUnit.SECONDS)).isTrue()

        processor.stop()

        assertThat(writeActive.get())
            .describedAs("stop returned while an accepted evaluation was still being persisted")
            .isTrue()

        releaseWrite.countDown()
        producer.join(5_000)
        assertThat(producer.isAlive).isFalse()
    }

    @Test
    fun `known counterexample - processor accepts evaluations after stop`() {
        val written = AtomicInteger()
        val writer = object : EvaluationEventWriter {
            override fun writeAll(events: List<EvaluationAggregationStats>) {
                written.addAndGet(events.sumOf { it.count })
            }
        }
        val processor = processor(writer)

        processor.stop()
        recordOne(processor)

        assertThat(written.get()).isZero()
        processor.flush()
        assertThat(written.get())
            .describedAs("the stopped processor retained and later wrote post-stop work")
            .isEqualTo(1)
    }

    @Test
    fun `known counterexample - missing feature silently consumes drained evaluations`() {
        val sdkCore = mock<FeatureSdkCore>()
        val processor = processor(EvaluationEventRecordWriter(sdkCore))
        recordOne(processor)

        processor.flush()
        processor.flush()

        verify(sdkCore, times(1)).getFeature(Feature.FLAGS_EVALUATIONS_FEATURE_NAME)
    }

    private fun processor(
        writer: EvaluationEventWriter,
        maxAggregations: Int = 100
    ): EvaluationEventsProcessor {
        val executor = mock<ScheduledExecutorService> {
            on { awaitTermination(any(), any()) } doReturn true
        }
        return EvaluationEventsProcessor(
            writer = writer,
            timeProvider = mock<TimeProvider> {
                on { getDeviceTimestampMillis() } doReturn 1L
            },
            scheduledExecutor = executor,
            internalLogger = mock<InternalLogger>(),
            flushIntervalMs = 60_000,
            aggregator = EvaluationAggregator(maxAggregations),
            periodicFlushEnabled = false
        )
    }

    private fun recordOne(processor: EvaluationEventsProcessor) {
        processor.processEvaluation(
            flagKey = "formal-methods-flag",
            context = EvaluationContext(targetingKey = "formal-methods-user"),
            rumApplicationId = null,
            rumViewName = null,
            variantKey = "control",
            allocationKey = null,
            errorCode = null,
            errorMessage = null
        )
    }
}
