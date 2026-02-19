/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.metric

import com.datadog.android.api.InternalLogger
import com.datadog.android.rum.internal.domain.scope.RumSessionScope
import com.datadog.android.rum.internal.domain.scope.RumViewManagerScope
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
class RumSessionEndedMetricTest {

    @StringForgery
    private lateinit var fakeSessionId: String

    @Forgery
    private lateinit var fakeStartReason: RumSessionScope.StartReason

    @LongForgery
    private var fakeNtpOffsetAtStart: Long = 0L

    @LongForgery
    private var fakeNtpOffsetAtEnd: Long = 0L

    @BoolForgery
    private var backgroundEventTracking: Boolean = false

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Test
    fun `M create attributes with session ended process type W toGeneratedRse`() {
        // Given
        val sessionEndedMetric = stubSessionEndedMetric()

        // When
        val rse = sessionEndedMetric.toGeneratedRse(fakeNtpOffsetAtEnd).toMap()

        // Then
        val processType = rse["process_type"] as String
        assertThat(processType).isEqualTo("app")
    }

    @Test
    fun `M create attributes with session ended precondition W toGeneratedRse`() {
        // Given
        val sessionEndedMetric = stubSessionEndedMetric()
        // When
        val rse = sessionEndedMetric.toGeneratedRse(fakeNtpOffsetAtEnd).toMap()

        // Then
        val precondition = rse["precondition"] as String
        assertThat(precondition).isEqualTo(fakeStartReason.asString)
    }

    @Test
    fun `M init attributes W toGeneratedRse { empty metric }`() {
        // Given
        val sessionEndedMetric = stubSessionEndedMetric()

        // When
        val rse = sessionEndedMetric.toGeneratedRse(fakeNtpOffsetAtEnd).toMap()

        // Then
        assertThat(rse["duration"] as Long).isEqualTo(0L)
        assertThat(rse["was_stopped"] as Boolean).isFalse()

        val viewCounts = rse["views_count"] as Map<*, *>
        assertThat(viewCounts["total"] as Int).isEqualTo(0)
        assertThat(viewCounts["background"] as Int).isEqualTo(0)
        assertThat(viewCounts["app_launch"] as Int).isEqualTo(0)

        val errorCounts = rse["sdk_errors_count"] as Map<*, *>
        assertThat(errorCounts["total"] as Int).isEqualTo(0)
        assertTrue((errorCounts["by_kind"] as Map<*, *>).isEmpty())
    }

    @Test
    fun `M calculate wasStopped attribute W onSessionStopped`() {
        // Given
        val sessionEndedMetric = stubSessionEndedMetric()

        // When
        sessionEndedMetric.onSessionStopped()
        val rse = sessionEndedMetric.toGeneratedRse(fakeNtpOffsetAtEnd).toMap()

        // Then
        val wasStopped = rse["was_stopped"] as Boolean
        assertThat(wasStopped).isTrue()
    }

    @Test
    fun `M calculate duration W onViewTracked { track single view }`(@Forgery event: ViewEvent) {
        // Given
        val sessionEndedMetric = stubSessionEndedMetric(sessionId = event.session.id)

        // When
        sessionEndedMetric.onViewTracked(event)

        val rse = sessionEndedMetric.toGeneratedRse(fakeNtpOffsetAtEnd).toMap()

        // Then
        val duration = rse["duration"] as Long
        assertThat(duration).isEqualTo(event.view.timeSpent)
    }

