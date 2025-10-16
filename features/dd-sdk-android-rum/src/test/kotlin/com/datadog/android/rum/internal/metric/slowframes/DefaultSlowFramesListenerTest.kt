/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.internal.metric.slowframes

import androidx.metrics.performance.FrameData
import com.datadog.android.rum.configuration.SlowFramesConfiguration
import com.datadog.android.rum.internal.domain.state.SlowFrameRecord
import com.datadog.android.rum.internal.instrumentation.insights.InsightsCollector
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(value = Configurator::class)
internal class DefaultSlowFramesListenerTest {

    @StringForgery
    lateinit var viewId: String

    // max here to prevent Long overflow and avoid negative values
    @LongForgery(min = 1L, max = 10_000_000_000L)
    var viewCreatedTimestampNs: Long = 0L

    @LongForgery(min = 1L, max = 10_000_000_000L)
    var fakeViewDurationNs: Long = 0L

    @Mock
    lateinit var mockMetricDispatcher: UISlownessMetricDispatcher

    @Mock
    private lateinit var mockInsightsCollector: InsightsCollector

    private lateinit var testedListener: DefaultSlowFramesListener

    @BeforeEach
    fun `set up`() {
        testedListener = stubSlowFramesListener(
            configuration = SlowFramesConfiguration(
                maxSlowFrameThresholdNs = Long.MAX_VALUE,
                minViewLifetimeThresholdNs = 0
            )
        )
    }

    @Test
    fun `M return report W resolveReport { jank frame occurred }`(forge: Forge) {
        // Given
        testedListener.onViewCreated(viewId, viewCreatedTimestampNs)
        val jankFrameData = forge.aFrameData()
        testedListener.onFrame(jankFrameData)

        // When
        val report = checkNotNull(testedListener.resolveReport(viewId, false, fakeViewDurationNs))

        // Then
        assertThat(report.slowFramesRecords).hasSize(1)
        assertThat(report.slowFramesRecords.first()).isEqualTo(jankFrameData.toSlowFrame())
    }

    @Test
    fun `M return report only once W resolveReport(viewId, true)`(forge: Forge) {
        // Given
        testedListener.onViewCreated(viewId, viewCreatedTimestampNs)
        val jankFrameData = forge.aFrameData()
        testedListener.onFrame(jankFrameData)

        // When
        val report1 = checkNotNull(testedListener.resolveReport(viewId, true, fakeViewDurationNs))
        val report2 = testedListener.resolveReport(viewId, false, fakeViewDurationNs)

        // Then
        assertThat(report1.isEmpty()).isFalse()
        assertThat(report2).isNull()
    }

    @Test
    fun `M return report same report W resolveReport(viewId, false)`(forge: Forge) {
        // Given
        testedListener.onViewCreated(viewId, viewCreatedTimestampNs)
        val jankFrameData = forge.aFrameData()
        testedListener.onFrame(jankFrameData)

        // When
        val report1 = checkNotNull(testedListener.resolveReport(viewId, false, fakeViewDurationNs))
        val report2 = checkNotNull(testedListener.resolveReport(viewId, false, fakeViewDurationNs))

        // Then
        assertThat(report1).isEqualTo(report2)
        assertThat(report1.isEmpty()).isFalse()
        assertThat(report1.isEmpty()).isFalse()
    }

    @Test
    fun `M return empty report W resolveReport { no jank frame occurred }`(forge: Forge) {
        // Given
        testedListener.onViewCreated(viewId, viewCreatedTimestampNs)
        val jankFrameData = forge.aFrameData(isJank = false)
        testedListener.onFrame(jankFrameData)

        // When
        val report = checkNotNull(testedListener.resolveReport(viewId, false, fakeViewDurationNs))

        // Then
        assertThat(report.isEmpty()).isTrue()
    }

    @Test
    fun `M return report W resolveReport { view changed }`(forge: Forge) {
        // Given
        testedListener.onViewCreated(viewId, viewCreatedTimestampNs)
        testedListener.onFrame(forge.aFrameData())

        // When
        testedListener.onViewCreated(viewId + forge.aString(), viewCreatedTimestampNs)
        val report = checkNotNull(testedListener.resolveReport(viewId, false, fakeViewDurationNs))

        // Then
        assertThat(report.slowFramesRecords).isNotEmpty()
    }

