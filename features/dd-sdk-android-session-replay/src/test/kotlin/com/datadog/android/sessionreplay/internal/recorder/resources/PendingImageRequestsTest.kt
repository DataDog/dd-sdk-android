/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.system.BuildSdkVersionProvider
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.resources.PendingImageRequests.Companion.HEAP_FRACTION
import com.datadog.android.sessionreplay.internal.recorder.resources.PendingImageRequests.Companion.MAX_HELD_DRAWABLES
import com.datadog.android.sessionreplay.internal.recorder.resources.PendingImageRequests.Companion.MAX_PENDING_BYTES
import com.datadog.android.sessionreplay.recorder.resources.DrawableCopier
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.LongForgery
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
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class PendingImageRequestsTest {

    private lateinit var testedRequests: PendingImageRequests

    @Mock
    lateinit var mockBuildSdkVersionProvider: BuildSdkVersionProvider

    @Mock
    lateinit var mockCallback: ResourceResolverCallback

    @Mock
    lateinit var mockOtherCallback: ResourceResolverCallback

    @Mock
    lateinit var mockDrawable: Drawable

    @Mock
    lateinit var mockDrawableCopier: DrawableCopier

    @Mock
    lateinit var mockResources: Resources

    @Mock
    lateinit var mockDisplayMetrics: DisplayMetrics

    @StringForgery
    lateinit var fakeKey: String

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @StringForgery
    lateinit var fakeResourceId: String

    @LongForgery(min = 0, max = MAX_PENDING_BYTES)
    var fakeByteCount: Long = 0L

    @BeforeEach
    fun `set up`() {
        whenever(mockBuildSdkVersionProvider.isAtLeastO).thenReturn(true)
        testedRequests =
            PendingImageRequests(mockInternalLogger, mockBuildSdkVersionProvider, maxHeapBytes = Long.MAX_VALUE)
    }

    @Test
    fun `M return a request W start() { nothing in flight }`() {
        // When
        val request = testedRequests.start(bitmapKey(fakeKey), fakeByteCount, mockCallback)

        // Then
        assertThat(request).isNotNull
        verifyNoInteractions(mockCallback)
    }

    @Test
    fun `M deliver the resource to every capture W start() + onSuccess() { same key in flight }`() {
        // Given
        val request = startRequest(bitmapKey(fakeKey), fakeByteCount, mockCallback)

        // When
        val joinedRequest = testedRequests.start(bitmapKey(fakeKey), fakeByteCount, mockOtherCallback)
        request.onSuccess(fakeResourceId)

        // Then
        assertThat(joinedRequest).isNull()
        verify(mockCallback).onSuccess(fakeResourceId)
        verify(mockOtherCallback).onSuccess(fakeResourceId)
    }

    @Test
    fun `M fail every capture W start() + onFailure() { same key in flight }`() {
        // Given
        val request = startRequest(bitmapKey(fakeKey), fakeByteCount, mockCallback)

        // When
        val joinedRequest = testedRequests.start(bitmapKey(fakeKey), fakeByteCount, mockOtherCallback)
        request.onFailure()

        // Then
        assertThat(joinedRequest).isNull()
        verify(mockCallback).onFailure()
        verify(mockOtherCallback).onFailure()
    }

    @Test
    fun `M start a request per image W start() { different keys }`(
        @StringForgery fakeOtherKey: String
    ) {
        // Given
        startRequest(bitmapKey(fakeKey), fakeByteCount, mockCallback)

        // When
        val otherRequest = testedRequests.start(bitmapKey("$fakeKey-$fakeOtherKey"), 0L, mockOtherCallback)

        // Then
        assertThat(otherRequest).isNotNull
    }

    @Test
    fun `M refuse and fail the capture W start() { budget exceeded }`(
        @StringForgery fakeOtherKey: String,
        @LongForgery(min = 1, max = MAX_PENDING_BYTES) fakeOtherByteCount: Long
    ) {
        // Given
        startRequest(bitmapKey(fakeKey), MAX_PENDING_BYTES, mockCallback)

        // When
        val otherRequest = testedRequests.start(
            bitmapKey("$fakeKey-$fakeOtherKey"),
            fakeOtherByteCount,
            mockOtherCallback
        )

        // Then
        assertThat(otherRequest).isNull()
        verify(mockOtherCallback).onFailure()
        verifyNoInteractions(mockCallback)
    }

    @Test
    fun `M admit the request W start() { larger than the budget, nothing pending }`(
        @LongForgery(min = MAX_PENDING_BYTES + 1, max = Long.MAX_VALUE / 2) fakeLargeByteCount: Long
    ) {
        // When
        val request = testedRequests.start(bitmapKey(fakeKey), fakeLargeByteCount, mockCallback)

        // Then
        assertThat(request).isNotNull
        verifyNoInteractions(mockCallback)
    }

    @Test
    fun `M release the budget W onSuccess() or onFailure()`(
        @BoolForgery fakeSucceeded: Boolean,
        @StringForgery fakeOtherKey: String
    ) {
        // Given
        val request = startRequest(bitmapKey(fakeKey), MAX_PENDING_BYTES, mockCallback)

        // When
        if (fakeSucceeded) request.onSuccess(fakeResourceId) else request.onFailure()
        val otherRequest = testedRequests.start(
            bitmapKey("$fakeKey-$fakeOtherKey"),
            MAX_PENDING_BYTES,
            mockOtherCallback
        )

        // Then
        assertThat(otherRequest).isNotNull
        verifyNoInteractions(mockOtherCallback)
    }

    @Test
    fun `M complete the captures once W onSuccess() + onFailure()`() {
        // Given
        val request = startRequest(bitmapKey(fakeKey), fakeByteCount, mockCallback)

        // When
        request.onSuccess(fakeResourceId)
        request.onFailure()

        // Then
        verify(mockCallback).onSuccess(fakeResourceId)
        verify(mockCallback, never()).onFailure()
    }

    @Test
    fun `M keep the newer request W onFailure() { late completion of a finished request }`(
        @Mock mockThirdCallback: ResourceResolverCallback
    ) {
        // Given
        val request = startRequest(bitmapKey(fakeKey), fakeByteCount, mockCallback)
        request.onSuccess(fakeResourceId)
        val newerRequest = startRequest(bitmapKey(fakeKey), fakeByteCount, mockOtherCallback)

        // When
        request.onFailure()
        val joinedRequest = testedRequests.start(bitmapKey(fakeKey), fakeByteCount, mockThirdCallback)
        newerRequest.onSuccess(fakeResourceId)

        // Then
        assertThat(joinedRequest).isNull()
        verify(mockOtherCallback).onSuccess(fakeResourceId)
        verify(mockThirdCallback).onSuccess(fakeResourceId)
    }

    @Test
    fun `M let a completed capture start new work W onSuccess() { callback captures the same image }`() {
        // Given
        var restartedRequest: PendingImageRequests.Request? = null
        doAnswer {
            restartedRequest = testedRequests.start(bitmapKey(fakeKey), fakeByteCount, mockOtherCallback)
        }.whenever(mockCallback).onSuccess(fakeResourceId)
        val request = startRequest(bitmapKey(fakeKey), fakeByteCount, mockCallback)

        // When
        request.onSuccess(fakeResourceId)

        // Then
        assertThat(restartedRequest).isNotNull
        verifyNoInteractions(mockOtherCallback)
    }

    @Test
    fun `M limit the budget to part of the heap W start() { before API 26 }`(
        @LongForgery(min = HEAP_FRACTION.toLong(), max = HEAP_FRACTION * MAX_PENDING_BYTES) fakeMaxHeapBytes: Long,
        @StringForgery fakeOtherKey: String
    ) {
        // Given
        whenever(mockBuildSdkVersionProvider.isAtLeastO).thenReturn(false)
        testedRequests = PendingImageRequests(mockInternalLogger, mockBuildSdkVersionProvider, fakeMaxHeapBytes)
        startRequest(bitmapKey(fakeKey), fakeMaxHeapBytes / HEAP_FRACTION, mockCallback)

        // When
        val otherRequest = testedRequests.start(bitmapKey("$fakeKey-$fakeOtherKey"), 1L, mockOtherCallback)

        // Then
        assertThat(otherRequest).isNull()
        verify(mockOtherCallback).onFailure()
    }

    @Test
    fun `M keep the full budget W start() { API 26+, small heap }`(
        @LongForgery(min = 1, max = MAX_PENDING_BYTES) fakeMaxHeapBytes: Long,
        @StringForgery fakeOtherKey: String
    ) {
        // Given
        testedRequests = PendingImageRequests(mockInternalLogger, mockBuildSdkVersionProvider, fakeMaxHeapBytes)
        startRequest(bitmapKey(fakeKey), MAX_PENDING_BYTES - 1, mockCallback)

        // When
        val otherRequest = testedRequests.start(bitmapKey("$fakeKey-$fakeOtherKey"), 1L, mockOtherCallback)

        // Then
        assertThat(otherRequest).isNotNull
        verifyNoInteractions(mockOtherCallback)
    }

    @Test
    fun `M complete every capture and log W onSuccess() or onFailure() { a callback throws }`(
        @BoolForgery fakeSucceeded: Boolean
    ) {
        // Given
        val fakeError = IllegalStateException()
        doThrow(fakeError).whenever(mockCallback).onSuccess(fakeResourceId)
        doThrow(fakeError).whenever(mockCallback).onFailure()
        val request = startRequest(bitmapKey(fakeKey), fakeByteCount, mockCallback)
        testedRequests.start(bitmapKey(fakeKey), fakeByteCount, mockOtherCallback)

        // When
        if (fakeSucceeded) request.onSuccess(fakeResourceId) else request.onFailure()

        // Then
        if (fakeSucceeded) {
            verify(mockOtherCallback).onSuccess(fakeResourceId)
        } else {
            verify(mockOtherCallback).onFailure()
        }
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            PendingImageRequests.CAPTURE_CALLBACK_FAILED,
            fakeError,
            onlyOnce = true
        )
    }

    @Test
    fun `M complete both captures W start() + onSuccess() { drawable, worker not started }`() {
        // Given
        val request = startRequest(drawableKey(), 0L, mockCallback)

        // When
        val joinedRequest = testedRequests.start(drawableKey(), 0L, mockOtherCallback)
        request.begin()
        request.onSuccess(fakeResourceId)

        // Then
        assertThat(joinedRequest).isNull()
        verify(mockCallback).onSuccess(fakeResourceId)
        verify(mockOtherCallback).onSuccess(fakeResourceId)
    }

    @Test
    fun `M start new work W start() { drawable, worker started }`() {
        // Given
        val request = startRequest(drawableKey(), 0L, mockCallback)
        request.begin()

        // When
        val otherRequest = testedRequests.start(drawableKey(), 0L, mockOtherCallback)

        // Then
        assertThat(otherRequest).isNotNull
        verifyNoInteractions(mockCallback)
        verifyNoInteractions(mockOtherCallback)
    }

    @Test
    fun `M keep the newer request W onFailure() { started request finishes after a newer one was started }`(
        @Mock mockThirdCallback: ResourceResolverCallback
    ) {
        // Given
        val request = startRequest(drawableKey(), 0L, mockCallback)
        request.begin()
        val newerRequest = startRequest(drawableKey(), 0L, mockOtherCallback)

        // When
        request.onFailure()
        val joinedRequest = testedRequests.start(drawableKey(), 0L, mockThirdCallback)
        newerRequest.onSuccess(fakeResourceId)

        // Then
        assertThat(joinedRequest).isNull()
        verify(mockCallback).onFailure()
        verify(mockOtherCallback).onSuccess(fakeResourceId)
        verify(mockThirdCallback).onSuccess(fakeResourceId)
    }

    @Test
    fun `M refuse and fail the capture W start() { too many drawables held }`() {
        // Given
        repeat(MAX_HELD_DRAWABLES) {
            startRequest(drawableKey(drawable = mock()), 0L, mockCallback)
        }

        // When
        val otherRequest = testedRequests.start(drawableKey(drawable = mock()), 0L, mockOtherCallback)

        // Then
        assertThat(otherRequest).isNull()
        verify(mockOtherCallback).onFailure()
        verifyNoInteractions(mockCallback)
    }

    @Test
    fun `M admit the capture W start() { too many drawables held, drawable not held }`() {
        // Given
        repeat(MAX_HELD_DRAWABLES) {
            startRequest(drawableKey(drawable = mock()), 0L, mockCallback)
        }

        // When
        val otherRequest = testedRequests.start(drawableKey(isHolding = false), 0L, mockOtherCallback)

        // Then
        assertThat(otherRequest).isNotNull
        verifyNoInteractions(mockOtherCallback)
    }

    @Test
    fun `M admit the capture W start() { held drawables reached the limit, one completes }`(
        @BoolForgery fakeSucceeded: Boolean
    ) {
        // Given
        val request = startRequest(drawableKey(drawable = mock()), 0L, mockCallback)
        repeat(MAX_HELD_DRAWABLES - 1) {
            startRequest(drawableKey(drawable = mock()), 0L, mockCallback)
        }

        // When
        if (fakeSucceeded) request.onSuccess(fakeResourceId) else request.onFailure()
        val otherRequest = testedRequests.start(drawableKey(drawable = mock()), 0L, mockOtherCallback)

        // Then
        assertThat(otherRequest).isNotNull
        verifyNoInteractions(mockOtherCallback)
    }

    @Test
    fun `M admit the capture W start() { reserves nothing, a larger capture than the budget pending }`(
        @LongForgery(min = MAX_PENDING_BYTES + 1, max = Long.MAX_VALUE / 2) fakeLargeByteCount: Long
    ) {
        // Given
        startRequest(bitmapKey(fakeKey), fakeLargeByteCount, mockCallback)

        // When
        val otherRequest = testedRequests.start(drawableKey(), 0L, mockOtherCallback)

        // Then
        assertThat(otherRequest).isNotNull
        verifyNoInteractions(mockOtherCallback)
    }

    private fun drawableKey(
        drawable: Drawable = mockDrawable,
        isHolding: Boolean = true
    ): CaptureKey = CaptureKey.OfDrawable(
        drawable = drawable,
        copier = mockDrawableCopier,
        resources = mockResources,
        displayMetrics = mockDisplayMetrics,
        width = 0,
        height = 0,
        customResourceIdCacheKey = null,
        holdsDrawableStrongly = isHolding
    )

    private fun bitmapKey(signature: String): CaptureKey = CaptureKey.OfBitmap(signature)

    private fun startRequest(
        key: CaptureKey,
        byteCount: Long,
        callback: ResourceResolverCallback
    ): PendingImageRequests.Request {
        return checkNotNull(testedRequests.start(key, byteCount, callback)) { "Expected the capture to start work" }
    }
}