    @Test
    fun `M calculate duration W onViewTracked {  multiple views tracked }`(
        @Forgery event1: ViewEvent,
        @Forgery event2: ViewEvent,
        @Forgery event3: ViewEvent
    ) {
        // Given
        val duration1 = TimeUnit.SECONDS.toNanos(15)
        val duration2 = TimeUnit.SECONDS.toNanos(10)
        val duration3 = TimeUnit.SECONDS.toNanos(5)
        val date1 = TimeUnit.SECONDS.toMillis(20)
        val date2 = TimeUnit.SECONDS.toMillis(30)
        val date3 = TimeUnit.SECONDS.toMillis(40)
        val sessionEvent1 = event1.copy(
            session = event1.session.copy(id = fakeSessionId),
            date = date1,
            view = event1.view.copy(timeSpent = duration1)
        )
        val sessionEvent2 = event2.copy(
            session = event2.session.copy(id = fakeSessionId),
            date = date2,
            view = event2.view.copy(timeSpent = duration2)
        )
        val sessionEvent3 = event3.copy(
            session = event3.session.copy(id = fakeSessionId),
            date = date3,
            view = event3.view.copy(timeSpent = duration3)
        )
        val sessionEndedMetric = stubSessionEndedMetric()

        // When
        sessionEndedMetric.onViewTracked(sessionEvent1)
        sessionEndedMetric.onViewTracked(sessionEvent2)
        sessionEndedMetric.onViewTracked(sessionEvent3)

        // Then
        val rse = sessionEndedMetric.toGeneratedRse(fakeNtpOffsetAtEnd).toMap()
        val duration = rse["duration"] as Long
        val expected = TimeUnit.MILLISECONDS.toNanos(date3 - date1) + duration3
        assertThat(duration).isEqualTo(expected)
    }

    @Test
    fun `M ignore unexpected view W onViewTracked { track different session id view event }`(
        @Forgery event1: ViewEvent,
        @Forgery event2: ViewEvent
    ) {
        // Given
        val duration1 = TimeUnit.SECONDS.toNanos(15)
        val duration2 = TimeUnit.SECONDS.toNanos(10)
        val date1 = TimeUnit.SECONDS.toMillis(20)
        val date2 = TimeUnit.SECONDS.toMillis(30)

        val currentSessionViewEvent = event1.copy(
            session = event1.session.copy(id = fakeSessionId),
            date = date1,
            view = event1.view.copy(timeSpent = duration1)
        )
        val differentSessionViewEvent = event2.copy(
            date = date2,
            view = event2.view.copy(timeSpent = duration2)
        )
        val sessionEndedMetric = stubSessionEndedMetric()

        // When
        sessionEndedMetric.onViewTracked(currentSessionViewEvent)
        sessionEndedMetric.onViewTracked(differentSessionViewEvent)

        // Then
        val rse = sessionEndedMetric.toGeneratedRse(fakeNtpOffsetAtEnd).toMap()
        val viewCounts = rse["views_count"] as Map<*, *>
        assertThat(viewCounts["total"]).isEqualTo(1)
    }

    @Test
    fun `M calculate total view counts W onViewTracked`(@Forgery events: List<ViewEvent>) {
        // Given
        val sessionEndedMetric = stubSessionEndedMetric()

        // When
        events.map {
            it.copy(session = it.session.copy(id = fakeSessionId))
        }.forEach {
            sessionEndedMetric.onViewTracked(it)
        }

        // Then
        val rse = sessionEndedMetric.toGeneratedRse(fakeNtpOffsetAtEnd).toMap()
        val viewCounts = rse["views_count"] as Map<*, *>
        assertThat(viewCounts["total"]).isEqualTo(events.count())
    }

    @Test
    fun `M calculate has replay count W onViewTracked()`(@Forgery events: List<ViewEvent>) {
        // Given
        val sessionEndedMetric = stubSessionEndedMetric()

        // When
        events.map {
            it.copy(session = it.session.copy(id = fakeSessionId))
        }.forEach {
            sessionEndedMetric.onViewTracked(it)
        }

        // Then
        val rse = sessionEndedMetric.toGeneratedRse(fakeNtpOffsetAtEnd).toMap()
        val viewCounts = rse["views_count"] as Map<*, *>
        assertThat(viewCounts["with_has_replay"]).isEqualTo(
            events.count {
                it.session.hasReplay == true
            }
        )
    }

