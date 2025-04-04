/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.internal.metric.slowframes

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.InternalLogger.Target
import com.datadog.android.rum.configuration.SlowFramesConfiguration
import com.datadog.android.rum.internal.metric.slowframes.DefaultUISlownessMetricDispatcher.Companion.KEY_COUNT
import com.datadog.android.rum.internal.metric.slowframes.DefaultUISlownessMetricDispatcher.Companion.KEY_IGNORED_COUNT
import com.datadog.android.rum.internal.metric.slowframes.DefaultUISlownessMetricDispatcher.Companion.KEY_MISSED_COUNT
import com.datadog.android.rum.internal.metric.slowframes.DefaultUISlownessMetricDispatcher.Companion.KEY_RUM_UI_SLOWNESS
import com.datadog.android.rum.internal.metric.slowframes.DefaultUISlownessMetricDispatcher.Companion.KEY_SLOW_FRAMES
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
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
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
    private lateinit var mockInternalLogger: InternalLogger

    private lateinit var testedDispatcher: DefaultUISlownessMetricDispatcher

    @BeforeEach
    fun `set up`() {
        testedDispatcher = DefaultUISlownessMetricDispatcher(
            config = fakeSlowFramesConfiguration,
            internalLogger = mockInternalLogger,
            samplingRate = fakeSamplingRate
        )
    }

    @Test
    fun `M increment slowFramesCount W incrementSlowFrameCount`() {
        // Given
        testedDispatcher.onViewCreated(fakeViewId)

        // When
        testedDispatcher.incrementSlowFrameCount(fakeViewId)
        testedDispatcher.sendMetric(fakeViewId)

        // Then
        verify(mockInternalLogger).logMetric(
            argThat { invoke() == DefaultUISlownessMetricDispatcher.UI_SLOWNESS_MESSAGE },
            argThat { hasExpectedValue(1, KEY_RUM_UI_SLOWNESS, KEY_SLOW_FRAMES, KEY_COUNT) },
            eq(fakeSamplingRate),
            eq(null)
        )
    }

    @Test
    fun `M increment ignoredFramesCount W incrementIgnoredFrameCount`() {
        // Given
        testedDispatcher.onViewCreated(fakeViewId)

        // When
        testedDispatcher.incrementIgnoredFrameCount(fakeViewId)
        testedDispatcher.sendMetric(fakeViewId)

        // Then
        verify(mockInternalLogger).logMetric(
            argThat { invoke() == DefaultUISlownessMetricDispatcher.UI_SLOWNESS_MESSAGE },
            argThat { hasExpectedValue(1, KEY_RUM_UI_SLOWNESS, KEY_SLOW_FRAMES, KEY_IGNORED_COUNT) },
            eq(fakeSamplingRate),
            eq(null)
        )
    }

    @Test
    fun `M increment missedFramesCount W incrementMissedFrameCount`() {
        // Given
        testedDispatcher.onViewCreated(fakeViewId)

        // When
        testedDispatcher.incrementMissedFrameCount(fakeViewId)
        testedDispatcher.sendMetric(fakeViewId)

        // Then
        verify(mockInternalLogger).logMetric(
            argThat { invoke() == DefaultUISlownessMetricDispatcher.UI_SLOWNESS_MESSAGE },
            argThat { hasExpectedValue(1, KEY_RUM_UI_SLOWNESS, KEY_SLOW_FRAMES, KEY_MISSED_COUNT) },
            eq(fakeSamplingRate),
            eq(null)
        )
    }

    @Test
    fun `M send telemetry only once W sendMetric`() {
        // Given
        testedDispatcher.onViewCreated(fakeViewId)

        // When
        testedDispatcher.sendMetric(fakeViewId)
        testedDispatcher.sendMetric(fakeViewId)

        // Then
        verify(mockInternalLogger).logMetric(
            argThat { invoke() == DefaultUISlownessMetricDispatcher.UI_SLOWNESS_MESSAGE },
            any(),
            eq(fakeSamplingRate),
            eq(null)
        )
    }

    @Test
    fun `M log warning W sendMetric twice`() {
        // Given
        testedDispatcher.onViewCreated(fakeViewId)

        // When
        testedDispatcher.sendMetric(fakeViewId)
        testedDispatcher.sendMetric(fakeViewId)

        // Then
        verify(mockInternalLogger).log(
            level = eq(InternalLogger.Level.WARN),
            target = eq(Target.TELEMETRY),
            messageBuilder = argThat { invoke() == "No telemetry found for viewId=$fakeViewId" },
            throwable = eq(null),
            onlyOnce = eq(false),
            additionalProperties = eq(null)
        )
    }

    @Test
    fun `M samplingRate = 0,75 W sendMetric { default sampling rate }`() {
        // Given
        testedDispatcher = DefaultUISlownessMetricDispatcher(
            config = fakeSlowFramesConfiguration,
            internalLogger = mockInternalLogger
        )
        testedDispatcher.onViewCreated(fakeViewId)

        // When
        testedDispatcher.sendMetric(fakeViewId)

        // Then
        verify(mockInternalLogger).logMetric(
            messageBuilder = any(),
            additionalProperties = any(),
            samplingRate = eq(0.75f),
            creationSampleRate = eq(null)
        )
    }

    companion object {
        @Suppress("UNCHECKED_CAST", "SameParameterValue")
        private fun Map<String, Any?>.hasExpectedValue(value: Any, vararg keys: String): Boolean {
            var targetMap = this
            for (key in keys.slice(0 until keys.size - 1)) {
                targetMap = targetMap[key] as Map<String, Any>
            }

            return targetMap[keys.last()] == value
        }
    }
}
