/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.storage

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.flags.internal.aggregation.EvaluationAggregationStats
import com.datadog.android.flags.utils.forge.ForgeConfigurator
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class, ForgeExtension::class)
@ForgeConfiguration(ForgeConfigurator::class)
internal class EvaluationEventRecordWriterTest {

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockFeature: FeatureScope

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    private lateinit var testedWriter: EvaluationEventRecordWriter

    @BeforeEach
    fun setUp() {
        testedWriter = EvaluationEventRecordWriter(sdkCore = mockSdkCore)
    }

    // region writeAll

    @Test
    fun `M write all events to storage W writeAll() { multiple events }`(
        @Forgery events: List<EvaluationAggregationStats>
    ) {
        // Given
        whenever(mockSdkCore.getFeature(Feature.FLAGS_EVALUATIONS_FEATURE_NAME)).thenReturn(mockFeature)
        whenever(mockFeature.withWriteContext(any(), any()))
            .thenAnswer { invocation ->
                val callback = invocation.getArgument<(DatadogContext, EventWriteScope) -> Unit>(1)
                callback.invoke(fakeDatadogContext) { writerScope ->
                    writerScope.invoke(mockEventBatchWriter)
                }
            }

        // When
        testedWriter.writeAll(events)

        // Then
        val eventCaptor = argumentCaptor<RawBatchEvent>()
        verify(mockEventBatchWriter, times(events.size)).write(
            event = eventCaptor.capture(),
            batchMetadata = isNull(),
            eventType = eq(EventType.DEFAULT)
        )

        val capturedEvents = eventCaptor.allValues
        assertThat(capturedEvents).hasSize(events.size)

        events.withIndex().forEach {
            val expectedJson = events[it.index].toEvaluationEvent(fakeDatadogContext).toJson().toString()
            assertThat(String(capturedEvents[it.index].data, Charsets.UTF_8)).isEqualTo(expectedJson)
        }
    }

    @Test
    fun `M do nothing W writeAll() { empty list }`() {
        // When
        testedWriter.writeAll(emptyList())

        // Then
        verifyNoInteractions(mockSdkCore)
        verifyNoInteractions(mockEventBatchWriter)
    }

    @Test
    fun `M do nothing W writeAll() { feature not available }`(
        @Forgery events: List<EvaluationAggregationStats>
    ) {
        // Given
        whenever(mockSdkCore.getFeature(Feature.FLAGS_EVALUATIONS_FEATURE_NAME)).thenReturn(null)

        // When
        testedWriter.writeAll(events)

        // Then
        verify(mockSdkCore).getFeature(Feature.FLAGS_EVALUATIONS_FEATURE_NAME)
        verifyNoInteractions(mockEventBatchWriter)
    }

    // endregion
}
