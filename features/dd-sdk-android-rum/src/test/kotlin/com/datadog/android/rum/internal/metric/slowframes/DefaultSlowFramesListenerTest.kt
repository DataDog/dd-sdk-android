/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.internal.metric.slowframes

import androidx.metrics.performance.FrameData
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
            frozenFrameThresholdNs = Long.MAX_VALUE,
            minViewLifetimeThresholdNs = 0
        )
    }

    @Test
    fun `M return non empty report W resolveReport { jank frame occurred }`(forge: Forge) {
        // Given
        testedListener.onViewCreated(viewId, viewCreatedTimestampNs)
        val jankFrameData = forge.aFrameData()
        testedListener.onFrame(jankFrameData)

        // When
        val report = testedListener.resolveReport(viewId)

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
    fun `M return report only once W resolveReport`(forge: Forge) {
        // Given
        testedListener.onViewCreated(viewId, viewCreatedTimestampNs)
        val jankFrameData = forge.aFrameData()
        testedListener.onFrame(jankFrameData)

        // When
        val report1 = testedListener.resolveReport(viewId)
        val report2 = testedListener.resolveReport(viewId)

        // Then
        assertThat(report1.isEmpty()).isFalse()
        assertThat(report2.isEmpty()).isTrue()
    }

    @Test
    fun `M return empty report W resolveReport { no jank frame occurred }`(forge: Forge) {
        // Given
        testedListener.onViewCreated(viewId, viewCreatedTimestampNs)
        val jankFrameData = forge.aFrameData(isJank = false)
        testedListener.onFrame(jankFrameData)

        // When
        val report = testedListener.resolveReport(viewId)

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
        val report = testedListener.resolveReport(viewId)

        // Then
        assertThat(report.slowFramesRecords.size).isGreaterThan(0)
    }

    @Test
    fun `M return empty report W resolveReport { view is not created }`(forge: Forge) {
        // Given
        testedListener.onFrame(forge.aFrameData())

        // When
        testedListener.onViewCreated(viewId, viewCreatedTimestampNs)
        val report = testedListener.resolveReport(viewId)

        // Then
        assertThat(report.isEmpty()).isTrue()
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
            frozenFrameThresholdNs = Long.MAX_VALUE,
            continuousSlowFrameThresholdNs = continuousSlowFrameThresholdNs
        )
        testedListener.onViewCreated(viewId, viewCreatedTimestampNs)
        testedListener.onFrame(jank1)
        testedListener.onFrame(jank2)

        // When
        val report = testedListener.resolveReport(viewId)

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
            frozenFrameThresholdNs = Long.MAX_VALUE,
            continuousSlowFrameThresholdNs = 16
        )
        testedListener.onViewCreated(viewId, viewCreatedTimestampNs)
        testedListener.onFrame(jank1)
        testedListener.onFrame(jank2)

        // When
        val report = testedListener.resolveReport(viewId)

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
            frozenFrameThresholdNs = 700,
            continuousSlowFrameThresholdNs = 16
        )
        testedListener.onViewCreated(viewId, viewCreatedTimestampNs)
        testedListener.onFrame(jank)

        // When
        val report = testedListener.resolveReport(viewId)

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
            frozenFrameThresholdNs = frozenFrameThresholdNs,
            continuousSlowFrameThresholdNs = Long.MAX_VALUE
        )
        testedListener.onViewCreated(viewId, viewCreatedTimestampNs)
        testedListener.onFrame(jank1)
        testedListener.onFrame(jank2)

        // When
        val report = testedListener.resolveReport(viewId)

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

        // When
        val report = testedListener.resolveReport(viewId)

        // Then
        assertThat(report.slowFramesDurationNs)
            .isEqualTo(expectedSlowFramesDuration)

        assertThat(report.totalFramesDurationNs)
            .isEqualTo(expectedTotalFrameDuration)

        assertThat(report.slowFramesRate(viewCreatedTimestampNs + viewDurationNs))
            .isEqualTo(expectedSlowFramesDuration.toDouble() / expectedTotalFrameDuration)
    }

    @Test
    fun `M return 0 W slowFramesRate { totalFramesDurationNs = 0 }`(
        @LongForgery(min = 1, max = MAX_DURATION_NS) viewDurationNs: Long
    ) {
        // Given
        testedListener.onViewCreated(viewId, viewCreatedTimestampNs)

        // When
        val report = testedListener.resolveReport(viewId)

        // Then
        assertDoesNotThrow { // No ArithmeticException
            assertThat(report.slowFramesDurationNs)
                .isZero()

            assertThat(report.totalFramesDurationNs)
                .isZero()

            assertThat(report.slowFramesRate(viewCreatedTimestampNs + viewDurationNs)).isZero()
        }
    }

    @Test
    fun `M be expected constant values for thresholds`() {
        // When
        val testedListener = DefaultSlowFramesListener()

        // Then
        assertThat(testedListener.maxSlowFramesAmount).isEqualTo(512)
        assertThat(testedListener.frozenFrameThresholdNs).isEqualTo(700_000_000)
        assertThat(testedListener.continuousSlowFrameThresholdNs).isEqualTo(16_666_666L)
    }

    @Test
    fun `M compute expected ANR rate W onAddLongTask`(
        @StringForgery viewId: String,
        // max here to avoid Long overflow
        @LongForgery(min = 1L, max = MAX_DURATION_NS) viewDurationNs: Long,
        @LongForgery(min = 1L, max = MAX_DURATION_NS) anrDurationThresholdNs: Long
    ) {
        // Given
        val viewStartedAtTimestampNs = 0L
        val longTaskDuration = anrDurationThresholdNs + 1
        val viewEndedTimestampNs = viewStartedAtTimestampNs + viewDurationNs
        val expectedAnrRatio = longTaskDuration.toDouble() / viewDurationNs
        val testedListener = DefaultSlowFramesListener(
            anrDuration = anrDurationThresholdNs
        ).apply {
            onViewCreated(viewId, viewStartedAtTimestampNs)
        }

        // When
        testedListener.onAddLongTask(longTaskDuration)

        // Then
        val report = testedListener.resolveReport(viewId)
        assertThat(
            report.anrDurationRatio(viewEndedTimestampNs)
        ).isEqualTo(
            expectedAnrRatio
        )
    }

    @Test
    fun `M return 0 W anrDurationRatio { view lived less than minViewLifetimeThresholdNs }`(
        @StringForgery viewId: String,
        @LongForgery(min = 1L, max = MAX_DURATION_NS) longTaskDuration: Long,
        @LongForgery(min = 100L, max = MAX_DURATION_NS) minViewLifetimeThresholdNs: Long
    ) {
        // Given
        val viewStartedAtTimestampNs = 0L
        val testedListener = DefaultSlowFramesListener(
            anrDuration = 0L, // every long task is ANR now
            minViewLifetimeThresholdNs = minViewLifetimeThresholdNs
        ).apply {
            onViewCreated(viewId, viewStartedAtTimestampNs)
        }

        // When
        testedListener.onAddLongTask(longTaskDuration)

        // Then
        assertDoesNotThrow { // No ArithmeticException
            val report = testedListener.resolveReport(viewId)
            assertThat(report.anrDurationRatio(minViewLifetimeThresholdNs - 1)).isZero()
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
