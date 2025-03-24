/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.internal.metric.slowframes

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.InternalLogger.Target
import com.datadog.android.rum.configuration.SlowFramesConfiguration
import com.datadog.android.rum.internal.metric.slowframes.DefaultUISlownessMetricDispatcher.Companion.KEY_CONFIG
import com.datadog.android.rum.internal.metric.slowframes.DefaultUISlownessMetricDispatcher.Companion.KEY_COUNT
import com.datadog.android.rum.internal.metric.slowframes.DefaultUISlownessMetricDispatcher.Companion.KEY_FREEZED_FRAMES
import com.datadog.android.rum.internal.metric.slowframes.DefaultUISlownessMetricDispatcher.Companion.KEY_IGNORED_COUNT
import com.datadog.android.rum.internal.metric.slowframes.DefaultUISlownessMetricDispatcher.Companion.KEY_MAX_COUNT
import com.datadog.android.rum.internal.metric.slowframes.DefaultUISlownessMetricDispatcher.Companion.KEY_MAX_DURATION
import com.datadog.android.rum.internal.metric.slowframes.DefaultUISlownessMetricDispatcher.Companion.KEY_METRIC_TYPE
import com.datadog.android.rum.internal.metric.slowframes.DefaultUISlownessMetricDispatcher.Companion.KEY_MIN_DURATION
import com.datadog.android.rum.internal.metric.slowframes.DefaultUISlownessMetricDispatcher.Companion.KEY_RUM_UI_SLOWNESS
import com.datadog.android.rum.internal.metric.slowframes.DefaultUISlownessMetricDispatcher.Companion.KEY_SLOW_FRAMES
import com.datadog.android.rum.internal.metric.slowframes.DefaultUISlownessMetricDispatcher.Companion.KEY_SLOW_FRAME_THRESHOLD
import com.datadog.android.rum.internal.metric.slowframes.DefaultUISlownessMetricDispatcher.Companion.KEY_VIEW_MIN_DURATION
import com.datadog.android.rum.internal.metric.slowframes.DefaultUISlownessMetricDispatcher.Companion.VALUE_METRIC_TYPE
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DefaultUISlownessMetricDispatcherTest {

    @StringForgery
    lateinit var fakeViewId: String

    @FloatForgery(min = 0f, max = 100f)
    var fakeSamplingRate: Float = 0f

    @Forgery
    lateinit var fakeSlowFramesConfiguration: SlowFramesConfiguration

    @Mock
    private lateinit var internalLogger: InternalLogger

    private lateinit var testedDispatcher: DefaultUISlownessMetricDispatcher

    @BeforeEach
    fun `set up`() {
        testedDispatcher = DefaultUISlownessMetricDispatcher(
            config = fakeSlowFramesConfiguration,
            internalLogger = internalLogger,
            samplingRate = fakeSamplingRate
        )
    }

    @Test
    fun `M increment slowFramesCount W incSlowFrame`() {
        // Given
        testedDispatcher.onViewCreated(fakeViewId)

        // When
        testedDispatcher.incSlowFrame(fakeViewId)
        testedDispatcher.sendMetric(fakeViewId)

        // Then
        verify(internalLogger).logMetric(
            argThat { invoke() == DefaultUISlownessMetricDispatcher.UI_SLOWNESS_MESSAGE },
            eq(buildMetricAttributesMap(slowFramesCount = 1, config = fakeSlowFramesConfiguration)),
            eq(fakeSamplingRate),
            eq(null)
        )
    }

    @Test
    fun `M increment freezeFramesCount W incFreezeFrame`() {
        // Given
        testedDispatcher.onViewCreated(fakeViewId)

        // When
        testedDispatcher.incFreezeFrame(fakeViewId)
        testedDispatcher.sendMetric(fakeViewId)

        // Then
        verify(internalLogger).logMetric(
            argThat { invoke() == DefaultUISlownessMetricDispatcher.UI_SLOWNESS_MESSAGE },
            eq(buildMetricAttributesMap(freezeFramesCount = 1, config = fakeSlowFramesConfiguration)),
            eq(fakeSamplingRate),
            eq(null)
        )
    }

    @Test
    fun `M increment ignoredFramesCount W incFreezeFrame`() {
        // Given
        testedDispatcher.onViewCreated(fakeViewId)

        // When
        testedDispatcher.incIgnoredFrame(fakeViewId)
        testedDispatcher.sendMetric(fakeViewId)

        // Then
        verify(internalLogger).logMetric(
            argThat { invoke() == DefaultUISlownessMetricDispatcher.UI_SLOWNESS_MESSAGE },
            eq(buildMetricAttributesMap(ignoredFramesCount = 1, config = fakeSlowFramesConfiguration)),
            eq(fakeSamplingRate),
            eq(null)
        )
    }

    @Test
    fun `M send telemetry only once W sendSlowFramesTelemetry`() {
        // Given
        testedDispatcher.onViewCreated(fakeViewId)

        // When
        testedDispatcher.sendMetric(fakeViewId)
        testedDispatcher.sendMetric(fakeViewId)

        // Then
        verify(internalLogger, times(1)).logMetric(
            argThat { invoke() == DefaultUISlownessMetricDispatcher.UI_SLOWNESS_MESSAGE },
            eq(buildMetricAttributesMap(config = fakeSlowFramesConfiguration)),
            eq(fakeSamplingRate),
            eq(null)
        )
    }

    @Test
    fun `M log warning W sendSlowFramesTelemetry twice`() {
        // Given
        testedDispatcher.onViewCreated(fakeViewId)

        // When
        testedDispatcher.sendMetric(fakeViewId)
        testedDispatcher.sendMetric(fakeViewId)

        // Then
        verify(internalLogger).log(
            level = eq(InternalLogger.Level.WARN),
            target = eq(Target.TELEMETRY),
            messageBuilder = argThat { invoke() == "No telemetry found for viewId=$fakeViewId" },
            throwable = eq(null),
            onlyOnce = eq(false),
            additionalProperties = eq(null)
        )
    }

    companion object {

        private fun buildMetricAttributesMap(
            slowFramesCount: Int = 0,
            ignoredFramesCount: Int = 0,
            freezeFramesCount: Int = 0,
            config: SlowFramesConfiguration
        ) = buildMetricAttributesMap(
            slowFramesCount = slowFramesCount,
            ignoredFramesCount = ignoredFramesCount,
            freezeFramesCount = freezeFramesCount,
            freezeDurationThreshold = config.freezeDurationThresholdNs,
            maxSlowFramesAmount = config.maxSlowFramesAmount,
            maxSlowFrameThresholdNs = config.maxSlowFrameThresholdNs,
            minViewLifetimeThresholdNs = config.minViewLifetimeThresholdNs
        )

        private fun buildMetricAttributesMap(
            slowFramesCount: Int = 0,
            ignoredFramesCount: Int = 0,
            freezeFramesCount: Int = 0,
            freezeDurationThreshold: Long = 0L,
            maxSlowFramesAmount: Int = 0,
            maxSlowFrameThresholdNs: Long = 0L,
            minViewLifetimeThresholdNs: Long = 0L
        ): Map<String, Any> = mapOf(
            KEY_METRIC_TYPE to VALUE_METRIC_TYPE,
            KEY_RUM_UI_SLOWNESS to mapOf<String, Any>(
                KEY_SLOW_FRAMES to mapOf(
                    KEY_COUNT to slowFramesCount,
                    KEY_IGNORED_COUNT to ignoredFramesCount,
                    KEY_CONFIG to mapOf(
                        KEY_MAX_COUNT to maxSlowFramesAmount,
                        KEY_SLOW_FRAME_THRESHOLD to 2.0f,
                        KEY_MAX_DURATION to maxSlowFrameThresholdNs,
                        KEY_VIEW_MIN_DURATION to minViewLifetimeThresholdNs
                    )
                ),
                KEY_FREEZED_FRAMES to mapOf(
                    KEY_COUNT to freezeFramesCount,
                    KEY_CONFIG to mapOf(KEY_MIN_DURATION to freezeDurationThreshold)
                )
            )
        )
    }
}
