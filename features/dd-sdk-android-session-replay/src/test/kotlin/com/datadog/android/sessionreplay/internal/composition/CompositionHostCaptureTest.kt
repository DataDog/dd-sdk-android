/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.view.View
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.sessionreplay.composition.CapturedIdentity
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedMappingContext
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
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
internal class CompositionHostCaptureTest {
    @Mock lateinit var mockDecomposer: CompositionHostDecomposer

    @Mock lateinit var mockLogger: InternalLogger

    @Mock lateinit var mockHost: View

    @Mock lateinit var mockInteropView: View

    @Mock lateinit var mockNativeCopier: (View, CapturedIdentity) -> WindowCopyResult.Present?

    private lateinit var fakeMappingContext: CapturedMappingContext
    private lateinit var fakeChildIdentity: CapturedIdentity
    private lateinit var fakeNativeCopy: WindowCopyResult.Present
    private lateinit var testedCapture: CompositionHostCapture
    private val fakeDecomposition = CompositionHostDecomposeResult(emptyList(), emptyList(), emptyList())

    @BeforeEach
    fun setUp(
        @Forgery fakeContext: CapturedMappingContext,
        @Forgery fakeWindow: CapturedNativeWindow,
        @StringForgery fakeHostName: String,
        @StringForgery fakeNodeName: String
    ) {
        val fakeHostIdentity = fakeContext.identityFactory.composeHost(fakeContext.ownerIdentity, fakeHostName)
        fakeMappingContext = fakeContext.copy(ownerIdentity = fakeHostIdentity)
        fakeChildIdentity = fakeContext.identityFactory.composeNode(fakeHostIdentity, fakeNodeName)
        fakeNativeCopy = WindowCopyResult.Present(fakeWindow, emptyMap())
        testedCapture = CompositionHostCapture(mockDecomposer, mockLogger)
        whenever(mockNativeCopier(mockInteropView, fakeChildIdentity)).thenReturn(fakeNativeCopy)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M report native copy availability W capture()`(fakeCopyAvailable: Boolean) {
        // Given
        whenever(mockNativeCopier(mockInteropView, fakeChildIdentity))
            .thenReturn(if (fakeCopyAvailable) fakeNativeCopy else null)
        var fakeHandoffAccepted: Boolean? = null
        whenever(mockDecomposer.decompose(eq(mockHost), any())).thenAnswer { invocation ->
            val request = invocation.getArgument<CompositionHostDecomposeRequest>(1)
            fakeHandoffAccepted = request.nativeViewHandoff(mockInteropView, fakeChildIdentity)
            fakeDecomposition
        }

        // When
        val result = testedCapture.capture(mockHost, fakeMappingContext, mockNativeCopier)

        // Then
        val capturedHost = checkNotNull(result)
        assertThat(fakeHandoffAccepted).isEqualTo(fakeCopyAvailable)
        assertThat(capturedHost.nativeCopies).isEqualTo(if (fakeCopyAvailable) listOf(fakeNativeCopy) else emptyList())
        assertThat(capturedHost.decomposition).isSameAs(fakeDecomposition)
        verify(mockNativeCopier)(mockInteropView, fakeChildIdentity)
        val requestCaptor = argumentCaptor<CompositionHostDecomposeRequest>()
        verify(mockDecomposer).decompose(eq(mockHost), requestCaptor.capture())
        assertThat(requestCaptor.firstValue.identityFactory).isSameAs(fakeMappingContext.identityFactory)
        assertThat(requestCaptor.firstValue.hostIdentity).isEqualTo(fakeMappingContext.ownerIdentity)
        assertThat(requestCaptor.firstValue.screenDensity).isEqualTo(fakeMappingContext.screenDensity)
        assertThat(requestCaptor.firstValue.hostBounds).isEqualTo(fakeMappingContext.bounds)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M discard native copies W capture() { decomposition returns null or throws }`(
        fakeThrows: Boolean,
        @StringForgery fakeErrorMessage: String
    ) {
        // Given
        val fakeError = NoSuchMethodError(fakeErrorMessage)
        whenever(mockDecomposer.decompose(eq(mockHost), any())).thenAnswer { invocation ->
            val request = invocation.getArgument<CompositionHostDecomposeRequest>(1)
            request.nativeViewHandoff(mockInteropView, fakeChildIdentity)
            if (fakeThrows) throw fakeError
            null
        }

        // When
        val result = assertDoesNotThrow<CompositionHostCapture.Result?> {
            testedCapture.capture(mockHost, fakeMappingContext, mockNativeCopier)
        }

        // Then
        assertThat(result).isNull()
        verify(mockNativeCopier)(mockInteropView, fakeChildIdentity)
        if (fakeThrows) {
            mockLogger.verifyLog(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.TELEMETRY,
                "Composition host decomposition failed; falling back to native capture",
                throwable = fakeError,
                onlyOnce = true
            )
        } else {
            verifyNoInteractions(mockLogger)
        }

        // Given
        whenever(mockDecomposer.decompose(eq(mockHost), any())).thenReturn(fakeDecomposition)

        // When
        val nextResult = testedCapture.capture(mockHost, fakeMappingContext, mockNativeCopier)

        // Then
        val nextCapture = checkNotNull(nextResult)
        assertThat(nextCapture.nativeCopies).isEmpty()
    }

    @Test
    fun `M isolate pending native copies W capture() { nested host capture }`(
        @Forgery fakeNestedWindow: CapturedNativeWindow,
        @StringForgery fakeNestedNodeName: String
    ) {
        // Given
        val mockNestedHost: View = mock()
        val mockNestedInterop: View = mock()
        val fakeNestedCopy = WindowCopyResult.Present(fakeNestedWindow, emptyMap())
        val fakeNestedIdentity = fakeMappingContext.identityFactory
            .composeNode(fakeMappingContext.ownerIdentity, fakeNestedNodeName)
        whenever(mockNativeCopier(mockNestedInterop, fakeNestedIdentity)).thenReturn(fakeNestedCopy)
        whenever(mockDecomposer.decompose(eq(mockNestedHost), any())).thenAnswer { invocation ->
            val request = invocation.getArgument<CompositionHostDecomposeRequest>(1)
            request.nativeViewHandoff(mockNestedInterop, fakeNestedIdentity)
            fakeDecomposition
        }
        var fakeNestedCapture: CompositionHostCapture.Result? = null
        whenever(mockDecomposer.decompose(eq(mockHost), any())).thenAnswer { invocation ->
            val request = invocation.getArgument<CompositionHostDecomposeRequest>(1)
            request.nativeViewHandoff(mockInteropView, fakeChildIdentity)
            fakeNestedCapture = testedCapture.capture(mockNestedHost, fakeMappingContext, mockNativeCopier)
            fakeDecomposition
        }

        // When
        val result = testedCapture.capture(mockHost, fakeMappingContext, mockNativeCopier)

        // Then
        val capturedHost = checkNotNull(result)
        val nestedCapture = checkNotNull(fakeNestedCapture)
        assertThat(capturedHost.nativeCopies).containsExactly(fakeNativeCopy)
        assertThat(nestedCapture.nativeCopies).containsExactly(fakeNestedCopy)
    }
}
