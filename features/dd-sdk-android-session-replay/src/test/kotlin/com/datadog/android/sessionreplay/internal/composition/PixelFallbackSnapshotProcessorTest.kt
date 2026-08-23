/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.graphics.Bitmap
import android.graphics.Rect
import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.CapturedChild
import com.datadog.android.internal.sessionreplay.composition.CapturedLayer
import com.datadog.android.internal.sessionreplay.composition.CapturedLayerKind
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.PixelResource
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.resources.ResourceResolver
import com.datadog.android.sessionreplay.internal.recorder.resources.ResourceResolverCallback
import com.datadog.android.sessionreplay.recorder.privacy.TextDetectionOutcome
import com.datadog.android.sessionreplay.recorder.privacy.TextDetector
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class PixelFallbackSnapshotProcessorTest {

    private val mockResourceResolver: ResourceResolver = mock()
    private val immediateMainThreadExecutor = CaptureMainThreadExecutor { task ->
        task()
        CancellableCaptureWork.NONE
    }

    private fun generationContext(): CaptureGenerationContext = CaptureGenerationContext(
        id = 1L,
        startedAtNs = 0L,
        deadlineNs = 1_000_000_000L,
        timeProvider = CaptureTimeProvider { 0L }
    )

    private class Fixture(scope: String) {
        val identityFactory = DefaultCapturedIdentityFactory(RumViewIdentityScope(scope))
        val ownerIdentity = identityFactory.view(identityFactory.window("window"), "owner")
        val pixelIdentity = identityFactory.imageWireframe(ownerIdentity)
        val bounds = CapturedBounds(0, 0, 100, 100)
        val bitmap: Bitmap = mock()

        val pixelWireframe = CapturedWireframe.Pixel(
            identity = pixelIdentity,
            bounds = bounds,
            resource = PixelResource.Unresolved
        )
        val ownerLayer = CapturedLayer(
            identity = ownerIdentity,
            kind = CapturedLayerKind.NATIVE_VIEW,
            bounds = bounds,
            children = listOf(CapturedChild.Wireframe(pixelIdentity))
        )
        val root = CapturedLayer(
            identity = identityFactory.screenRoot(),
            kind = CapturedLayerKind.SYNTHETIC_SCREEN_ROOT,
            bounds = bounds,
            children = listOf(CapturedChild.Layer(ownerIdentity))
        )
        val snapshot = CapturedFullSnapshot(
            timestamp = 0L,
            scope = RumViewIdentityScope(scope),
            root = root,
            layers = listOf(ownerLayer),
            wireframes = listOf(pixelWireframe)
        )
        val pending = PendingPixelCapture(pixelIdentity, ownerIdentity, bitmap)
        val pendingTextFree = PendingPixelCapture(pixelIdentity, ownerIdentity, bitmap, isTextFree = true)
    }

    @Test
    fun `M complete immediately W process { no pending captures }`(@StringForgery fakeScope: String) {
        // Given
        val fixture = Fixture(fakeScope)
        val testedProcessor = PixelFallbackSnapshotProcessor(
            mockResourceResolver,
            textDetector = null,
            immediateMainThreadExecutor
        )
        val results = mutableListOf<SnapshotProcessingResult>()

        // When
        val work = testedProcessor.process(
            SnapshotProcessingRequest(generationContext(), fixture.snapshot, emptyList(), fixture.identityFactory),
            SnapshotProcessingCallback { results += it }
        )

        // Then
        assertThat(work).isEqualTo(CancellableCaptureWork.NONE)
        assertThat(results).hasSize(1)
        val completed = results.single() as SnapshotProcessingResult.Completed
        assertThat(completed.snapshot).isEqualTo(fixture.snapshot)
    }

    @Test
    fun `M downgrade to a placeholder W process { no text detector configured }`(@StringForgery fakeScope: String) {
        // Given
        val fixture = Fixture(fakeScope)
        val testedProcessor = PixelFallbackSnapshotProcessor(
            mockResourceResolver,
            textDetector = null,
            immediateMainThreadExecutor
        )
        val results = mutableListOf<SnapshotProcessingResult>()

        // When
        testedProcessor.process(
            SnapshotProcessingRequest(
                generationContext(),
                fixture.snapshot,
                listOf(fixture.pending),
                fixture.identityFactory
            ),
            SnapshotProcessingCallback { results += it }
        )

        // Then
        val completed = results.single() as SnapshotProcessingResult.Completed
        val finalSnapshot = completed.snapshot
        assertThat(finalSnapshot.wireframes).noneMatch { it.identity == fixture.pixelIdentity }
        val placeholder = finalSnapshot.wireframes.single() as CapturedWireframe.PrivacyPlaceholder
        assertThat(placeholder.bounds).isEqualTo(fixture.bounds)
        val ownerLayer = finalSnapshot.layers.single { it.identity == fixture.ownerIdentity }
        assertThat(ownerLayer.children).containsExactly(CapturedChild.Wireframe(placeholder.identity))
    }

    @Test
    fun `M resolve the pixel resource W process { capture is text-free, no detector configured }`(
        @StringForgery fakeScope: String,
        @StringForgery fakeResourceId: String
    ) {
        // Given: a TextView/Button background-only rasterization is structurally guaranteed not
        // to contain text (its text is always captured separately), so it must skip the
        // text-detection safety net entirely - even when no detector is configured at all.
        val fixture = Fixture(fakeScope)
        doAnswer { invocation ->
            val callback = invocation.getArgument<ResourceResolverCallback>(1)
            callback.onSuccess(fakeResourceId)
            null
        }.whenever(mockResourceResolver).resolveResourceIdFromBitmap(any(), any())
        val testedProcessor = PixelFallbackSnapshotProcessor(
            mockResourceResolver,
            textDetector = null,
            immediateMainThreadExecutor
        )
        val results = mutableListOf<SnapshotProcessingResult>()

        // When
        testedProcessor.process(
            SnapshotProcessingRequest(
                generationContext(),
                fixture.snapshot,
                listOf(fixture.pendingTextFree),
                fixture.identityFactory
            ),
            SnapshotProcessingCallback { results += it }
        )

        // Then
        val completed = results.single() as SnapshotProcessingResult.Completed
        val resolvedWireframe = completed.snapshot.wireframes.single { it.identity == fixture.pixelIdentity }
            as CapturedWireframe.Pixel
        assertThat(resolvedWireframe.resource).isEqualTo(PixelResource.Resolved(fakeResourceId, "image/webp"))
    }

    @Test
    fun `M downgrade to a placeholder W process { detector reports Unavailable }`(@StringForgery fakeScope: String) {
        // Given
        val fixture = Fixture(fakeScope)
        val mockTextDetector: TextDetector = mock()
        doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val onComplete = invocation.getArgument<(TextDetectionOutcome) -> Unit>(1)
            onComplete(TextDetectionOutcome.Unavailable)
        }.whenever(mockTextDetector).detectTextRegions(any(), any())
        val testedProcessor = PixelFallbackSnapshotProcessor(
            mockResourceResolver,
            mockTextDetector,
            immediateMainThreadExecutor
        )
        val results = mutableListOf<SnapshotProcessingResult>()

        // When
        testedProcessor.process(
            SnapshotProcessingRequest(
                generationContext(),
                fixture.snapshot,
                listOf(fixture.pending),
                fixture.identityFactory
            ),
            SnapshotProcessingCallback { results += it }
        )

        // Then
        val completed = results.single() as SnapshotProcessingResult.Completed
        assertThat(
            completed.snapshot.wireframes.single()
        ).isInstanceOf(CapturedWireframe.PrivacyPlaceholder::class.java)
        verify(mockResourceResolver, never()).resolveResourceIdFromBitmap(any(), any())
    }

    @Test
    fun `M resolve the pixel resource W process { text detected and resource resolution succeeds }`(
        @StringForgery fakeScope: String,
        @StringForgery fakeResourceId: String
    ) {
        // Given
        val fixture = Fixture(fakeScope)
        val mockTextDetector: TextDetector = mock()
        val maskedRegions = listOf(Rect(0, 0, 5, 5))
        doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val onComplete = invocation.getArgument<(TextDetectionOutcome) -> Unit>(1)
            onComplete(TextDetectionOutcome.Detected(maskedRegions))
            null
        }.whenever(mockTextDetector).detectTextRegions(any(), any())
        doAnswer { invocation ->
            val callback = invocation.getArgument<ResourceResolverCallback>(1)
            callback.onSuccess(fakeResourceId)
            null
        }.whenever(mockResourceResolver).resolveResourceIdFromBitmap(any(), any())
        val testedProcessor = PixelFallbackSnapshotProcessor(
            mockResourceResolver,
            mockTextDetector,
            immediateMainThreadExecutor
        )
        val results = mutableListOf<SnapshotProcessingResult>()

        // When
        testedProcessor.process(
            SnapshotProcessingRequest(
                generationContext(),
                fixture.snapshot,
                listOf(fixture.pending),
                fixture.identityFactory
            ),
            SnapshotProcessingCallback { results += it }
        )

        // Then
        val completed = results.single() as SnapshotProcessingResult.Completed
        val pixel = completed.snapshot.wireframes.single() as CapturedWireframe.Pixel
        assertThat(pixel.identity).isEqualTo(fixture.pixelIdentity)
        assertThat(pixel.resource).isEqualTo(PixelResource.Resolved(fakeResourceId, "image/webp"))
        val bitmapCaptor = argumentCaptor<Bitmap>()
        verify(mockResourceResolver).resolveResourceIdFromBitmap(bitmapCaptor.capture(), any())
        assertThat(bitmapCaptor.firstValue).isSameAs(fixture.bitmap)
    }

    @Test
    fun `M downgrade to a placeholder W process { resource resolution fails }`(@StringForgery fakeScope: String) {
        // Given
        val fixture = Fixture(fakeScope)
        val mockTextDetector: TextDetector = mock()
        doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val onComplete = invocation.getArgument<(TextDetectionOutcome) -> Unit>(1)
            onComplete(TextDetectionOutcome.Detected(emptyList()))
            null
        }.whenever(mockTextDetector).detectTextRegions(any(), any())
        doAnswer { invocation ->
            val callback = invocation.getArgument<ResourceResolverCallback>(1)
            callback.onFailure()
            null
        }.whenever(mockResourceResolver).resolveResourceIdFromBitmap(any(), any())
        val testedProcessor = PixelFallbackSnapshotProcessor(
            mockResourceResolver,
            mockTextDetector,
            immediateMainThreadExecutor
        )
        val results = mutableListOf<SnapshotProcessingResult>()

        // When
        testedProcessor.process(
            SnapshotProcessingRequest(
                generationContext(),
                fixture.snapshot,
                listOf(fixture.pending),
                fixture.identityFactory
            ),
            SnapshotProcessingCallback { results += it }
        )

        // Then
        val completed = results.single() as SnapshotProcessingResult.Completed
        assertThat(
            completed.snapshot.wireframes.single()
        ).isInstanceOf(CapturedWireframe.PrivacyPlaceholder::class.java)
    }
}
