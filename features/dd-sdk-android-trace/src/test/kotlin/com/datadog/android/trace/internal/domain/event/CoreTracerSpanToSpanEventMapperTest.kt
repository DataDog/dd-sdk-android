/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.domain.event

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.context.TimeInfo
import com.datadog.android.log.LogAttributes
import com.datadog.android.trace.assertj.SpanEventAssert.Companion.assertThat
import com.datadog.android.utils.forge.Configurator
import com.datadog.trace.api.DDSpanId
import com.datadog.trace.api.internal.util.LongStringUtils
import com.datadog.trace.core.DDSpan
import com.datadog.trace.core.DDSpanContext
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat as assertThatJ
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class CoreTracerSpanToSpanEventMapperTest {

    private lateinit var testedMapper: CoreTracerSpanToSpanEventMapper

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @BoolForgery
    var fakeNetworkInfoEnabled: Boolean = false

    // region Tests

    @BeforeEach
    fun `set up`() {
        testedMapper = CoreTracerSpanToSpanEventMapper(fakeNetworkInfoEnabled)
    }

    @Test
    fun `M map a DdSpan to a SpanEvent W map()`(
        @Forgery fakeSpan: DDSpan
    ) {
        // Given
        val expectedMeta = fakeSpan.baggage + fakeSpan.tags.map {
            it.key to it.value.toString()
        }
        val expectedMetrics = fakeSpan.expectedMetrics()

        // When
        val event = testedMapper.map(fakeDatadogContext, fakeSpan)

        // Then
        assertThat(event)
            .hasSpanId(DDSpanId.toHexStringPadded(fakeSpan.spanId))
            .hasLeastSignificant64BitsTraceId(LongStringUtils.toHexStringPadded(fakeSpan.traceId.toLong(), 16))
            .hasMostSignificant64BitsTraceId(LongStringUtils.toHexStringPadded(fakeSpan.traceId.toHighOrderLong(), 16))
            .hasParentId(DDSpanId.toHexStringPadded(fakeSpan.parentId))
            .hasServiceName(fakeSpan.serviceName)
            .hasOperationName(fakeSpan.operationName.toString())
            .hasResourceName(fakeSpan.resourceName.toString())
            .hasSpanType("custom")
            .hasSpanSource(fakeDatadogContext.source)
            .hasApplicationId(null)
            .hasSessionId(null)
            .hasViewId(null)
            .hasErrorFlag(fakeSpan.error.toLong())
            .hasSpanStartTime(fakeSpan.startTime + fakeDatadogContext.time.serverTimeOffsetNs)
            .hasSpanDuration(fakeSpan.durationNano)
            .hasSpanLinks(fakeSpan.links)
            .hasTracerVersion(fakeDatadogContext.sdkVersion)
            .hasClientPackageVersion(fakeDatadogContext.version).apply {
                if (fakeNetworkInfoEnabled) {
                    hasNetworkInfo(fakeDatadogContext.networkInfo)
                } else {
                    doesntHaveNetworkInfo()
                }
            }
            .hasDeviceInfo(fakeDatadogContext.deviceInfo)
            .hasOsInfo(fakeDatadogContext.deviceInfo)
            .hasUserInfo(fakeDatadogContext.userInfo)
            .hasAccountInfo(fakeDatadogContext.accountInfo)
            .hasVariant(fakeDatadogContext.variant)
            .hasMeta(expectedMeta)
            .hasMetrics(expectedMetrics)
    }

    @Test
    fun `M map a DdSpan to a SpanEvent with RUM info W map() {RUM info present}`(
        @Forgery fakeSpan: DDSpan,
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeSessionId: String,
        @StringForgery fakeViewId: String
    ) {
        // Given
        val tags = fakeSpan.tags.toMutableMap().apply {
            this[LogAttributes.RUM_APPLICATION_ID] = fakeApplicationId
            this[LogAttributes.RUM_SESSION_ID] = fakeSessionId
            this[LogAttributes.RUM_VIEW_ID] = fakeViewId
        }
        whenever(fakeSpan.tags).thenReturn(tags)
        whenever(fakeSpan.context().tags).thenReturn(tags)

        // Given
        val expectedMeta = fakeSpan.baggage + fakeSpan.tags.map {
            it.key to it.value.toString()
        }
        val expectedMetrics = fakeSpan.expectedMetrics()

        // When
        val event = testedMapper.map(fakeDatadogContext, fakeSpan)

        // Then
        assertThat(event)
            .hasSpanId(DDSpanId.toHexStringPadded(fakeSpan.spanId))
            .hasLeastSignificant64BitsTraceId(LongStringUtils.toHexStringPadded(fakeSpan.traceId.toLong(), 16))
            .hasMostSignificant64BitsTraceId(LongStringUtils.toHexStringPadded(fakeSpan.traceId.toHighOrderLong(), 16))
            .hasParentId(DDSpanId.toHexStringPadded(fakeSpan.parentId))
            .hasServiceName(fakeSpan.serviceName)
            .hasOperationName(fakeSpan.operationName.toString())
            .hasResourceName(fakeSpan.resourceName.toString())
            .hasSpanType("custom")
            .hasSpanSource(fakeDatadogContext.source)
            .hasApplicationId(fakeApplicationId)
            .hasSessionId(fakeSessionId)
            .hasViewId(fakeViewId)
            .hasErrorFlag(fakeSpan.error.toLong())
            .hasSpanStartTime(fakeSpan.startTime + fakeDatadogContext.time.serverTimeOffsetNs)
            .hasSpanDuration(fakeSpan.durationNano)
            .hasSpanLinks(fakeSpan.links)
            .hasTracerVersion(fakeDatadogContext.sdkVersion)
            .hasClientPackageVersion(fakeDatadogContext.version).apply {
                if (fakeNetworkInfoEnabled) {
                    hasNetworkInfo(fakeDatadogContext.networkInfo)
                } else {
                    doesntHaveNetworkInfo()
                }
            }.hasUserInfo(fakeDatadogContext.userInfo)
            .hasAccountInfo(fakeDatadogContext.accountInfo)
            .hasDeviceInfo(fakeDatadogContext.deviceInfo)
            .hasOsInfo(fakeDatadogContext.deviceInfo)
            .hasVariant(fakeDatadogContext.variant)
            .hasMeta(expectedMeta)
            .hasMetrics(expectedMetrics)
    }

    @Test
    fun `M mark the SpanEvent as top span W map() { parentId is 0 }`(
        @Forgery fakeSpan: DDSpan
    ) {
        // Given
        whenever(fakeSpan.parentId).thenReturn(0L)

        // When
        val event = testedMapper.map(fakeDatadogContext, fakeSpan)

        // Then
        assertThat(event).isTopSpan()
    }

    @Test
    fun `M not mark the SpanEvent as top span W map() { parentId is different than 0 }`(
        forge: Forge,
        @Forgery fakeSpan: DDSpan
    ) {
        // Given
        whenever(fakeSpan.parentId).thenReturn(forge.aLong(min = 1))

        // When
        val event = testedMapper.map(fakeDatadogContext, fakeSpan)

        // Then
        assertThat(event).isNotTopSpan()
    }

    // region Reproduction tests for RUMS-5093: Incorrect Timing and Ordering of Traces

    @Test
    fun `REPRO RUMS-5093 - M not shift span start earlier W map() { device clock ahead of server by 5 seconds }`(
        @Forgery fakeSpan: DDSpan
    ) {
        // Given
        // Simulate device clock being 5 seconds AHEAD of server: serverOffset is negative.
        // When serialization applies this negative offset, the span start is shifted to BEFORE
        // the actual request started, making the android.request span appear shorter than backend
        // child spans whose clocks are server-authoritative.
        val deviceClockAheadOffsetNs = -5_000_000_000L // device is 5s ahead of server
        val fakeSpanStartTime = 1_700_000_000_000_000_000L // arbitrary span start in nanos
        val fakeSpanDuration = 2_000_000_000L // 2 second span duration
        whenever(fakeSpan.startTime).thenReturn(fakeSpanStartTime)
        whenever(fakeSpan.durationNano).thenReturn(fakeSpanDuration)

        val timeInfo = TimeInfo(
            deviceTimeNs = fakeSpanStartTime,
            serverTimeNs = fakeSpanStartTime + deviceClockAheadOffsetNs,
            serverTimeOffsetNs = deviceClockAheadOffsetNs,
            serverTimeOffsetMs = -5_000L
        )
        val contextWithNegativeOffset = fakeDatadogContext.copy(time = timeInfo)

        // When
        val event = testedMapper.map(contextWithNegativeOffset, fakeSpan)

        // Then: the serialized span start should NOT be before the actual span start on device.
        // A negative serverOffset shifts start earlier, causing the span to appear to start
        // before the actual HTTP request was initiated. The fix should capture the NTP offset
        // at span creation time (not serialization time) or clamp negative offsets.
        // FAILS because: event.start = fakeSpanStartTime + (-5s) = fakeSpanStartTime - 5s
        // which is EARLIER than fakeSpanStartTime, making the android.request span
        // appear to have negligible duration relative to server-authoritative backend spans.
        assertThatJ(event.start)
            .describedAs(
                "RUMS-5093: Span start must not be shifted earlier than the actual span start " +
                    "when device clock is ahead of server. " +
                    "Current bug: start=${event.start} is before spanStartTime=$fakeSpanStartTime " +
                    "because serverOffset=$deviceClockAheadOffsetNs is applied at serialization time."
            )
            .isGreaterThanOrEqualTo(fakeSpanStartTime)
    }

    @Test
    fun `REPRO RUMS-5093 - M preserve span duration W map() { device clock ahead of server by 5 seconds }`(
        @Forgery fakeSpan: DDSpan
    ) {
        // Given
        // Even with negative server offset, the duration field (derived from monotonic nanoTime)
        // should remain correct. This test documents that the duration is correct (computed from
        // monotonic clock) while the start timestamp is wrong (shifted by serialization-time NTP offset).
        val deviceClockAheadOffsetNs = -5_000_000_000L
        val fakeSpanStartTime = 1_700_000_000_000_000_000L
        val fakeSpanDuration = 2_000_000_000L // 2 seconds duration (monotonic, correct)
        whenever(fakeSpan.startTime).thenReturn(fakeSpanStartTime)
        whenever(fakeSpan.durationNano).thenReturn(fakeSpanDuration)

        val timeInfo = TimeInfo(
            deviceTimeNs = fakeSpanStartTime,
            serverTimeNs = fakeSpanStartTime + deviceClockAheadOffsetNs,
            serverTimeOffsetNs = deviceClockAheadOffsetNs,
            serverTimeOffsetMs = -5_000L
        )
        val contextWithNegativeOffset = fakeDatadogContext.copy(time = timeInfo)

        // When
        val event = testedMapper.map(contextWithNegativeOffset, fakeSpan)

        // Then: duration is correct (monotonic), but start is wrong.
        // The backend child span starting at server-time T0+1s will have start > event.start,
        // making the android.request span appear to have negligible duration vs. its children.
        // This assertion documents the duration is preserved even though start is incorrectly shifted.
        assertThatJ(event.duration)
            .describedAs("Duration must equal the original span duration (monotonic, unaffected by NTP offset)")
            .isEqualTo(fakeSpanDuration)

        // This assertion shows the bug: start is offset to before actual span start.
        // Together with correct duration, the span appears correct internally but wrong in the timeline.
        val incorrectStart = fakeSpanStartTime + deviceClockAheadOffsetNs
        assertThatJ(event.start)
            .describedAs(
                "RUMS-5093: BUG DOCUMENTED - start=${ event.start } is incorrectly shifted " +
                    "to $incorrectStart (5s before actual span start). " +
                    "A backend child span starting at server-time T0+1s has start > event.start, " +
                    "making the android.request span appear to have negligible duration."
            )
            // FAILS: we assert start should equal span start time (no NTP shift), but it's shifted
            .isEqualTo(fakeSpanStartTime)
    }

    // endregion

    // region Internal

    private fun DDSpan.expectedMetrics(): Map<String, Number> {
        return tags.filterValues { it is Number }.mapValues { it.value as Number }.toMutableMap().apply {
            this[DDSpanContext.PRIORITY_SAMPLING_KEY] = spanSamplingPriority
        }
    }

    // endregion
}