    @Test
    fun `M count correct number of background views W track views`(
        @Forgery events: List<ViewEvent>,
        forge: Forge
    ) {
        // Given
        val sessionEndedMetric = stubSessionEndedMetric()
        val backgroundViewCount = forge.anInt(min = 1, max = events.size + 1)

        // When
        events.mapIndexed { index, viewEvent ->
            val url = if (index in 0 until backgroundViewCount) {
                RumViewManagerScope.RUM_BACKGROUND_VIEW_URL
            } else {
                viewEvent.view.url
            }
            viewEvent.copy(
                session = viewEvent.session.copy(id = fakeSessionId),
                view = viewEvent.view.copy(url = url)
            )
        }.forEach {
            sessionEndedMetric.onViewTracked(it)
        }

        // Then
        val rse = sessionEndedMetric.toGeneratedRse(fakeNtpOffsetAtEnd).toMap()
        val viewCounts = rse["views_count"] as Map<*, *>
        assertThat(viewCounts["background"]).isEqualTo(backgroundViewCount)
    }

    @Test
    fun `M view counts app launch correct W track views`(
        @Forgery events: List<ViewEvent>,
        forge: Forge
    ) {
        // Given
        val sessionEndedMetric = stubSessionEndedMetric()
        val appLaunchViewCount = forge.anInt(min = 1, max = events.size + 1)

        // When
        events.mapIndexed { index, viewEvent ->
            val url = if (index in 0 until appLaunchViewCount) {
                RumViewManagerScope.RUM_APP_LAUNCH_VIEW_URL
            } else {
                viewEvent.view.url
            }
            viewEvent.copy(
                session = viewEvent.session.copy(id = fakeSessionId),
                view = viewEvent.view.copy(url = url)
            )
        }.forEach {
            sessionEndedMetric.onViewTracked(it)
        }

        // Then
        val rse = sessionEndedMetric.toGeneratedRse(fakeNtpOffsetAtEnd).toMap()
        val viewCounts = rse["views_count"] as Map<*, *>
        assertThat(viewCounts["app_launch"]).isEqualTo(appLaunchViewCount)
    }

    @Test
    fun `M error counts correct W track errors`(@StringForgery errorKinds: List<String>) {
        // Given
        val sessionEndedMetric = stubSessionEndedMetric()

        // When
        errorKinds.forEach {
            sessionEndedMetric.onErrorTracked(it)
        }

        // Then
        val rse = sessionEndedMetric.toGeneratedRse(fakeNtpOffsetAtEnd).toMap()
        val sdkErrorCount = rse["sdk_errors_count"] as Map<*, *>
        assertThat(sdkErrorCount["total"]).isEqualTo(errorKinds.size)
    }

    @Test
    fun `M error attribute correct W track errors`() {
        // Given
        val errorKindsMap = mapOf(
            "top1" to 10,
            "top2" to 9,
            "top3" to 8,
            "top4" to 7,
            "top5" to 6,
            "top6" to 5,
            "top7" to 4,
            "top8" to 3
        )

        val sessionEndedMetric = stubSessionEndedMetric()

        // When
        errorKindsMap.forEach { errorKind ->
            repeat(errorKind.value) {
                sessionEndedMetric.onErrorTracked(errorKind.key)
            }
        }

        // Then
        val rse = sessionEndedMetric.toGeneratedRse(fakeNtpOffsetAtEnd).toMap()
        val sdkErrorCount = rse["sdk_errors_count"] as Map<*, *>

        val expectedErrorKind = mapOf(
            "top1" to 10,
            "top2" to 9,
            "top3" to 8,
            "top4" to 7,
            "top5" to 6
        )
        assertThat(sdkErrorCount["by_kind"]).isEqualTo(expectedErrorKind)
    }

