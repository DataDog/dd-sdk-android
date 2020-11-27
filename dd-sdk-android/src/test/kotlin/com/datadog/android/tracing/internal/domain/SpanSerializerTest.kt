/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tracing.internal.domain

import com.datadog.android.BuildConfig
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.net.info.NetworkInfo
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.core.internal.utils.NULL_MAP_VALUE
import com.datadog.android.log.LogAttributes
import com.datadog.android.log.internal.user.UserInfo
import com.datadog.android.log.internal.user.UserInfoProvider
import com.datadog.android.utils.extension.getString
import com.datadog.android.utils.extension.toHexString
import com.datadog.android.utils.forge.Configurator
import com.datadog.opentracing.DDSpan
import com.datadog.tools.unit.assertj.JsonObjectAssert
import com.datadog.tools.unit.assertj.JsonObjectAssert.Companion.assertThat
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.math.BigInteger
import java.util.Date
import org.assertj.core.api.Assertions.assertThat
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
internal class SpanSerializerTest {

    lateinit var testedSerializer: SpanSerializer

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @Mock
    lateinit var mockUserInfoProvider: UserInfoProvider

    @Mock
    lateinit var mockNetworkInfoProvider: NetworkInfoProvider

    @StringForgery
    lateinit var fakeEnvName: String

    @Forgery
    lateinit var fakeUserInfo: UserInfo

    @Forgery
    lateinit var fakeNetworkInfo: NetworkInfo

    @BeforeEach
    fun `set up`() {
        whenever(mockUserInfoProvider.getUserInfo()) doReturn fakeUserInfo
        whenever(mockNetworkInfoProvider.getLatestNetworkInfo()) doReturn fakeNetworkInfo
        testedSerializer = SpanSerializer(
            mockTimeProvider,
            mockNetworkInfoProvider,
            mockUserInfoProvider,
            fakeEnvName
        )
    }

    @Test
    fun `serializes a span to a Json string representation`(
        @Forgery span: DDSpan,
        @LongForgery serverOffsetNanos: Long
    ) {
        // Given
        whenever(mockTimeProvider.getServerOffsetNanos()) doReturn serverOffsetNanos
        val serialized = testedSerializer.serialize(span)

        // When
        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        val spanObject = jsonObject.getAsJsonArray("spans").first() as JsonObject

        // Then
        assertJsonMatchesInputSpan(spanObject, span, serverOffsetNanos)
        assertThat(jsonObject.getString("env")).isEqualTo(fakeEnvName)
        val metaObj = spanObject.getAsJsonObject(SpanSerializer.TAG_META)
        assertJsonContainsUserInfo(metaObj, fakeUserInfo)
        assertJsonContainsNetworkInfo(metaObj, fakeNetworkInfo)
        assertJsonContainsGlobalInfo(metaObj)

        // close the span
        span.finish()
    }

    @Test
    fun `M serialise a Span W minimum user information provided`(
        @Forgery span: DDSpan,
        @LongForgery serverOffsetNanos: Long
    ) {
        // Given
        val fakeMinimalUserInfo = UserInfo()
        whenever(mockUserInfoProvider.getUserInfo()) doReturn fakeMinimalUserInfo
        whenever(mockTimeProvider.getServerOffsetNanos()) doReturn serverOffsetNanos
        val serialized = testedSerializer.serialize(span)

        // When
        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        val spanObject = jsonObject.getAsJsonArray("spans").first() as JsonObject

        // Then
        assertJsonMatchesInputSpan(spanObject, span, serverOffsetNanos)
        assertThat(jsonObject.getString("env")).isEqualTo(fakeEnvName)
        val metaObj = spanObject.getAsJsonObject(SpanSerializer.TAG_META)
        assertJsonContainsUserInfo(metaObj, fakeMinimalUserInfo)
        assertJsonContainsNetworkInfo(metaObj, fakeNetworkInfo)
        assertJsonContainsGlobalInfo(metaObj)

        // close the span
        span.finish()
    }

