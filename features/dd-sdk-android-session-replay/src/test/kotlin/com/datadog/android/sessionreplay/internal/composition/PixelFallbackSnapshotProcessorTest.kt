/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.collection.LruCache
import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.CapturedChild
import com.datadog.android.internal.sessionreplay.composition.CapturedLayer
import com.datadog.android.internal.sessionreplay.composition.CapturedLayerKind
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.PixelResource
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.resources.DefaultBitmapSignatureGenerator
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
import java.util.concurrent.TimeUnit

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

    private class FakeCaptureTaskScheduler : CaptureTaskScheduler {
        val scheduled = mutableListOf<Pair<Long, () -> Unit>>()
        var cancelCount = 0

        override fun schedule(delayNs: Long, task: () -> Unit): CancellableCaptureWork {
            scheduled += delayNs to task
            return CancellableCaptureWork { cancelCount++ }
        }
    }

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
        val fakeScheduler = FakeCaptureTaskScheduler()
        val testedProcessor = PixelFallbackSnapshotProcessor(
            mockResourceResolver,
            mockTextDetector,
            immediateMainThreadExecutor,
            fakeScheduler
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
        assertThat(fakeScheduler.cancelCount).isEqualTo(1)
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
        val fakeScheduler = FakeCaptureTaskScheduler()
        val testedProcessor = PixelFallbackSnapshotProcessor(
            mockResourceResolver,
            mockTextDetector,
            immediateMainThreadExecutor,
            fakeScheduler
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
        assertThat(fakeScheduler.cancelCount).isEqualTo(1)
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
        val fakeScheduler = FakeCaptureTaskScheduler()
        val testedProcessor = PixelFallbackSnapshotProcessor(
            mockResourceResolver,
            mockTextDetector,
            immediateMainThreadExecutor,
            fakeScheduler
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
        assertThat(fakeScheduler.cancelCount).isEqualTo(1)
    }

    @Test
    fun `M schedule the detector timeout relative to remaining budget W process()`(@StringForgery fakeScope: String) {
        // Given
        val fixture = Fixture(fakeScope)
        val mockTextDetector: TextDetector = mock() // deliberately never invokes onComplete
        val fakeScheduler = FakeCaptureTaskScheduler()
        val testedProcessor = PixelFallbackSnapshotProcessor(
            mockResourceResolver,
            mockTextDetector,
            immediateMainThreadExecutor,
            fakeScheduler
        )
        val generation = CaptureGenerationContext(
            id = 1L,
            startedAtNs = 0L,
            deadlineNs = 50_000_000L,
            timeProvider = CaptureTimeProvider { 0L } // 50ms remaining, well above the 10ms safety margin
        )

        // When
        testedProcessor.process(
            SnapshotProcessingRequest(generation, fixture.snapshot, listOf(fixture.pending), fixture.identityFactory),
            SnapshotProcessingCallback { }
        )

        // Then
        assertThat(fakeScheduler.scheduled).hasSize(1)
        assertThat(fakeScheduler.scheduled.single().first)
            .isEqualTo(50_000_000L - TimeUnit.MILLISECONDS.toNanos(10))
    }

    @Test
    fun `M resolve to a placeholder W process { timeout fires before detector callback }`(
        @StringForgery fakeScope: String
    ) {
        // Given
        val fixture = Fixture(fakeScope)
        val mockTextDetector: TextDetector = mock() // deliberately never invokes onComplete
        val fakeScheduler = FakeCaptureTaskScheduler()
        val testedProcessor = PixelFallbackSnapshotProcessor(
            mockResourceResolver,
            mockTextDetector,
            immediateMainThreadExecutor,
            fakeScheduler
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
        fakeScheduler.scheduled.single().second.invoke() // fire the timeout manually

        // Then
        val completed = results.single() as SnapshotProcessingResult.Completed
        assertThat(
            completed.snapshot.wireframes.single()
        ).isInstanceOf(CapturedWireframe.PrivacyPlaceholder::class.java)
    }

    @Test
    fun `M ignore a late timeout W process { detector resolves before timeout fires }`(
        @StringForgery fakeScope: String,
        @StringForgery fakeResourceId: String
    ) {
        // Given
        val fixture = Fixture(fakeScope)
        val mockTextDetector: TextDetector = mock()
        doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val onComplete = invocation.getArgument<(TextDetectionOutcome) -> Unit>(1)
            onComplete(TextDetectionOutcome.Detected(emptyList()))
        }.whenever(mockTextDetector).detectTextRegions(any(), any())
        doAnswer { invocation ->
            val callback = invocation.getArgument<ResourceResolverCallback>(1)
            callback.onSuccess(fakeResourceId)
            null
        }.whenever(mockResourceResolver).resolveResourceIdFromBitmap(any(), any())
        val fakeScheduler = FakeCaptureTaskScheduler()
        val testedProcessor = PixelFallbackSnapshotProcessor(
            mockResourceResolver,
            mockTextDetector,
            immediateMainThreadExecutor,
            fakeScheduler
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
        fakeScheduler.scheduled.single().second.invoke() // late-firing timeout, must be a no-op now

        // Then
        val completed = results.single() as SnapshotProcessingResult.Completed
        val pixel = completed.snapshot.wireframes.single() as CapturedWireframe.Pixel
        assertThat(pixel.resource).isEqualTo(PixelResource.Resolved(fakeResourceId, "image/webp"))
    }

    @Test
    fun `M resolve from cache without touching the detector W process { bitmap signature already resolved }`(
        @StringForgery fakeScope: String,
        @StringForgery fakeResourceId: String
    ) {
        // Given: a prior generation already resolved this exact bitmap content once - a fresh
        // generation capturing the same (unchanged) view must not pay for another detector
        // round-trip at all.
        val fixture = Fixture(fakeScope)
        stubBitmapDimensions(fixture.bitmap, width = 20, height = 20)
        val signature = DefaultBitmapSignatureGenerator().generateSignature(fixture.bitmap)
        checkNotNull(signature)
        val cache = LruCache<Long, String>(10).apply { put(signature, fakeResourceId) }
        val mockTextDetector: TextDetector = mock()
        val testedProcessor = PixelFallbackSnapshotProcessor(
            resourceResolver = mockResourceResolver,
            textDetector = mockTextDetector,
            mainThreadExecutor = immediateMainThreadExecutor,
            resolvedContentCache = cache
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
        assertThat(pixel.resource).isEqualTo(PixelResource.Resolved(fakeResourceId, "image/webp"))
        verify(mockTextDetector, never()).detectTextRegions(any(), any())
        verify(mockResourceResolver, never()).resolveResourceIdFromBitmap(any(), any())
    }

    @Test
    fun `M cache a late success and request a fresh capture W process { detector resolves after own timeout fired }`(
        @StringForgery fakeScope: String,
        @StringForgery fakeResourceId: String
    ) {
        // Given: the safety-margin timeout fires first (as it does whenever the 90ms generation
        // budget loses the race against a real detector round-trip), but the detector call itself
        // was never cancelled and keeps running in the background.
        val fixture = Fixture(fakeScope)
        stubBitmapDimensions(fixture.bitmap, width = 20, height = 20)
        val mockTextDetector: TextDetector = mock()
        var lateOnComplete: ((TextDetectionOutcome) -> Unit)? = null
        doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            lateOnComplete = invocation.getArgument<(TextDetectionOutcome) -> Unit>(1)
            null
        }.whenever(mockTextDetector).detectTextRegions(any(), any())
        doAnswer { invocation ->
            val callback = invocation.getArgument<ResourceResolverCallback>(1)
            callback.onSuccess(fakeResourceId)
            null
        }.whenever(mockResourceResolver).resolveResourceIdFromBitmap(any(), any())
        val fakeScheduler = FakeCaptureTaskScheduler()
        val recaptureRequests = mutableListOf<Unit>()
        val cache = LruCache<Long, String>(10)
        val testedProcessor = PixelFallbackSnapshotProcessor(
            resourceResolver = mockResourceResolver,
            textDetector = mockTextDetector,
            mainThreadExecutor = immediateMainThreadExecutor,
            taskScheduler = fakeScheduler,
            recaptureTrigger = RecaptureTrigger { recaptureRequests += Unit },
            resolvedContentCache = cache
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
        fakeScheduler.scheduled.single().second.invoke() // this generation's own deadline expires first
        lateOnComplete?.invoke(TextDetectionOutcome.Detected(emptyList())) // the real result lands after

        // Then: this generation already emitted a placeholder and is not revised in place ...
        val completed = results.single() as SnapshotProcessingResult.Completed
        assertThat(
            completed.snapshot.wireframes.single()
        ).isInstanceOf(CapturedWireframe.PrivacyPlaceholder::class.java)
        // ... but the late success is not wasted: it's cached and a fresh capture is requested so
        // the corrected content reaches the recording without waiting for an unrelated redraw.
        assertThat(recaptureRequests).hasSize(1)
        val signature = DefaultBitmapSignatureGenerator().generateSignature(fixture.bitmap)
        checkNotNull(signature)
        assertThat(cache.get(signature)).isEqualTo(fakeResourceId)
    }

    private fun stubBitmapDimensions(bitmap: Bitmap, width: Int, height: Int) {
        whenever(bitmap.width).thenReturn(width)
        whenever(bitmap.height).thenReturn(height)
    }
}
