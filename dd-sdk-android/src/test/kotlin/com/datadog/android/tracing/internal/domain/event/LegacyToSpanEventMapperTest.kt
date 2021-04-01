/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tracing.internal.domain.event

import com.datadog.android.BuildConfig
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.core.internal.utils.toHexString
import com.datadog.android.core.model.NetworkInfo
import com.datadog.android.core.model.UserInfo
import com.datadog.android.log.internal.user.UserInfoProvider
import com.datadog.android.tracing.assertj.SpanEventAssert.Companion.assertThat
import com.datadog.android.utils.forge.Configurator
import com.datadog.opentracing.DDSpan
import com.datadog.tools.unit.setFieldValue
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.math.BigInteger
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)

@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@ForgeConfiguration(Configurator::class)
internal class LegacyToSpanEventMapperTest {

    lateinit var testedMapper: LegacyToSpanEventMapper

    @Forgery
    lateinit var fakeUserInfo: UserInfo

    @Forgery
    lateinit var fakeNetworkInfo: NetworkInfo

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @Mock
    lateinit var mockUserInfoProvider: UserInfoProvider

    @Mock
    lateinit var mockNetworkInfoProvider: NetworkInfoProvider

    @StringForgery(regex = "[0-9]\\.[0-9]\\.[0-9]")
    lateinit var fakeClientPackageVersion: String

    @LongForgery
    var fakeServerOffsetNanos: Long = 0L

    @BeforeEach
    fun `set up`() {
        CoreFeature.packageVersion = fakeClientPackageVersion
        whenever(mockTimeProvider.getServerOffsetNanos()).thenReturn(fakeServerOffsetNanos)
        whenever(mockUserInfoProvider.getUserInfo()) doReturn fakeUserInfo
        whenever(mockNetworkInfoProvider.getLatestNetworkInfo()) doReturn fakeNetworkInfo
        testedMapper =
            LegacyToSpanEventMapper(mockTimeProvider, mockNetworkInfoProvider, mockUserInfoProvider)
    }

    @Test
    fun `M map a DdSpan to a SpanEvent W map`(
        @Forgery fakeSpan: DDSpan
    ) {
        // GIVEN
        whenever(mockTimeProvider.getServerOffsetNanos()).thenReturn(fakeServerOffsetNanos)

        // WHEN
        val event = testedMapper.map(fakeSpan)

        // THEN
        assertThat(event)
            .hasSpanId(fakeSpan.spanId.toHexString())
            .hasTraceId(fakeSpan.traceId.toHexString())
            .hasParentId(fakeSpan.parentId.toHexString())
            .hasServiceName(fakeSpan.serviceName)
            .hasOperationName(fakeSpan.operationName)
            .hasResourceName(fakeSpan.resourceName)
            .hasSpanType("custom")
            .hasSpanSource("android")
            .hasErrorFlag(fakeSpan.error.toLong())
            .hasSpanStartTime(fakeSpan.startTime + fakeServerOffsetNanos)
            .hasSpanDuration(fakeSpan.durationNano)
            .hasTracerVersion(BuildConfig.SDK_VERSION_NAME)
            .hasClientPackageVersion(fakeClientPackageVersion)
            .hasNetworkInfo(fakeNetworkInfo)
            .hasUserInfo(fakeUserInfo)
            .hasMeta(fakeSpan.meta)
            .hasMetrics(fakeSpan.metrics)
    }

    @Test
    fun `M mark the SpanEvent as top span W map { parentId is 0 }`(
        @Forgery fakeSpan: DDSpan
    ) {
        // GIVEN
        fakeSpan.setFieldValue("parentId", 0)

        // WHEN
        val event = testedMapper.map(fakeSpan)

        // THEN
        assertThat(event)
            .isTopSpan()
    }

    @Test
    fun `M not mark the SpanEvent as top span W map { parentId is different than 0 }`(
        forge: Forge,
        @Forgery fakeSpan: DDSpan
    ) {
        // GIVEN
        fakeSpan.context().setFieldValue("parentId", BigInteger.valueOf(forge.aLong(min = 1)))

        // WHEN
        val event = testedMapper.map(fakeSpan)

        // THEN
        assertThat(event)
            .isNotTopSpan()
    }
}