    @Test
    fun `it will only add the metrics key top level for the top span`(forge: Forge) {
        // Given
        val fakeParentSpanId = BigInteger.valueOf(forge.aLong(min = 1))
        val fakeTraceId = BigInteger.valueOf(forge.aLong(min = 1))
        val parentSpan: DDSpan = mock {
            whenever(it.parentId).thenReturn(BigInteger.ZERO)
            whenever(it.spanId).thenReturn(fakeParentSpanId)
            whenever(it.traceId).thenReturn(fakeTraceId)
        }
        val childSpan: DDSpan = mock {
            whenever(it.parentId).thenReturn(fakeParentSpanId)
            whenever(it.traceId).thenReturn(fakeTraceId)
            whenever(it.spanId).thenReturn(BigInteger.valueOf(forge.aLong(min = 1)))
        }

        // When
        val serializedParent = JsonParser.parseString(testedSerializer.serialize(parentSpan))
            .asJsonObject
        val serializedChild = JsonParser.parseString(testedSerializer.serialize(childSpan))
            .asJsonObject

        // Then
        val serializedParentSpan = serializedParent.getAsJsonArray("spans").first() as JsonObject
        val serializedChildSpan = serializedChild.getAsJsonArray("spans").first() as JsonObject
        assertThat(serializedParentSpan).hasField(SpanSerializer.TAG_METRICS) {
            hasField(SpanSerializer.TAG_METRICS_TOP_LEVEL, 1)
        }
        assertThat(serializedChildSpan).hasField(SpanSerializer.TAG_METRICS) {
            doesNotHaveField(SpanSerializer.TAG_METRICS_TOP_LEVEL)
        }
    }

    // region Internal

    private fun assertJsonContainsGlobalInfo(jsonObject: JsonObject) {
        assertThat(jsonObject)
            .hasField(LogAttributes.APPLICATION_VERSION, CoreFeature.packageVersion)
    }

    private fun assertJsonMatchesInputSpan(
        jsonObject: JsonObject,
        span: DDSpan,
        serverOffsetNanos: Long
    ) {
        assertThat(jsonObject)
            .hasField(SpanSerializer.TAG_START_TIMESTAMP, span.startTime + serverOffsetNanos)
            .hasField(SpanSerializer.TAG_DURATION, span.durationNano)
            .hasField(SpanSerializer.TAG_SERVICE_NAME, span.serviceName)
            .hasField(SpanSerializer.TAG_TRACE_ID, span.traceId.toHexString())
            .hasField(SpanSerializer.TAG_SPAN_ID, span.spanId.toHexString())
            .hasField(SpanSerializer.TAG_PARENT_ID, span.parentId.toHexString())
            .hasField(SpanSerializer.TAG_RESOURCE, span.resourceName)
            .hasField(SpanSerializer.TAG_OPERATION_NAME, span.operationName)
            .hasField(SpanSerializer.TAG_ERROR, if (span.isError) 1 else 0)
            .hasField(SpanSerializer.TAG_TYPE, SpanSerializer.TYPE_CUSTOM)
            .hasField(SpanSerializer.TAG_META, span.meta)
            .hasField(SpanSerializer.TAG_META) {
                hasField(SpanSerializer.TAG_DD_SOURCE, SpanSerializer.DD_SOURCE_ANDROID)
                hasField(SpanSerializer.TAG_SPAN_KIND, SpanSerializer.KIND_CLIENT)
                hasField(SpanSerializer.TAG_TRACER_VERSION, BuildConfig.VERSION_NAME)
                hasField(SpanSerializer.TAG_APPLICATION_VERSION, CoreFeature.packageVersion)
            }
            .hasField(SpanSerializer.TAG_METRICS, span.metrics)
            .hasField(SpanSerializer.TAG_METRICS) {
                if (span.parentId.toLong() == 0L) {
                    hasField(SpanSerializer.TAG_METRICS_TOP_LEVEL, 1)
                }
            }
    }

