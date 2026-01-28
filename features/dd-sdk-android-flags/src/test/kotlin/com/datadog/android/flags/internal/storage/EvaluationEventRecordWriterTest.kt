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
import com.datadog.android.flags.model.BatchedFlagEvaluations
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
import org.mockito.kotlin.mock
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

    private lateinit var testedWriter: EvaluationEventRecordWriter

    @BeforeEach
    fun setUp() {
        testedWriter = EvaluationEventRecordWriter(sdkCore = mockSdkCore)
    }

    // region writeAll

    @Test
    fun `M write all events to storage W writeAll() { multiple events }`(
        @Forgery event1: BatchedFlagEvaluations.FlagEvaluation,
        @Forgery event2: BatchedFlagEvaluations.FlagEvaluation,
        @Forgery event3: BatchedFlagEvaluations.FlagEvaluation
    ) {
        // Given
        val events = listOf(event1, event2, event3)

        whenever(mockSdkCore.getFeature(Feature.FLAGS_EVALUATIONS_FEATURE_NAME)).thenReturn(mockFeature)
        whenever(mockFeature.withWriteContext(any(), any()))
            .thenAnswer { invocation ->
                val callback = invocation.getArgument<(DatadogContext, EventWriteScope) -> Unit>(1)
                val mockContext = mock<DatadogContext>()
                callback.invoke(mockContext) { writerScope ->
                    writerScope.invoke(mockEventBatchWriter)
                }
            }

        // When
        testedWriter.writeAll(events)

        // Then - all 3 events should be written
        val eventCaptor = argumentCaptor<RawBatchEvent>()
        verify(mockEventBatchWriter, times(3)).write(
            event = eventCaptor.capture(),
            batchMetadata = isNull(),
            eventType = eq(EventType.DEFAULT)
        )

        // Verify each event was serialized correctly
        val capturedEvents = eventCaptor.allValues
        assertThat(capturedEvents).hasSize(3)

        val expectedJson1 = event1.toJson().toString()
        val expectedJson2 = event2.toJson().toString()
        val expectedJson3 = event3.toJson().toString()

        assertThat(String(capturedEvents[0].data, Charsets.UTF_8)).isEqualTo(expectedJson1)
        assertThat(String(capturedEvents[1].data, Charsets.UTF_8)).isEqualTo(expectedJson2)
        assertThat(String(capturedEvents[2].data, Charsets.UTF_8)).isEqualTo(expectedJson3)
    }

    @Test
    fun `M use write context W writeAll() { events provided }`(
        @Forgery event: BatchedFlagEvaluations.FlagEvaluation
    ) {
        // Given
        whenever(mockSdkCore.getFeature(Feature.FLAGS_EVALUATIONS_FEATURE_NAME)).thenReturn(mockFeature)
        whenever(mockFeature.withWriteContext(any(), any()))
            .thenAnswer { invocation ->
                val callback = invocation.getArgument<(DatadogContext, EventWriteScope) -> Unit>(1)
                val mockContext = mock<DatadogContext>()
                callback.invoke(mockContext) { writerScope ->
                    writerScope.invoke(mockEventBatchWriter)
                }
            }

        // When
        testedWriter.writeAll(listOf(event))

        // Then - should use withWriteContext
        verify(mockFeature).withWriteContext(any(), any())
    }

    @Test
    fun `M do nothing W writeAll() { empty list }`() {
        // When
        testedWriter.writeAll(emptyList())

        // Then - should not interact with SDK Core at all
        verifyNoInteractions(mockSdkCore)
        verifyNoInteractions(mockEventBatchWriter)
    }

    @Test
    fun `M do nothing W writeAll() { feature not available }`(
        @Forgery event1: BatchedFlagEvaluations.FlagEvaluation,
        @Forgery event2: BatchedFlagEvaluations.FlagEvaluation
    ) {
        // Given
        val events = listOf(event1, event2)
        whenever(mockSdkCore.getFeature(Feature.FLAGS_EVALUATIONS_FEATURE_NAME)).thenReturn(null)

        // When
        testedWriter.writeAll(events)

        // Then - should not write anything
        verify(mockSdkCore).getFeature(Feature.FLAGS_EVALUATIONS_FEATURE_NAME)
        verifyNoInteractions(mockEventBatchWriter)
    }

    @Test
    fun `M serialize events correctly W writeAll() { events with all fields }`() {
        // Given - event with all fields populated
        val event = BatchedFlagEvaluations.FlagEvaluation(
            timestamp = 1234567890L,
            flag = BatchedFlagEvaluations.Identifier("test-flag"),
            variant = BatchedFlagEvaluations.Identifier("variant-a"),
            allocation = BatchedFlagEvaluations.Identifier("allocation-1"),
            targetingRule = BatchedFlagEvaluations.Identifier("rule-1"),
            targetingKey = "user-123",
            context = null,
            error = null,
            evaluationCount = 42L,
            firstEvaluation = 1234567890L,
            lastEvaluation = 1234567999L,
            runtimeDefaultUsed = false
        )

        whenever(mockSdkCore.getFeature(Feature.FLAGS_EVALUATIONS_FEATURE_NAME)).thenReturn(mockFeature)
        whenever(mockFeature.withWriteContext(any(), any()))
            .thenAnswer { invocation ->
                val callback = invocation.getArgument<(DatadogContext, EventWriteScope) -> Unit>(1)
                val mockContext = mock<DatadogContext>()
                callback.invoke(mockContext) { writerScope ->
                    writerScope.invoke(mockEventBatchWriter)
                }
            }

        // When
        testedWriter.writeAll(listOf(event))

        // Then - event should be serialized as JSON
        val eventCaptor = argumentCaptor<RawBatchEvent>()
        verify(mockEventBatchWriter).write(
            event = eventCaptor.capture(),
            batchMetadata = isNull(),
            eventType = eq(EventType.DEFAULT)
        )

        val capturedJson = String(eventCaptor.firstValue.data, Charsets.UTF_8)
        val expectedJson = event.toJson().toString()
        assertThat(capturedJson).isEqualTo(expectedJson)
    }

    @Test
    fun `M write event with UTF-8 encoding W writeAll() { event provided }`(
        @Forgery event: BatchedFlagEvaluations.FlagEvaluation
    ) {
        // Given
        whenever(mockSdkCore.getFeature(Feature.FLAGS_EVALUATIONS_FEATURE_NAME)).thenReturn(mockFeature)
        whenever(mockFeature.withWriteContext(any(), any()))
            .thenAnswer { invocation ->
                val callback = invocation.getArgument<(DatadogContext, EventWriteScope) -> Unit>(1)
                val mockContext = mock<DatadogContext>()
                callback.invoke(mockContext) { writerScope ->
                    writerScope.invoke(mockEventBatchWriter)
                }
            }

        // When
        testedWriter.writeAll(listOf(event))

        // Then - data should be UTF-8 encoded
        val eventCaptor = argumentCaptor<RawBatchEvent>()
        verify(mockEventBatchWriter).write(
            event = eventCaptor.capture(),
            batchMetadata = isNull(),
            eventType = eq(EventType.DEFAULT)
        )

        val capturedData = eventCaptor.firstValue.data
        val asString = String(capturedData, Charsets.UTF_8)
        // Should be valid JSON
        assertThat(asString).startsWith("{")
        assertThat(asString).endsWith("}")
    }

    // endregion
}
