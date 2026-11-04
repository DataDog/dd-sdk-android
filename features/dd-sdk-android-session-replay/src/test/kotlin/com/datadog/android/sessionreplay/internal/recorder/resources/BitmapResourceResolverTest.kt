/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import android.graphics.Bitmap
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
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException

@Extensions(ExtendWith(MockitoExtension::class), ExtendWith(ForgeExtension::class))
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class BitmapResourceResolverTest {
    private lateinit var testedResolver: BitmapResourceResolver

    @Mock lateinit var mockExecutor: ExecutorService

    @Mock lateinit var mockLogger: InternalLogger

    @Mock lateinit var stubTimeProvider: TimeProvider

    private var fakeNowNs = 0L

    @Mock lateinit var mockCallback: ResourceResolverCallback

    @StringForgery lateinit var fakeResourceId: String

    private val fakeTasks = mutableListOf<Runnable>()
    private val fakeProcessedBitmaps = mutableListOf<Bitmap>()

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeNowNs = forge.aLong(min = 0, max = Long.MAX_VALUE - PendingResourceRequests.ALLOCATION_BACKOFF_NS)
        whenever(stubTimeProvider.getDeviceElapsedTimeNanos()).thenAnswer { fakeNowNs }
        whenever(mockExecutor.execute(any())).thenAnswer { fakeTasks.add(it.getArgument(0)); null }
        testedResolver = createResolver()
    }

    @Test
    fun `M reject before copying W resolve { bitmap exceeds byte budget }`(forge: Forge) {
        // Given
        val (stubSource, mockCopy) = forgeBitmap(forge)
        whenever(stubSource.width) doReturn 4097
        whenever(stubSource.height) doReturn 4096

        // When
        testedResolver.resolve(stubSource, mockCallback)

        // Then
        assertThat(fakeTasks).isEmpty()
        verify(stubSource, never()).copy(any(), any())
        verifyNoInteractions(mockCopy)
        verify(mockCallback).onFailure()
    }

    @Test
    fun `M recover capacity W resolve { byte budget exhausted then work completes }`(forge: Forge) {
        // Given
        val (stubFirst, mockFirstCopy) = forgeBitmap(forge)
        val (stubSecond, mockSecondCopy) = forgeBitmap(forge)
        whenever(stubFirst.width) doReturn 4096
        whenever(stubFirst.height) doReturn 4096
        testedResolver.resolve(stubFirst, mockCallback)

        // When
        testedResolver.resolve(stubSecond, mockCallback)
        fakeTasks.removeAt(0).run()
        testedResolver.resolve(stubSecond, mockCallback)
        fakeTasks.single().run()

        // Then
        assertThat(fakeProcessedBitmaps).containsExactly(mockFirstCopy, mockSecondCopy)
        verify(stubSecond).copy(Bitmap.Config.ARGB_8888, false)
        verify(mockCallback).onFailure()
        verify(mockCallback, times(2)).onSuccess(fakeResourceId)
    }

    @Test
    fun `M bound queued jobs W resolve { many distinct small bitmaps }`(forge: Forge) {
        // Given
        val fakeBitmaps = List(PendingResourceRequests.MAX_PENDING_REQUESTS + 1) { forgeBitmap(forge) }

        // When
        fakeBitmaps.forEach { testedResolver.resolve(it.first, mockCallback) }

        // Then
        assertThat(fakeTasks).hasSize(PendingResourceRequests.MAX_PENDING_REQUESTS)
        verify(fakeBitmaps.last().first, never()).copy(any(), any())
        verify(mockCallback).onFailure()
    }

    @Test
    fun `M release reservation W resolve { copy fails }`(forge: Forge) {
        // Given
        val (stubSource, mockCopy) = forgeBitmap(forge)
        whenever(stubSource.copy(Bitmap.Config.ARGB_8888, false)).thenReturn(null, mockCopy)

        // When
        testedResolver.resolve(stubSource, mockCallback)
        testedResolver.resolve(stubSource, mockCallback)
        fakeTasks.single().run()

        // Then
        verify(mockCallback).onFailure()
        verify(mockCallback).onSuccess(fakeResourceId)
        verify(mockCopy).recycle()
    }

    @Test
    fun `M release reservation and recover W resolve { bitmap copy throws illegal state }`(forge: Forge) {
        // Given
        val fakeException = IllegalStateException(forge.aString())
        val (stubSource, mockCopy) = forgeBitmap(forge)
        whenever(stubSource.copy(Bitmap.Config.ARGB_8888, false)).thenThrow(fakeException).thenReturn(mockCopy)

        // When
        testedResolver.resolve(stubSource, mockCallback)
        testedResolver.resolve(stubSource, mockCallback)
        fakeTasks.single().run()

        // Then
        verify(mockCallback).onFailure()
        verify(mockCallback).onSuccess(fakeResourceId)
        verify(mockCopy).recycle()
        mockLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            "Unable to copy a Session Replay bitmap",
            throwable = fakeException,
            onlyOnce = true
        )
    }

    @Test
    fun `M recycle and release reservation W resolve { executor rejects work }`(forge: Forge) {
        // Given
        val (stubSource, mockCopy) = forgeBitmap(forge)
        whenever(mockExecutor.execute(any())) doThrow RejectedExecutionException()

        // When
        repeat(PendingResourceRequests.MAX_PENDING_REQUESTS + 1) {
            testedResolver.resolve(stubSource, mockCallback)
        }

        // Then
        verify(mockCopy, times(PendingResourceRequests.MAX_PENDING_REQUESTS + 1)).recycle()
        verify(mockCallback, times(PendingResourceRequests.MAX_PENDING_REQUESTS + 1)).onFailure()
        assertThat(fakeProcessedBitmaps).isEmpty()
    }

    @Test
    fun `M retry failed processing W resolve`(forge: Forge) {
        // Given
        val (stubSource, mockCopy) = forgeBitmap(forge)
        var fakeAttempt = 0
        testedResolver =
            BitmapResourceResolver(mockExecutor, mockLogger, PendingResourceRequests(mockLogger, stubTimeProvider)) {
                if (fakeAttempt++ == 0) null else fakeResourceId
            }
        testedResolver.resolve(stubSource, mockCallback)
        fakeTasks.removeAt(0).run()

        // When
        testedResolver.resolve(stubSource, mockCallback)
        fakeTasks.single().run()

        // Then
        verify(mockCopy, times(2)).recycle()
        verify(mockCallback).onFailure()
        verify(mockCallback).onSuccess(fakeResourceId)
    }

    @Test
    fun `M preserve alpha8 pixels W resolve`(forge: Forge) {
        // Given
        val (stubSource, mockCopy) = forgeBitmap(forge)
        whenever(stubSource.config) doReturn Bitmap.Config.ALPHA_8
        whenever(stubSource.copy(Bitmap.Config.ALPHA_8, false)) doReturn mockCopy

        // When
        testedResolver.resolve(stubSource, mockCallback)
        fakeTasks.single().run()

        // Then
        assertThat(fakeProcessedBitmaps).containsExactly(mockCopy)
        verify(stubSource).copy(Bitmap.Config.ALPHA_8, false)
        verify(stubSource, never()).recycle()
        verify(mockCopy).recycle()
    }

    @Test
    fun `M isolate resource state W resolve { two SDK instances }`(forge: Forge) {
        // Given
        val (stubSource, mockCopy) = forgeBitmap(forge)
        val testedSecondResolver = createResolver()

        // When
        testedResolver.resolve(stubSource, mockCallback)
        fakeTasks.removeAt(0).run()
        testedSecondResolver.resolve(stubSource, mockCallback)
        fakeTasks.single().run()

        // Then
        verify(stubSource, times(2)).copy(Bitmap.Config.ARGB_8888, false)
        assertThat(fakeProcessedBitmaps).containsExactly(mockCopy, mockCopy)
    }

    @Test
    fun `M release queued pixels W resolve { snapshot discarded before compression }`(forge: Forge) {
        // Given
        val (stubSource, mockCopy) = forgeBitmap(forge)
        val fakeCancellation = ResourceCancellation()
        whenever(mockCallback.cancellation) doReturn fakeCancellation
        testedResolver.resolve(stubSource, mockCallback)

        // When
        fakeCancellation.cancel()
        fakeTasks.single().run()

        // Then
        verify(mockCopy).recycle()
        verify(mockCallback).onFailure()
        assertThat(fakeProcessedBitmaps).isEmpty()
    }

    @Test
    fun `M pause copies and resume after backoff W resolve { copying runs out of memory }`(forge: Forge) {
        // Given
        val (stubSource, mockCopy) = forgeBitmap(forge)
        whenever(stubSource.width).thenReturn(4096)
        whenever(stubSource.height).thenReturn(4096)
        whenever(stubSource.copy(Bitmap.Config.ARGB_8888, false)).thenThrow(OutOfMemoryError()).thenReturn(mockCopy)

        // When
        testedResolver.resolve(stubSource, mockCallback)
        testedResolver.resolve(stubSource, mockCallback)

        // Then
        verify(stubSource).copy(Bitmap.Config.ARGB_8888, false)
        verify(mockCallback, times(2)).onFailure()
        assertThat(fakeTasks).isEmpty()

        // When
        fakeNowNs += PendingResourceRequests.ALLOCATION_BACKOFF_NS
        testedResolver.resolve(stubSource, mockCallback)
        fakeTasks.single().run()

        // Then
        verify(mockCallback).onSuccess(fakeResourceId)
        verify(mockCopy).recycle()
        verify(stubSource, never()).recycle()
    }

    @Test
    fun `M release queued pixels without processing W resolve { earlier encode runs out of memory }`(forge: Forge) {
        // Given
        val (stubFirstSource, mockFirstCopy) = forgeBitmap(forge)
        val (stubSecondSource, mockSecondCopy) = forgeBitmap(forge)
        var fakeAttempts = 0
        testedResolver =
            BitmapResourceResolver(mockExecutor, mockLogger, PendingResourceRequests(mockLogger, stubTimeProvider)) {
                fakeAttempts++
                throw OutOfMemoryError()
            }
        testedResolver.resolve(stubFirstSource, mockCallback)
        testedResolver.resolve(stubSecondSource, mockCallback)

        // When
        fakeTasks.forEach { it.run() }

        // Then
        assertThat(fakeAttempts).isEqualTo(1)
        verify(mockFirstCopy).recycle()
        verify(mockSecondCopy).recycle()
        verify(mockCallback, times(2)).onFailure()
        verify(stubFirstSource, never()).recycle()
        verify(stubSecondSource, never()).recycle()
    }

    private fun createResolver() = BitmapResourceResolver(
        mockExecutor,
        mockLogger,
        PendingResourceRequests(mockLogger, stubTimeProvider)
    ) { bitmap ->
        fakeProcessedBitmaps.add(bitmap)
        fakeResourceId
    }

    private fun forgeBitmap(forge: Forge): Pair<Bitmap, Bitmap> {
        val mockCopy = mock<Bitmap>()
        val stubSource = mock<Bitmap> {
            on { width } doReturn forge.anInt(min = 1, max = 64)
            on { height } doReturn forge.anInt(min = 1, max = 64)
            on { generationId } doReturn forge.anInt()
            on { config } doReturn Bitmap.Config.ARGB_8888
            on { copy(Bitmap.Config.ARGB_8888, false) } doReturn mockCopy
        }
        return stubSource to mockCopy
    }
}