    @Test
    fun `M error kind escape non alpha numeric W track errors`() {
        // Given
        val errorKindsMap = mapOf(
            "top 1 error" to 10,
            "top:2:error" to 9,
            "top_3_error" to 8,
            "top/4/error" to 7
        )
        val sessionEndedMetric = stubSessionEndedMetric()

        // When
        errorKindsMap.forEach { errorKind ->
            repeat(errorKind.value) {
                sessionEndedMetric.onErrorTracked(errorKind.key)
            }
        }

        // Then
        val rse = sessionEndedMetric.toGeneratedRse(fakeNtpOffsetAtEnd).toMap()
        val sdkErrorCount = rse["sdk_errors_count"] as Map<*, *>

        val expectedErrorKind = mapOf(
            "top_1_error" to 10,
            "top_2_error" to 9,
            "top_3_error" to 8,
            "top_4_error" to 7
        )
        assertThat(sdkErrorCount["by_kind"]).isEqualTo(expectedErrorKind)
    }

    @Test
    fun `M have correct no_view_events_count W toGeneratedRse()`(
        @BoolForgery fakeBackgroundEventTracking: Boolean,
        @IntForgery(min = 0, max = 5) randomActionCount: Int,
        @IntForgery(min = 0, max = 5) randomResourceCount: Int,
        @IntForgery(min = 0, max = 5) randomErrorCount: Int,
        @IntForgery(min = 0, max = 5) randomLongTaskCount: Int
    ) {
        // Given
        val sessionEndedMetric = stubSessionEndedMetric(
            hasTrackBackgroundEventsEnabled = fakeBackgroundEventTracking
        )

        // When
        repeat(randomActionCount) {
            sessionEndedMetric.onMissedEventTracked(SessionEndedMetric.MissedEventType.ACTION)
        }
        repeat(randomErrorCount) {
            sessionEndedMetric.onMissedEventTracked(SessionEndedMetric.MissedEventType.ERROR)
        }
        repeat(randomResourceCount) {
            sessionEndedMetric.onMissedEventTracked(SessionEndedMetric.MissedEventType.RESOURCE)
        }
        repeat(randomLongTaskCount) {
            sessionEndedMetric.onMissedEventTracked(SessionEndedMetric.MissedEventType.LONG_TASK)
        }

        val rse = sessionEndedMetric.toGeneratedRse(fakeNtpOffsetAtEnd).toMap()

        // Then
        val noViewCounts = rse["no_view_events_count"] as Map<*, *>
        assertThat(noViewCounts["actions"]).isEqualTo(randomActionCount)
        assertThat(noViewCounts["errors"]).isEqualTo(randomErrorCount)
        assertThat(noViewCounts["resources"]).isEqualTo(randomResourceCount)
        assertThat(noViewCounts["long_tasks"]).isEqualTo(randomLongTaskCount)
    }

    @Test
    fun `M have correct has_background_events_tracking_enabled W toGeneratedRse()`(
        @BoolForgery fakeBackgroundEventTracking: Boolean
    ) {
        // Given
        val sessionEndedMetric = stubSessionEndedMetric(
            hasTrackBackgroundEventsEnabled = fakeBackgroundEventTracking
        )

        // When
        val rse = sessionEndedMetric.toGeneratedRse(fakeNtpOffsetAtEnd).toMap()

        // Then
        assertThat(
            rse["has_background_events_tracking_enabled"]
        ).isEqualTo(fakeBackgroundEventTracking)
    }

    @Test
    fun `M have correct ntp_offset W toGeneratedRse()`(
        @LongForgery ntpOffsetAtStart: Long,
        @LongForgery ntpOffsetAtEnd: Long
    ) {
        // Given
        val sessionEndedMetric = stubSessionEndedMetric(ntpOffsetAtStartMs = ntpOffsetAtStart)

        // When
        val rse = sessionEndedMetric.toGeneratedRse(ntpOffsetAtEnd).toMap()

        // Then
        val ntpOffset = rse["ntp_offset"] as Map<*, *>
        assertThat(ntpOffset["at_start"]).isEqualTo(ntpOffsetAtStart)
        assertThat(ntpOffset["at_end"]).isEqualTo(ntpOffsetAtEnd)
    }

