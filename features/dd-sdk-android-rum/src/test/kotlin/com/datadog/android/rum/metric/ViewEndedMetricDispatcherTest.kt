/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.metric

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.InternalLogger.Target
import com.datadog.android.rum.internal.metric.NoValueReason
import com.datadog.android.rum.internal.metric.ViewEndedMetricDispatcher
import com.datadog.android.rum.internal.metric.ViewEndedMetricDispatcher.Companion.KEY_CONFIG
import com.datadog.android.rum.internal.metric.ViewEndedMetricDispatcher.Companion.KEY_DURATION
import com.datadog.android.rum.internal.metric.ViewEndedMetricDispatcher.Companion.KEY_INTERACTION_TO_NEXT_VIEW
import com.datadog.android.rum.internal.metric.ViewEndedMetricDispatcher.Companion.KEY_LOADING_TIME
import com.datadog.android.rum.internal.metric.ViewEndedMetricDispatcher.Companion.KEY_METRIC_TYPE
import com.datadog.android.rum.internal.metric.ViewEndedMetricDispatcher.Companion.KEY_NO_VALUE_REASON
import com.datadog.android.rum.internal.metric.ViewEndedMetricDispatcher.Companion.KEY_RVE
import com.datadog.android.rum.internal.metric.ViewEndedMetricDispatcher.Companion.KEY_TIME_TO_NETWORK_SETTLED
import com.datadog.android.rum.internal.metric.ViewEndedMetricDispatcher.Companion.KEY_VALUE
import com.datadog.android.rum.internal.metric.ViewEndedMetricDispatcher.Companion.KEY_VIEW_TYPE
import com.datadog.android.rum.internal.metric.ViewEndedMetricDispatcher.Companion.VIEW_ENDED_MESSAGE
import com.datadog.android.rum.internal.metric.ViewEndedMetricDispatcher.Companion.toAttributeValue
import com.datadog.android.rum.internal.metric.ViewInitializationMetricsConfig
import com.datadog.android.rum.internal.metric.ViewInitializationMetricsState
import com.datadog.android.rum.internal.metric.ViewMetricDispatcher.ViewType
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class ViewEndedMetricDispatcherTest {

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @FloatForgery(min = 0f, max = 1f)
    private var fakeSampleRate: Float = 0f

    @LongForgery(min = 0)
    private var fakeDuration: Long = 0

    @LongForgery(min = 0)
    private var fakeLoadingTime: Long = 0

    private lateinit var fakeViewType: ViewType
    private lateinit var fakeInvState: ViewInitializationMetricsState
    private lateinit var fakeTnsState: ViewInitializationMetricsState

    private lateinit var dispatcherUnderTest: ViewEndedMetricDispatcher

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeViewType = forge.aValueFrom(ViewType::class.java)
        fakeTnsState = forge.aViewInitializationMetricsState(NoValueReason.TimeToNetworkSettle::class.java)
        fakeInvState = forge.aViewInitializationMetricsState(NoValueReason.InteractionToNextView::class.java)

        dispatcherUnderTest = ViewEndedMetricDispatcher(
            viewType = fakeViewType,
            internalLogger = mockInternalLogger,
            samplingRate = fakeSampleRate
        )
    }

    @Test
    fun `M sendMetric only once W sendViewEnded`() {
        // When
        dispatcherUnderTest.sendViewEnded(fakeInvState, fakeTnsState)
        // Then
        verify(mockInternalLogger).logMetric(
            messageBuilder = any(),
            additionalProperties = any(),
            samplingRate = eq(fakeSampleRate),
            creationSampleRate = eq(null)
        )

        // When
        dispatcherUnderTest.sendViewEnded(fakeInvState, fakeTnsState)

        // Then
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.WARN),
            target = eq(Target.TELEMETRY),
            messageBuilder = any(),
            throwable = eq(null),
            onlyOnce = eq(false),
            additionalProperties = eq(null)
        )
        verifyNoMoreInteractions(mockInternalLogger)
    }

    @Test
    fun `M logMetric with expected arguments W sendViewEnded`(forge: Forge) {
        // Given
        dispatcherUnderTest.onDurationResolved(fakeDuration)
        dispatcherUnderTest.onViewLoadingTimeResolved(fakeLoadingTime)

        // When
        dispatcherUnderTest.sendViewEnded(fakeInvState, fakeTnsState)

        // Then
        verify(mockInternalLogger).logMetric(
            messageBuilder = argThat { invoke() == VIEW_ENDED_MESSAGE },
            additionalProperties = eq(expectedAttributes()),
            samplingRate = eq(fakeSampleRate),
            creationSampleRate = eq(null)
        )
    }

    @Test
    fun `M return valid string W toAttributeValue(ViewInitializationMetricsConfig)`() {
        toExhaustiveMap(ViewInitializationMetricsConfig::class.java) {
            when (it) {
                ViewInitializationMetricsConfig.CUSTOM -> "custom"
                ViewInitializationMetricsConfig.TIME_BASED_CUSTOM -> "time_based_custom"
                ViewInitializationMetricsConfig.TIME_BASED_DEFAULT -> "time_based_default"
            }
        }.forEach { (config, expectedValue) ->
            // When
            val actualValue = toAttributeValue(config)

            // Then
            assertThat(actualValue).isEqualTo(expectedValue)
        }
    }

    @Test
    fun `M return valid string W toAttributeValue(InteractionToNextView)`() {
        toExhaustiveMap(NoValueReason.InteractionToNextView::class.java) {
            when (it) {
                NoValueReason.InteractionToNextView.UNKNOWN -> "unknown"
                NoValueReason.InteractionToNextView.NO_ACTION -> "no_action"
                NoValueReason.InteractionToNextView.NO_PREVIOUS_VIEW -> "no_previous_view"
                NoValueReason.InteractionToNextView.NO_ELIGIBLE_ACTION -> "no_eligible_action"
            }
        }.forEach { (noValueReason, expectedValue) ->
            // When
            val actualValue = toAttributeValue(noValueReason)

            // Then
            assertThat(actualValue).isEqualTo(expectedValue)
        }
    }

    @Test
    fun `M return valid string W toAttributeValue(TimeToNetworkSettle)`() {
        toExhaustiveMap(NoValueReason.TimeToNetworkSettle::class.java) {
            when (it) {
                NoValueReason.TimeToNetworkSettle.UNKNOWN -> "unknown"
                NoValueReason.TimeToNetworkSettle.NO_RESOURCES -> "no_resources"
                NoValueReason.TimeToNetworkSettle.NOT_SETTLED_YET -> "not_settled_yet"
                NoValueReason.TimeToNetworkSettle.NO_INITIAL_RESOURCES -> "no_initial_resources"
            }
        }.forEach { (noValueReason, expectedValue) ->
            // When
            val actualValue = toAttributeValue(noValueReason)

            // Then
            assertThat(actualValue).isEqualTo(expectedValue)
        }
    }

    @Test
    fun `M return valid string W toAttributeValue(ViewType)`() {
        toExhaustiveMap(ViewType::class.java) {
            when (it) {
                ViewType.CUSTOM -> "custom"
                ViewType.BACKGROUND -> "background"
                ViewType.APPLICATION -> "application_launch"
            }
        }.forEach { (viewType, expectedValue) ->
            // When
            val actualValue = toAttributeValue(viewType)

            // Then
            assertThat(actualValue).isEqualTo(expectedValue)
        }
    }

    private fun expectedAttributes(
        duration: Long? = fakeDuration,
        loadingTime: Long? = fakeLoadingTime,
        viewType: ViewType = fakeViewType,
        invState: ViewInitializationMetricsState = fakeInvState,
        tnsState: ViewInitializationMetricsState = fakeTnsState
    ) = buildAttributesMap(
        duration = duration,
        loadingTime = loadingTime,
        viewType = toAttributeValue(viewType),

        invValue = invState.initializationTime,
        invConfig = toAttributeValue(invState.config),
        invNoValueReason = toAttributeValue(invState.noValueReason),

        tnsValue = tnsState.initializationTime,
        tnsConfig = toAttributeValue(tnsState.config),
        tnsNoValueReason = toAttributeValue(tnsState.noValueReason)
    )

    companion object {
        private fun buildAttributesMap(
            viewType: String,
            duration: Long?,
            loadingTime: Long?,
            invValue: Long?,
            invConfig: String,
            invNoValueReason: String?,
            tnsValue: Long?,
            tnsConfig: String,
            tnsNoValueReason: String?
        ): Map<String, Any?> = mapOf(
            KEY_METRIC_TYPE to "rum view ended",
            KEY_RVE to mapOf(
                KEY_DURATION to duration,
                KEY_LOADING_TIME to buildMap { put(KEY_VALUE, loadingTime) },
                KEY_VIEW_TYPE to viewType,
                KEY_TIME_TO_NETWORK_SETTLED to buildMetricSpecificAttributes(
                    tnsValue,
                    tnsConfig,
                    tnsNoValueReason
                ),
                KEY_INTERACTION_TO_NEXT_VIEW to buildMetricSpecificAttributes(
                    invValue,
                    invConfig,
                    invNoValueReason
                )
            )
        )

        private fun buildMetricSpecificAttributes(
            tnsValue: Long?,
            tnsConfig: String,
            tnsNoValueReason: String?
        ): Map<Any, Any?> = buildMap {
            put(KEY_VALUE, tnsValue)
            put(KEY_CONFIG, tnsConfig)
            if (null == tnsValue) {
                put(KEY_NO_VALUE_REASON, tnsNoValueReason)
            }
        }

        @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        private inline fun <E : Enum<E>> toExhaustiveMap(
            enumClass: Class<E>,
            valueSelector: (E) -> String
        ): Map<E, String> = enumClass.enumConstants.associateWith(valueSelector)

        private fun <E> Forge.aViewInitializationMetricsState(
            enumClass: Class<E>
        ): ViewInitializationMetricsState where E : Enum<E>, E : NoValueReason {
            return getForgery<ViewInitializationMetricsState>().let { state ->
                if (state.noValueReason != null) return@let state
                state.copy(noValueReason = aValueFrom(enumClass))
            }
        }
    }
}
