/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.flags.FlagsConfiguration
import com.datadog.android.flags.internal.model.FlagEvaluation
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.utils.forge.ForgeConfigurator
import com.datadog.android.internal.FeatureContextKeys
import com.datadog.android.internal.rum.RumContext
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.ScheduledExecutorService

@ExtendWith(MockitoExtension::class, ForgeExtension::class)
@ForgeConfiguration(ForgeConfigurator::class)
internal class EvaluationsFeatureTest {

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M correlate evaluations W processEvaluation {typed RUM context}`(
        fakeHasRum: Boolean,
        @Forgery fakeDatadogContext: DatadogContext,
        @Forgery fakeConfiguration: FlagsConfiguration,
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeViewName: String,
        @StringForgery fakeFlagKey: String,
        @StringForgery fakeTargetingKey: String
    ) {
        // Given
        val mockExecutor = mock<ScheduledExecutorService>()
        val mockFeature = mock<FeatureScope>()
        val mockBatchWriter = mock<EventBatchWriter>()
        val mockSdkCore = mock<FeatureSdkCore>()
        whenever(mockSdkCore.internalLogger).thenReturn(mock())
        whenever(mockSdkCore.timeProvider).thenReturn(mock())
        whenever(mockSdkCore.createScheduledExecutorService(any())).thenReturn(mockExecutor)
        whenever(mockSdkCore.getFeature(Feature.FLAGS_EVALUATIONS_FEATURE_NAME)).thenReturn(mockFeature)
        whenever(mockFeature.withWriteContext(any(), any())).thenAnswer {
            val callback = it.getArgument<(DatadogContext, EventWriteScope) -> Unit>(1)
            callback(fakeDatadogContext) { writerScope -> writerScope(mockBatchWriter) }
        }
        val testedFeature = EvaluationsFeature(mockSdkCore, fakeConfiguration)
        testedFeature.onInitialize(mock())
        val fakeRumContext = RumContext(applicationId = fakeApplicationId, viewName = fakeViewName)
        testedFeature.onContextUpdate(
            Feature.RUM_FEATURE_NAME,
            if (fakeHasRum) mapOf(FeatureContextKeys.RUM_CONTEXT to fakeRumContext) else emptyMap()
        )

        // When
        testedFeature.processEvaluation(
            fakeFlagKey,
            EvaluationContext(fakeTargetingKey),
            null,
            null,
            null,
            null
        )
        testedFeature.onStop()

        // Then
        val eventCaptor = argumentCaptor<RawBatchEvent>()
        verify(mockBatchWriter).write(eventCaptor.capture(), isNull(), eq(EventType.DEFAULT))
        val event = FlagEvaluation.fromJson(String(eventCaptor.firstValue.data))
        assertThat(event.context?.dd?.rum?.application?.id).isEqualTo(if (fakeHasRum) fakeApplicationId else null)
        assertThat(event.context?.dd?.rum?.view?.url).isEqualTo(if (fakeHasRum) fakeViewName else null)
    }
}