    @Test
    fun `M return null W resolveReport { view is not created }`(forge: Forge) {
        // Given
        testedListener.onFrame(forge.aFrameData())

        // When
        testedListener.onViewCreated(viewId, viewCreatedTimestampNs)
        val report = testedListener.resolveReport(viewId, false, fakeViewDurationNs)

        // Then
        assertThat(report).isNull()
    }

    @Test
    fun `M return report with single item W resolveReport { continuous janks }`(
        @LongForgery(min = 16L, max = 1000) continuousSlowFrameThresholdNs: Long,
        forge: Forge
    ) {
        // Given
        val jank1 = forge.aFrameData(
            frameStartNanos = viewCreatedTimestampNs,
            frameDurationUiNanos = forge.aLong(min = 1, max = continuousSlowFrameThresholdNs / 2)
        )

        val jank2 = forge.aFrameData(
            frameStartNanos = viewCreatedTimestampNs + continuousSlowFrameThresholdNs - 1,
            frameDurationUiNanos = forge.aLong(min = 1, max = continuousSlowFrameThresholdNs / 2)
        )

        val testedListener = stubSlowFramesListener(
            SlowFramesConfiguration(
                maxSlowFrameThresholdNs = Long.MAX_VALUE,
                continuousSlowFrameThresholdNs = continuousSlowFrameThresholdNs
            )
        )
        testedListener.onViewCreated(viewId, viewCreatedTimestampNs)
        testedListener.onFrame(jank1)
        testedListener.onFrame(jank2)

        // When
        val report = checkNotNull(testedListener.resolveReport(viewId, false, fakeViewDurationNs))

        // Then
        assertThat(report.size).isEqualTo(1)
        assertThat(report.slowFramesRecords.first()).isEqualTo(
            jank1.toSlowFrame(
                durationNs = jank1.frameDurationUiNanos + jank2.frameDurationUiNanos
            )
        )
    }

    @Test
    fun `M return report with two items W resolveReport { separate janks }`(
        forge: Forge,
        @LongForgery(min = 16, max = 1000) continuousSlowFrameThresholdNs: Long
    ) {
        // Given
        val jank1 = forge.aFrameData(
            frameStartNanos = viewCreatedTimestampNs,
            frameDurationUiNanos = forge.aLong(
                min = continuousSlowFrameThresholdNs,
                max = 2 * continuousSlowFrameThresholdNs
            )
        )
        val jank2 = forge.aFrameData(
            frameStartNanos = jank1.frameStartNanos + continuousSlowFrameThresholdNs + 1,
            frameDurationUiNanos = forge.aLong(min = 1, max = 100)
        )

        val testedListener = stubSlowFramesListener(
            SlowFramesConfiguration(
                maxSlowFrameThresholdNs = Long.MAX_VALUE,
                continuousSlowFrameThresholdNs = 16
            )
        )
        testedListener.onViewCreated(viewId, viewCreatedTimestampNs)
        testedListener.onFrame(jank1)
        testedListener.onFrame(jank2)

        // When
        val report = checkNotNull(testedListener.resolveReport(viewId, false, fakeViewDurationNs))

        // Then
        assertThat(report.size).isEqualTo(2)
        assertThat(report.slowFramesRecords.first()).isEqualTo(jank1.toSlowFrame())
        assertThat(report.slowFramesRecords.last()).isEqualTo(jank2.toSlowFrame())
    }

    private fun stubSlowFramesListener(
        configuration: SlowFramesConfiguration = SlowFramesConfiguration(
            maxSlowFrameThresholdNs = Long.MAX_VALUE,
            minViewLifetimeThresholdNs = 0
        ),
        metricDispatcher: UISlownessMetricDispatcher = mockMetricDispatcher,
        insightsCollector: InsightsCollector = mockInsightsCollector
    ): DefaultSlowFramesListener {
        return DefaultSlowFramesListener(configuration, metricDispatcher, insightsCollector)
    }

    @Test
    fun `M return empty report W resolveReport { frozen frame should be ignored }`(forge: Forge) {
        // Given
        val jank = forge.aFrameData(
            frameStartNanos = viewCreatedTimestampNs + 1,
            frameDurationUiNanos = 800
        )

        val testedListener = stubSlowFramesListener(
            SlowFramesConfiguration(
                maxSlowFrameThresholdNs = 700,
                continuousSlowFrameThresholdNs = 16
            )
        )
        testedListener.onViewCreated(viewId, viewCreatedTimestampNs)
        testedListener.onFrame(jank)

        // When
        val report = checkNotNull(testedListener.resolveReport(viewId, false, fakeViewDurationNs))

        // Then
        assertThat(report.size).isEqualTo(0)
    }

