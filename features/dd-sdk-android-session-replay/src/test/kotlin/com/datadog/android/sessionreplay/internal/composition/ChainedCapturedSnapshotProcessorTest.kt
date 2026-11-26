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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
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
        CapturedViewportSize(0L, 0L),
        listOf(PendingPixelCapture(fakePixelIdentity, fakeOwner, mockBitmap, skipsTextDetection = true)),
        fakeFactory
    )

    @Test
    fun `M pass the constructed snapshot and pixel captures to the pixel processor W process()`() {
        // Given
        val fakeConstructed = checkNotNull(CapturedNativeSnapshotBuilder().build(fakeSnapshot, fakeGeneration))
        val fakeResolved = fakeConstructed.copy(timestamp = fakeConstructed.timestamp + 1)
        val fakeRequest = SnapshotProcessingRequest(
            fakeGeneration,
            fakeSnapshot,
            fakeSnapshot.pendingPixelCaptures,
            fakeSnapshot.identityFactory
        )
        lateinit var fakePixelRequest: SnapshotProcessingRequest
        val fakeResults = mutableListOf<SnapshotProcessingResult>()
        val testedProcessor = ChainedCapturedSnapshotProcessor(
            CapturedSnapshotProcessor { _, callback ->
                callback.onProcessed(SnapshotProcessingResult.Completed(fakeGeneration.id, fakeConstructed))
                CancellableCaptureWork.NONE
            },
            CapturedSnapshotProcessor { request, callback ->
                fakePixelRequest = request
                callback.onProcessed(SnapshotProcessingResult.Completed(fakeGeneration.id, fakeResolved))
                CancellableCaptureWork.NONE
            }
        )

        // When
        testedProcessor.process(fakeRequest, fakeResults::add)

        // Then
        assertThat(fakePixelRequest.snapshot).isSameAs(fakeConstructed)
        assertThat(fakePixelRequest.pendingPixelCaptures).isEqualTo(fakeSnapshot.pendingPixelCaptures)
        assertThat(fakePixelRequest.identityFactory).isSameAs(fakeSnapshot.identityFactory)
        assertThat(fakeResults).containsExactly(SnapshotProcessingResult.Completed(fakeGeneration.id, fakeResolved))
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
