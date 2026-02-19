/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.metric

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.InternalLogger.Target
import com.datadog.android.internal.attributes.ViewScopeInstrumentationType
import com.datadog.android.rum.internal.domain.scope.RumViewType
import com.datadog.android.rum.internal.metric.NoValueReason
import com.datadog.android.rum.internal.metric.ViewEndedMetricDispatcher
import com.datadog.android.rum.internal.metric.ViewEndedMetricDispatcher.Companion.VIEW_ENDED_MESSAGE
import com.datadog.android.rum.internal.metric.ViewInitializationMetricsConfig
import com.datadog.android.rum.internal.metric.ViewInitializationMetricsState
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
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

    @LongForgery(min = 0)
    private var fakeDuration: Long = 0

    @LongForgery(min = 0)
    private var fakeLoadingTime: Long = 0

    private lateinit var fakeViewType: RumViewType
    private lateinit var fakeInvState: ViewInitializationMetricsState
    private lateinit var fakeTnsState: ViewInitializationMetricsState
    private var fakeInstrumentationType: ViewScopeInstrumentationType? = null

    private lateinit var dispatcherUnderTest: ViewEndedMetricDispatcher

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeViewType = forge.aValueFrom(RumViewType::class.java)
        fakeTnsState = forge.aViewInitializationMetricsState(NoValueReason.TimeToNetworkSettle::class.java)
        fakeInvState = forge.aViewInitializationMetricsState(NoValueReason.InteractionToNextView::class.java)
        fakeInstrumentationType = forge.aNullable {
            anElementFrom(
                ViewScopeInstrumentationType.Native.COMPOSE,
                ViewScopeInstrumentationType.Native.MANUAL,
                ViewScopeInstrumentationType.Native.ACTIVITY,
                ViewScopeInstrumentationType.Native.FRAGMENT
            )
        }

        dispatcherUnderTest = ViewEndedMetricDispatcher(
            viewType = fakeViewType,
            internalLogger = mockInternalLogger,
            instrumentationType = fakeInstrumentationType
        )
    }

    @Test
    fun `M sendMetric only once W sendViewEnded`() {
        // When
        dispatcherUnderTest.sendViewEnded(fakeInvState, fakeTnsState)
        dispatcherUnderTest.sendViewEnded(fakeInvState, fakeTnsState)

        // Then
        verify(mockInternalLogger).logMetric(
            messageBuilder = any(),
            additionalProperties = any(),
            samplingRate = eq(EXPECTED_SAMPLE_RATE),
            creationSampleRate = eq(100.0f)
        )

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
    fun `M samplingRate = 0,75 W sendViewEnded`() {
        // Given
        val dispatcherUnderTest = ViewEndedMetricDispatcher(fakeViewType, mockInternalLogger)
        // When
        dispatcherUnderTest.sendViewEnded(fakeInvState, fakeTnsState)

        // Then
        verify(mockInternalLogger).logMetric(
            messageBuilder = any(),
            additionalProperties = any(),
            samplingRate = eq(EXPECTED_SAMPLE_RATE),
            creationSampleRate = eq(100.0f)
        )
    }

    @Test
    fun `M logMetric with expected arguments W sendViewEnded`() {
        // Given
        dispatcherUnderTest.onDurationResolved(fakeDuration)
        dispatcherUnderTest.onViewLoadingTimeResolved(fakeLoadingTime)

        // When
        dispatcherUnderTest.sendViewEnded(fakeInvState, fakeTnsState)

        // Then
        verify(mockInternalLogger).logMetric(
            messageBuilder = argThat { invoke() == VIEW_ENDED_MESSAGE },
            additionalProperties = eq(expectedAttributes()),
            samplingRate = eq(EXPECTED_SAMPLE_RATE),
            creationSampleRate = eq(100.0f)
        )
    }

    @Test
    fun `M send custom instrumentation type W sendViewEnded() { cross-platform type }`() {
        // Given
        val customInstrumentationType = ViewScopeInstrumentationType.Custom.create("cross_platform_navigator")
        val dispatcher = ViewEndedMetricDispatcher(
            viewType = fakeViewType,
            internalLogger = mockInternalLogger,
            instrumentationType = customInstrumentationType
        )
        dispatcher.onDurationResolved(fakeDuration)
        dispatcher.onViewLoadingTimeResolved(fakeLoadingTime)

        // When
        dispatcher.sendViewEnded(fakeInvState, fakeTnsState)

        // Then
        verify(mockInternalLogger).logMetric(
            messageBuilder = argThat { invoke() == VIEW_ENDED_MESSAGE },
            additionalProperties = eq(expectedAttributes(instrumentationType = "cross_platform_navigator")),
            samplingRate = eq(EXPECTED_SAMPLE_RATE),
            creationSampleRate = eq(100.0f)
        )
    }

    @Test
    fun `M default to manual W sendViewEnded() { null instrumentation type }`() {
        // Given
        val dispatcher = ViewEndedMetricDispatcher(
            viewType = fakeViewType,
            internalLogger = mockInternalLogger,
            instrumentationType = null
        )
        dispatcher.onDurationResolved(fakeDuration)
        dispatcher.onViewLoadingTimeResolved(fakeLoadingTime)

        // When
        dispatcher.sendViewEnded(fakeInvState, fakeTnsState)

        // Then
        verify(mockInternalLogger).logMetric(
            messageBuilder = argThat { invoke() == VIEW_ENDED_MESSAGE },
            additionalProperties = eq(expectedAttributes(instrumentationType = "manual")),
            samplingRate = eq(EXPECTED_SAMPLE_RATE),
            creationSampleRate = eq(100.0f)
        )
    }

    private fun expectedAttributes(
        duration: Long? = fakeDuration,
        loadingTime: Long? = fakeLoadingTime,
        viewType: RumViewType = fakeViewType,
        invState: ViewInitializationMetricsState = fakeInvState,
        tnsState: ViewInitializationMetricsState = fakeTnsState,
        instrumentationType: String? = fakeInstrumentationType?.value
    ) = buildAttributesMap(
        duration = duration,
        loadingTime = loadingTime,
        viewType = viewTypeToString(viewType),
        invValue = invState.initializationTime,
        invConfig = configToString(invState.config),
        invNoValueReason = invNoValueReasonToString(invState.noValueReason),
        tnsValue = tnsState.initializationTime,
        tnsConfig = configToString(tnsState.config),
        tnsNoValueReason = tnsNoValueReasonToString(tnsState.noValueReason),
        instrumentationType = instrumentationType
    )

    companion object {

        private const val EXPECTED_SAMPLE_RATE = 0.75f

        private fun viewTypeToString(viewType: RumViewType): String = when (viewType) {
            RumViewType.NONE,
            RumViewType.FOREGROUND -> "custom"
            RumViewType.BACKGROUND -> "background"
            RumViewType.APPLICATION_LAUNCH -> "application_launch"
        }

        private fun configToString(config: ViewInitializationMetricsConfig): String = when (config) {
            ViewInitializationMetricsConfig.DISABLED -> "disabled"
            ViewInitializationMetricsConfig.CUSTOM -> "custom"
            ViewInitializationMetricsConfig.TIME_BASED_DEFAULT -> "time_based_default"
            ViewInitializationMetricsConfig.TIME_BASED_CUSTOM -> "time_based_custom"
        }

        private fun tnsNoValueReasonToString(reason: NoValueReason?): String? = when (reason) {
            null -> "unknown"
            is NoValueReason.TimeToNetworkSettle -> when (reason) {
                NoValueReason.TimeToNetworkSettle.UNKNOWN -> "unknown"
                NoValueReason.TimeToNetworkSettle.NO_RESOURCES -> "no_resources"
                NoValueReason.TimeToNetworkSettle.NO_INITIAL_RESOURCES -> "no_initial_resources"
                NoValueReason.TimeToNetworkSettle.NOT_SETTLED_YET -> "not_settled_yet"
            }
            else -> "unknown"
        }

        private fun invNoValueReasonToString(reason: NoValueReason?): String? = when (reason) {
            null -> "unknown"
            is NoValueReason.InteractionToNextView -> when (reason) {
                NoValueReason.InteractionToNextView.UNKNOWN -> "unknown"
                NoValueReason.InteractionToNextView.NO_PREVIOUS_VIEW -> "no_previous_view"
                NoValueReason.InteractionToNextView.NO_ACTION -> "no_action"
                NoValueReason.InteractionToNextView.NO_ELIGIBLE_ACTION -> "no_eligible_action"
                NoValueReason.InteractionToNextView.DISABLED -> "disabled"
            }
            else -> "unknown"
        }

        private fun buildAttributesMap(
            viewType: String,
            duration: Long?,
            loadingTime: Long?,
            invValue: Long?,
            invConfig: String,
            invNoValueReason: String?,
            tnsValue: Long?,
            tnsConfig: String,
            tnsNoValueReason: String?,
            instrumentationType: String?
        ): Map<String, Any?> = mapOf(
            "metric_type" to "rum view ended",
            "rve" to buildMap<String, Any?> {
                if (duration != null) put("duration", duration)
                if (loadingTime != null) {
                    put("loading_time", buildMap<String, Any?> {
                        put("value", loadingTime)
                    })
                }
                put("view_type", viewType)
                put(
                    "tns",
                    buildMetricSpecificAttributes(tnsValue, tnsConfig, tnsNoValueReason)
                )
                put(
                    "inv",
                    buildMetricSpecificAttributes(invValue, invConfig, invNoValueReason)
                )
                put("instrumentation_type", instrumentationType ?: "manual")
            }
        )

        private fun buildMetricSpecificAttributes(
            value: Long?,
            config: String,
            noValueReason: String?
        ): Map<String, Any?> = buildMap {
            if (value != null) put("value", value)
            put("config", config)
            if (noValueReason != null && value == null) {
                put("no_value_reason", noValueReason)
            }
        }

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