    @Test
    fun `M return report with total duration less than frozen W resolveReport`(
        @LongForgery(min = 700, max = 1000) frozenFrameThresholdNs: Long,
        forge: Forge
    ) {
        // Given
        val jankDuration = frozenFrameThresholdNs - 1
        val jank1 = forge.aFrameData(
            frameStartNanos = viewCreatedTimestampNs,
            frameDurationUiNanos = jankDuration
        )
        val jank2 = forge.aFrameData(
            frameStartNanos = viewCreatedTimestampNs + jankDuration,
            frameDurationUiNanos = jankDuration
        )

        val testedListener = stubSlowFramesListener(
            SlowFramesConfiguration(
                maxSlowFrameThresholdNs = frozenFrameThresholdNs,
                continuousSlowFrameThresholdNs = Long.MAX_VALUE
            )
        )
        testedListener.onViewCreated(viewId, viewCreatedTimestampNs)
        testedListener.onFrame(jank1)
        testedListener.onFrame(jank2)

        // When
        val report = checkNotNull(testedListener.resolveReport(viewId, false, fakeViewDurationNs))

        // Then
        assertThat(report.size).isEqualTo(1)
        assertThat(report.slowFramesRecords.first()).isEqualTo(
            jank1.toSlowFrame(durationNs = frozenFrameThresholdNs - 1)
        )
    }

    @Test
    fun `M compute only jank frames for slow frames duration W onFrame`(
        @LongForgery(min = 1, max = MAX_DURATION_NS) viewDurationNs: Long,
        forge: Forge
    ) {
        // Given
        var item = 0
        val frameData = forge.aList(size = 100) {
            aFrameData(isJank = ++item % 2 == 0)
        }
        val expectedSlowFramesDuration =
            frameData.filter { it.isJank }.sumOf { it.frameDurationUiNanos }
        val expectedTotalFrameDuration = frameData.sumOf { it.frameDurationUiNanos }

        testedListener.onViewCreated(viewId, viewCreatedTimestampNs)
        frameData.forEach { testedListener.onFrame(it) }
        val expectedSlowFrameRate = expectedSlowFramesDuration.toDouble() / expectedTotalFrameDuration * 1000

        // When
        val report = checkNotNull(testedListener.resolveReport(viewId, false, fakeViewDurationNs))

        // Then
        assertThat(report.slowFramesDurationNs)
            .isEqualTo(expectedSlowFramesDuration)

        assertThat(report.totalFramesDurationNs)
            .isEqualTo(expectedTotalFrameDuration)

        assertThat(report.slowFramesRate(viewCreatedTimestampNs + viewDurationNs))
            .isEqualTo(expectedSlowFrameRate)
    }

    @Test
    fun `M return 0 slowFramesRate W resolveReport { totalFramesDurationNs = 0 }`(
        @LongForgery(min = 1, max = MAX_DURATION_NS) viewDurationNs: Long,
        forge: Forge
    ) {
        // Given
        testedListener.onViewCreated(viewId, viewCreatedTimestampNs)
        testedListener.onFrame(forge.aFrameData(frameDurationUiNanos = 0))

        // When
        val report = testedListener.resolveReport(viewId, false, fakeViewDurationNs)

        // Then
        assertThat(checkNotNull(report).slowFramesRate(viewDurationNs)).isZero()
    }

    @Test
    fun `M return 0 freezeFramesRate W resolveReport { totalFramesDurationNs = 0 }`(
        @LongForgery(min = 1, max = MAX_DURATION_NS) viewDurationNs: Long,
        forge: Forge
    ) {
        // Given
        testedListener.onViewCreated(viewId, viewCreatedTimestampNs)
        testedListener.onFrame(forge.aFrameData(frameDurationUiNanos = 0))

        // When
        val report = testedListener.resolveReport(viewId, false, fakeViewDurationNs)

        // Then
        assertThat(checkNotNull(report).freezeFramesRate(viewDurationNs)).isZero()
    }

