/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.internal.metric.slowframes

import androidx.metrics.performance.FrameData
import com.datadog.android.rum.configuration.SlowFramesConfiguration
import com.datadog.android.rum.internal.domain.state.SlowFrameRecord
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
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
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

    private lateinit var testedListener: DefaultSlowFramesListener

    @BeforeEach
    fun `set up`() {
        testedListener = DefaultSlowFramesListener(
            SlowFramesConfiguration(
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
        val report = checkNotNull(testedListener.resolveReport(viewId, false))

        // Then
        assertThat(report.slowFramesRecords).hasSize(1)
        assertThat(report.slowFramesRecords.first()).isEqualTo(
            SlowFrameRecord(
                startTimestampNs = jankFrameData.frameStartNanos,
                durationNs = jankFrameData.frameDurationUiNanos
            )
        )
    }

    @Test
    fun `M return report only once W resolveReport(viewId, true)`(forge: Forge) {
        // Given
        testedListener.onViewCreated(viewId, viewCreatedTimestampNs)
        val jankFrameData = forge.aFrameData()
        testedListener.onFrame(jankFrameData)

        // When
        val report1 = checkNotNull(testedListener.resolveReport(viewId, true))
        val report2 = testedListener.resolveReport(viewId, false)

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
        val report1 = checkNotNull(testedListener.resolveReport(viewId, false))
        val report2 = checkNotNull(testedListener.resolveReport(viewId, false))

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
        val report = checkNotNull(testedListener.resolveReport(viewId, false))

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
        val report = checkNotNull(testedListener.resolveReport(viewId, false))

        // Then
        assertThat(report.slowFramesRecords).isNotEmpty()
    }

    @Test
    fun `M return null W resolveReport { view is not created }`(forge: Forge) {
        // Given
        testedListener.onFrame(forge.aFrameData())

        // When
        testedListener.onViewCreated(viewId, viewCreatedTimestampNs)
        val report = testedListener.resolveReport(viewId, false)

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

        val testedListener = DefaultSlowFramesListener(
            SlowFramesConfiguration(
                maxSlowFrameThresholdNs = Long.MAX_VALUE,
                continuousSlowFrameThresholdNs = continuousSlowFrameThresholdNs
            )
        )
        testedListener.onViewCreated(viewId, viewCreatedTimestampNs)
        testedListener.onFrame(jank1)
        testedListener.onFrame(jank2)

        // When
        val report = checkNotNull(testedListener.resolveReport(viewId, false))

        // Then
        assertThat(report.size).isEqualTo(1)
        assertThat(report.slowFramesRecords.first()).isEqualTo(
            SlowFrameRecord(
                startTimestampNs = jank1.frameStartNanos,
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

        val testedListener = DefaultSlowFramesListener(
            SlowFramesConfiguration(
                maxSlowFrameThresholdNs = Long.MAX_VALUE,
                continuousSlowFrameThresholdNs = 16
            )
        )
        testedListener.onViewCreated(viewId, viewCreatedTimestampNs)
        testedListener.onFrame(jank1)
        testedListener.onFrame(jank2)

        // When
        val report = checkNotNull(testedListener.resolveReport(viewId, false))

        // Then
        assertThat(report.size).isEqualTo(2)
        assertThat(report.slowFramesRecords.first()).isEqualTo(
            SlowFrameRecord(
                startTimestampNs = jank1.frameStartNanos,
                durationNs = jank1.frameDurationUiNanos
            )
        )
        assertThat(report.slowFramesRecords.last()).isEqualTo(
            SlowFrameRecord(
                startTimestampNs = jank2.frameStartNanos,
                durationNs = jank2.frameDurationUiNanos
            )
        )
    }

    @Test
    fun `M return empty report W resolveReport { frozen frame should be ignored }`(forge: Forge) {
        // Given
        val jank = forge.aFrameData(
            frameStartNanos = viewCreatedTimestampNs + 1,
            frameDurationUiNanos = 800
        )

        val testedListener = DefaultSlowFramesListener(
            SlowFramesConfiguration(
                maxSlowFrameThresholdNs = 700,
                continuousSlowFrameThresholdNs = 16
            )
        )
        testedListener.onViewCreated(viewId, viewCreatedTimestampNs)
        testedListener.onFrame(jank)

        // When
        val report = checkNotNull(testedListener.resolveReport(viewId, false))

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

        val testedListener = DefaultSlowFramesListener(
            SlowFramesConfiguration(
                maxSlowFrameThresholdNs = frozenFrameThresholdNs,
                continuousSlowFrameThresholdNs = Long.MAX_VALUE
            )
        )
        testedListener.onViewCreated(viewId, viewCreatedTimestampNs)
        testedListener.onFrame(jank1)
        testedListener.onFrame(jank2)

        // When
        val report = checkNotNull(testedListener.resolveReport(viewId, false))

        // Then
        assertThat(report.size).isEqualTo(1)
        assertThat(report.slowFramesRecords.first()).isEqualTo(
            SlowFrameRecord(
                jank1.frameStartNanos,
                durationNs = frozenFrameThresholdNs - 1
            )
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
        val report = checkNotNull(testedListener.resolveReport(viewId, false))

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
        val report = testedListener.resolveReport(viewId, false)

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
        val report = testedListener.resolveReport(viewId, false)

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
        assertThat(defaultConfiguration.freezeDurationThreshold).isEqualTo(5_000_000_000L)
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
        val testedListener = DefaultSlowFramesListener(
            SlowFramesConfiguration(
                freezeDurationThreshold = freezeDurationThresholdNs
            )
        ).apply {
            onViewCreated(viewId, viewStartedAtTimestampNs)
        }

        // When
        testedListener.onAddLongTask(longTaskDuration)

        // Then
        val report = checkNotNull(testedListener.resolveReport(viewId, false))
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
        val testedListener = DefaultSlowFramesListener(
            SlowFramesConfiguration(
                freezeDurationThreshold = 0L, // every long task considered as freeze now
                minViewLifetimeThresholdNs = minViewLifetimeThresholdNs
            )
        ).apply {
            onViewCreated(viewId, viewStartedAtTimestampNs)
        }

        // When
        testedListener.onAddLongTask(longTaskDuration)

        // Then
        assertDoesNotThrow { // No ArithmeticException
            val report = checkNotNull(testedListener.resolveReport(viewId, false))
            assertThat(report.freezeFramesRate(minViewLifetimeThresholdNs - 1)).isZero()
        }
    }

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
