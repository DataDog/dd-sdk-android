/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.configuration.BackPressureMitigation
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Extensions(ExtendWith(MockitoExtension::class), ExtendWith(ForgeExtension::class))
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class ResourceProcessingTaskTest {

    @Mock
    lateinit var mockLogger: InternalLogger

    @Test
    fun `M finish every capture W execute() { SDK queue is full }`(forge: Forge) {
        // Given
        val fakeStarted = CountDownLatch(1)
        val fakeRelease = CountDownLatch(1)
        val fakeFinished = AtomicInteger()
        val fakeCancelled = AtomicInteger()
        val testedExecutor = createDroppingExecutor(forge.getForgery())
        try {
            countingTask(fakeFinished, fakeCancelled) {
                fakeStarted.countDown()
                check(fakeRelease.await(30, TimeUnit.SECONDS))
            }.execute(testedExecutor, mockLogger)
            check(fakeStarted.await(5, TimeUnit.SECONDS))
            countingTask(fakeFinished, fakeCancelled).execute(testedExecutor, mockLogger)

            // When
            countingTask(fakeFinished, fakeCancelled).execute(testedExecutor, mockLogger)
            fakeRelease.countDown()
            testedExecutor.shutdown()
            check(testedExecutor.awaitTermination(5, TimeUnit.SECONDS))

            // Then
            assertSoftly { softly ->
                softly.assertThat(fakeFinished.get()).isEqualTo(2)
                softly.assertThat(fakeCancelled.get()).isEqualTo(1)
            }
        } finally {
            fakeRelease.countDown()
            testedExecutor.shutdownNow()
        }
    }

    private fun countingTask(
        finished: AtomicInteger,
        cancelled: AtomicInteger,
        work: () -> Unit = {}
    ) = ResourceProcessingTask(
        onCancelled = { cancelled.incrementAndGet() },
        onFailure = { finished.incrementAndGet() },
        action = {
            work()
            finished.incrementAndGet()
        }
    )

    // Like the SDK's backpressured queues, a full queue drops a task silently instead of rejecting it.
    private fun createDroppingExecutor(mitigation: BackPressureMitigation): ThreadPoolExecutor {
        val queue = object : LinkedBlockingQueue<Runnable>(1) {
            override fun offer(e: Runnable): Boolean {
                if (remainingCapacity() > 0) return super.offer(e)
                return when (mitigation) {
                    BackPressureMitigation.DROP_OLDEST -> {
                        poll()
                        super.offer(e)
                    }
                    BackPressureMitigation.IGNORE_NEWEST -> true
                }
            }
        }
        return ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, queue)
    }
}
