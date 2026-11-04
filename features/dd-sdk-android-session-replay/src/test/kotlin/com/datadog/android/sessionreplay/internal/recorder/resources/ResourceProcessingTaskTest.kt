/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.threads.createBackPressureExecutorService
import com.datadog.android.core.configuration.BackPressureMitigation
import com.datadog.android.core.configuration.BackPressureStrategy
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
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
    @Mock lateinit var mockLogger: InternalLogger

    @Test
    fun `M complete failed work once W run { action throws }`(@StringForgery fakeMessage: String) {
        // Given
        var fakeCancelled = 0
        var fakeFailed = 0
        val fakeFailure = IllegalStateException(fakeMessage)
        val testedTask = ResourceProcessingTask(
            cancellation = null,
            onCancelled = { fakeCancelled++ },
            onFailure = { fakeFailed++ },
            action = { throw fakeFailure }
        )

        // When
        val actualFailure = assertThrows<IllegalStateException> { testedTask.run() }
        testedTask.run()

        // Then
        assertThat(actualFailure).isSameAs(fakeFailure)
        assertThat(fakeFailed).isEqualTo(1)
        assertThat(fakeCancelled).isZero()
    }

    @ParameterizedTest
    @EnumSource(CancellationTiming::class)
    fun `M remove cancelled tasks W execute { cancellation races with submission }`(timing: CancellationTiming) {
        // Given
        val fakeStarted = CountDownLatch(1)
        val fakeRelease = CountDownLatch(1)
        val fakeQueue = LinkedBlockingQueue<Runnable>()
        var fakeBeforeSubmit: (() -> Unit)? = null
        var fakeAfterSubmit: (() -> Unit)? = null
        var fakeCancelled = 0
        var fakeFailed = 0
        var fakeExecuted = 0
        val testedExecutor = object : ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, fakeQueue) {
            override fun execute(command: Runnable) {
                fakeBeforeSubmit?.invoke()
                super.execute(command)
                fakeAfterSubmit?.invoke()
            }
        }
        testedExecutor.execute {
            fakeStarted.countDown()
            check(fakeRelease.await(30, TimeUnit.SECONDS))
        }
        check(fakeStarted.await(5, TimeUnit.SECONDS))

        // When
        try {
            repeat(1000) {
                val fakeCancellation = ResourceCancellation()
                fakeBeforeSubmit = if (timing == CancellationTiming.BEFORE_ENQUEUE) {
                    { fakeCancellation.cancel() }
                } else {
                    null
                }
                fakeAfterSubmit = if (timing == CancellationTiming.AFTER_ENQUEUE) {
                    { fakeCancellation.cancel() }
                } else {
                    null
                }
                ResourceProcessingTask(
                    cancellation = fakeCancellation,
                    onCancelled = { fakeCancelled++ },
                    onFailure = { fakeFailed++ },
                    action = { fakeExecuted++ }
                ).execute(testedExecutor, mockLogger)
                fakeCancellation.cancel()
            }

            // Then
            assertThat(fakeQueue).isEmpty()
            assertThat(fakeCancelled).isEqualTo(1000)
            assertThat(fakeFailed).isZero()
            assertThat(fakeExecuted).isZero()
        } finally {
            fakeRelease.countDown()
            testedExecutor.shutdown()
            check(testedExecutor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @ParameterizedTest
    @EnumSource(BackPressureMitigation::class)
    fun `M recover admission W execute { SDK queue is full }`(
        mitigation: BackPressureMitigation,
        @StringForgery fakeKey: String
    ) {
        // Given
        val fakeStarted = CountDownLatch(1)
        val fakeRelease = CountDownLatch(1)
        val fakeFinished = AtomicInteger()
        val fakeCancellation = ResourceCancellation()
        val testedRequests = PendingResourceRequests(mockLogger)
        val stubCallback = object : ResourceResolverCallback {
            override val cancellation = fakeCancellation
            override fun onFailure() { fakeFinished.incrementAndGet() }
            override fun onSuccess(resourceId: String) { fakeFinished.incrementAndGet() }
        }
        val testedExecutor = createBackPressureExecutorService(
            logger = mockLogger,
            executorContext = fakeKey,
            backpressureStrategy = BackPressureStrategy(1, {}, {}, mitigation),
            timeProvider = mock<TimeProvider>()
        )
        testedRequests.resolve(fakeKey, 1, stubCallback) { request ->
            ResourceProcessingTask(
                cancellation = request.cancellation,
                onCancelled = { request.onFailure() },
                onFailure = { request.onFailure() },
                action = {
                    fakeStarted.countDown()
                    check(fakeRelease.await(30, TimeUnit.SECONDS))
                    request.onSuccess(fakeKey)
                }
            ).execute(testedExecutor, mockLogger)
        }
        check(fakeStarted.await(5, TimeUnit.SECONDS))

        // When
        try {
            repeat(2) { index ->
                testedRequests.resolve(fakeKey + index, 1, stubCallback) { request ->
                    ResourceProcessingTask(
                        cancellation = request.cancellation,
                        onCancelled = { request.onFailure() },
                        onFailure = { request.onFailure() },
                        action = { request.onSuccess(fakeKey) }
                    ).execute(testedExecutor, mockLogger)
                }
            }
            fakeRelease.countDown()
            testedExecutor.shutdown()
            check(testedExecutor.awaitTermination(5, TimeUnit.SECONDS))
            val fakeCompletedBeforeRetry = fakeFinished.get()
            var fakeRetryAccepted = false
            testedRequests.resolve(
                fakeKey + "retry",
                PendingResourceRequests.MAX_PENDING_BYTES,
                stubCallback
            ) { request ->
                fakeRetryAccepted = true
                request.onFailure()
            }

            // Then
            assertSoftly { softly ->
                softly.assertThat(fakeCompletedBeforeRetry).isEqualTo(3)
                softly.assertThat(fakeRetryAccepted).isTrue()
            }
        } finally {
            fakeCancellation.cancel()
            fakeRelease.countDown()
            testedExecutor.shutdownNow()
        }
    }

    internal enum class CancellationTiming { BEFORE_ENQUEUE, AFTER_ENQUEUE, AFTER_SUBMISSION }
}
