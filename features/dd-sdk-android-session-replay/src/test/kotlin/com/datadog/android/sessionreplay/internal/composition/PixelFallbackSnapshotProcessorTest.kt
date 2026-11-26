/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.datadog.android.api.InternalLogger
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
import com.datadog.android.sessionreplay.recorder.wrappers.CanvasWrapper
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
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
internal class PixelFallbackSnapshotProcessorTest {

    private val mockResourceResolver: ResourceResolver = mock()
    private val mockInternalLogger: InternalLogger = mock()
    private val mockCanvasWrapper: CanvasWrapper = mock()
    private val mockCanvas: Canvas = mock()
    private val fakeScheduler = FakeCaptureTaskScheduler()
    private var fakeNowNs = 0L

    @BeforeEach
    fun setUp() {
        whenever(mockCanvasWrapper.createCanvas(any())).thenReturn(mockCanvas)
    }

    private fun generationContext(): CaptureGenerationContext = CaptureGenerationContext(
        id = 1L,
        startedAtNs = 0L,
        deadlineNs = FAKE_DEADLINE_NS,
        timeProvider = CaptureTimeProvider { fakeNowNs }
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
            wireframes = listOf(pixelWireframe),
            viewportSize = CapturedViewportSize(0L, 0L)
        )
        val pending = PendingPixelCapture(pixelIdentity, ownerIdentity, bitmap)
        val pendingSkippingDetection =
            PendingPixelCapture(pixelIdentity, ownerIdentity, bitmap, skipsTextDetection = true)

