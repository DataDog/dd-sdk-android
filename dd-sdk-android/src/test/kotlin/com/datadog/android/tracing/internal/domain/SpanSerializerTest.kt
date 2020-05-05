package com.datadog.android.tracing.internal.domain

import com.datadog.android.BuildConfig
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.net.info.NetworkInfo
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.log.internal.user.UserInfo
import com.datadog.android.log.internal.user.UserInfoProvider
import com.datadog.android.utils.extension.getString
import com.datadog.android.utils.extension.toHexString
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.forge.SpanForgeryFactory
import com.datadog.tools.unit.assertj.JsonObjectAssert.Companion.assertThat
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import datadog.opentracing.DDSpan
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
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

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @Mock
    lateinit var mockUserInfoProvider: UserInfoProvider

    @Mock
    lateinit var mockNetworkInfoProvider: NetworkInfoProvider

    lateinit var underTest: SpanSerializer

    @StringForgery(StringForgeryType.ALPHABETICAL)
    lateinit var fakeEnvName: String

    @Forgery
    lateinit var fakeUserInfo: UserInfo

    @Forgery
    lateinit var fakeNetworkInfo: NetworkInfo

    @BeforeEach
    fun `set up`() {
        whenever(mockUserInfoProvider.getUserInfo()) doReturn fakeUserInfo
        whenever(mockNetworkInfoProvider.getLatestNetworkInfo()) doReturn fakeNetworkInfo
        underTest = SpanSerializer(
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
        // given
        whenever(mockTimeProvider.getServerOffsetNanos()) doReturn serverOffsetNanos
        val serialized = underTest.serialize(span)

        // when
        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        val spanObject = jsonObject.getAsJsonArray("spans").first() as JsonObject

        // then
        assertSpanMatches(span, spanObject, serverOffsetNanos)
        assertThat(jsonObject.getString("env")).isEqualTo(fakeEnvName)
        val metaObj = spanObject.getAsJsonObject(SpanSerializer.TAG_META)
        assertUserInfoMatches(fakeUserInfo, metaObj)
        assertNetworkInfoMatches(fakeNetworkInfo, metaObj)
        assertGlobalInfoMatches(metaObj)
    }

    @Test
    fun `it will only add the metrics key top level for the top span`(forge: Forge) {
        // given
        val parentSpan =
            SpanForgeryFactory.TEST_TRACER
                .buildSpan(forge.anAlphabeticalString())
                .start() as DDSpan
        val childSpan =
            SpanForgeryFactory.TEST_TRACER
                .buildSpan(forge.anAlphabeticalString())
                .asChildOf(parentSpan)
                .start() as DDSpan

        // when
        val serializedParent = JsonParser.parseString(underTest.serialize(parentSpan)).asJsonObject
        val serializedChild = JsonParser.parseString(underTest.serialize(childSpan)).asJsonObject

        // then
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

    private fun assertGlobalInfoMatches(jsonObject: JsonObject) {
        assertThat(jsonObject)
            .hasField(SpanSerializer.TAG_VERSION_NAME, BuildConfig.VERSION_NAME)
            .hasField(SpanSerializer.TAG_APP_VERSION_NAME, CoreFeature.packageVersion)
            .hasField(SpanSerializer.TAG_APP_PACKAGE_NAME, CoreFeature.packageName)
    }

    private fun assertSpanMatches(
        span: DDSpan,
        jsonObject: JsonObject,
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
                hasField(SpanSerializer.TAG_DD_SOURCE, SpanSerializer.DD_SOURCE_MOBILE)
            }
            .hasField(SpanSerializer.TAG_METRICS, span.metrics)
            .hasField(SpanSerializer.TAG_METRICS) {
                if (span.parentId.toLong() == 0L) {
                    hasField(SpanSerializer.TAG_METRICS_TOP_LEVEL, 1)
                }
            }
    }

    private fun assertNetworkInfoMatches(networkInfo: NetworkInfo?, jsonObject: JsonObject) {
        if (networkInfo != null) {
            assertThat(jsonObject).apply {
                hasField(
                    SpanSerializer.TAG_NETWORK_CONNECTIVITY,
                    networkInfo.connectivity.serialized
                )
                if (!networkInfo.carrierName.isNullOrBlank()) {
                    hasField(SpanSerializer.TAG_NETWORK_CARRIER_NAME, networkInfo.carrierName)
                } else {
                    doesNotHaveField(SpanSerializer.TAG_NETWORK_CARRIER_NAME)
                }
                if (networkInfo.carrierId >= 0) {
                    hasField(
                        SpanSerializer.TAG_NETWORK_CARRIER_ID,
                        networkInfo.carrierId.toString()
                    )
                } else {
                    doesNotHaveField(SpanSerializer.TAG_NETWORK_CARRIER_ID)
                }
                if (networkInfo.upKbps >= 0) {
                    hasField(SpanSerializer.TAG_NETWORK_UP_KBPS, networkInfo.upKbps.toString())
                } else {
                    doesNotHaveField(SpanSerializer.TAG_NETWORK_UP_KBPS)
                }
                if (networkInfo.downKbps >= 0) {
                    hasField(
                        SpanSerializer.TAG_NETWORK_DOWN_KBPS,
                        networkInfo.downKbps.toString()
                    )
                } else {
                    doesNotHaveField(SpanSerializer.TAG_NETWORK_DOWN_KBPS)
                }
                if (networkInfo.strength > Int.MIN_VALUE) {
                    hasField(
                        SpanSerializer.TAG_NETWORK_SIGNAL_STRENGTH,
                        networkInfo.strength.toString()
                    )
                } else {
                    doesNotHaveField(SpanSerializer.TAG_NETWORK_SIGNAL_STRENGTH)
                }
            }
        } else {
            assertThat(jsonObject)
                .doesNotHaveField(SpanSerializer.TAG_NETWORK_CONNECTIVITY)
                .doesNotHaveField(SpanSerializer.TAG_NETWORK_CARRIER_NAME)
                .doesNotHaveField(SpanSerializer.TAG_NETWORK_CARRIER_ID)
        }
    }

    private fun assertUserInfoMatches(userInfo: UserInfo, jsonObject: JsonObject) {
        assertThat(jsonObject).apply {
            if (userInfo.id.isNullOrEmpty()) {
                doesNotHaveField(SpanSerializer.TAG_USER_ID)
            } else {
                hasField(SpanSerializer.TAG_USER_ID, userInfo.id)
            }
            if (userInfo.name.isNullOrEmpty()) {
                doesNotHaveField(SpanSerializer.TAG_USER_NAME)
            } else {
                hasField(SpanSerializer.TAG_USER_NAME, userInfo.name)
            }
            if (userInfo.email.isNullOrEmpty()) {
                doesNotHaveField(SpanSerializer.TAG_USER_EMAIL)
            } else {
                hasField(SpanSerializer.TAG_USER_EMAIL, userInfo.email)
            }
        }
    }

    // endregion
}
