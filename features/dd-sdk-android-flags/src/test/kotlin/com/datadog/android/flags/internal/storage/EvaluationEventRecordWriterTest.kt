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

    // region write

    @Test
    fun `M write flag evaluation W write() { feature available }`(
        @Forgery fakeEvent: BatchedFlagEvaluations.FlagEvaluation
    ) {
        // Given
        val expectedJson = fakeEvent.toJson().toString()

        whenever(mockSdkCore.getFeature(Feature.FLAGS_FEATURE_NAME)).thenReturn(mockFeature)
        whenever(
            mockFeature.withWriteContext(any(), any())
        ).thenAnswer { invocation ->
            val callback = invocation.getArgument<(DatadogContext, EventWriteScope) -> Unit>(1)
            val mockContext = mock<DatadogContext>()
            callback.invoke(mockContext) { writerScope ->
                writerScope.invoke(mockEventBatchWriter)
            }
        }

        // When
        testedWriter.write(fakeEvent)

        // Then
        val eventCaptor = argumentCaptor<RawBatchEvent>()
        verify(mockEventBatchWriter).write(
            event = eventCaptor.capture(),
            batchMetadata = isNull(),
            eventType = eq(EventType.DEFAULT)
        )

        val capturedEvent = eventCaptor.firstValue
        assertThat(String(capturedEvent.data, Charsets.UTF_8)).isEqualTo(expectedJson)
    }

    @Test
    fun `M not write W write() { feature not available }`(@Forgery fakeEvent: BatchedFlagEvaluations.FlagEvaluation) {
        // Given
        whenever(mockSdkCore.getFeature(Feature.FLAGS_FEATURE_NAME)).thenReturn(null)

        // When
        testedWriter.write(fakeEvent)

        // Then
        verifyNoInteractions(mockEventBatchWriter)
    }

    @Test
    fun `M serialize event correctly W write() { complete event }`() {
        // Given
        val fakeEvent = BatchedFlagEvaluations.FlagEvaluation(
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

        whenever(mockSdkCore.getFeature(Feature.FLAGS_FEATURE_NAME)).thenReturn(mockFeature)
        whenever(
            mockFeature.withWriteContext(any(), any())
        ).thenAnswer { invocation ->
            val callback = invocation.getArgument<(DatadogContext, EventWriteScope) -> Unit>(1)
            val mockContext = mock<DatadogContext>()
            callback.invoke(mockContext) { writerScope ->
                writerScope.invoke(mockEventBatchWriter)
            }
        }

        // When
        testedWriter.write(fakeEvent)

        // Then
        val eventCaptor = argumentCaptor<RawBatchEvent>()
        verify(mockEventBatchWriter).write(
            event = eventCaptor.capture(),
            batchMetadata = isNull(),
            eventType = eq(EventType.DEFAULT)
        )

        val capturedJson = String(eventCaptor.firstValue.data, Charsets.UTF_8)
        assertThat(capturedJson).contains("\"test-flag\"")
        assertThat(capturedJson).contains("\"variant-a\"")
        assertThat(capturedJson).contains("\"allocation-1\"")
        assertThat(capturedJson).contains("\"user-123\"")
        assertThat(capturedJson).contains("\"evaluation_count\":42")
    }

    @Test
    fun `M serialize event correctly W write() { with error }`() {
        // Given
        val fakeEvent = BatchedFlagEvaluations.FlagEvaluation(
            timestamp = 1234567890L,
            flag = BatchedFlagEvaluations.Identifier("test-flag"),
            variant = null,
            allocation = null,
            targetingRule = null,
            targetingKey = null,
            context = null,
            error = BatchedFlagEvaluations.Error(message = "Test error message"),
            evaluationCount = 1L,
            firstEvaluation = 1234567890L,
            lastEvaluation = 1234567890L,
            runtimeDefaultUsed = true
        )

        whenever(mockSdkCore.getFeature(Feature.FLAGS_FEATURE_NAME)).thenReturn(mockFeature)
        whenever(
            mockFeature.withWriteContext(any(), any())
        ).thenAnswer { invocation ->
            val callback = invocation.getArgument<(DatadogContext, EventWriteScope) -> Unit>(1)
            val mockContext = mock<DatadogContext>()
            callback.invoke(mockContext) { writerScope ->
                writerScope.invoke(mockEventBatchWriter)
            }
        }

        // When
        testedWriter.write(fakeEvent)

        // Then
        val eventCaptor = argumentCaptor<RawBatchEvent>()
        verify(mockEventBatchWriter).write(
            event = eventCaptor.capture(),
            batchMetadata = isNull(),
            eventType = eq(EventType.DEFAULT)
        )

        val capturedJson = String(eventCaptor.firstValue.data, Charsets.UTF_8)
        assertThat(capturedJson).contains("\"error\"")
        assertThat(capturedJson).contains("\"Test error message\"")
        assertThat(capturedJson).contains("\"runtime_default_used\":true")
    }

    @Test
    fun `M handle null targeting key W write() { null value }`() {
        // Given
        val fakeEvent = BatchedFlagEvaluations.FlagEvaluation(
            timestamp = 1234567890L,
            flag = BatchedFlagEvaluations.Identifier("test-flag"),
            variant = null,
            allocation = null,
            targetingRule = null,
            targetingKey = null,
            context = null,
            error = null,
            evaluationCount = 1L,
            firstEvaluation = 1234567890L,
            lastEvaluation = 1234567890L,
            runtimeDefaultUsed = true
        )

        whenever(mockSdkCore.getFeature(Feature.FLAGS_FEATURE_NAME)).thenReturn(mockFeature)
        whenever(
            mockFeature.withWriteContext(any(), any())
        ).thenAnswer { invocation ->
            val callback = invocation.getArgument<(DatadogContext, EventWriteScope) -> Unit>(1)
            val mockContext = mock<DatadogContext>()
            callback.invoke(mockContext) { writerScope ->
                writerScope.invoke(mockEventBatchWriter)
            }
        }

        // When
        testedWriter.write(fakeEvent)

        // Then
        val eventCaptor = argumentCaptor<RawBatchEvent>()
        verify(mockEventBatchWriter).write(
            event = eventCaptor.capture(),
            batchMetadata = isNull(),
            eventType = eq(EventType.DEFAULT)
        )

        // EVALLOG.7: null targeting_key should be omitted from JSON
        val capturedJson = String(eventCaptor.firstValue.data, Charsets.UTF_8)
        assertThat(capturedJson).doesNotContain("targeting_key")
    }

    @Test
    fun `M handle empty targeting key W write() { empty string }`() {
        // Given
        val fakeEvent = BatchedFlagEvaluations.FlagEvaluation(
            timestamp = 1234567890L,
            flag = BatchedFlagEvaluations.Identifier("test-flag"),
            variant = null,
            allocation = null,
            targetingRule = null,
            targetingKey = "",
            context = null,
            error = null,
            evaluationCount = 1L,
            firstEvaluation = 1234567890L,
            lastEvaluation = 1234567890L,
            runtimeDefaultUsed = true
        )

        whenever(mockSdkCore.getFeature(Feature.FLAGS_FEATURE_NAME)).thenReturn(mockFeature)
        whenever(
            mockFeature.withWriteContext(any(), any())
        ).thenAnswer { invocation ->
            val callback = invocation.getArgument<(DatadogContext, EventWriteScope) -> Unit>(1)
            val mockContext = mock<DatadogContext>()
            callback.invoke(mockContext) { writerScope ->
                writerScope.invoke(mockEventBatchWriter)
            }
        }

        // When
        testedWriter.write(fakeEvent)

        // Then
        val eventCaptor = argumentCaptor<RawBatchEvent>()
        verify(mockEventBatchWriter).write(
            event = eventCaptor.capture(),
            batchMetadata = isNull(),
            eventType = eq(EventType.DEFAULT)
        )

        // EVALLOG.7: empty string targeting_key should be included in JSON
        val capturedJson = String(eventCaptor.firstValue.data, Charsets.UTF_8)
        assertThat(capturedJson).contains("\"targeting_key\":\"\"")
    }

    // endregion

    // region writeAll

    @Test
    fun `M write multiple events W writeAll() { batch of events }`(
        @Forgery event1: BatchedFlagEvaluations.FlagEvaluation,
        @Forgery event2: BatchedFlagEvaluations.FlagEvaluation,
        @Forgery event3: BatchedFlagEvaluations.FlagEvaluation
    ) {
        // Given
        val events = listOf(event1, event2, event3)

        whenever(mockSdkCore.getFeature(Feature.FLAGS_FEATURE_NAME)).thenReturn(mockFeature)
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

        // Then - should write all 3 events
        val eventCaptor = argumentCaptor<RawBatchEvent>()
        verify(mockEventBatchWriter, times(3)).write(
            event = eventCaptor.capture(),
            batchMetadata = isNull(),
            eventType = eq(EventType.DEFAULT)
        )

        val capturedEvents = eventCaptor.allValues
        assertThat(capturedEvents).hasSize(3)
    }

    @Test
    fun `M not write W writeAll() { empty list }`() {
        // When
        testedWriter.writeAll(emptyList())

        // Then
        verifyNoInteractions(mockSdkCore)
        verifyNoInteractions(mockEventBatchWriter)
    }

    @Test
    fun `M not write W writeAll() { feature not available }`(
        @Forgery event1: BatchedFlagEvaluations.FlagEvaluation,
        @Forgery event2: BatchedFlagEvaluations.FlagEvaluation
    ) {
        // Given
        val events = listOf(event1, event2)
        whenever(mockSdkCore.getFeature(Feature.FLAGS_FEATURE_NAME)).thenReturn(null)

        // When
        testedWriter.writeAll(events)

        // Then
        verify(mockSdkCore).getFeature(Feature.FLAGS_FEATURE_NAME)
        verifyNoInteractions(mockEventBatchWriter)
    }

    // endregion
}
