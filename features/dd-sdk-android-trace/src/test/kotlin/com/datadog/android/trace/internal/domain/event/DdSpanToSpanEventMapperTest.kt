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
import com.datadog.opentracing.DDSpan
import com.datadog.tools.unit.setFieldValue
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.math.BigInteger

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@ForgeConfiguration(Configurator::class)
internal class DdSpanToSpanEventMapperTest {

    lateinit var testedMapper: DdSpanToSpanEventMapper

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @BoolForgery
    var fakeNetworkInfoEnabled: Boolean = false

    @Mock
    lateinit var mockBigIntegerUtils: BigIntegerUtils

    @StringForgery(regex = "[a-f0-9]{16}")
    lateinit var fakeLeastSignificantTraceId: String

    @StringForgery(regex = "[a-f0-9]{16}")
    lateinit var fakeMostSignificantTraceId: String

    @Forgery
    lateinit var fakeSpan: DDSpan

    @BeforeEach
    fun `set up`() {
        testedMapper = DdSpanToSpanEventMapper(fakeNetworkInfoEnabled, mockBigIntegerUtils)
        whenever(mockBigIntegerUtils.leastSignificant64BitsAsHex(fakeSpan.traceId))
            .thenReturn(fakeLeastSignificantTraceId)
        whenever(mockBigIntegerUtils.mostSignificant64BitsAsHex(fakeSpan.traceId))
            .thenReturn(fakeMostSignificantTraceId)
    }

    @RepeatedTest(4)
    fun `M map a DdSpan to a SpanEvent W map()`() {
        // When
        val event = testedMapper.map(fakeDatadogContext, fakeSpan)

        // Then
        assertThat(event)
            .hasSpanId(fakeSpan.spanId.toHexString())
            .hasLeastSignificant64BitsTraceId(fakeLeastSignificantTraceId)
            .hasMostSignificant64BitsTraceId(fakeMostSignificantTraceId)
            .hasParentId(fakeSpan.parentId.toHexString())
            .hasServiceName(fakeSpan.serviceName)
            .hasOperationName(fakeSpan.operationName)
            .hasResourceName(fakeSpan.resourceName)
            .hasSpanType("custom")
            .hasSpanSource(fakeDatadogContext.source)
            .hasApplicationId(null)
            .hasSessionId(null)
            .hasViewId(null)
            .hasErrorFlag(fakeSpan.error.toLong())
            .hasSpanStartTime(fakeSpan.startTime + fakeDatadogContext.time.serverTimeOffsetNs)
            .hasSpanDuration(fakeSpan.durationNano)
            .hasTracerVersion(fakeDatadogContext.sdkVersion)
            .hasVariant(fakeDatadogContext.variant)
            .hasClientPackageVersion(fakeDatadogContext.version)
            .apply {
                if (fakeNetworkInfoEnabled) {
                    hasNetworkInfo(fakeDatadogContext.networkInfo)
                } else {
                    doesntHaveNetworkInfo()
                }
            }
            .hasUserInfo(fakeDatadogContext.userInfo)
            .hasMeta(fakeSpan.meta)
            .hasDeviceInfo(fakeDatadogContext.deviceInfo)
            .hasOsInfo(fakeDatadogContext.deviceInfo)
            .hasMetrics(fakeSpan.metrics)
    }

    @RepeatedTest(4)
    fun `M map a DdSpan to a SpanEvent with RUM info W map() {RUM info present}`(
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeSessionId: String,
        @StringForgery fakeViewId: String
    ) {
        // Given
        fakeSpan.setTag(LogAttributes.RUM_APPLICATION_ID, fakeApplicationId)
        fakeSpan.setTag(LogAttributes.RUM_SESSION_ID, fakeSessionId)
        fakeSpan.setTag(LogAttributes.RUM_VIEW_ID, fakeViewId)

        // When
        val event = testedMapper.map(fakeDatadogContext, fakeSpan)

        // Then
        assertThat(event)
            .hasSpanId(fakeSpan.spanId.toHexString())
            .hasLeastSignificant64BitsTraceId(fakeLeastSignificantTraceId)
            .hasMostSignificant64BitsTraceId(fakeMostSignificantTraceId)
            .hasParentId(fakeSpan.parentId.toHexString())
            .hasServiceName(fakeSpan.serviceName)
            .hasOperationName(fakeSpan.operationName)
            .hasResourceName(fakeSpan.resourceName)
            .hasSpanType("custom")
            .hasSpanSource(fakeDatadogContext.source)
            .hasApplicationId(fakeApplicationId)
            .hasSessionId(fakeSessionId)
            .hasViewId(fakeViewId)
            .hasErrorFlag(fakeSpan.error.toLong())
            .hasSpanStartTime(fakeSpan.startTime + fakeDatadogContext.time.serverTimeOffsetNs)
            .hasSpanDuration(fakeSpan.durationNano)
            .hasTracerVersion(fakeDatadogContext.sdkVersion)
            .hasVariant(fakeDatadogContext.variant)
            .hasClientPackageVersion(fakeDatadogContext.version)
            .apply {
                if (fakeNetworkInfoEnabled) {
                    hasNetworkInfo(fakeDatadogContext.networkInfo)
                } else {
                    doesntHaveNetworkInfo()
                }
            }
            .hasUserInfo(fakeDatadogContext.userInfo)
            .hasMeta(fakeSpan.meta)
            .hasDeviceInfo(fakeDatadogContext.deviceInfo)
            .hasOsInfo(fakeDatadogContext.deviceInfo)
            .hasMetrics(fakeSpan.metrics)
    }

    @Test
    fun `M mark the SpanEvent as top span W map() { parentId is 0 }`() {
        // Given
        fakeSpan.setFieldValue("parentId", 0)

        // When
        val event = testedMapper.map(fakeDatadogContext, fakeSpan)

        // Then
        assertThat(event)
            .isTopSpan()
    }

    @Test
    fun `M not mark the SpanEvent as top span W map() { parentId is different than 0 }`(forge: Forge) {
        // Given
        fakeSpan.context().setFieldValue("parentId", BigInteger.valueOf(forge.aLong(min = 1)))

        // When
        val event = testedMapper.map(fakeDatadogContext, fakeSpan)

        // Then
        assertThat(event)
            .isNotTopSpan()
    }
}