    @Test
    fun `M have correct sr_skipped_frames_count W toGeneratedRse()`(
        @IntForgery(min = 0, max = 100) count: Int
    ) {
        // Given
        val sessionEndedMetric = stubSessionEndedMetric()

        // When
        repeat(count) {
            sessionEndedMetric.onSessionReplaySkippedFrameTracked()
        }
        val rse = sessionEndedMetric.toGeneratedRse(fakeNtpOffsetAtEnd).toMap()

        // Then
        val skippedFramesCount = rse["sr_skipped_frames_count"] as Int
        assertThat(skippedFramesCount).isEqualTo(count)
    }

    @Test
    fun `M encode type W creating metric`(@Forgery viewEvent: ViewEvent) {
        // Given
        val sessionEndedMetric = stubSessionEndedMetric()

        // When
        viewEvent.copy(session = viewEvent.session.copy(id = fakeSessionId)).apply {
            sessionEndedMetric.onViewTracked(this)
        }

        // Then

        val rse = sessionEndedMetric.toGeneratedRse(fakeNtpOffsetAtEnd).toMap()
        val rseObject = JSONObject(rse)

        assertThat(rseObject.get("process_type")).isInstanceOf(String::class.java)
        assertThat(rseObject.get("precondition")).isInstanceOf(String::class.java)
        assertThat(rseObject.get("duration")).isInstanceOf(java.lang.Long::class.java)
        assertThat(rseObject.get("was_stopped")).isInstanceOf(java.lang.Boolean::class.java)

        val viewCountsObject = rseObject.get("views_count") as JSONObject
        assertThat(
            viewCountsObject.get("total")
        ).isInstanceOf(java.lang.Integer::class.java)
        assertThat(
            viewCountsObject.get("background")
        ).isInstanceOf(java.lang.Integer::class.java)
        assertThat(
            viewCountsObject.get("app_launch")
        ).isInstanceOf(java.lang.Integer::class.java)
        assertThat(
            viewCountsObject.get("with_has_replay")
        ).isInstanceOf(java.lang.Integer::class.java)

        val sdkErrorsCount = rseObject.get("sdk_errors_count") as JSONObject

        assertThat(
            sdkErrorsCount.get("total")
        ).isInstanceOf(java.lang.Integer::class.java)
        assertThat(
            sdkErrorsCount.get("by_kind")
        ).isInstanceOf(JSONObject::class.java)

        val noViewCounts = rseObject.get("no_view_events_count") as JSONObject
        assertThat(
            noViewCounts.get("actions")
        ).isInstanceOf(java.lang.Integer::class.java)
        assertThat(
            noViewCounts.get("resources")
        ).isInstanceOf(java.lang.Integer::class.java)
        assertThat(
            noViewCounts.get("errors")
        ).isInstanceOf(java.lang.Integer::class.java)
        assertThat(
            noViewCounts.get("long_tasks")
        ).isInstanceOf(java.lang.Integer::class.java)

        assertThat(rseObject.get("has_background_events_tracking_enabled"))
            .isInstanceOf(java.lang.Boolean::class.java)

        val nptOffset = rseObject.get("ntp_offset") as JSONObject
        assertThat(nptOffset.get("at_start")).isInstanceOf(java.lang.Long::class.java)
        assertThat(nptOffset.get("at_end")).isInstanceOf(java.lang.Long::class.java)
    }

    private fun stubSessionEndedMetric(
        sessionId: String = fakeSessionId,
        startReason: RumSessionScope.StartReason = fakeStartReason,
        ntpOffsetAtStartMs: Long = fakeNtpOffsetAtStart,
        hasTrackBackgroundEventsEnabled: Boolean = backgroundEventTracking
    ) = SessionEndedMetric(
        sessionId,
        startReason,
        ntpOffsetAtStartMs,
        hasTrackBackgroundEventsEnabled
    )
}