    private fun assertJsonContainsNetworkInfo(
        jsonObject: JsonObject,
        networkInfo: NetworkInfo?
    ) {
        if (networkInfo != null) {
            assertThat(jsonObject).apply {
                hasField(
                    LogAttributes.NETWORK_CONNECTIVITY,
                    networkInfo.connectivity.serialized
                )
                if (!networkInfo.carrierName.isNullOrBlank()) {
                    hasField(LogAttributes.NETWORK_CARRIER_NAME, networkInfo.carrierName)
                } else {
                    doesNotHaveField(LogAttributes.NETWORK_CARRIER_NAME)
                }
                if (networkInfo.carrierId >= 0) {
                    hasField(
                        LogAttributes.NETWORK_CARRIER_ID,
                        networkInfo.carrierId.toString()
                    )
                } else {
                    doesNotHaveField(LogAttributes.NETWORK_CARRIER_ID)
                }
                if (networkInfo.upKbps >= 0) {
                    hasField(LogAttributes.NETWORK_UP_KBPS, networkInfo.upKbps.toString())
                } else {
                    doesNotHaveField(LogAttributes.NETWORK_UP_KBPS)
                }
                if (networkInfo.downKbps >= 0) {
                    hasField(
                        LogAttributes.NETWORK_DOWN_KBPS,
                        networkInfo.downKbps.toString()
                    )
                } else {
                    doesNotHaveField(LogAttributes.NETWORK_DOWN_KBPS)
                }
                if (networkInfo.strength > Int.MIN_VALUE) {
                    hasField(
                        LogAttributes.NETWORK_SIGNAL_STRENGTH,
                        networkInfo.strength.toString()
                    )
                } else {
                    doesNotHaveField(LogAttributes.NETWORK_SIGNAL_STRENGTH)
                }
            }
        } else {
            assertThat(jsonObject)
                .doesNotHaveField(LogAttributes.NETWORK_CONNECTIVITY)
                .doesNotHaveField(LogAttributes.NETWORK_CARRIER_NAME)
                .doesNotHaveField(LogAttributes.NETWORK_CARRIER_ID)
        }
    }

    private fun assertJsonContainsUserInfo(
        jsonObject: JsonObject,
        userInfo: UserInfo
    ) {
        assertThat(jsonObject).apply {
            if (userInfo.id.isNullOrEmpty()) {
                doesNotHaveField(LogAttributes.USR_ID)
            } else {
                hasField(LogAttributes.USR_ID, userInfo.id)
            }
            if (userInfo.name.isNullOrEmpty()) {
                doesNotHaveField(LogAttributes.USR_NAME)
            } else {
                hasField(LogAttributes.USR_NAME, userInfo.name)
            }
            if (userInfo.email.isNullOrEmpty()) {
                doesNotHaveField(LogAttributes.USR_EMAIL)
            } else {
                hasField(LogAttributes.USR_EMAIL, userInfo.email)
            }
            containsExtraAttributesMappedToStrings(
                userInfo.extraInfo,
                LogAttributes.USR_ATTRIBUTES_GROUP + "."
            )
        }
    }

    private fun JsonObjectAssert.containsExtraAttributesMappedToStrings(
        attributes: Map<String, Any?>,
        keyNamePrefix: String = ""
    ) {
        attributes.filter { it.key.isNotBlank() }
            .forEach {
                val value = it.value
                val key = keyNamePrefix + it.key
                when (value) {
                    NULL_MAP_VALUE -> doesNotHaveField(key)
                    null -> doesNotHaveField(key)
                    is Date -> hasField(key, value.time.toString())
                    is Iterable<*> -> hasField(key, value.toString())
                    else -> hasField(key, value.toString())
                }
            }
    }

    // endregion
}
