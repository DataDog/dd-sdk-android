/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.ExecutorService

@Extensions(ExtendWith(MockitoExtension::class), ExtendWith(ForgeExtension::class))
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class PendingResourceRequestsTest {
    private lateinit var testedRequests: PendingResourceRequests
    private val fakeTasks = mutableListOf<Runnable>()
    private val fakeProcessed = mutableListOf<String>()

    @Mock lateinit var mockLogger: InternalLogger

    @Mock lateinit var mockExecutor: ExecutorService

    @Mock lateinit var stubTimeProvider: TimeProvider

    private var fakeNowNs = 0L

    @Mock lateinit var mockCallback: ResourceResolverCallback

    @StringForgery lateinit var fakeKey: String

    @StringForgery lateinit var fakeId: String

    @BeforeEach
    fun setup(forge: Forge) {
        fakeNowNs = forge.aLong(min = 0, max = Long.MAX_VALUE - 3 * PendingResourceRequests.ALLOCATION_BACKOFF_NS)
        whenever(stubTimeProvider.getDeviceElapsedTimeNanos()).thenAnswer { fakeNowNs }
        testedRequests = PendingResourceRequests(mockLogger, stubTimeProvider)
        whenever(mockExecutor.execute(any())).thenAnswer { fakeTasks.add(it.getArgument(0)); null }
    }

    @Test
    fun `M share pending drawable work W resolve { same resource key }`() {
        // Given
        val mockSecond = mock<ResourceResolverCallback>()

        // When
        resolve(fakeKey, mockCallback)
        resolve(fakeKey, mockSecond)
        fakeTasks.single().run()

        // Then
        assertThat(fakeProcessed).containsExactly(fakeKey)
        verify(mockCallback).onSuccess(fakeId)
        verify(mockSecond).onSuccess(fakeId)
    }

    @Test
    fun `M reject before loading W resolve { shared byte budget exhausted }`() {
        // Given
        var fakeFirstRequest: ResourceResolverCallback? = null
        testedRequests.resolve(fakeKey + "first", PendingResourceRequests.MAX_PENDING_BYTES, mockCallback) {
            fakeFirstRequest = it
        }

        // When
        resolve(fakeKey, mockCallback)
        checkNotNull(fakeFirstRequest).onSuccess(fakeId)
        resolve(fakeKey, mockCallback)
        fakeTasks.single().run()

        // Then
        assertThat(fakeProcessed).containsExactly(fakeKey)
        verify(mockCallback).onFailure()
        verify(mockCallback, times(2)).onSuccess(fakeId)
    }

    @Test
    fun `M bound waiting snapshots W resolve { same resource stays queued }`() {
        // Given
        val fakeCount = PendingResourceRequests.MAX_CALLBACKS_PER_RESOURCE

        // When
        repeat(fakeCount + 1) { resolve(fakeKey, mockCallback) }
        fakeTasks.single().run()

        // Then
        verify(mockCallback, times(fakeCount)).onSuccess(fakeId)
        verify(mockCallback).onFailure()
        assertThat(fakeProcessed).containsExactly(fakeKey)
    }

    @Test
    fun `M discard queued work and recover budget W resolve { all snapshots cancelled }`() {
        // Given
        val fakeCancellation = ResourceCancellation()
        val mockCancelled = mock<ResourceResolverCallback> { on { cancellation } doReturn fakeCancellation }
        resolve(fakeKey, mockCancelled, PendingResourceRequests.MAX_PENDING_BYTES)

        // When
        fakeCancellation.cancel()
        resolve(fakeKey, mockCallback, PendingResourceRequests.MAX_PENDING_BYTES)
        fakeTasks.forEach { it.run() }

        // Then
        assertThat(fakeProcessed).containsExactly(fakeKey)
        verify(mockCancelled).onFailure()
        verify(mockCallback).onSuccess(fakeId)
    }

    @Test
    fun `M keep shared work W resolve { only one snapshot cancelled }`() {
        // Given
        val fakeCancellation = ResourceCancellation()
        val mockCancelled = mock<ResourceResolverCallback> { on { cancellation } doReturn fakeCancellation }
        resolve(fakeKey, mockCancelled)
        resolve(fakeKey, mockCallback)

        // When
        fakeCancellation.cancel()
        fakeTasks.single().run()

        // Then
        assertThat(fakeProcessed).containsExactly(fakeKey)
        verify(mockCancelled).onFailure()
        verify(mockCallback).onSuccess(fakeId)
    }

    @Test
    fun `M skip loading W resolve { snapshot already cancelled }`() {
        // Given
        val fakeCancellation = ResourceCancellation().apply { cancel() }
        val mockCancelled = mock<ResourceResolverCallback> { on { cancellation } doReturn fakeCancellation }

        // When
        resolve(fakeKey, mockCancelled)
        fakeTasks.forEach { it.run() }

        // Then
        assertThat(fakeProcessed).isEmpty()
        verify(mockCancelled).onFailure()
    }

    @Test
    fun `M release failed work once W resolve { duplicate completion and retry }`() {
        // Given
        var fakeRequest: ResourceResolverCallback? = null
        testedRequests.resolve(fakeKey, PendingResourceRequests.MAX_PENDING_BYTES, mockCallback) { fakeRequest = it }

        // When
        checkNotNull(fakeRequest).onFailure()
        checkNotNull(fakeRequest).onFailure()
        resolve(fakeKey, mockCallback, PendingResourceRequests.MAX_PENDING_BYTES)
        fakeTasks.single().run()

        // Then
        verify(mockCallback).onFailure()
        verify(mockCallback).onSuccess(fakeId)
        assertThat(fakeProcessed).containsExactly(fakeKey)
    }

    @Test
    fun `M detach cancellation W resolve { completed request }`() {
        // Given
        val fakeCancellation = ResourceCancellation()
        val mockFinished = mock<ResourceResolverCallback> { on { cancellation } doReturn fakeCancellation }
        resolve(fakeKey, mockFinished)
        fakeTasks.single().run()

        // When
        fakeCancellation.cancel()

        // Then
        verify(mockFinished).onSuccess(fakeId)
        verifyNoInteractions(mockCallback)
        assertThat(fakeProcessed).containsExactly(fakeKey)
    }

    @Test
    fun `M retain capacity until running work finishes W resolve { snapshot cancelled during processing }`() {
        // Given
        val fakeCancellation = ResourceCancellation()
        val mockCancelled = mock<ResourceResolverCallback> { on { cancellation } doReturn fakeCancellation }
        var fakeLoadedWhileRunning = false
        testedRequests.resolve(fakeKey, PendingResourceRequests.MAX_PENDING_BYTES, mockCancelled) { request ->
            ResourceProcessingTask(
                request.cancellation,
                onCancelled = { request.onFailure() },
                onFailure = { request.onFailure() },
                action = {
                    fakeCancellation.cancel()
                    testedRequests.resolve(fakeKey + "next", 1L, mockCallback) {
                        fakeLoadedWhileRunning = true
                        it.onFailure()
                    }
                    request.onSuccess(fakeId)
                }
            ).execute(mockExecutor, mockLogger)
        }

        // When
        fakeTasks.removeAt(0).run()
        resolve(fakeKey + "next", mockCallback, PendingResourceRequests.MAX_PENDING_BYTES)
        fakeTasks.single().run()

        // Then
        assertThat(fakeLoadedWhileRunning).isFalse()
        verify(mockCancelled).onFailure()
        verify(mockCallback).onFailure()
        verify(mockCallback).onSuccess(fakeId)
    }

    @Test
    fun `M recover capacity W resolve { processing throws then snapshot is cancelled }`() {
        // Given
        val fakeCancellation = ResourceCancellation()
        val mockCancelled = mock<ResourceResolverCallback> { on { cancellation } doReturn fakeCancellation }
        testedRequests.resolve(fakeKey, PendingResourceRequests.MAX_PENDING_BYTES, mockCancelled) { request ->
            ResourceProcessingTask(
                request.cancellation,
                onCancelled = { request.onFailure() },
                onFailure = { request.onFailure() },
                action = { throw IllegalStateException("Drawable bounds update failed") }
            ).execute(mockExecutor, mockLogger)
        }
        assertThrows<IllegalStateException> { fakeTasks.single().run() }
        fakeCancellation.cancel()
        var fakeRetryLoaded = false

        // When
        testedRequests.resolve(fakeKey + "retry", 1, mockCallback) { request ->
            fakeRetryLoaded = true
            request.onFailure()
        }

        // Then
        assertThat(fakeRetryLoaded).isTrue()
    }

    @Test
    fun `M release capacity before diagnostics and pause admissions W onOutOfMemory`() {
        // Given
        val fakeFailure = OutOfMemoryError()
        var fakeRequest: ResourceResolverCallback? = null
        testedRequests.resolve(fakeKey, PendingResourceRequests.MAX_PENDING_BYTES, mockCallback) { fakeRequest = it }
        whenever(mockLogger.log(any(), any<InternalLogger.Target>(), any(), any(), any(), anyOrNull())).thenAnswer {
            verify(mockCallback).onFailure()
            assertThat(testedRequests.canAllocate).isFalse()
            null
        }

        // When
        testedRequests.onOutOfMemory(fakeFailure, checkNotNull(fakeRequest))
        resolve(fakeKey, mockCallback)
        fakeNowNs += PendingResourceRequests.ALLOCATION_BACKOFF_NS - 1
        resolve(fakeKey, mockCallback)
        fakeNowNs++
        resolve(fakeKey, mockCallback, PendingResourceRequests.MAX_PENDING_BYTES)
        fakeTasks.single().run()

        // Then
        verify(mockCallback, times(3)).onFailure()
        verify(mockCallback).onSuccess(fakeId)
        assertThat(fakeProcessed).containsExactly(fakeKey)
        mockLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            "Pausing Session Replay image allocations for 5 seconds after an allocation failure",
            throwable = fakeFailure,
            onlyOnce = true
        )
    }

    @Test
    fun `M extend backoff without affecting another recorder W onOutOfMemory`() {
        // Given
        val testedOtherRequests = PendingResourceRequests(mockLogger, stubTimeProvider)
        testedRequests.onOutOfMemory(OutOfMemoryError(), mockCallback)
        fakeNowNs += PendingResourceRequests.ALLOCATION_BACKOFF_NS - 1

        // When
        testedRequests.onOutOfMemory(OutOfMemoryError(), mockCallback)
        fakeNowNs++

        // Then
        assertThat(testedRequests.canAllocate).isFalse()
        assertThat(testedOtherRequests.canAllocate).isTrue()
    }

    private fun resolve(key: String, callback: ResourceResolverCallback, bytes: Long = 1L) {
        testedRequests.resolve(key, bytes, callback) { request ->
            ResourceProcessingTask(
                request.cancellation,
                onCancelled = { request.onFailure() },
                onFailure = { request.onFailure() },
                action = {
                    fakeProcessed.add(key)
                    request.onSuccess(fakeId)
                }
            ).execute(mockExecutor, mockLogger)
        }
    }
}
