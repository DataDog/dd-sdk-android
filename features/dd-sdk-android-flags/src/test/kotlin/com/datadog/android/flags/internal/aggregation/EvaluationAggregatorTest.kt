/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.aggregation

import com.datadog.android.flags.model.ErrorCode
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.ResolutionReason
import com.datadog.android.flags.utils.forge.ForgeConfigurator
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(ForgeConfigurator::class)
internal class EvaluationAggregatorTest {

    @StringForgery
    lateinit var fakeFlagKey: String

    @StringForgery
    lateinit var fakeTargetingKey: String

    @StringForgery
    lateinit var fakeVariantKey: String

    @LongForgery(min = 1000000L)
    var fakeTimestamp = 0L

    private lateinit var fakeContext: EvaluationContext
    private lateinit var testedAggregator: EvaluationAggregator

    @BeforeEach
    fun `set up`() {
        fakeContext = EvaluationContext(targetingKey = fakeTargetingKey)
        testedAggregator = EvaluationAggregator(maxAggregations = 100)
    }

    // region record - threshold

    @Test
    fun `M return false W record() { below threshold }`() {
        val result = record()

        assertThat(result).isFalse()
    }

    @Test
    fun `M return true W record() { at threshold }`() {
        val aggregator = EvaluationAggregator(maxAggregations = 5)
        var lastResult = false

        repeat(5) { index ->
            lastResult = aggregator.record(
                timestamp = fakeTimestamp,
                flagKey = "flag-$index",
                context = fakeContext,
                service = null,
                rumApplicationId = null,
                rumViewName = null,
                variantKey = fakeVariantKey,
                allocationKey = null,
                reason = ResolutionReason.TARGETING_MATCH.name,
                errorCode = null,
                errorMessage = null
            )
        }

        assertThat(lastResult).isTrue()
    }

    // endregion

    // region record - aggregation

    @Test
    fun `M aggregate same key W record() { multiple calls }`() {
        repeat(10) { index ->
            record(timestamp = fakeTimestamp + index)
        }

        val events = testedAggregator.drain()
        assertThat(events).hasSize(1)
        assertThat(events.first().evaluationCount).isEqualTo(10L)
    }

    @Test
    fun `M create separate entries W record() { different flag keys }`() {
        repeat(5) { index ->
            record(flagKey = "flag-$index")
        }

        assertThat(testedAggregator.drain()).hasSize(5)
    }

    @Test
    fun `M create separate entries W record() { different targeting keys }`() {
        repeat(5) { index ->
            record(context = EvaluationContext(targetingKey = "user-$index"))
        }

        assertThat(testedAggregator.drain()).hasSize(5)
    }

    @Test
    fun `M create separate entries W record() { different variant keys }`() {
        repeat(5) { index ->
            record(variantKey = "variant-$index")
        }

        assertThat(testedAggregator.drain()).hasSize(5)
    }

    @Test
    fun `M create separate entries W record() { different error codes }`() {
        val errorCodes = listOf(
            ErrorCode.FLAG_NOT_FOUND.name,
            ErrorCode.PROVIDER_NOT_READY.name,
            ErrorCode.TYPE_MISMATCH.name
        )

        errorCodes.forEach { code ->
            record(errorCode = code, errorMessage = "msg")
        }

        assertThat(testedAggregator.drain()).hasSize(3)
    }

    @Test
    fun `M aggregate by error code W record() { same code different messages }`() {
        val errorCode = ErrorCode.TYPE_MISMATCH.name

        listOf("msg1", "msg2", "msg3").forEach { msg ->
            record(errorCode = errorCode, errorMessage = msg)
        }

        val events = testedAggregator.drain()
        assertThat(events).hasSize(1)
        assertThat(events.first().evaluationCount).isEqualTo(3L)
        assertThat(events.first().error?.message).isEqualTo("msg3")
    }

    @Test
    fun `M track timestamps W record() { first and last }`() {
        val timestamps = listOf(fakeTimestamp, fakeTimestamp + 1000, fakeTimestamp + 2000)

        timestamps.forEach { ts ->
            record(timestamp = ts)
        }

        val event = testedAggregator.drain().first()
        assertThat(event.firstEvaluation).isEqualTo(timestamps.first())
        assertThat(event.lastEvaluation).isEqualTo(timestamps.last())
    }

