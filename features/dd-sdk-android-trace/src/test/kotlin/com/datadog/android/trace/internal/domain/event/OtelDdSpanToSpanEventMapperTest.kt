/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.domain.event

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.core.internal.utils.toHexString
import com.datadog.android.log.LogAttributes
import com.datadog.android.trace.assertj.SpanEventAssert.Companion.assertThat
import com.datadog.android.utils.forge.Configurator
import com.datadog.trace.api.DD128bTraceId
import com.datadog.trace.core.DDSpan
import com.datadog.trace.core.DDSpanContext
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class), ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class OtelDdSpanToSpanEventMapperTest {

    lateinit var testedMapper: OtelDdSpanToSpanEventMapper

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @BoolForgery
    var fakeNetworkInfoEnabled: Boolean = false

    // region Tests

    @BeforeEach
    fun `set up`() {
        testedMapper = OtelDdSpanToSpanEventMapper(fakeNetworkInfoEnabled)
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
        assertThat(event).hasSpanId(fakeSpan.spanId.toHexString())
            .hasTraceId(fakeSpan.traceId.toHexString().substring(16))
            .hasParentId(fakeSpan.parentId.toHexString())
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
            .hasTracerVersion(fakeDatadogContext.sdkVersion)
            .hasClientPackageVersion(fakeDatadogContext.version).apply {
                if (fakeNetworkInfoEnabled) {
                    hasNetworkInfo(fakeDatadogContext.networkInfo)
                } else {
                    doesntHaveNetworkInfo()
                }
            }
            .hasUserInfo(fakeDatadogContext.userInfo)
            .hasMeta(expectedMeta)
            .hasMetrics(expectedMetrics)
    }

    @Test
    fun `M make sure the traceId is always a 16 characters length hexa W map()`(
        @Forgery fakeSpan: DDSpan,
        forge: Forge
    ) {
        // Given
        // we will generate a traceId with a length of 128 bits
        val fakeTraceId = DD128bTraceId.from((forge.aLong() + 1), (forge.aLong() + 1))
        val expectedMeta = fakeSpan.baggage + fakeSpan.tags.map {
            it.key to it.value.toString()
        }
        whenever(fakeSpan.traceId).thenReturn(fakeTraceId)
        val expectedMetrics = fakeSpan.expectedMetrics()

        // When
        val event = testedMapper.map(fakeDatadogContext, fakeSpan)

        // Then
        assertThat(event)
            .hasSpanId(fakeSpan.spanId.toHexString())
            .hasTraceId(fakeSpan.traceId.toHexString().substring(16))
            .hasParentId(fakeSpan.parentId.toHexString())
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
            .hasTracerVersion(fakeDatadogContext.sdkVersion)
            .hasClientPackageVersion(fakeDatadogContext.version).apply {
                if (fakeNetworkInfoEnabled) {
                    hasNetworkInfo(fakeDatadogContext.networkInfo)
                } else {
                    doesntHaveNetworkInfo()
                }
            }
            .hasUserInfo(fakeDatadogContext.userInfo)
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
        assertThat(event).hasSpanId(fakeSpan.spanId.toHexString())
            .hasTraceId(fakeSpan.traceId.toHexString().substring(16))
            .hasParentId(fakeSpan.parentId.toHexString())
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
            .hasTracerVersion(fakeDatadogContext.sdkVersion)
            .hasClientPackageVersion(fakeDatadogContext.version).apply {
                if (fakeNetworkInfoEnabled) {
                    hasNetworkInfo(fakeDatadogContext.networkInfo)
                } else {
                    doesntHaveNetworkInfo()
                }
            }.hasUserInfo(fakeDatadogContext.userInfo)
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
        forge: Forge, @Forgery fakeSpan: DDSpan
    ) {
        // Given
        whenever(fakeSpan.parentId).thenReturn(forge.aLong(min = 1))

        // When
        val event = testedMapper.map(fakeDatadogContext, fakeSpan)

        // Then
        assertThat(event).isNotTopSpan()
    }

    // endregion

    // region Internal

    private fun DDSpan.expectedMetrics(): Map<String, Number> {
        return tags.filterValues { it is Number }.mapValues { it.value as Number }.toMutableMap().apply {
            this[DDSpanContext.PRIORITY_SAMPLING_KEY] = samplingPriority()
        }
    }

    // endregion
}
