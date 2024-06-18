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
import fr.xgouchet.elmyr.annotation.Forgery
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

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Test
    fun `M create attributes with session ended metric type W toMetricAttributes`() {
        // Given
        val sessionEndedMetric = SessionEndedMetric(fakeSessionId, fakeStartReason)

        // When
        val attributes = sessionEndedMetric.toMetricAttributes()

        // Then
        assertThat(attributes[SessionEndedMetric.METRIC_TYPE_KEY]).isEqualTo(SessionEndedMetric.METRIC_TYPE_VALUE)
    }

    @Test
    fun `M create attributes with session ended process type W toMetricAttributes`() {
        // Given
        val sessionEndedMetric = SessionEndedMetric(fakeSessionId, fakeStartReason)

        // When
        val attributes = sessionEndedMetric.toMetricAttributes()

        // Then
        val rse = attributes[SessionEndedMetric.RSE_KEY] as Map<*, *>
        val processType = rse[SessionEndedMetric.PROCESS_TYPE_KEY] as String
        assertThat(processType).isEqualTo(SessionEndedMetric.PROCESS_TYPE_VALUE)
    }

    @Test
    fun `M create attributes with session ended precondition W toMetricAttributes`() {
        // Given
        val sessionEndedMetric = SessionEndedMetric(fakeSessionId, fakeStartReason)

        // When
        val attributes = sessionEndedMetric.toMetricAttributes()

        // Then
        val rse = attributes[SessionEndedMetric.RSE_KEY] as Map<*, *>
        val precondition = rse[SessionEndedMetric.PRECONDITION_KEY] as String
        assertThat(precondition).isEqualTo(fakeStartReason.asString)
    }

    @Test
    fun `M init attributes W toMetricAttributes { empty metric }`() {
        // Given
        val sessionEndedMetric = SessionEndedMetric(fakeSessionId, fakeStartReason)

        // When
        val attributes = sessionEndedMetric.toMetricAttributes()

        // Then
        val rse = attributes[SessionEndedMetric.RSE_KEY] as Map<*, *>

        assertThat(rse[SessionEndedMetric.DURATION_KEY] as Long).isEqualTo(0L)
        assertThat(rse[SessionEndedMetric.WAS_STOPPED_KEY] as Boolean).isFalse()

        val viewCounts = rse[SessionEndedMetric.VIEW_COUNTS_KEY] as Map<*, *>
        assertThat(viewCounts[SessionEndedMetric.VIEW_COUNTS_TOTAL_KEY] as Int).isEqualTo(0)
        assertThat(viewCounts[SessionEndedMetric.VIEW_COUNTS_BG_KEY] as Int).isEqualTo(0)
        assertThat(viewCounts[SessionEndedMetric.VIEW_COUNTS_APP_LAUNCH_KEY] as Int).isEqualTo(0)

        val errorCounts = rse[SessionEndedMetric.SDK_ERRORS_COUNT_KEY] as Map<*, *>
        assertThat(errorCounts[SessionEndedMetric.SDK_ERRORS_COUNT_TOTAL_KEY] as Int).isEqualTo(0)
        assertTrue((errorCounts[SessionEndedMetric.SDK_ERRORS_COUNT_BY_KIND_KEY] as Map<*, *>).isEmpty())
    }

    @Test
    fun `M calculate wasStopped attribute W onSessionStopped`() {
        // Given
        val sessionEndedMetric = SessionEndedMetric(fakeSessionId, fakeStartReason)

        // When
        sessionEndedMetric.onSessionStopped()
        val attributes = sessionEndedMetric.toMetricAttributes()

        // Then
        val rse = attributes[SessionEndedMetric.RSE_KEY] as Map<*, *>
        val wasStopped = rse[SessionEndedMetric.WAS_STOPPED_KEY] as Boolean
        assertThat(wasStopped).isTrue()
    }

    @Test
    fun `M calculate duration W onViewTracked { track single view }`(@Forgery event: ViewEvent) {
        // Given
        val sessionEndedMetric = SessionEndedMetric(event.session.id, fakeStartReason)

        // When
        sessionEndedMetric.onViewTracked(event)

        val attributes = sessionEndedMetric.toMetricAttributes()

        // Then
        val rse = attributes[SessionEndedMetric.RSE_KEY] as Map<*, *>
        val duration = rse[SessionEndedMetric.DURATION_KEY] as Long
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
        val sessionEndedMetric = SessionEndedMetric(fakeSessionId, fakeStartReason)

        // When
        sessionEndedMetric.onViewTracked(sessionEvent1)
        sessionEndedMetric.onViewTracked(sessionEvent2)
        sessionEndedMetric.onViewTracked(sessionEvent3)

        // Then
        val attributes = sessionEndedMetric.toMetricAttributes()
        val rse = attributes[SessionEndedMetric.RSE_KEY] as Map<*, *>
        val duration = rse[SessionEndedMetric.DURATION_KEY] as Long
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
        val sessionEndedMetric = SessionEndedMetric(fakeSessionId, fakeStartReason)

        // When
        sessionEndedMetric.onViewTracked(currentSessionViewEvent)
        sessionEndedMetric.onViewTracked(differentSessionViewEvent)

        // Then
        val attributes = sessionEndedMetric.toMetricAttributes()
        val rse = attributes[SessionEndedMetric.RSE_KEY] as Map<*, *>
        val viewCounts = rse[SessionEndedMetric.VIEW_COUNTS_KEY] as Map<*, *>
        assertThat(viewCounts[SessionEndedMetric.VIEW_COUNTS_TOTAL_KEY]).isEqualTo(1)
    }

    @Test
    fun `M calculate total view counts W onViewTracked`(@Forgery events: List<ViewEvent>) {
        // Given
        val sessionEndedMetric = SessionEndedMetric(fakeSessionId, fakeStartReason)

        // When
        events.map {
            it.copy(session = it.session.copy(id = fakeSessionId))
        }.forEach {
            sessionEndedMetric.onViewTracked(it)
        }

        // Then
        val attributes = sessionEndedMetric.toMetricAttributes()
        val rse = attributes[SessionEndedMetric.RSE_KEY] as Map<*, *>
        val viewCounts = rse[SessionEndedMetric.VIEW_COUNTS_KEY] as Map<*, *>
        assertThat(viewCounts[SessionEndedMetric.VIEW_COUNTS_TOTAL_KEY]).isEqualTo(events.count())
    }

    @Test
    fun `M count correct number of background views W track views`(
        @Forgery events: List<ViewEvent>,
        forge: Forge
    ) {
        // Given
        val sessionEndedMetric = SessionEndedMetric(fakeSessionId, fakeStartReason)
        val backgroundViewCount = forge.anInt(min = 1, max = events.size)

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
        val attributes = sessionEndedMetric.toMetricAttributes()
        val rse = attributes[SessionEndedMetric.RSE_KEY] as Map<*, *>
        val viewCounts = rse[SessionEndedMetric.VIEW_COUNTS_KEY] as Map<*, *>
        assertThat(viewCounts[SessionEndedMetric.VIEW_COUNTS_BG_KEY]).isEqualTo(backgroundViewCount)
    }

    @Test
    fun `M view counts app launch correct W track views`(
        @Forgery events: List<ViewEvent>,
        forge: Forge
    ) {
        // Given
        val sessionEndedMetric = SessionEndedMetric(fakeSessionId, fakeStartReason)
        val appLaunchViewCount = forge.anInt(min = 1, max = events.size)

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
        val attributes = sessionEndedMetric.toMetricAttributes()
        val rse = attributes[SessionEndedMetric.RSE_KEY] as Map<*, *>
        val viewCounts = rse[SessionEndedMetric.VIEW_COUNTS_KEY] as Map<*, *>
        assertThat(viewCounts[SessionEndedMetric.VIEW_COUNTS_APP_LAUNCH_KEY]).isEqualTo(appLaunchViewCount)
    }

    @Test
    fun `M error counts correct W track errors`(@StringForgery errorKinds: List<String>) {
        // Given
        val sessionEndedMetric = SessionEndedMetric(fakeSessionId, fakeStartReason)

        // When
        errorKinds.forEach {
            sessionEndedMetric.onErrorTracked(it)
        }

        // Then
        val attributes = sessionEndedMetric.toMetricAttributes()
        val rse = attributes[SessionEndedMetric.RSE_KEY] as Map<*, *>
        val sdkErrorCount = rse[SessionEndedMetric.SDK_ERRORS_COUNT_KEY] as Map<*, *>
        assertThat(sdkErrorCount[SessionEndedMetric.SDK_ERRORS_COUNT_TOTAL_KEY]).isEqualTo(errorKinds.size)
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

        val sessionEndedMetric = SessionEndedMetric(fakeSessionId, fakeStartReason)

        // When
        errorKindsMap.forEach { errorKind ->
            repeat(errorKind.value) {
                sessionEndedMetric.onErrorTracked(errorKind.key)
            }
        }

        // Then
        val attributes = sessionEndedMetric.toMetricAttributes()
        val rse = attributes[SessionEndedMetric.RSE_KEY] as Map<*, *>
        val sdkErrorCount = rse[SessionEndedMetric.SDK_ERRORS_COUNT_KEY] as Map<*, *>

        val expectedErrorKind = mapOf(
            "top1" to 10,
            "top2" to 9,
            "top3" to 8,
            "top4" to 7,
            "top5" to 6
        )
        assertThat(sdkErrorCount[SessionEndedMetric.SDK_ERRORS_COUNT_BY_KIND_KEY]).isEqualTo(expectedErrorKind)
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
        val sessionEndedMetric = SessionEndedMetric(fakeSessionId, fakeStartReason)

        // When
        errorKindsMap.forEach { errorKind ->
            repeat(errorKind.value) {
                sessionEndedMetric.onErrorTracked(errorKind.key)
            }
        }

        // Then
        val attributes = sessionEndedMetric.toMetricAttributes()
        val rse = attributes[SessionEndedMetric.RSE_KEY] as Map<*, *>
        val sdkErrorCount = rse[SessionEndedMetric.SDK_ERRORS_COUNT_KEY] as Map<*, *>

        val expectedErrorKind = mapOf(
            "top_1_error" to 10,
            "top_2_error" to 9,
            "top_3_error" to 8,
            "top_4_error" to 7
        )
        assertThat(sdkErrorCount[SessionEndedMetric.SDK_ERRORS_COUNT_BY_KIND_KEY]).isEqualTo(expectedErrorKind)
    }

    @Test
    fun `M encode type W creating metric`(@Forgery viewEvent: ViewEvent) {
        // Given
        val sessionEndedMetric = SessionEndedMetric(fakeSessionId, fakeStartReason)

        // When
        viewEvent.copy(session = viewEvent.session.copy(id = fakeSessionId)).apply {
            sessionEndedMetric.onViewTracked(this)
        }

        // Then

        val attributes = sessionEndedMetric.toMetricAttributes()
        val json = JSONObject(attributes)

        val rseObject = json.get(SessionEndedMetric.RSE_KEY) as JSONObject

        assertThat(rseObject.get(SessionEndedMetric.PROCESS_TYPE_KEY)).isInstanceOf(String::class.java)
        assertThat(rseObject.get(SessionEndedMetric.PRECONDITION_KEY)).isInstanceOf(String::class.java)
        assertThat(rseObject.get(SessionEndedMetric.DURATION_KEY)).isInstanceOf(java.lang.Long::class.java)
        assertThat(rseObject.get(SessionEndedMetric.WAS_STOPPED_KEY)).isInstanceOf(java.lang.Boolean::class.java)

        val viewCountsObject = rseObject.get(SessionEndedMetric.VIEW_COUNTS_KEY) as JSONObject
        assertThat(
            viewCountsObject.get(SessionEndedMetric.VIEW_COUNTS_TOTAL_KEY)
        ).isInstanceOf(java.lang.Integer::class.java)
        assertThat(
            viewCountsObject.get(SessionEndedMetric.VIEW_COUNTS_BG_KEY)
        ).isInstanceOf(java.lang.Integer::class.java)
        assertThat(
            viewCountsObject.get(SessionEndedMetric.VIEW_COUNTS_APP_LAUNCH_KEY)
        ).isInstanceOf(java.lang.Integer::class.java)

        val sdkErrorsCount = rseObject.get(SessionEndedMetric.SDK_ERRORS_COUNT_KEY) as JSONObject

        assertThat(
            sdkErrorsCount.get(SessionEndedMetric.SDK_ERRORS_COUNT_TOTAL_KEY)
        ).isInstanceOf(java.lang.Integer::class.java)
        assertThat(
            sdkErrorsCount.get(SessionEndedMetric.SDK_ERRORS_COUNT_BY_KIND_KEY)
        ).isInstanceOf(JSONObject::class.java)
    }
}
