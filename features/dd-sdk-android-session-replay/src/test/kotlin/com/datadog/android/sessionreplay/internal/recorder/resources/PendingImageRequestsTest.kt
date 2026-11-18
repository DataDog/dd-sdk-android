/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import com.datadog.android.internal.system.BuildSdkVersionProvider
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.resources.PendingImageRequests.Companion.HEAP_FRACTION
import com.datadog.android.sessionreplay.internal.recorder.resources.PendingImageRequests.Companion.MAX_PENDING_BYTES
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

    @StringForgery
    lateinit var fakeKey: String

    @StringForgery
    lateinit var fakeResourceId: String

    @LongForgery(min = 0, max = MAX_PENDING_BYTES)
    var fakeByteCount: Long = 0L

    @BeforeEach
    fun `set up`() {
        whenever(mockBuildSdkVersionProvider.isAtLeastO).thenReturn(true)
        testedRequests = PendingImageRequests(mockBuildSdkVersionProvider, maxHeapBytes = Long.MAX_VALUE)
    }

    @Test
    fun `M return a request W start() { nothing in flight }`() {
        // When
        val request = testedRequests.start(fakeKey, fakeByteCount, mockCallback)

        // Then
        assertThat(request).isNotNull
        verifyNoInteractions(mockCallback)
    }

    @Test
    fun `M deliver the resource to every capture W start() + onSuccess() { same key in flight }`() {
        // Given
        val request = startRequest(fakeKey, fakeByteCount, mockCallback)

        // When
        val joinedRequest = testedRequests.start(fakeKey, fakeByteCount, mockOtherCallback)
        request.onSuccess(fakeResourceId)

        // Then
        assertThat(joinedRequest).isNull()
        verify(mockCallback).onSuccess(fakeResourceId)
        verify(mockOtherCallback).onSuccess(fakeResourceId)
    }

    @Test
    fun `M fail every capture W start() + onFailure() { same key in flight }`() {
        // Given
        val request = startRequest(fakeKey, fakeByteCount, mockCallback)

        // When
        val joinedRequest = testedRequests.start(fakeKey, fakeByteCount, mockOtherCallback)
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
        startRequest(fakeKey, fakeByteCount, mockCallback)

        // When
        val otherRequest = testedRequests.start("$fakeKey-$fakeOtherKey", 0L, mockOtherCallback)

        // Then
        assertThat(otherRequest).isNotNull
    }

    @Test
    fun `M refuse and fail the capture W start() { budget exceeded }`(
        @StringForgery fakeOtherKey: String,
        @LongForgery(min = 1, max = MAX_PENDING_BYTES) fakeOtherByteCount: Long
    ) {
        // Given
        startRequest(fakeKey, MAX_PENDING_BYTES, mockCallback)

        // When
        val otherRequest = testedRequests.start("$fakeKey-$fakeOtherKey", fakeOtherByteCount, mockOtherCallback)

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
        val request = testedRequests.start(fakeKey, fakeLargeByteCount, mockCallback)

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
        val request = startRequest(fakeKey, MAX_PENDING_BYTES, mockCallback)

        // When
        if (fakeSucceeded) request.onSuccess(fakeResourceId) else request.onFailure()
        val otherRequest = testedRequests.start("$fakeKey-$fakeOtherKey", MAX_PENDING_BYTES, mockOtherCallback)

        // Then
        assertThat(otherRequest).isNotNull
        verifyNoInteractions(mockOtherCallback)
    }

    @Test
    fun `M complete the captures once W onSuccess() + onFailure()`() {
        // Given
        val request = startRequest(fakeKey, fakeByteCount, mockCallback)

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
        val request = startRequest(fakeKey, fakeByteCount, mockCallback)
        request.onSuccess(fakeResourceId)
        val newerRequest = startRequest(fakeKey, fakeByteCount, mockOtherCallback)

        // When
        request.onFailure()
        val joinedRequest = testedRequests.start(fakeKey, fakeByteCount, mockThirdCallback)
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
            restartedRequest = testedRequests.start(fakeKey, fakeByteCount, mockOtherCallback)
        }.whenever(mockCallback).onSuccess(fakeResourceId)
        val request = startRequest(fakeKey, fakeByteCount, mockCallback)

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
        testedRequests = PendingImageRequests(mockBuildSdkVersionProvider, fakeMaxHeapBytes)
        startRequest(fakeKey, fakeMaxHeapBytes / HEAP_FRACTION, mockCallback)

        // When
        val otherRequest = testedRequests.start("$fakeKey-$fakeOtherKey", 1L, mockOtherCallback)

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
        testedRequests = PendingImageRequests(mockBuildSdkVersionProvider, fakeMaxHeapBytes)
        startRequest(fakeKey, MAX_PENDING_BYTES - 1, mockCallback)

        // When
        val otherRequest = testedRequests.start("$fakeKey-$fakeOtherKey", 1L, mockOtherCallback)

        // Then
        assertThat(otherRequest).isNotNull
        verifyNoInteractions(mockOtherCallback)
    }

    private fun startRequest(
        key: String,
        byteCount: Long,
        callback: ResourceResolverCallback
    ): PendingImageRequests.Request {
        return checkNotNull(testedRequests.start(key, byteCount, callback)) { "Expected the capture to start work" }
    }
}
