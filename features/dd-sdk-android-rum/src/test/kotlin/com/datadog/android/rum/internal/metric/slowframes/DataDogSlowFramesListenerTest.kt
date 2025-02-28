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
@ForgeConfiguration(value = Configurator::class, seed = 0xe7bbb28646f1L)
internal class DataDogSlowFramesListenerTest {

    @StringForgery
    lateinit var viewId: String

    @LongForgery(min = 1L, Long.MAX_VALUE / 3)
    var viewCreatedTimestampNs: Long = 0L

    private lateinit var testedListener: DataDogSlowFramesListener

    @BeforeEach
    fun `set up`() {
        testedListener = DataDogSlowFramesListener(
            frozenFrameThresholdNs = Long.MAX_VALUE
        )
    }

    @Test
    fun `M return non empty report W resolveReport { jank frame occurred }`(forge: Forge) {
        // Given
        testedListener.onViewCreated(viewId, viewCreatedTimestampNs)
        val jankFrameData = forge.stubJankFrameData()
        testedListener.onFrame(jankFrameData)

        // When
        val report = testedListener.resolveReport(viewId)

        // Then
        assertThat(report.slowFramesRecords.size).isEqualTo(1)
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
        val jankFrameData = forge.stubJankFrameData()
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
        val jankFrameData = forge.stubJankFrameData(isJank = false)
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
        testedListener.onFrame(forge.stubJankFrameData())

        // When
        testedListener.onViewCreated(viewId + forge.aString(), viewCreatedTimestampNs)
        val report = testedListener.resolveReport(viewId)

        // Then
        assertThat(report.slowFramesRecords.size).isGreaterThan(0)
    }

    @Test
    fun `M return empty report W resolveReport { view is not created }`(forge: Forge) {
        // Given
        testedListener.onFrame(forge.stubJankFrameData())

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
        val jank1 = forge.stubJankFrameData(
            frameStartNanos = viewCreatedTimestampNs,
            frameDurationUiNanos = forge.aLong(min = 1, max = continuousSlowFrameThresholdNs / 2)
        )

        val jank2 = forge.stubJankFrameData(
            frameStartNanos = viewCreatedTimestampNs + continuousSlowFrameThresholdNs - 1,
            frameDurationUiNanos = forge.aLong(min = 1, max = continuousSlowFrameThresholdNs / 2)
        )

        val testedListener = DataDogSlowFramesListener(
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
        val jank1 = forge.stubJankFrameData(
            frameStartNanos = viewCreatedTimestampNs,
            frameDurationUiNanos = forge.aLong(
                min = continuousSlowFrameThresholdNs,
                max = 2 * continuousSlowFrameThresholdNs
            )
        )
        val jank2 = forge.stubJankFrameData(
            frameStartNanos = jank1.frameStartNanos + continuousSlowFrameThresholdNs + 1,
            frameDurationUiNanos = forge.aLong(min = 1, max = 100)
        )

        val testedListener = DataDogSlowFramesListener(
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
        val jank = forge.stubJankFrameData(
            frameStartNanos = viewCreatedTimestampNs + 1,
            frameDurationUiNanos = 800
        )

        val testedListener = DataDogSlowFramesListener(
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
        val jank1 = forge.stubJankFrameData(
            frameStartNanos = viewCreatedTimestampNs,
            frameDurationUiNanos = jankDuration
        )
        val jank2 = forge.stubJankFrameData(
            frameStartNanos = viewCreatedTimestampNs + jankDuration,
            frameDurationUiNanos = jankDuration
        )

        val testedListener = DataDogSlowFramesListener(
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
        forge: Forge
    ) {
        // Given
        var item = 0
        val frameData = forge.aList(size = 100) {
            stubJankFrameData(isJank = ++item % 2 == 0)
        }
        val expectedSlowFramesDuration = frameData.filter { it.isJank }.sumOf { it.frameDurationUiNanos }
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

        assertThat(report.slowFramesRate)
            .isEqualTo(expectedSlowFramesDuration.toDouble() / (expectedTotalFrameDuration + 1))
    }

    @Test
    fun `M be expected constant values for thresholds`() {
        // When
        val listener = DataDogSlowFramesListener()

        // Then
        assertThat(listener.maxSlowFramesAmount).isEqualTo(512)
        assertThat(listener.frozenFrameThresholdNs).isEqualTo(700_000_000)
        assertThat(listener.continuousSlowFrameThresholdNs).isEqualTo(16_666_666L)
    }

    private fun Forge.stubJankFrameData(
        frameStartNanos: Long = aLong(min = 1, max = 100) + viewCreatedTimestampNs,
        frameDurationUiNanos: Long = aLong(min = 1, max = 100),
        isJank: Boolean = true
    ) = FrameData(
        frameStartNanos = frameStartNanos,
        frameDurationUiNanos = frameDurationUiNanos,
        isJank = isJank,
        states = emptyList()
    )
}