    @Test
    fun `M be expected constant values`() {
        // When
        val defaultConfiguration = SlowFramesConfiguration()

        // Then
        assertThat(defaultConfiguration.maxSlowFramesAmount).isEqualTo(1000)
        assertThat(defaultConfiguration.maxSlowFrameThresholdNs).isEqualTo(700_000_000)
        assertThat(defaultConfiguration.continuousSlowFrameThresholdNs).isEqualTo(16_666_666L)
        assertThat(defaultConfiguration.freezeDurationThresholdNs).isEqualTo(5_000_000_000L)
        assertThat(defaultConfiguration.minViewLifetimeThresholdNs).isEqualTo(1_000_000_000L)
    }

    @Test
    fun `M compute expected freezeFramesRate W onAddLongTask`(
        @StringForgery viewId: String,
        // max here to avoid Long overflow
        @LongForgery(min = 1L, max = MAX_DURATION_NS) viewDurationNs: Long,
        @LongForgery(min = 1L, max = MAX_DURATION_NS) freezeDurationThresholdNs: Long
    ) {
        // Given
        val viewStartedAtTimestampNs = 0L
        val longTaskDuration = freezeDurationThresholdNs + 1
        val viewEndedTimestampNs = viewStartedAtTimestampNs + viewDurationNs
        val expectedFreezeFramesRate = longTaskDuration.toDouble() / viewDurationNs * 3600
        val testedListener = stubSlowFramesListener(
            SlowFramesConfiguration(
                freezeDurationThresholdNs = freezeDurationThresholdNs
            )
        ).apply {
            onViewCreated(viewId, viewStartedAtTimestampNs)
        }

        // When
        testedListener.onAddLongTask(longTaskDuration)

        // Then
        val report = checkNotNull(testedListener.resolveReport(viewId, false, fakeViewDurationNs))
        assertThat(
            report.freezeFramesRate(viewEndedTimestampNs)
        ).isEqualTo(
            expectedFreezeFramesRate
        )
    }

    @Test
    fun `M return 0 freezeFramesRate W resolveReport { view lived less than minViewLifetimeThresholdNs }`(
        @StringForgery viewId: String,
        @LongForgery(min = 1L, max = MAX_DURATION_NS) longTaskDuration: Long,
        @LongForgery(min = 100L, max = MAX_DURATION_NS) minViewLifetimeThresholdNs: Long
    ) {
        // Given
        val viewStartedAtTimestampNs = 0L
        val testedListener = stubSlowFramesListener(
            SlowFramesConfiguration(
                freezeDurationThresholdNs = 0L, // every long task considered as freeze now
                minViewLifetimeThresholdNs = minViewLifetimeThresholdNs
            )
        ).apply {
            onViewCreated(viewId, viewStartedAtTimestampNs)
        }

        // When
        testedListener.onAddLongTask(longTaskDuration)

        // Then
        assertDoesNotThrow { // No ArithmeticException
            val report = checkNotNull(testedListener.resolveReport(viewId, false, fakeViewDurationNs))
            assertThat(report.freezeFramesRate(minViewLifetimeThresholdNs - 1)).isZero()
        }
    }

    @Test
    fun `M proxy to metricDispatcher W onViewCreated`(
        @StringForgery viewId: String,
        @LongForgery(min = 0) startedTimestampNs: Long
    ) {
        // When
        testedListener.onViewCreated(viewId, startedTimestampNs)

        // Then
        verify(mockMetricDispatcher).onViewCreated(viewId)
    }

    @Test
    fun `M incrementSlowFrameCount W onFrame { isJank = true }`(
        forge: Forge
    ) {
        // Given
        testedListener.onViewCreated(viewId, viewCreatedTimestampNs)

        // When
        testedListener.onFrame(forge.aFrameData(isJank = true))

        // Then
        verify(mockMetricDispatcher).incrementSlowFrameCount(viewId)
        verify(mockMetricDispatcher, never()).incrementIgnoredFrameCount(viewId)
    }

    @Test
    fun `M incrementIgnoredFrameCount W onFrame { isJank = false }`(
        forge: Forge
    ) {
        // Given
        testedListener.onViewCreated(viewId, viewCreatedTimestampNs)

        // When
        testedListener.onFrame(forge.aFrameData(isJank = false))

        // Then
        verify(mockMetricDispatcher).incrementIgnoredFrameCount(viewId)
        verify(mockMetricDispatcher, never()).incrementSlowFrameCount(viewId)
    }