        val otherBitmap: Bitmap = mock()
        val otherOwner = identityFactory.view(identityFactory.window("window"), "other-owner")
        val otherPixel = identityFactory.imageWireframe(otherOwner)
        val twoPixelSnapshot =
            snapshot.copy(wireframes = snapshot.wireframes + pixelWireframe.copy(identity = otherPixel))
    }

    @Test
    fun `M complete immediately W process { no pending captures }`(@StringForgery fakeScope: String) {
        // Given
        val fixture = Fixture(fakeScope)

        // When
        val processed = process(fixture, textDetector = null, captures = emptyList())

        // Then
        assertThat(processed.work).isEqualTo(CancellableCaptureWork.NONE)
        assertThat(processed.snapshot).isEqualTo(fixture.snapshot)
    }

    @Test
    fun `M skip captures without a wireframe W process { wireframe dropped by construction }`(
        @StringForgery fakeScope: String
    ) {
        // Given
        val fixture = Fixture(fakeScope)
        val fakeSnapshot = fixture.snapshot.copy(wireframes = emptyList())

        // When
        val processed = process(
            fixture,
            textDetector = null,
            captures = listOf(fixture.pendingSkippingDetection),
            snapshot = fakeSnapshot
        )

        // Then
        verifyNoInteractions(mockResourceResolver)
        assertThat(processed.work).isEqualTo(CancellableCaptureWork.NONE)
        assertThat(processed.snapshot).isEqualTo(fakeSnapshot)
    }

    @Test
    fun `M downgrade to a placeholder W process { no text detector configured }`(@StringForgery fakeScope: String) {
        // Given
        val fixture = Fixture(fakeScope)

        // When
        val processed = process(fixture, textDetector = null)

        // Then
        val placeholder = processed.snapshot.wireframes.single() as CapturedWireframe.PrivacyPlaceholder
        assertThat(placeholder.bounds).isEqualTo(fixture.bounds)
        val ownerLayer = processed.snapshot.layers.single { it.identity == fixture.ownerIdentity }
        assertThat(ownerLayer.children).containsExactly(CapturedChild.Wireframe(placeholder.identity))
    }

    @Test
    fun `M resolve the pixel resource W process { capture skips text detection, no detector configured }`(
        @StringForgery fakeScope: String,
        @StringForgery fakeResourceId: String
    ) {
        // Given
        val fixture = Fixture(fakeScope)
        stubResolution(fakeResourceId)

        // When
        val processed = process(fixture, textDetector = null, captures = listOf(fixture.pendingSkippingDetection))

        // Then
        assertResolved(processed.snapshot, fakeResourceId)
    }

    @Test
    fun `M downgrade to a placeholder W process { detector reports Unavailable }`(@StringForgery fakeScope: String) {
        // Given
        val fixture = Fixture(fakeScope)

        // When
        val processed = process(fixture, TextDetector { _, onComplete -> onComplete(TextDetectionOutcome.Unavailable) })

        // Then
        assertPlaceholderWithoutUpload(processed.snapshot)
    }

    @Test
    fun `M paint the regions then resolve the pixel resource W process { text detected }`(
        @StringForgery fakeScope: String,
        @StringForgery fakeResourceId: String
    ) {
        // Given
        val fixture = Fixture(fakeScope)
        val fakeRegion = Rect(0, 0, 5, 5)
        stubResolution(fakeResourceId)

        // When
        val processed = process(
            fixture,
            TextDetector { _, onComplete -> onComplete(TextDetectionOutcome.Detected(listOf(fakeRegion))) }
        )

        // Then
        assertResolved(processed.snapshot, fakeResourceId)
        verify(mockCanvasWrapper).createCanvas(fixture.bitmap)
        verify(mockCanvas).drawRect(eq(fakeRegion), any<Paint>())
        verify(mockResourceResolver).resolveResourceIdFromBitmap(eq(fixture.bitmap), any())
    }

    @Test
    fun `M resolve the pixel resource without painting W process { no text detected }`(
        @StringForgery fakeScope: String,
        @StringForgery fakeResourceId: String
    ) {
        // Given
        val fixture = Fixture(fakeScope)
        stubResolution(fakeResourceId)

        // When
        val processed = process(
            fixture,
            TextDetector { _, onComplete -> onComplete(TextDetectionOutcome.Detected(emptyList())) }
        )

        // Then
        assertResolved(processed.snapshot, fakeResourceId)
        verifyNoInteractions(mockCanvasWrapper)
    }

    @Test
    fun `M downgrade to a placeholder W process { resource resolution fails }`(@StringForgery fakeScope: String) {
        // Given
        val fixture = Fixture(fakeScope)
        stubResolution(resourceId = null)

        // When
        val processed = process(
            fixture,
            TextDetector { _, onComplete -> onComplete(TextDetectionOutcome.Detected(emptyList())) }
        )

        // Then
        assertThat(processed.snapshot.wireframes.single())
            .isInstanceOf(CapturedWireframe.PrivacyPlaceholder::class.java)
    }

    @Test
    fun `M downgrade to a placeholder and never upload W process { regions cannot be painted }`(
        @StringForgery fakeScope: String
    ) {
        // Given
        val fixture = Fixture(fakeScope)
        whenever(mockCanvasWrapper.createCanvas(any())).thenReturn(null)

        // When
        val processed = process(fixture, detectedRegion())

        // Then
        assertPlaceholderWithoutUpload(processed.snapshot)
    }

    @Test
    fun `M downgrade to a placeholder and never upload W process { painting a region throws }`(
        @StringForgery fakeScope: String
    ) {
        // Given
        val fixture = Fixture(fakeScope)
        doThrow(IllegalStateException()).whenever(mockCanvas).drawRect(any<Rect>(), any<Paint>())

        // When
        val processed = process(fixture, detectedRegion())

        // Then
        assertPlaceholderWithoutUpload(processed.snapshot)
    }

    @Test
    fun `M downgrade to a placeholder W process { detector throws }`(@StringForgery fakeScope: String) {
        // Given
        val fixture = Fixture(fakeScope)

        // When
        val processed = process(fixture, TextDetector { _, _ -> throw IllegalArgumentException() })

        // Then
        assertPlaceholderWithoutUpload(processed.snapshot)
        assertThat(fakeScheduler.cancelCount).isEqualTo(1)
    }

    @Test
    fun `M downgrade to a placeholder W process { detector fails to link }`(@StringForgery fakeScope: String) {
        // Given
        val fixture = Fixture(fakeScope)

        // When
        val processed = process(fixture, TextDetector { _, _ -> throw NoClassDefFoundError() })

        // Then
        assertPlaceholderWithoutUpload(processed.snapshot)
    }

    @Test
    fun `M downgrade to a placeholder W process { detector fails an assertion }`(@StringForgery fakeScope: String) {
        // Given
        val fixture = Fixture(fakeScope)

        // When
        val processed = process(fixture, TextDetector { _, _ -> throw AssertionError() })

        // Then
        assertPlaceholderWithoutUpload(processed.snapshot)
    }

    @Test
    fun `M keep the first result W process { detector completes twice }`(
        @StringForgery fakeScope: String,
        @StringForgery fakeResourceId: String
    ) {
        // Given
        val fixture = Fixture(fakeScope)
        stubResolution(fakeResourceId)

        // When
        val processed = process(
            fixture,
            TextDetector { _, onComplete ->
                onComplete(TextDetectionOutcome.Detected(emptyList()))
                onComplete(TextDetectionOutcome.Unavailable)
                onComplete(TextDetectionOutcome.Detected(emptyList()))
            }
        )

        // Then
        assertResolved(processed.snapshot, fakeResourceId)
        verify(mockResourceResolver).resolveResourceIdFromBitmap(any(), any())
    }

    @Test
    fun `M wait for the other captures W process { resolver reports twice for one capture }`(
        @StringForgery fakeScope: String
    ) {
        // Given
        val fixture = Fixture(fakeScope)
        doAnswer { invocation ->
            val callback = invocation.getArgument<ResourceResolverCallback>(1)
            callback.onSuccess("first")
            callback.onFailure()
            null
        }.whenever(mockResourceResolver).resolveResourceIdFromBitmap(any(), any())
        var otherOnComplete: ((TextDetectionOutcome) -> Unit)? = null

        // When
        val processed = process(
            fixture,
            TextDetector { bitmap, onComplete ->
                if (bitmap === fixture.otherBitmap) {
                    otherOnComplete = onComplete
                } else {
                    onComplete(TextDetectionOutcome.Detected(emptyList()))
                }
            },
            captures = listOf(
                fixture.pending,
                PendingPixelCapture(fixture.otherPixel, fixture.otherOwner, fixture.otherBitmap)
            ),
            snapshot = fixture.twoPixelSnapshot
        )

        // Then
        assertThat(processed.results).isEmpty()
        otherOnComplete?.invoke(TextDetectionOutcome.Unavailable)
        assertThat(processed.results).hasSize(1)
    }

    @Test
    fun `M arm the timeout before calling the detector W process { detector blocks }`(
        @StringForgery fakeScope: String
    ) {
        // Given
        val fixture = Fixture(fakeScope)
        var scheduledWhenCalled = 0

        // When
        process(fixture, TextDetector { _, _ -> scheduledWhenCalled = fakeScheduler.scheduled.size })

        // Then
        assertThat(scheduledWhenCalled).isEqualTo(1)
    }

    @Test
    fun `M keep the encoding W process { detector throws after completing, resolver is asynchronous }`(
        @StringForgery fakeScope: String,
        @StringForgery fakeResourceId: String
    ) {
        // Given
        val fixture = Fixture(fakeScope)
        var capturedCallback: ResourceResolverCallback? = null
        doAnswer { invocation ->
            capturedCallback = invocation.getArgument<ResourceResolverCallback>(1)
            null
        }.whenever(mockResourceResolver).resolveResourceIdFromBitmap(any(), any())
        val processed = process(
            fixture,
            TextDetector { _, onComplete ->
                onComplete(TextDetectionOutcome.Detected(emptyList()))
                error("fake detector failure")
            }
        )

        // When
        capturedCallback?.onSuccess(fakeResourceId)

        // Then
        assertResolved(processed.snapshot, fakeResourceId)
    }

    @Test
    fun `M cancel the timeout W process { detection completes before process returns }`(
        @StringForgery fakeScope: String
    ) {
        // Given
        val fixture = Fixture(fakeScope)

        // When
        process(fixture, TextDetector { _, onComplete -> onComplete(TextDetectionOutcome.Unavailable) })

        // Then
        assertThat(fakeScheduler.cancelCount).isEqualTo(1)
    }

    @Test
    fun `M downgrade to a placeholder and ignore the late result W process { detector misses its deadline }`(
        @StringForgery fakeScope: String
    ) {
        // Given
        val fixture = Fixture(fakeScope)
        var capturedOnComplete: ((TextDetectionOutcome) -> Unit)? = null
        val processed = process(fixture, TextDetector { _, onComplete -> capturedOnComplete = onComplete })
        assertThat(processed.results).isEmpty()

        // When
        val (delayNs, timeoutTask) = fakeScheduler.scheduled.single()
        timeoutTask()
        capturedOnComplete?.invoke(TextDetectionOutcome.Detected(listOf(Rect(0, 0, 5, 5))))

        // Then
        assertThat(delayNs).isEqualTo(
            FAKE_DEADLINE_NS * PixelFallbackSnapshotProcessor.DETECTION_BUDGET_PERCENT / 100
        )
        assertPlaceholderWithoutUpload(processed.snapshot)
        verifyNoInteractions(mockCanvasWrapper)
    }

    @Test
    fun `M cancel the timeout W cancel returned work`(@StringForgery fakeScope: String) {
        // Given
        val fixture = Fixture(fakeScope)
        val processed = process(fixture, TextDetector { _, _ -> })

        // When
        processed.work.cancel()

        // Then
        assertThat(fakeScheduler.cancelCount).isEqualTo(1)
    }

    @Test
    fun `M neither paint nor encode W process { generation is over before the detector completes }`(
        @StringForgery fakeScope: String
    ) {
        // Given
        val fixture = Fixture(fakeScope)
        var capturedOnComplete: ((TextDetectionOutcome) -> Unit)? = null
        process(fixture, TextDetector { _, onComplete -> capturedOnComplete = onComplete })

        // When
        fakeNowNs = FAKE_DEADLINE_NS + 1
        capturedOnComplete?.invoke(TextDetectionOutcome.Detected(listOf(Rect(0, 0, 5, 5))))

        // Then
        verifyNoInteractions(mockCanvasWrapper)
        verify(mockResourceResolver, never()).resolveResourceIdFromBitmap(any(), any())
    }

    @Test
    fun `M not encode W process { generation is over before a capture with no detection starts }`(
        @StringForgery fakeScope: String
    ) {
        // Given
        val fixture = Fixture(fakeScope)

        // When
        process(
            fixture,
            TextDetector { _, _ -> fakeNowNs = FAKE_DEADLINE_NS + 1 },
            captures = listOf(
                fixture.pending,
                PendingPixelCapture(fixture.otherPixel, fixture.otherOwner, fixture.otherBitmap, true)
            ),
            snapshot = fixture.twoPixelSnapshot
        )

        // Then
        verifyNoInteractions(mockResourceResolver)
    }

    private class Processed(val work: CancellableCaptureWork, val results: List<SnapshotProcessingResult>) {
        val snapshot: CapturedFullSnapshot get() = (results.single() as SnapshotProcessingResult.Completed).snapshot
    }

    private class FakeCaptureTaskScheduler : CaptureTaskScheduler {
        val scheduled = mutableListOf<Pair<Long, () -> Unit>>()
        var cancelCount = 0

        override fun schedule(delayNs: Long, task: () -> Unit): CancellableCaptureWork {
            scheduled += delayNs to task
            return CancellableCaptureWork { cancelCount++ }
        }
    }

    private fun process(
        fixture: Fixture,
        textDetector: TextDetector?,
        captures: List<PendingPixelCapture> = listOf(fixture.pending),
        snapshot: CapturedFullSnapshot = fixture.snapshot
    ): Processed {
        val testedProcessor = PixelFallbackSnapshotProcessor(
            resourceResolver = mockResourceResolver,
            textDetector = textDetector,
            internalLogger = mockInternalLogger,
            detectionTimeoutScheduler = fakeScheduler,
            canvasWrapper = mockCanvasWrapper
        )
        val results = mutableListOf<SnapshotProcessingResult>()
        val work = testedProcessor.process(
            SnapshotProcessingRequest(generationContext(), snapshot, captures, fixture.identityFactory),
            SnapshotProcessingCallback { results += it }
        )
        return Processed(work, results)
    }

    private fun detectedRegion() = TextDetector { _, onComplete ->
        onComplete(TextDetectionOutcome.Detected(listOf(Rect(0, 0, 5, 5))))
    }

    private fun stubResolution(resourceId: String?) {
        doAnswer { invocation ->
            val callback = invocation.getArgument<ResourceResolverCallback>(1)
            if (resourceId != null) callback.onSuccess(resourceId) else callback.onFailure()
            null
        }.whenever(mockResourceResolver).resolveResourceIdFromBitmap(any(), any())
    }

    private fun assertResolved(snapshot: CapturedFullSnapshot, resourceId: String) {
        val pixel = snapshot.wireframes.single() as CapturedWireframe.Pixel
        assertThat(pixel.resource).isEqualTo(PixelResource.Resolved(resourceId, "image/webp"))
    }

    private fun assertPlaceholderWithoutUpload(snapshot: CapturedFullSnapshot) {
        assertThat(snapshot.wireframes.single()).isInstanceOf(CapturedWireframe.PrivacyPlaceholder::class.java)
        verify(mockResourceResolver, never()).resolveResourceIdFromBitmap(any(), any())
    }

    private companion object {
        const val FAKE_DEADLINE_NS = 1_000_000_000L
    }
}
