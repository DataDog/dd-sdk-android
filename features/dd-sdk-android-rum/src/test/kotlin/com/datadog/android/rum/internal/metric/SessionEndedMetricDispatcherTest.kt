/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.metric

import com.datadog.android.rum.internal.domain.scope.RumSessionScope
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
class SessionEndedMetricDispatcherTest {

    private val fakeInternalLogger = FakeInternalLogger()

    @Forgery
    private lateinit var fakeStartReason: RumSessionScope.StartReason

    @Test
    fun `M register session stop W call onSessionStopped()`(@StringForgery fakeSessionId: String) {
        // Given
        val dispatcher = SessionEndedMetricDispatcher(fakeInternalLogger)
        dispatcher.startMetric(fakeSessionId, fakeStartReason)

        // When
        dispatcher.onSessionStopped(fakeSessionId)
        dispatcher.endMetric(fakeSessionId)

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
        val dispatcher = SessionEndedMetricDispatcher(fakeInternalLogger)

        // When
        dispatcher.startMetric(fakeSessionId1, fakeStartReason)
        dispatcher.startMetric(fakeSessionId2, fakeStartReason)
        viewEvent.copy(session = viewEvent.session.copy(id = fakeSessionId2)).apply {
            dispatcher.onViewTracked(fakeSessionId1, this)
        }

        dispatcher.endMetric(fakeSessionId1)
        dispatcher.endMetric(fakeSessionId2)

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
        val dispatcher = SessionEndedMetricDispatcher(fakeInternalLogger)

        // When
        dispatcher.startMetric(fakeSessionId1, fakeStartReason)
        dispatcher.startMetric(fakeSessionId2, fakeStartReason)
        dispatcher.startMetric(fakeSessionId3, fakeStartReason)
        dispatcher.startMetric(fakeSessionId4, fakeStartReason)

        viewEvents.map {
            it.copy(session = it.session.copy(id = fakeSessionId2))
        }.forEach {
            dispatcher.onViewTracked(fakeSessionId2, it)
        }

        // Then
        dispatcher.endMetric(fakeSessionId1)
        assertThat(fakeInternalLogger.getViewCounts()).isEqualTo(0)

        dispatcher.endMetric(fakeSessionId2)
        assertThat(fakeInternalLogger.getViewCounts()).isEqualTo(fakeInternalLogger.getViewCounts())

        dispatcher.endMetric(fakeSessionId3)
        assertThat(fakeInternalLogger.getViewCounts()).isEqualTo(0)

        dispatcher.endMetric(fakeSessionId1)
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
        val dispatcher = SessionEndedMetricDispatcher(fakeInternalLogger)

        // When
        dispatcher.startMetric(fakeSessionId1, fakeStartReason)
        dispatcher.startMetric(fakeSessionId2, fakeStartReason)
        dispatcher.startMetric(fakeSessionId3, fakeStartReason)
        dispatcher.startMetric(fakeSessionId4, fakeStartReason)

        // Ends the last started session
        dispatcher.endMetric(fakeSessionId4)
        // Ends a middle session
        dispatcher.endMetric(fakeSessionId2)

        errorKinds.forEach {
            dispatcher.onSdkErrorTracked(fakeSessionId3, it)
        }

        // Then
        dispatcher.endMetric(fakeSessionId1)
        assertThat(fakeInternalLogger.getErrorCounts()).isEqualTo(0)

        // Errors can is reported by the last session when they are tracked
        dispatcher.endMetric(fakeSessionId3)
        assertThat(fakeInternalLogger.getErrorCounts()).isEqualTo(errorKinds.size)
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
            val viewCount = rse[SessionEndedMetric.SDK_ERRORS_COUNT_KEY] as Map<*, *>
            viewCount[SessionEndedMetric.SDK_ERRORS_COUNT_TOTAL_KEY] as Int
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
