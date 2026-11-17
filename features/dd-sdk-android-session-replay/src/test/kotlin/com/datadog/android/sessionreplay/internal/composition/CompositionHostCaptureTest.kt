/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.graphics.Rect
import android.view.View
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.sessionreplay.composition.CapturedIdentity
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.TouchPrivacy
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedMappingContext
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.Forge
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
    private val fakeDecomposition = CompositionHostDecomposeResult(emptyList(), emptyList(), emptyList(), emptyMap())

    @BeforeEach
    fun setUp(
        @Forgery fakeContext: CapturedMappingContext,
        @Forgery fakeWindow: CapturedNativeWindow,
        @StringForgery fakeHostName: String,
        @StringForgery fakeNodeName: String,
        forge: Forge
    ) {
        val fakeHostIdentity = fakeContext.identityFactory.composeHost(fakeContext.ownerIdentity, fakeHostName)
        fakeMappingContext = fakeContext.copy(
            ownerIdentity = fakeHostIdentity,
            textAndInputPrivacy = forge.aValueFrom(
                TextAndInputPrivacy::class.java,
                exclude = listOf(TextAndInputPrivacy.MASK_ALL)
            )
        )
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
        val result = testedCapture.capture(mockHost, fakeMappingContext, { true }, mockNativeCopier)

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
        assertThat(requestCaptor.firstValue.textAndInputPrivacy).isEqualTo(fakeMappingContext.textAndInputPrivacy)
        assertThat(requestCaptor.firstValue.internalLogger).isSameAs(mockLogger)
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
            testedCapture.capture(mockHost, fakeMappingContext, { true }, mockNativeCopier)
        }

        // Then
        assertThat(result).isNull()
        verify(mockNativeCopier)(mockInteropView, fakeChildIdentity)
        if (fakeThrows) {
            mockLogger.verifyLog(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.TELEMETRY,
                "Composition host decomposition failed; leaving the host content out of the replay",
                throwable = fakeError,
                onlyOnce = true
            )
        } else {
            verifyNoInteractions(mockLogger)
        }

        // Given
        whenever(mockDecomposer.decompose(eq(mockHost), any())).thenReturn(fakeDecomposition)

        // When
        val nextResult = testedCapture.capture(mockHost, fakeMappingContext, { true }, mockNativeCopier)

        // Then
        val nextCapture = checkNotNull(nextResult)
        assertThat(nextCapture.nativeCopies).isEmpty()
    }

    @Test
    fun `M skip decomposition W capture() { capture has already expired }`() {
        // When
        val result = testedCapture.capture(mockHost, fakeMappingContext, { false }, mockNativeCopier)

        // Then
        assertThat(result).isNull()
        verifyNoInteractions(mockDecomposer, mockNativeCopier, mockLogger)
    }

    @Test
    fun `M discard partial output without disabling capture W capture() { cancellation during decomposition }`() {
        // Given
        var fakeAllowed = true
        var fakeRequestAllowed: Boolean? = null
        whenever(mockDecomposer.decompose(eq(mockHost), any())).thenAnswer { invocation ->
            val request = invocation.getArgument<CompositionHostDecomposeRequest>(1)
            request.nativeViewHandoff(mockInteropView, fakeChildIdentity)
            fakeAllowed = false
            fakeRequestAllowed = request.shouldContinue()
            fakeDecomposition
        }

        // When
        val result = testedCapture.capture(mockHost, fakeMappingContext, { fakeAllowed }, mockNativeCopier)

        // Then
        assertThat(result).isNull()
        assertThat(fakeRequestAllowed).isFalse()
        verify(mockNativeCopier)(mockInteropView, fakeChildIdentity)
        verifyNoInteractions(mockLogger)

        // Given
        whenever(mockDecomposer.decompose(eq(mockHost), any())).thenReturn(fakeDecomposition)

        // When
        val nextResult = testedCapture.capture(mockHost, fakeMappingContext, { true }, mockNativeCopier)

        // Then
        val nextCapture = checkNotNull(nextResult)
        assertThat(nextCapture.nativeCopies).isEmpty()
        assertThat(nextCapture.decomposition).isSameAs(fakeDecomposition)
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
            fakeNestedCapture = testedCapture.capture(mockNestedHost, fakeMappingContext, { true }, mockNativeCopier)
            fakeDecomposition
        }

        // When
        val result = testedCapture.capture(mockHost, fakeMappingContext, { true }, mockNativeCopier)

        // Then
        val capturedHost = checkNotNull(result)
        val nestedCapture = checkNotNull(fakeNestedCapture)
        assertThat(capturedHost.nativeCopies).containsExactly(fakeNativeCopy)
        assertThat(nestedCapture.nativeCopies).containsExactly(fakeNestedCopy)
    }

    @Test
    fun `M treat the view as a native view and log W canDecompose() { recognition throws }`(
        @StringForgery fakeMessage: String
    ) {
        // Given
        val fakeFailure = NoSuchMethodError(fakeMessage)
        whenever(mockDecomposer.canDecompose(mockHost)).thenThrow(fakeFailure)

        // When
        val result = testedCapture.canDecompose(mockHost)

        // Then
        assertThat(result).isFalse()
        mockLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.TELEMETRY,
            "Composition host recognition failed; capturing the view as a native view",
            throwable = fakeFailure,
            onlyOnce = true
        )
    }

    @Test
    fun `M contain extension failure during live privacy refresh W readTouchOverrideAreas()`(
        @StringForgery fakeMessage: String
    ) {
        // Given
        val fakeFailure = NoSuchMethodError(fakeMessage)
        whenever(mockDecomposer.readTouchOverrideAreas(eq(mockHost), any())).thenThrow(fakeFailure)

        // When
        val result = assertDoesNotThrow<Map<Rect, TouchPrivacy>?> {
            testedCapture.readTouchOverrideAreas(mockHost) { true }
        }

        // Then
        assertThat(result).isNull()
        mockLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.TELEMETRY,
            "Composition touch privacy refresh failed; skipping touch recording",
            throwable = fakeFailure,
            onlyOnce = true
        )
    }
}
