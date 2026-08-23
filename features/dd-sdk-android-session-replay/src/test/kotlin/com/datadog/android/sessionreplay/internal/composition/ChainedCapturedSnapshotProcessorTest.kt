/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.graphics.Bitmap
import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.PixelResource
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewData
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperResult
import com.datadog.android.sessionreplay.internal.recorder.resources.ResourceResolver
import com.datadog.android.sessionreplay.internal.recorder.resources.ResourceResolverCallback
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import java.util.concurrent.Executor

internal class ChainedCapturedSnapshotProcessorTest {
    private val fakeFactory = DefaultCapturedIdentityFactory(RumViewIdentityScope("view"))
    private val fakeGeneration = CaptureGenerationContext(1L, 0L, 100L, CaptureTimeProvider { 0L })
    private val fakeOwner = fakeFactory.window("window")
    private val fakePixelIdentity = fakeFactory.imageWireframe(fakeOwner)
    private val mockBitmap: Bitmap = mock()
    private val fakeBounds = CapturedBounds(0, 0, 100, 100)
    private val fakeSnapshot = CapturedNativeSnapshot(
        123L,
        fakeFactory.scope,
        listOf(
            CapturedNativeWindow(
                1f,
                listOf(
                    CapturedNativeView(
                        fakeOwner,
                        -1,
                        fakeBounds,
                        1.0,
                        CapturedViewData.Mapped(
                            CapturedViewMapperResult.Wireframes(
                                listOf(
                                    CapturedWireframe.Pixel(
                                        fakePixelIdentity,
                                        fakeBounds,
                                        resource = PixelResource.Unresolved
                                    )
                                )
                            )
                        ),
                        true,
                        null,
                        null
                    )
                )
            )
        ),
        listOf(PendingPixelCapture(fakePixelIdentity, fakeOwner, mockBitmap, isTextFree = true)),
        fakeFactory
    )

    @Test
    fun `M resolve copied pixels after worker construction W orchestrator processes native capture`() {
        // Given
        val fakeConstructionTasks = mutableListOf<Runnable>()
        val fakeCaptureTasks = mutableListOf<() -> Unit>()
        val fakeMainTasks = mutableListOf<() -> Unit>()
        val fakeCaptures = mutableListOf<CompletedSnapshotCapture>()
        val mockResolver: ResourceResolver = mock()
        doAnswer {
            it.getArgument<ResourceResolverCallback>(1).onSuccess("resource")
            null
        }.`when`(mockResolver).resolveResourceIdFromBitmap(any(), any())
        val fakeMainExecutor = CaptureMainThreadExecutor { task ->
            fakeMainTasks += task
            CancellableCaptureWork.NONE
        }
        val testedProcessor = ChainedCapturedSnapshotProcessor(
            ExecutorCapturedSnapshotProcessor(Executor(fakeConstructionTasks::add), mock()),
            PixelFallbackSnapshotProcessor(mockResolver, null, fakeMainExecutor)
        )
        val testedOrchestrator = SnapshotCaptureOrchestrator(
            producer = CapturedSnapshotProducer { fakeSnapshot },
            processor = testedProcessor,
            consumer = CompletedSnapshotConsumer(fakeCaptures::add),
            timeProvider = CaptureTimeProvider { 0L },
            captureScheduler = CaptureTaskScheduler { _, task ->
                fakeCaptureTasks += task
                CancellableCaptureWork.NONE
            },
            mainThreadExecutor = fakeMainExecutor,
            expiryScheduler = CaptureTaskScheduler { _, _ -> CancellableCaptureWork.NONE }
        )
        testedOrchestrator.start()
        testedOrchestrator.requestCapture()
        fakeCaptureTasks.removeAt(0).invoke()
        fakeMainTasks.removeAt(0).invoke()
        verifyNoInteractions(mockResolver)

        // When
        Thread { fakeConstructionTasks.single().run() }.apply { start(); join() }
        assertThat(fakeCaptures).isEmpty()
        verifyNoInteractions(mockResolver)
        fakeMainTasks.single().invoke()

        // Then
        verify(mockResolver).resolveResourceIdFromBitmap(eq(mockBitmap), any())
        val fakeResult = fakeCaptures.single().snapshot
        assertThat((fakeResult.wireframes.single() as CapturedWireframe.Pixel).resource)
            .isEqualTo(PixelResource.Resolved("resource", "image/webp"))
        assertThat(DefaultCapturedTreeValidator().validate(fakeResult)).isEqualTo(CaptureValidationResult.Valid)
    }

    @Test
    fun `M discard queued construction W cancel`() {
        // Given
        val fakeTasks = mutableListOf<Runnable>()
        val mockPixelProcessor: CapturedSnapshotProcessor = mock()
        val fakeResults = mutableListOf<SnapshotProcessingResult>()
        val testedProcessor = ChainedCapturedSnapshotProcessor(
            ExecutorCapturedSnapshotProcessor(Executor(fakeTasks::add), mock()),
            mockPixelProcessor
        )
        val fakeWork = testedProcessor.process(
            SnapshotProcessingRequest(fakeGeneration, fakeSnapshot),
            fakeResults::add
        )

        // When
        fakeWork.cancel()
        fakeTasks.single().run()

        // Then
        assertThat(fakeGeneration.isActive()).isFalse()
        assertThat(fakeResults).isEmpty()
        verifyNoInteractions(mockPixelProcessor)
    }

    @Test
    fun `M ignore late pixel completion W cancel after construction`() {
        // Given
        lateinit var fakePixelCallback: SnapshotProcessingCallback
        lateinit var fakeConstructedSnapshot: CapturedFullSnapshot
        val mockPixelWork: CancellableCaptureWork = mock()
        val fakeResults = mutableListOf<SnapshotProcessingResult>()
        val testedProcessor = ChainedCapturedSnapshotProcessor(
            ImmediateCapturedSnapshotProcessor(),
            CapturedSnapshotProcessor { request, callback ->
                fakeConstructedSnapshot = request.snapshot as CapturedFullSnapshot
                fakePixelCallback = callback
                mockPixelWork
            }
        )
        val fakeWork = testedProcessor.process(
            SnapshotProcessingRequest(fakeGeneration, fakeSnapshot),
            fakeResults::add
        )

        // When
        fakeWork.cancel()
        fakePixelCallback.onProcessed(SnapshotProcessingResult.Completed(fakeGeneration.id, fakeConstructedSnapshot))

        // Then
        assertThat(fakeGeneration.isActive()).isFalse()
        assertThat(fakeResults).isEmpty()
        verify(mockPixelWork).cancel()
    }

    @Test
    fun `M retain completed generation W cancel after synchronous completion`() {
        // Given
        val fakeResults = mutableListOf<SnapshotProcessingResult>()
        val testedProcessor = ChainedCapturedSnapshotProcessor(
            ImmediateCapturedSnapshotProcessor(),
            ImmediateCapturedSnapshotProcessor()
        )

        // When
        val fakeWork = testedProcessor.process(
            SnapshotProcessingRequest(fakeGeneration, fakeSnapshot),
            fakeResults::add
        )
        fakeWork.cancel()

        // Then
        assertThat(fakeResults).hasSize(1)
        assertThat(fakeGeneration.isActive()).isTrue()
    }
}