    // endregion

    // region drain

    @Test
    fun `M return empty list W drain() { no records }`() {
        assertThat(testedAggregator.drain()).isEmpty()
    }

    @Test
    fun `M clear internal state W drain()`() {
        record()

        val firstDrain = testedAggregator.drain()
        val secondDrain = testedAggregator.drain()

        assertThat(firstDrain).hasSize(1)
        assertThat(secondDrain).isEmpty()
    }

    @Test
    fun `M return all events W drain()`() {
        repeat(10) { index ->
            record(flagKey = "flag-$index")
        }

        val events = testedAggregator.drain()

        assertThat(events).hasSize(10)
        assertThat(events.map { it.flag.key }).containsExactlyInAnyOrder(
            "flag-0", "flag-1", "flag-2", "flag-3", "flag-4",
            "flag-5", "flag-6", "flag-7", "flag-8", "flag-9"
        )
    }

    // endregion

    // region Thread Safety

    @Test
    fun `M aggregate all W record() { concurrent same key }`() {
        val threadCount = 10
        val recordsPerThread = 100

        runConcurrently(threadCount) {
            repeat(recordsPerThread) { index ->
                record(timestamp = fakeTimestamp + index)
            }
        }

        val events = testedAggregator.drain()
        assertThat(events).hasSize(1)
        assertThat(events.first().evaluationCount).isEqualTo((threadCount * recordsPerThread).toLong())
    }

    @Test
    fun `M create all entries W record() { concurrent different keys }`() {
        val threadCount = 10
        val keysPerThread = 50

        runConcurrently(threadCount) { threadId ->
            repeat(keysPerThread) { index ->
                record(flagKey = "thread-$threadId-flag-$index")
            }
        }

        assertThat(testedAggregator.drain()).hasSize(threadCount * keysPerThread)
    }

    @Test
    fun `M not lose records W drain() { concurrent with record }`() {
        val recordThreads = 5
        val recordsPerThread = 100
        val drainCount = 10
        val expectedTotal = recordThreads * recordsPerThread

        val startLatch = CountDownLatch(1)
        val finishLatch = CountDownLatch(recordThreads + 1)
        val allDrained = CopyOnWriteArrayList<Long>()

        val recorders = (1..recordThreads).map { threadId ->
            Thread {
                startLatch.await()
                repeat(recordsPerThread) { index ->
                    record(flagKey = "thread-$threadId-flag-$index")
                }
                finishLatch.countDown()
            }
        }

        val drainer = Thread {
            startLatch.await()
            repeat(drainCount) {
                Thread.sleep(1)
                allDrained.addAll(testedAggregator.drain().map { it.evaluationCount })
            }
            finishLatch.countDown()
        }

        recorders.forEach { it.start() }
        drainer.start()
        startLatch.countDown()
        finishLatch.await()

        allDrained.addAll(testedAggregator.drain().map { it.evaluationCount })

        assertThat(allDrained.sum()).isEqualTo(expectedTotal.toLong())
    }

    // endregion

    // region Helpers

    private fun record(
        timestamp: Long = fakeTimestamp,
        flagKey: String = fakeFlagKey,
        context: EvaluationContext = fakeContext,
        variantKey: String? = fakeVariantKey,
        errorCode: String? = null,
        errorMessage: String? = null
    ): Boolean {
        return testedAggregator.record(
            timestamp = timestamp,
            flagKey = flagKey,
            context = context,
            service = null,
            rumApplicationId = null,
            rumViewName = null,
            variantKey = variantKey,
            allocationKey = null,
            reason = ResolutionReason.TARGETING_MATCH.name,
            errorCode = errorCode,
            errorMessage = errorMessage
        )
    }

    private fun runConcurrently(threadCount: Int, action: (Int) -> Unit) {
        val startLatch = CountDownLatch(1)
        val finishLatch = CountDownLatch(threadCount)

        val threads = (1..threadCount).map { index ->
            Thread {
                startLatch.await()
                action(index)
                finishLatch.countDown()
            }
        }

        threads.forEach { it.start() }
        startLatch.countDown()
        finishLatch.await()
    }

    // endregion
}
