/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.metric

import com.datadog.android.api.InternalLogger
import com.datadog.android.rum.internal.domain.scope.RumSessionScope
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class SessionEndedMetricDispatcherTest {

    private val fakeInternalLogger = FakeInternalLogger()

    @Forgery
    private lateinit var fakeStartReason: RumSessionScope.StartReason

    @LongForgery
    private var fakeNtpOffsetAtStart: Long = 0L

    @LongForgery
    private var fakeNtpOffsetAtEnd: Long = 0L

    @BoolForgery
    private var backgroundEventTracking: Boolean = false

    @FloatForgery(min = 0f, max = 100f)
    private var fakeSessionSampleRate: Float = 1f

    @Test
    fun `M register session stop W call onSessionStopped()`(@StringForgery fakeSessionId: String) {
        // Given
        val dispatcher = SessionEndedMetricDispatcher(fakeInternalLogger, fakeSessionSampleRate)
        dispatcher.startMetric(
            fakeSessionId,
            fakeStartReason,
            fakeNtpOffsetAtStart,
            backgroundEventTracking
        )

        // When
        dispatcher.onSessionStopped(fakeSessionId)
        dispatcher.endMetric(fakeSessionId, fakeNtpOffsetAtEnd)

        // Then
        assertThat(fakeInternalLogger.isSessionStopped()).isTrue()
    }

    @Test
    fun `M log error W track view in different sessions`(
        @StringForgery fakeSessionId1: String,
        @StringForgery fakeSessionId2: String,
        @Forgery viewEvent: ViewEvent
    ) {
        // Given
        val dispatcher = SessionEndedMetricDispatcher(fakeInternalLogger, fakeSessionSampleRate)

        // When
        dispatcher.startMetric(
            fakeSessionId1,
            fakeStartReason,
            fakeNtpOffsetAtStart,
            backgroundEventTracking
        )
        dispatcher.startMetric(
            fakeSessionId2,
            fakeStartReason,
            fakeNtpOffsetAtStart,
            backgroundEventTracking
        )
        viewEvent.copy(session = viewEvent.session.copy(id = fakeSessionId2)).apply {
            dispatcher.onViewTracked(fakeSessionId1, this)
        }

        dispatcher.endMetric(fakeSessionId1, fakeNtpOffsetAtEnd)
        dispatcher.endMetric(fakeSessionId2, fakeNtpOffsetAtEnd)

        // Then
        assertThat(fakeInternalLogger.errorLog).isNotNull()
    }

    @Test
    fun `M count views for given session W track view with session id`(
        @StringForgery fakeSessionId1: String,
        @StringForgery fakeSessionId2: String,
        @StringForgery fakeSessionId3: String,
        @StringForgery fakeSessionId4: String,
        @Forgery viewEvents: List<ViewEvent>
    ) {
        // Given
        val dispatcher = SessionEndedMetricDispatcher(fakeInternalLogger, fakeSessionSampleRate)

        // When
        dispatcher.startMetric(
            fakeSessionId1,
            fakeStartReason,
            fakeNtpOffsetAtStart,
            backgroundEventTracking
        )
        dispatcher.startMetric(
            fakeSessionId2,
            fakeStartReason,
            fakeNtpOffsetAtStart,
            backgroundEventTracking
        )
        dispatcher.startMetric(
            fakeSessionId3,
            fakeStartReason,
            fakeNtpOffsetAtStart,
            backgroundEventTracking
        )
        dispatcher.startMetric(
            fakeSessionId4,
            fakeStartReason,
            fakeNtpOffsetAtStart,
            backgroundEventTracking
        )

        viewEvents.map {
            it.copy(session = it.session.copy(id = fakeSessionId2))
        }.forEach {
            dispatcher.onViewTracked(fakeSessionId2, it)
        }

        // Then
        dispatcher.endMetric(fakeSessionId1, fakeNtpOffsetAtEnd)
        assertThat(fakeInternalLogger.getViewCounts()).isEqualTo(0)

        dispatcher.endMetric(fakeSessionId2, fakeNtpOffsetAtEnd)
        assertThat(fakeInternalLogger.getViewCounts()).isEqualTo(fakeInternalLogger.getViewCounts())

        dispatcher.endMetric(fakeSessionId3, fakeNtpOffsetAtEnd)
        assertThat(fakeInternalLogger.getViewCounts()).isEqualTo(0)

        dispatcher.endMetric(fakeSessionId1, fakeNtpOffsetAtEnd)
        assertThat(fakeInternalLogger.getViewCounts()).isEqualTo(0)
    }

    @Test
    fun `M calculate error tracked count W onSdkErrorTracked`(
        @StringForgery fakeSessionId1: String,
        @StringForgery fakeSessionId2: String,
        @StringForgery fakeSessionId3: String,
        @StringForgery fakeSessionId4: String,
        @StringForgery errorKinds: List<String>
    ) {
        // Given
        val dispatcher = SessionEndedMetricDispatcher(fakeInternalLogger, fakeSessionSampleRate)

        // When
        dispatcher.startMetric(
            fakeSessionId1,
            fakeStartReason,
            fakeNtpOffsetAtStart,
            backgroundEventTracking
        )
        dispatcher.startMetric(
            fakeSessionId2,
            fakeStartReason,
            fakeNtpOffsetAtStart,
            backgroundEventTracking
        )
        dispatcher.startMetric(
            fakeSessionId3,
            fakeStartReason,
            fakeNtpOffsetAtStart,
            backgroundEventTracking
        )
        dispatcher.startMetric(
            fakeSessionId4,
            fakeStartReason,
            fakeNtpOffsetAtStart,
            backgroundEventTracking
        )

        // Ends the last started session
        dispatcher.endMetric(fakeSessionId4, fakeNtpOffsetAtEnd)
        // Ends a middle session
        dispatcher.endMetric(fakeSessionId2, fakeNtpOffsetAtEnd)

        errorKinds.forEach {
            dispatcher.onSdkErrorTracked(fakeSessionId3, it)
        }

        // Then
        dispatcher.endMetric(fakeSessionId1, fakeNtpOffsetAtEnd)
        assertThat(fakeInternalLogger.getErrorCounts()).isEqualTo(0)

        // Errors can is reported by the last session when they are tracked
        dispatcher.endMetric(fakeSessionId3, fakeNtpOffsetAtEnd)
        assertThat(fakeInternalLogger.getErrorCounts()).isEqualTo(errorKinds.size)
    }

    @Test
    fun `M has correct 'with_has_replay' W track multiple views`(
        @StringForgery fakeSessionId: String,
        @Forgery viewEvents: List<ViewEvent>
    ) {
        // Given
        val dispatcher = SessionEndedMetricDispatcher(fakeInternalLogger, fakeSessionSampleRate)

        // When
        dispatcher.startMetric(
            fakeSessionId,
            fakeStartReason,
            fakeNtpOffsetAtStart,
            backgroundEventTracking
        )

        viewEvents.map { it.copy(session = it.session.copy(id = fakeSessionId)) }.forEach {
            dispatcher.onViewTracked(fakeSessionId, it)
        }
        dispatcher.endMetric(fakeSessionId, fakeNtpOffsetAtEnd)

        // Then
        assertThat(fakeInternalLogger.getHasReplayCount()).isEqualTo(viewEvents.count { it.session.hasReplay == true })
    }

    @Test
    fun `M has correct 'no_view_events_count' W track missed event`(
        @StringForgery fakeSessionId: String,
        @Forgery missedTypes: List<SessionEndedMetric.MissedEventType>
    ) {
        // Given
        val dispatcher = SessionEndedMetricDispatcher(fakeInternalLogger, fakeSessionSampleRate)

        // When
        dispatcher.startMetric(
            fakeSessionId,
            fakeStartReason,
            fakeNtpOffsetAtStart,
            backgroundEventTracking
        )
        missedTypes.forEach {
            dispatcher.onMissedEventTracked(fakeSessionId, it)
        }
        dispatcher.endMetric(fakeSessionId, fakeNtpOffsetAtEnd)

        // Then
        assertThat(
            fakeInternalLogger.getMissedTypeCount(SessionEndedMetric.MissedEventType.ACTION)
        ).isEqualTo(
            missedTypes.count {
                it == SessionEndedMetric.MissedEventType.ACTION
            }
        )
        assertThat(
            fakeInternalLogger.getMissedTypeCount(SessionEndedMetric.MissedEventType.RESOURCE)
        ).isEqualTo(
            missedTypes.count {
                it == SessionEndedMetric.MissedEventType.RESOURCE
            }
        )
        assertThat(
            fakeInternalLogger.getMissedTypeCount(SessionEndedMetric.MissedEventType.ERROR)
        ).isEqualTo(
            missedTypes.count {
                it == SessionEndedMetric.MissedEventType.ERROR
            }
        )
        assertThat(
            fakeInternalLogger.getMissedTypeCount(SessionEndedMetric.MissedEventType.LONG_TASK)
        ).isEqualTo(
            missedTypes.count {
                it == SessionEndedMetric.MissedEventType.LONG_TASK
            }
        )
    }

    @Test
    fun `M has correct 'has_background_events_tracking_enabled' W start metric`(
        @StringForgery fakeSessionId: String,
        @BoolForgery backgroundEventTracking:
        Boolean
    ) {
        // Given
        val dispatcher = SessionEndedMetricDispatcher(fakeInternalLogger, fakeSessionSampleRate)

        // When
        dispatcher.startMetric(
            fakeSessionId,
            fakeStartReason,
            fakeNtpOffsetAtStart,
            backgroundEventTracking
        )
        dispatcher.endMetric(fakeSessionId, fakeNtpOffsetAtEnd)

        // Then
        assertThat(fakeInternalLogger.getBackgroundEventTracking()).isEqualTo(backgroundEventTracking)
    }

    @Test
    fun `M has correct ntp attribute W start and end metric`(
        @StringForgery fakeSessionId: String,
        @LongForgery fakeNtpOffsetAtStart: Long,
        @LongForgery fakeNtpOffsetAtEnd: Long
    ) {
        // Given
        val dispatcher = SessionEndedMetricDispatcher(fakeInternalLogger, fakeSessionSampleRate)

        // When
        dispatcher.startMetric(
            fakeSessionId,
            fakeStartReason,
            fakeNtpOffsetAtStart,
            backgroundEventTracking
        )
        dispatcher.endMetric(fakeSessionId, fakeNtpOffsetAtEnd)

        // Then
        assertThat(fakeInternalLogger.getNtpAtStartOffset()).isEqualTo(fakeNtpOffsetAtStart)
        assertThat(fakeInternalLogger.getNtpAtEndOffset()).isEqualTo(fakeNtpOffsetAtEnd)
    }

    @Test
    fun `M has correct skipped frames count W start metric`(
        @StringForgery fakeSessionId: String,
        @IntForgery(min = 0, max = 100) skippedFramesCount: Int
    ) {
        // Given
        val dispatcher = SessionEndedMetricDispatcher(fakeInternalLogger, fakeSessionSampleRate)

        // When
        dispatcher.startMetric(
            fakeSessionId,
            fakeStartReason,
            fakeNtpOffsetAtStart,
            backgroundEventTracking
        )
        repeat(skippedFramesCount) {
            dispatcher.onSessionReplaySkippedFrameTracked(fakeSessionId)
        }
        dispatcher.endMetric(fakeSessionId, fakeNtpOffsetAtEnd)

        // Then
        assertThat(fakeInternalLogger.getSkippedFramesCount()).isEqualTo(skippedFramesCount)
    }

    @Test
    fun `M has creationSamplingRate AND samplingRate W end metric`(
        @StringForgery fakeSessionId: String
    ) {
        // Given
        val mockInternalLogger = mock<InternalLogger>()
        val dispatcher = SessionEndedMetricDispatcher(mockInternalLogger, fakeSessionSampleRate)

        // When
        dispatcher.startMetric(
            fakeSessionId,
            fakeStartReason,
            fakeNtpOffsetAtStart,
            backgroundEventTracking
        )
        dispatcher.endMetric(fakeSessionId, fakeNtpOffsetAtEnd)

        // Then
        verify(mockInternalLogger).logMetric(
            any(),
            any(),
            eq(15.0f),
            eq(fakeSessionSampleRate)
        )
    }

    private fun FakeInternalLogger.getNtpAtStartOffset(): Long {
        return lastMetric?.second?.let { attributes ->
            val rse = attributes[SessionEndedMetric.RSE_KEY] as Map<*, *>
            val ntpOffset = rse[SessionEndedMetric.NTP_OFFSET_KEY] as Map<*, *>
            ntpOffset[SessionEndedMetric.NTP_OFFSET_AT_START_KEY] as Long
        } ?: -1
    }

    private fun FakeInternalLogger.getNtpAtEndOffset(): Long {
        return lastMetric?.second?.let { attributes ->
            val rse = attributes[SessionEndedMetric.RSE_KEY] as Map<*, *>
            val ntpOffset = rse[SessionEndedMetric.NTP_OFFSET_KEY] as Map<*, *>
            ntpOffset[SessionEndedMetric.NTP_OFFSET_AT_END_KEY] as Long
        } ?: -1
    }

    private fun FakeInternalLogger.getBackgroundEventTracking(): Boolean? {
        return lastMetric?.second?.let { attributes ->
            val rse = attributes[SessionEndedMetric.RSE_KEY] as Map<*, *>
            rse[SessionEndedMetric.HAS_BACKGROUND_EVENTS_TRACKING_ENABLED_KEY] as? Boolean
        }
    }

    private fun FakeInternalLogger.getSkippedFramesCount(): Int? {
        return lastMetric?.second?.let { attributes ->
            val rse = attributes[SessionEndedMetric.RSE_KEY] as Map<*, *>
            rse[SessionEndedMetric.SESSION_REPLAY_SKIPPED_FRAMES_COUNT] as? Int
        }
    }

    private fun FakeInternalLogger.getMissedTypeCount(missedEventType: SessionEndedMetric.MissedEventType): Int {
        return lastMetric?.second?.let { attributes ->
            val rse = attributes[SessionEndedMetric.RSE_KEY] as Map<*, *>
            val noViewEventCount = rse[SessionEndedMetric.NO_VIEW_EVENTS_COUNT_KEY] as Map<*, *>
            when (missedEventType) {
                SessionEndedMetric.MissedEventType.ACTION ->
                    noViewEventCount[SessionEndedMetric.NO_VIEW_EVENTS_COUNT_ACTIONS_KEY]

                SessionEndedMetric.MissedEventType.RESOURCE ->
                    noViewEventCount[SessionEndedMetric.NO_VIEW_EVENTS_COUNT_RESOURCES_KEY]

                SessionEndedMetric.MissedEventType.ERROR ->
                    noViewEventCount[SessionEndedMetric.NO_VIEW_EVENTS_COUNT_ERRORS_KEY]

                SessionEndedMetric.MissedEventType.LONG_TASK ->
                    noViewEventCount[SessionEndedMetric.NO_VIEW_EVENTS_COUNT_LONG_TASKS_KEY]
            } as Int
        } ?: -1
    }

    private fun FakeInternalLogger.getHasReplayCount(): Int {
        return lastMetric?.second?.let { attributes ->
            val rse = attributes[SessionEndedMetric.RSE_KEY] as Map<*, *>
            val viewCount = rse[SessionEndedMetric.VIEW_COUNTS_KEY] as Map<*, *>
            viewCount[SessionEndedMetric.VIEW_COUNT_WITH_HAS_REPLAY] as Int
        } ?: -1
    }

    private fun FakeInternalLogger.isSessionStopped(): Boolean? {
        return lastMetric?.second?.let { attributes ->
            val rse = attributes[SessionEndedMetric.RSE_KEY] as Map<*, *>
            rse[SessionEndedMetric.WAS_STOPPED_KEY] as? Boolean
        }
    }

    private fun FakeInternalLogger.getErrorCounts(): Int {
        return lastMetric?.second?.let { attributes ->
            val rse = attributes[SessionEndedMetric.RSE_KEY] as Map<*, *>
            val errorCount = rse[SessionEndedMetric.SDK_ERRORS_COUNT_KEY] as Map<*, *>
            errorCount[SessionEndedMetric.SDK_ERRORS_COUNT_TOTAL_KEY] as Int
        } ?: -1
    }

    private fun FakeInternalLogger.getViewCounts(): Int {
        return lastMetric?.second?.let { attributes ->
            val rse = attributes[SessionEndedMetric.RSE_KEY] as Map<*, *>
            val viewCount = rse[SessionEndedMetric.VIEW_COUNTS_KEY] as Map<*, *>
            viewCount[SessionEndedMetric.VIEW_COUNTS_TOTAL_KEY] as Int
        } ?: -1
    }
}
