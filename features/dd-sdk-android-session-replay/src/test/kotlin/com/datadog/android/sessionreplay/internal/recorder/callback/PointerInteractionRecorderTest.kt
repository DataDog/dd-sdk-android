/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.callback

import android.view.MotionEvent
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.TouchPrivacyManager
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import com.datadog.android.sessionreplay.internal.utils.SessionReplayRumContext
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.model.MobileSegment.MobileIncrementalData
import com.datadog.android.sessionreplay.model.MobileSegment.MobileRecord
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
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
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class PointerInteractionRecorderTest {

    private lateinit var testedRecorder: PointerInteractionRecorder

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @Mock
    lateinit var mockRumContextProvider: RumContextProvider

    @Mock
    lateinit var mockTouchPrivacyManager: TouchPrivacyManager

    @Mock
    lateinit var mockEventUtils: MotionEventUtils

    private val flushedBatches = mutableListOf<List<MobileRecord>>()

    @IntForgery(min = 1, max = 10)
    var fakeDensity: Int = 1

    @Forgery
    lateinit var fakeRumContext: SessionReplayRumContext

    @LongForgery(min = 0)
    var fakeTimestamp: Long = 0L

    @LongForgery(min = 0L)
    private var fakeElapsedTimeNs: Long = 0L

    @BeforeEach
    fun `set up`() {
        whenever(mockTimeProvider.getDeviceTimestampMillis()).thenReturn(fakeTimestamp)
        whenever(mockTimeProvider.getDeviceElapsedTimeNanos()).thenAnswer { fakeElapsedTimeNs }
        whenever(mockRumContextProvider.getRumContext()).thenReturn(fakeRumContext)
        whenever(mockTouchPrivacyManager.shouldRecordTouch(any())).thenReturn(true)
        testedRecorder = buildRecorder()
    }

    @Test
    fun `M do nothing W recordTouchEvent { TouchPrivacy hides the down location }`(forge: Forge) {
        // Given
        whenever(mockTouchPrivacyManager.shouldRecordTouch(any())).thenReturn(false)
        val downEvent = forge.touchRecords(MobileSegment.PointerEventType.DOWN).asMotionEvent()
        val upEvent = forge.touchRecords(MobileSegment.PointerEventType.UP).asMotionEvent()

        // When
        testedRecorder.recordTouchEvent(downEvent)
        testedRecorder.recordTouchEvent(upEvent)

        // Then
        assertThat(flushedBatches).isEmpty()
    }

    @Test
    fun `M flush on up W recordTouchEvent { full gesture lifecycle }`(forge: Forge) {
        // Given
        val fakeDownRecords = forge.touchRecords(MobileSegment.PointerEventType.DOWN)
        val fakeMoveRecords = forge.touchRecords(MobileSegment.PointerEventType.MOVE)
        val fakeUpRecords = forge.touchRecords(MobileSegment.PointerEventType.UP)

        // When
        testedRecorder.recordTouchEvent(fakeDownRecords.asMotionEvent())
        fakeElapsedTimeNs += TEST_MOTION_UPDATE_DELAY_THRESHOLD_NS
        testedRecorder.recordTouchEvent(fakeMoveRecords.asMotionEvent())
        testedRecorder.recordTouchEvent(fakeUpRecords.asMotionEvent())

        // Then
        assertThat(flushedBatches).containsExactly(fakeDownRecords + fakeMoveRecords + fakeUpRecords)
        assertThat(testedRecorder.pointerInteractions).isEmpty()
    }

    @Test
    fun `M always record the first move after a down W recordTouchEvent`(forge: Forge) {
        // Given
        val fakeDownRecords = forge.touchRecords(MobileSegment.PointerEventType.DOWN)
        val fakeMoveRecords = forge.touchRecords(MobileSegment.PointerEventType.MOVE)

        // When
        testedRecorder.recordTouchEvent(fakeDownRecords.asMotionEvent())
        // no time elapsed at all, yet the first move after a down is still never debounced
        testedRecorder.recordTouchEvent(fakeMoveRecords.asMotionEvent())

        // Then
        assertThat(testedRecorder.pointerInteractions).containsAll(fakeMoveRecords)
    }

    @Test
    fun `M debounce move updates W recordTouchEvent { updates arrive faster than the threshold }`(forge: Forge) {
        // Given
        val fakeDownRecords = forge.touchRecords(MobileSegment.PointerEventType.DOWN)
        val fakeMoveRecords1 = forge.touchRecords(MobileSegment.PointerEventType.MOVE)
        val fakeMoveRecords2 = forge.touchRecords(MobileSegment.PointerEventType.MOVE)
        val fakeMoveRecords3 = forge.touchRecords(MobileSegment.PointerEventType.MOVE)
        val fakeUpRecords = forge.touchRecords(MobileSegment.PointerEventType.UP)

        // When
        testedRecorder.recordTouchEvent(fakeDownRecords.asMotionEvent())
        // the first move after a down is never debounced, and establishes the debounce baseline
        testedRecorder.recordTouchEvent(fakeMoveRecords1.asMotionEvent())
        fakeElapsedTimeNs += TEST_MOTION_UPDATE_DELAY_THRESHOLD_NS / 2
        // skipped: the threshold has not elapsed since the previous recorded move
        testedRecorder.recordTouchEvent(fakeMoveRecords2.asMotionEvent())
        fakeElapsedTimeNs += TEST_MOTION_UPDATE_DELAY_THRESHOLD_NS
        testedRecorder.recordTouchEvent(fakeMoveRecords3.asMotionEvent())
        testedRecorder.recordTouchEvent(fakeUpRecords.asMotionEvent())

        // Then
        assertThat(flushedBatches).containsExactly(
            fakeDownRecords + fakeMoveRecords1 + fakeMoveRecords3 + fakeUpRecords
        )
    }

    @Test
    fun `M perform an intermediary flush W recordTouchEvent { long gesture cycle }`(forge: Forge) {
        // Given
        val fakeDownRecords = forge.touchRecords(MobileSegment.PointerEventType.DOWN)
        val fakeMoveRecords1 = forge.touchRecords(MobileSegment.PointerEventType.MOVE)
        val fakeMoveRecords2 = forge.touchRecords(MobileSegment.PointerEventType.MOVE)

        // When
        testedRecorder.recordTouchEvent(fakeDownRecords.asMotionEvent())
        fakeElapsedTimeNs += TEST_FLUSH_BUFFER_THRESHOLD_NS
        testedRecorder.recordTouchEvent(fakeMoveRecords1.asMotionEvent())
        fakeElapsedTimeNs += TEST_FLUSH_BUFFER_THRESHOLD_NS
        testedRecorder.recordTouchEvent(fakeMoveRecords2.asMotionEvent())

        // Then
        assertThat(flushedBatches).containsExactly(
            fakeDownRecords + fakeMoveRecords1,
            fakeMoveRecords2
        )
    }

    private fun buildRecorder() = PointerInteractionRecorder(
        pixelsDensity = fakeDensity.toFloat(),
        timeProvider = mockTimeProvider,
        rumContextProvider = mockRumContextProvider,
        touchPrivacyManager = mockTouchPrivacyManager,
        onFlush = { flushedBatches += it },
        copyEvent = { it },
        motionEventUtils = mockEventUtils,
        motionUpdateThresholdInNs = TEST_MOTION_UPDATE_DELAY_THRESHOLD_NS,
        flushPositionBufferThresholdInNs = TEST_FLUSH_BUFFER_THRESHOLD_NS
    )

    private fun Forge.touchRecords(
        eventType: MobileSegment.PointerEventType
    ): List<MobileRecord.MobileIncrementalSnapshotRecord> {
        val pointerIds = aList { anInt(min = 1) }
        val positionMaxValue = (FLOAT_MAX_INT_VALUE / fakeDensity).toLong()
        return pointerIds
            .map {
                MobileIncrementalData.PointerInteractionData(
                    eventType,
                    MobileSegment.PointerType.TOUCH,
                    it.toLong(),
                    aLong(min = 0, max = positionMaxValue),
                    aLong(min = 0, max = positionMaxValue)
                )
            }
            .map {
                MobileRecord.MobileIncrementalSnapshotRecord(
                    timestamp = fakeTimestamp + fakeRumContext.viewTimeOffsetMs,
                    data = it
                )
            }
    }

    private fun List<MobileRecord.MobileIncrementalSnapshotRecord>.asMotionEvent(): MotionEvent {
        val mockMotionEvent: MotionEvent = mock { event ->
            whenever(event.pointerCount).thenReturn(this@asMotionEvent.size)
        }
        this.forEachIndexed { index, record ->
            val pointerInteractionData = record.data as MobileIncrementalData.PointerInteractionData
            val pointerId = pointerInteractionData.pointerId.toInt()
            whenever(mockMotionEvent.getPointerId(index)).thenReturn(pointerId)
            val motionEventAction = pointerInteractionData.pointerEventType.asMotionEventAction()
            whenever(mockMotionEvent.action).thenReturn(motionEventAction)
            val expectedXPos = (pointerInteractionData.x.toInt() * fakeDensity).toFloat()
            val expectedYPos = (pointerInteractionData.y.toInt() * fakeDensity).toFloat()
            whenever(mockEventUtils.getPointerAbsoluteX(mockMotionEvent, index)).thenReturn(expectedXPos)
            whenever(mockEventUtils.getPointerAbsoluteY(mockMotionEvent, index)).thenReturn(expectedYPos)
        }
        return mockMotionEvent
    }

    private fun MobileSegment.PointerEventType.asMotionEventAction(): Int = when (this) {
        MobileSegment.PointerEventType.DOWN -> MotionEvent.ACTION_DOWN
        MobileSegment.PointerEventType.UP -> MotionEvent.ACTION_UP
        MobileSegment.PointerEventType.MOVE -> MotionEvent.ACTION_MOVE
    }

    private companion object {
        const val FLOAT_MAX_INT_VALUE = 8_388_608f // 2.0.pow(23.0)
        val TEST_MOTION_UPDATE_DELAY_THRESHOLD_NS: Long = TimeUnit.MILLISECONDS.toNanos(100)
        val TEST_FLUSH_BUFFER_THRESHOLD_NS: Long = TEST_MOTION_UPDATE_DELAY_THRESHOLD_NS * 10
    }
}
