/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.content.Context
import android.content.res.Resources
import android.util.DisplayMetrics
import android.view.MotionEvent
import android.view.Window
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.TouchPrivacyManager
import com.datadog.android.sessionreplay.internal.processor.EnrichedRecord
import com.datadog.android.sessionreplay.internal.recorder.callback.MotionEventUtils
import com.datadog.android.sessionreplay.internal.storage.RecordWriter
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import com.datadog.android.sessionreplay.internal.utils.SessionReplayRumContext
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.model.MobileSegment.MobileIncrementalData
import com.datadog.android.sessionreplay.model.MobileSegment.MobileRecord
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.TimeUnit
import kotlin.jvm.internal.Intrinsics

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class CompositionWindowTouchCallbackTest {

    private lateinit var testedCallback: CompositionWindowTouchCallback

    @Mock
    lateinit var mockWrappedCallback: Window.Callback

    @Mock
    lateinit var mockRecordWriter: RecordWriter

    @Mock
    lateinit var mockTouchPrivacyManager: TouchPrivacyManager

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @Mock
    lateinit var mockRumContextProvider: RumContextProvider

    @Mock
    lateinit var mockContext: Context

    @Mock
    lateinit var mockEventUtils: MotionEventUtils

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @LongForgery(min = 0)
    var fakeTimestamp: Long = 0L

    // ints avoid flakiness when converting to longs during density normalization.
    @IntForgery(min = 1, max = 10)
    var fakeDensity: Int = 1

    @Forgery
    lateinit var fakeRumContext: SessionReplayRumContext

    @LongForgery(min = 0L)
    private var fakeElapsedTimeNs: Long = 0L
    private var fakeReadyScope: TouchPrivacyScope? = null

    @BeforeEach
    fun `set up`() {
        fakeReadyScope = TouchPrivacyScope(RumViewIdentityScope(fakeRumContext.viewId), 0L)
        val mockResources = mock<Resources> {
            val fakeDisplayMetrics = DisplayMetrics().apply { density = fakeDensity.toFloat() }
            whenever(it.displayMetrics).thenReturn(fakeDisplayMetrics)
        }
        whenever(mockContext.resources).thenReturn(mockResources)
        whenever(mockTimeProvider.getDeviceTimestampMillis()).thenReturn(fakeTimestamp)
        whenever(mockTimeProvider.getDeviceElapsedTimeNanos()).thenAnswer { fakeElapsedTimeNs }
        whenever(mockRumContextProvider.getRumContext()).thenReturn(fakeRumContext)
        whenever(mockTouchPrivacyManager.shouldRecordTouch(any())).thenReturn(true)
        testedCallback = buildCallback()
    }

    @Test
    fun `M delegate to the wrappedCallback W dispatchTouchEvent`(@BoolForgery fakeReturnedValue: Boolean) {
        // Given
        val mockEvent: MotionEvent = mock()
        whenever(mockWrappedCallback.dispatchTouchEvent(mockEvent)).thenReturn(fakeReturnedValue)

        // When
        val eventConsumed = testedCallback.dispatchTouchEvent(mockEvent)

        // Then
        verify(mockWrappedCallback).dispatchTouchEvent(mockEvent)
        assertThat(eventConsumed).isEqualTo(fakeReturnedValue)
    }

    @Test
    fun `M recycle the copy after using it W dispatchTouchEvent`(forge: Forge) {
        // Given
        whenever(mockWrappedCallback.dispatchTouchEvent(any())).thenReturn(forge.aBool())
        val mockEvent: MotionEvent = mock()

        // When
        testedCallback.dispatchTouchEvent(mockEvent)

        // Then
        verify(mockEvent).recycle()
    }

    @Test
    fun `M log a null event error W dispatchTouchEvent { event is null }`() {
        // When
        testedCallback.dispatchTouchEvent(null)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            "CompositionWindowTouchCallback: intercepted null motion event"
        )
        verifyNoInteractions(mockRecordWriter)
        verifyNoInteractions(mockWrappedCallback)
    }

    @Test
    fun `M log the exception W dispatchTouchEvent() { wrappedCallback throws, null parameter }`() {
        // Given
        val mockEvent: MotionEvent = mock()
        whenever(mockWrappedCallback.dispatchTouchEvent(mockEvent)).thenAnswer {
            Intrinsics.checkNotNullParameter(null, "event")
        }

        // When
        testedCallback.dispatchTouchEvent(mockEvent)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            "CompositionWindowTouchCallback: wrapped callback failed to handle the motion event",
            throwableClass = NullPointerException::class.java
        )
    }

    @Test
    fun `M propagate the exception W dispatchTouchEvent() { wrappedCallback throws }`(
        @Forgery fakeException: Exception
    ) {
        // Given
        val mockEvent: MotionEvent = mock()
        whenever(mockWrappedCallback.dispatchTouchEvent(mockEvent)).thenThrow(fakeException)

        // When / Then
        val caught = assertThrows<Exception> { testedCallback.dispatchTouchEvent(mockEvent) }
        assertThat(caught).isEqualTo(fakeException)
    }

    @Test
    fun `M use absolute coordinates for touch privacy check W dispatchTouchEvent { ActionDown }`(
        forge: Forge
    ) {
        // Given
        val mockEvent: MotionEvent = mock { whenever(it.action).thenReturn(MotionEvent.ACTION_DOWN) }
        whenever(mockEventUtils.getPointerAbsoluteX(mockEvent, 0)).thenReturn(forge.aFloat(min = 0f, max = 500f))
        whenever(mockEventUtils.getPointerAbsoluteY(mockEvent, 0)).thenReturn(forge.aFloat(min = 0f, max = 500f))

        // When
        testedCallback.dispatchTouchEvent(mockEvent)

        // Then
        verify(mockEventUtils).getPointerAbsoluteX(mockEvent, 0)
        verify(mockEventUtils).getPointerAbsoluteY(mockEvent, 0)
        verify(mockEvent, never()).x
        verify(mockEvent, never()).y
        verify(mockTouchPrivacyManager).shouldRecordTouch(isA())
    }

    @Test
    fun `M not record positions W dispatchTouchEvent { TouchPrivacy hides the down location }`(forge: Forge) {
        // Given
        whenever(mockTouchPrivacyManager.shouldRecordTouch(any())).thenReturn(false)
        val fakeRecords = forge.touchRecords(MobileSegment.PointerEventType.DOWN)
        val mockDownEvent = fakeRecords.asMockMotionEvent()

        // When
        testedCallback.dispatchTouchEvent(mockDownEvent)
        val mockUpEvent = forge.touchRecords(MobileSegment.PointerEventType.UP).asMockMotionEvent()
        testedCallback.dispatchTouchEvent(mockUpEvent)

        // Then
        verifyNoInteractions(mockRecordWriter)
    }

    @Test
    fun `M flush the positions W dispatchTouchEvent { full gesture lifecycle, valid rum context }`(forge: Forge) {
        // Given
        val fakeDownRecords = forge.touchRecords(MobileSegment.PointerEventType.DOWN)
        val fakeMoveRecords = forge.touchRecords(MobileSegment.PointerEventType.MOVE)
        val fakeUpRecords = forge.touchRecords(MobileSegment.PointerEventType.UP)

        // When
        testedCallback.dispatchTouchEvent(fakeDownRecords.asMockMotionEvent())
        fakeElapsedTimeNs += TEST_MOTION_UPDATE_DELAY_THRESHOLD_NS
        testedCallback.dispatchTouchEvent(fakeMoveRecords.asMockMotionEvent())
        testedCallback.dispatchTouchEvent(fakeUpRecords.asMockMotionEvent())

        // Then
        val expectedRecords = fakeDownRecords + fakeMoveRecords + fakeUpRecords
        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockRecordWriter).write(captor.capture(), isA())
        assertThat(captor.firstValue).isEqualTo(
            EnrichedRecord(
                applicationId = fakeRumContext.applicationId,
                sessionId = fakeRumContext.sessionId,
                viewId = fakeRumContext.viewId,
                records = expectedRecords
            )
        )
    }

    @Test
    fun `M drop the buffered positions W dispatchTouchEvent { rum context is invalid at flush time }`(forge: Forge) {
        // Given
        whenever(mockRumContextProvider.getRumContext()).thenReturn(SessionReplayRumContext())
        val fakeUpRecords = forge.touchRecords(MobileSegment.PointerEventType.UP)

        // When
        testedCallback.dispatchTouchEvent(fakeUpRecords.asMockMotionEvent())

        // Then
        verifyNoInteractions(mockRecordWriter)
    }

    @Test
    fun `M debounce the positions update W dispatchTouchEvent { one gesture cycle }`(forge: Forge) {
        // Given
        val fakeEvent1Records = forge.touchRecords(MobileSegment.PointerEventType.DOWN)
        val fakeEvent2Records = forge.touchRecords(MobileSegment.PointerEventType.MOVE)
        val fakeEvent3Records = forge.touchRecords(MobileSegment.PointerEventType.MOVE)
        val fakeEvent4Records = forge.touchRecords(MobileSegment.PointerEventType.MOVE)
        val fakeEvent5Records = forge.touchRecords(MobileSegment.PointerEventType.UP)

        // When
        testedCallback.dispatchTouchEvent(fakeEvent1Records.asMockMotionEvent())
        fakeElapsedTimeNs += TEST_MOTION_UPDATE_DELAY_THRESHOLD_NS
        testedCallback.dispatchTouchEvent(fakeEvent2Records.asMockMotionEvent())
        fakeElapsedTimeNs += TEST_MOTION_UPDATE_DELAY_THRESHOLD_NS / 2
        testedCallback.dispatchTouchEvent(fakeEvent3Records.asMockMotionEvent())
        fakeElapsedTimeNs += TEST_MOTION_UPDATE_DELAY_THRESHOLD_NS
        testedCallback.dispatchTouchEvent(fakeEvent4Records.asMockMotionEvent())
        testedCallback.dispatchTouchEvent(fakeEvent5Records.asMockMotionEvent())

        // Then
        val expectedRecords = fakeEvent1Records + fakeEvent2Records + fakeEvent4Records + fakeEvent5Records
        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockRecordWriter).write(captor.capture(), isA())
        assertThat(captor.firstValue.records).isEqualTo(expectedRecords)
    }

    @Test
    fun `M perform an intermediary flush W dispatchTouchEvent { long gesture cycle }`(forge: Forge) {
        // Given
        val fakeDownRecords = forge.touchRecords(MobileSegment.PointerEventType.DOWN)
        val fakeMoveRecords1 = forge.touchRecords(MobileSegment.PointerEventType.MOVE)
        val fakeMoveRecords2 = forge.touchRecords(MobileSegment.PointerEventType.MOVE)

        // When
        testedCallback.dispatchTouchEvent(fakeDownRecords.asMockMotionEvent())
        fakeElapsedTimeNs += TEST_FLUSH_BUFFER_THRESHOLD_NS
        testedCallback.dispatchTouchEvent(fakeMoveRecords1.asMockMotionEvent())
        fakeElapsedTimeNs += TEST_FLUSH_BUFFER_THRESHOLD_NS
        testedCallback.dispatchTouchEvent(fakeMoveRecords2.asMockMotionEvent())

        // Then
        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockRecordWriter, times(2)).write(captor.capture(), isA())
        assertThat(captor.firstValue.records).isEqualTo(fakeDownRecords + fakeMoveRecords1)
        assertThat(captor.secondValue.records).isEqualTo(fakeMoveRecords2)
    }

    @Test
    fun `M discard a gesture and its buffer W dispatchTouchEvent() { privacy scope changes }`(forge: Forge) {
        // Given
        testedCallback.dispatchTouchEvent(forge.touchRecords(MobileSegment.PointerEventType.DOWN).asMockMotionEvent())

        // When
        fakeReadyScope = TouchPrivacyScope(RumViewIdentityScope(fakeRumContext.viewId + "-next"), 0L)
        testedCallback.dispatchTouchEvent(forge.touchRecords(MobileSegment.PointerEventType.UP).asMockMotionEvent())

        // Then
        verifyNoInteractions(mockRecordWriter)
    }

    @Test
    fun `M record a fresh gesture W dispatchTouchEvent() { previous RUM view gesture discarded }`(forge: Forge) {
        // Given
        testedCallback.dispatchTouchEvent(forge.touchRecords(MobileSegment.PointerEventType.DOWN).asMockMotionEvent())

        fakeReadyScope = TouchPrivacyScope(RumViewIdentityScope(fakeRumContext.viewId + "-next"), 0L)
        testedCallback.dispatchTouchEvent(forge.touchRecords(MobileSegment.PointerEventType.UP).asMockMotionEvent())

        val fakeDownRecords = forge.touchRecords(MobileSegment.PointerEventType.DOWN)
        val fakeUpRecords = forge.touchRecords(MobileSegment.PointerEventType.UP)

        // When
        testedCallback.dispatchTouchEvent(fakeDownRecords.asMockMotionEvent())
        testedCallback.dispatchTouchEvent(fakeUpRecords.asMockMotionEvent())
        val captor = argumentCaptor<EnrichedRecord>()

        // Then
        verify(mockRecordWriter).write(captor.capture(), isA())
        assertThat(captor.firstValue.records).isEqualTo(fakeDownRecords + fakeUpRecords)
    }

    @Test
    fun `M discard buffered gesture W dispatchTouchEvent() { privacy revision changes in the same RUM view }`(
        @Forgery fakePrivacyScope: TouchPrivacyScope,
        forge: Forge
    ) {
        // Given
        fakeReadyScope = fakePrivacyScope
        testedCallback.dispatchTouchEvent(forge.touchRecords(MobileSegment.PointerEventType.DOWN).asMockMotionEvent())

        // When
        fakeReadyScope = fakePrivacyScope.copy(version = fakePrivacyScope.version + 1)
        testedCallback.dispatchTouchEvent(forge.touchRecords(MobileSegment.PointerEventType.UP).asMockMotionEvent())

        // Then
        verifyNoInteractions(mockRecordWriter)
    }

    @Test
    fun `M record a fresh gesture W dispatchTouchEvent() { previous privacy revision discarded }`(
        @Forgery fakePrivacyScope: TouchPrivacyScope,
        forge: Forge
    ) {
        // Given
        fakeReadyScope = fakePrivacyScope
        testedCallback.dispatchTouchEvent(forge.touchRecords(MobileSegment.PointerEventType.DOWN).asMockMotionEvent())

        fakeReadyScope = fakePrivacyScope.copy(version = fakePrivacyScope.version + 1)
        testedCallback.dispatchTouchEvent(forge.touchRecords(MobileSegment.PointerEventType.UP).asMockMotionEvent())

        val fakeDownRecords = forge.touchRecords(MobileSegment.PointerEventType.DOWN)
        val fakeUpRecords = forge.touchRecords(MobileSegment.PointerEventType.UP)

        // When
        testedCallback.dispatchTouchEvent(fakeDownRecords.asMockMotionEvent())
        testedCallback.dispatchTouchEvent(fakeUpRecords.asMockMotionEvent())
        val captor = argumentCaptor<EnrichedRecord>()

        // Then
        verify(mockRecordWriter).write(captor.capture(), isA())
        assertThat(captor.firstValue.records).isEqualTo(fakeDownRecords + fakeUpRecords)
    }

    private fun buildCallback() = CompositionWindowTouchCallback(
        appContext = mockContext,
        wrappedCallback = mockWrappedCallback,
        recordWriter = mockRecordWriter,
        timeProvider = mockTimeProvider,
        rumContextProvider = mockRumContextProvider,
        touchPrivacyManager = mockTouchPrivacyManager,
        internalLogger = mockInternalLogger,
        touchRecordingScope = { fakeReadyScope },
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

    private fun List<MobileRecord.MobileIncrementalSnapshotRecord>.asMockMotionEvent(): MotionEvent =
        mock { mockEvent ->
            on { pointerCount } doReturn this@asMockMotionEvent.size
            this@asMockMotionEvent.forEachIndexed { index, record ->
                val fakeInteraction = record.data as MobileIncrementalData.PointerInteractionData
                on { getPointerId(index) } doReturn fakeInteraction.pointerId.toInt()
                on { action } doReturn fakeInteraction.pointerEventType.asMotionEventAction()
                val fakeX = fakeInteraction.x.toInt() * fakeDensity
                val fakeY = fakeInteraction.y.toInt() * fakeDensity
                whenever(mockEventUtils.getPointerAbsoluteX(mockEvent, index)).thenReturn(fakeX.toFloat())
                whenever(mockEventUtils.getPointerAbsoluteY(mockEvent, index)).thenReturn(fakeY.toFloat())
            }
        }

    private fun MobileSegment.PointerEventType.asMotionEventAction(): Int = when (this) {
        MobileSegment.PointerEventType.DOWN -> MotionEvent.ACTION_DOWN
        MobileSegment.PointerEventType.UP -> MotionEvent.ACTION_UP
        MobileSegment.PointerEventType.MOVE -> MotionEvent.ACTION_MOVE
    }

    companion object {
        private const val FLOAT_MAX_INT_VALUE = 8_388_608f // 2.0.pow(23.0)

        // higher threshold values than production to avoid flakiness
        private val TEST_MOTION_UPDATE_DELAY_THRESHOLD_NS: Long = TimeUnit.MILLISECONDS.toNanos(100)
        private val TEST_FLUSH_BUFFER_THRESHOLD_NS: Long = TEST_MOTION_UPDATE_DELAY_THRESHOLD_NS * 10
    }
}
