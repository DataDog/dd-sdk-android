/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.graphics.Rect
import android.view.SurfaceView
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.PixelCopyCapture.Companion.CAPTURE_BUDGET_MS
import com.datadog.android.sessionreplay.internal.recorder.resources.ResourceResolver
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.WireframeSlot
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.GlobalBounds
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

/**
 * [SurfaceView] is used as the isolation view throughout: [PixelCopyCapture.containsHardwareSurface]
 * short-circuits capture for it before any real [android.graphics.Bitmap]/[android.graphics.Canvas]
 * work is attempted, which keeps these tests free of native graphics calls (no `capturedBitmap` is
 * ever set here, so the crop path always resolves to a `null` bitmap and a fast, synchronous
 * [AsyncJobStatusCallback.jobFinished] call).
 */
@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class PixelCopyCaptureTest {

    private lateinit var testedPixelCopyCapture: PixelCopyCapture

    @Mock
    lateinit var mockResourceResolver: ResourceResolver

    @Mock
    lateinit var mockAsyncJobStatusCallback: AsyncJobStatusCallback

    @Forgery
    lateinit var fakeDpBounds: GlobalBounds

    @Forgery
    lateinit var fakeWireframe: MobileSegment.Wireframe.ImageWireframe

    private lateinit var fakeWindowRect: Rect
    private lateinit var fakeIsolationClipRect: Rect

    // SystemClock.elapsedRealtime() is not advanceable in local unit tests (the Android
    // unit-test stub returns a fixed 0L), so PixelCopyCapture's time source is injected here.
    private var fakeElapsedRealtimeMs = 0L

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeWindowRect = Rect(0, 0, forge.aSmallInt() + 1, forge.aSmallInt() + 1)
        fakeIsolationClipRect = Rect(0, 0, fakeWindowRect.width(), fakeWindowRect.height())
        testedPixelCopyCapture = PixelCopyCapture(
            resourceResolver = mockResourceResolver,
            elapsedRealtimeMs = { fakeElapsedRealtimeMs }
        )
        // Mirrors production call order (WindowsOnDrawListener): onPreTraversal marks the start
        // of the snapshot cycle that the CAPTURE_BUDGET_MS deadline is anchored to.
        testedPixelCopyCapture.onPreTraversal(forge.aNullable { aString() })
    }

    @Test
    fun `M call jobStarted immediately W registerPendingCrop()`() {
        // When
        testedPixelCopyCapture.registerPendingCrop(
            nodeId = 1L,
            windowRect = fakeWindowRect,
            dpBounds = fakeDpBounds,
            isolationView = mock<SurfaceView>(),
            isolationClipRect = fakeIsolationClipRect,
            wireframe = fakeWireframe,
            wireframeSlot = WireframeSlot { },
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        verify(mockAsyncJobStatusCallback).jobStarted()
    }

    @Test
    fun `M do nothing W processPendingCrops() {no pending crops}`() {
        // When / Then
        assertThatCode { testedPixelCopyCapture.processPendingCrops(emptyList()) }
            .doesNotThrowAnyException()
    }

    @Test
    fun `M complete normally W processPendingCrops() {within budget}`() {
        // Given
        testedPixelCopyCapture.registerPendingCrop(
            nodeId = 1L,
            windowRect = fakeWindowRect,
            dpBounds = fakeDpBounds,
            isolationView = mock<SurfaceView>(),
            isolationClipRect = fakeIsolationClipRect,
            wireframe = fakeWireframe,
            wireframeSlot = WireframeSlot { fail("Wireframe should not be replaced") },
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // When
        testedPixelCopyCapture.processPendingCrops(emptyList())

        // Then — the crop was actually processed to completion, not skipped
        verify(mockAsyncJobStatusCallback).jobFinished()
    }

    @Test
    fun `M replace with placeholder and skip capture W processPendingCrops() {traversal alone exceeds budget}`(
        forge: Forge
    ) {
        // Given — the budget is anchored at onPreTraversal (start of the whole snapshot cycle,
        // called in set up above), not at the start of this method. Simulate the traversal
        // phase (mapping every other view in the tree) alone taking longer than the budget,
        // before this pending crop is even registered.
        fakeElapsedRealtimeMs += CAPTURE_BUDGET_MS + 50L

        val nodeId = forge.aPositiveInt().toLong()
        val isolationView = mock<SurfaceView>()
        var replacedWireframe: MobileSegment.Wireframe? = null
        testedPixelCopyCapture.registerPendingCrop(
            nodeId = nodeId,
            windowRect = fakeWindowRect,
            dpBounds = fakeDpBounds,
            isolationView = isolationView,
            isolationClipRect = fakeIsolationClipRect,
            wireframe = fakeWireframe,
            wireframeSlot = WireframeSlot { replacedWireframe = it },
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // When
        testedPixelCopyCapture.processPendingCrops(emptyList())

        // Then — no capture work is attempted at all
        verifyNoInteractions(isolationView)
        verify(mockAsyncJobStatusCallback).jobFinished()

        val placeholder = replacedWireframe as? MobileSegment.Wireframe.PlaceholderWireframe
        assertThat(placeholder).isNotNull
        assertThat(placeholder!!.id).isEqualTo(nodeId)
        assertThat(placeholder.x).isEqualTo(fakeDpBounds.x)
        assertThat(placeholder.y).isEqualTo(fakeDpBounds.y)
        assertThat(placeholder.width).isEqualTo(fakeDpBounds.width)
        assertThat(placeholder.height).isEqualTo(fakeDpBounds.height)
    }

    @Test
    fun `M replace only the crops still pending once the budget is exceeded W processPendingCrops()`(
        forge: Forge
    ) {
        // Given — the first crop's completion is stubbed to push the clock past the budget,
        // simulating slow processing mid-cycle; the second crop registered afterwards should
        // then be skipped.
        val firstCallback = mock<AsyncJobStatusCallback>()
        whenever(firstCallback.jobFinished()).then {
            fakeElapsedRealtimeMs += CAPTURE_BUDGET_MS + 50L
            Unit
        }
        testedPixelCopyCapture.registerPendingCrop(
            nodeId = 1L,
            windowRect = fakeWindowRect,
            dpBounds = fakeDpBounds,
            isolationView = mock<SurfaceView>(),
            isolationClipRect = fakeIsolationClipRect,
            wireframe = fakeWireframe,
            wireframeSlot = WireframeSlot { },
            asyncJobStatusCallback = firstCallback
        )

        val skippedNodeId = forge.aPositiveInt().toLong()
        val skippedIsolationView = mock<SurfaceView>()
        val skippedCallback = mock<AsyncJobStatusCallback>()
        var replacedWireframe: MobileSegment.Wireframe? = null
        testedPixelCopyCapture.registerPendingCrop(
            nodeId = skippedNodeId,
            windowRect = fakeWindowRect,
            dpBounds = fakeDpBounds,
            isolationView = skippedIsolationView,
            isolationClipRect = fakeIsolationClipRect,
            wireframe = fakeWireframe,
            wireframeSlot = WireframeSlot { replacedWireframe = it },
            asyncJobStatusCallback = skippedCallback
        )

        // When
        testedPixelCopyCapture.processPendingCrops(emptyList())

        // Then — the second (skipped) crop never attempts any capture work
        verifyNoInteractions(skippedIsolationView)
        verify(skippedCallback).jobFinished()
        assertThat(replacedWireframe).isInstanceOf(MobileSegment.Wireframe.PlaceholderWireframe::class.java)
    }
}