    @Test
    fun `M incrementIgnoredFrameCount W onFrame { isJank = true, frameDurationUiNanos gt maxSlowFrameThresholdNs }`(
        @LongForgery(min = 0, max = Long.MAX_VALUE) maxSlowFrameThresholdNs: Long,
        forge: Forge
    ) {
        // Given
        val testedListener = stubSlowFramesListener(
            SlowFramesConfiguration(
                maxSlowFrameThresholdNs = maxSlowFrameThresholdNs
            )
        )
        testedListener.onViewCreated(viewId, viewCreatedTimestampNs)

        // When
        testedListener.onFrame(
            forge.aFrameData(
                isJank = true,
                frameDurationUiNanos = maxSlowFrameThresholdNs + 1
            )
        )

        // Then
        verify(mockMetricDispatcher).incrementIgnoredFrameCount(viewId)
        verify(mockMetricDispatcher, never()).incrementSlowFrameCount(viewId)
    }

    @Test
    fun `M sendSlowFramesTelemetry W resolveReport { isViewCompleted = true }`(
        forge: Forge
    ) {
        // Given
        testedListener.onViewCreated(viewId, viewCreatedTimestampNs)
        testedListener.onFrame(forge.aFrameData())

        // When
        testedListener.resolveReport(viewId, isViewCompleted = true, fakeViewDurationNs)

        // Then
        verify(mockMetricDispatcher).sendMetric(viewId, fakeViewDurationNs)
    }

    @Test
    fun `M not sendSlowFramesTelemetry W resolveReport { isViewCompleted = false }`(
        forge: Forge
    ) {
        // Given
        testedListener.onViewCreated(viewId, forge.aLong(min = 0))
        testedListener.onFrame(forge.aFrameData())

        // When
        testedListener.resolveReport(viewId, isViewCompleted = false, fakeViewDurationNs)

        // Then
        verify(mockMetricDispatcher, never()).sendMetric(viewId, fakeViewDurationNs)
    }

    @Test
    fun `M drop frame data if it started before view W onFrame { missed frame }`(
        forge: Forge
    ) {
        // Given
        val expiredFrameData = forge.aFrameData(frameStartNanos = viewCreatedTimestampNs - 1)
        val validFrameData = forge.aFrameData(frameStartNanos = viewCreatedTimestampNs + 1)
        testedListener.onViewCreated(viewId, viewCreatedTimestampNs)

        // When
        testedListener.onFrame(expiredFrameData)
        testedListener.onFrame(validFrameData)

        // Then
        val report = testedListener.resolveReport(viewId, true, fakeViewDurationNs)
        val slowFrameRecords = report?.slowFramesRecords?.toList()

        assertThat(report).isNotNull
        assertThat(report?.size).isOne()
        assertThat(slowFrameRecords).hasSize(1)
        assertThat(slowFrameRecords?.first()).isEqualTo(validFrameData.toSlowFrame())
    }

    @Test
    fun `M incrementMissedFrameCount W onFrame { missed frame }`(
        forge: Forge
    ) {
        // Given
        val expiredFrameData = forge.aFrameData(frameStartNanos = viewCreatedTimestampNs - 1)
        val validFrameData = forge.aFrameData(frameStartNanos = viewCreatedTimestampNs + 1)
        testedListener.onViewCreated(viewId, viewCreatedTimestampNs)

        // When
        testedListener.onFrame(expiredFrameData)
        testedListener.onFrame(validFrameData)

        // Then
        verify(mockMetricDispatcher).incrementMissedFrameCount(viewId)
    }

    private fun FrameData.toSlowFrame(
        startTimestampNs: Long = frameStartNanos,
        durationNs: Long = frameDurationUiNanos
    ) = SlowFrameRecord(startTimestampNs, durationNs)

    private fun Forge.aFrameData(
        frameStartNanos: Long = aLong(min = 1, max = 100) + viewCreatedTimestampNs,
        frameDurationUiNanos: Long = aLong(min = 1, max = 100),
        isJank: Boolean = true
    ) = FrameData(
        frameStartNanos = frameStartNanos,
        frameDurationUiNanos = frameDurationUiNanos,
        isJank = isJank,
        states = emptyList()
    )

    companion object {
        // clip max values to avoid Long overflow
        const val MAX_DURATION_NS = 1_000_000_000_000
    }
}
