/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp

import com.datadog.android.Datadog
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.stub.StubSDKCore
import com.datadog.android.internal.network.GraphQLHeaders
import com.datadog.android.okhttp.tests.elmyr.OkHttpConfigurator
import com.datadog.android.okhttp.trace.TracingInterceptor
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.RumResourceMethod
import com.datadog.android.rum.internal.monitor.AdvancedNetworkRumMonitor
import com.datadog.android.rum.resource.ResourceId
import com.datadog.android.trace.Trace
import com.datadog.android.trace.TraceConfiguration
import com.datadog.android.trace.TracingHeaderType
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.getFieldValue
import com.datadog.tools.unit.getStaticValue
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.isAccessible

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@ForgeConfiguration(OkHttpConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApolloDatadogIntegrationTest {

    @Mock
    internal lateinit var mockRumMonitor: FakeRumMonitor

    private lateinit var stubSdkCore: StubSDKCore
    private lateinit var mockServer: MockWebServer

    @BeforeEach
    fun `set up`(forge: Forge) {
        stubSdkCore = StubSDKCore(forge)
        val registry: Any = Datadog::class.java.getStaticValue("registry")
        val instances: MutableMap<String, SdkCore> = registry.getFieldValue("instances")
        instances += stubSdkCore.name to stubSdkCore

        // Setup RUM and Trace features using reflection to access private fields
        val mockSdkCoreField = StubSDKCore::class.java.getDeclaredField("mockSdkCore")
        mockSdkCoreField.isAccessible = true
        val mockSdkCore = mockSdkCoreField.get(stubSdkCore) as InternalSdkCore

        val featureScopesField = StubSDKCore::class.java.getDeclaredField("featureScopes")
        featureScopesField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val featureScopes = featureScopesField.get(stubSdkCore) as MutableMap<String, Any>

        // Mock the RUM feature in both places
        val mockFeatureScope = mock<FeatureScope>()
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockFeatureScope
        featureScopes[Feature.RUM_FEATURE_NAME] = mockFeatureScope
        val fakeTraceConfiguration = TraceConfiguration.Builder().build()
        Trace.enable(fakeTraceConfiguration, stubSdkCore)

        // Setup GlobalRumMonitor mock
        GlobalRumMonitor::class.declaredFunctions.first { it.name == "registerIfAbsent" }.apply {
            isAccessible = true
            call(GlobalRumMonitor::class.objectInstance, mockRumMonitor, stubSdkCore)
        }

        mockServer = MockWebServer()
        mockServer.start()
    }

    @AfterEach
    fun `tear down`() {
        Datadog.stopInstance(stubSdkCore.name)
        mockServer.shutdown()

        // Reset GlobalRumMonitor
        GlobalRumMonitor::class.java.getDeclaredMethod("reset").apply {
            isAccessible = true
            invoke(null)
        }
    }

    // region graphQL headers

    @Test
    fun `M remove GraphQL headers from outgoing requests W DatadogInterceptor { with all GraphQL headers }`(
        @StringForgery fakeOperationName: String,
        @StringForgery fakeOperationType: String,
        @StringForgery fakeVariables: String,
        @StringForgery fakePayload: String
    ) {
        // Given
        mockServer.enqueue(MockResponse().setResponseCode(200))

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(
                DatadogInterceptor.Builder(
                    tracedHostsWithHeaderType = mapOf(mockServer.hostName to setOf(TracingHeaderType.DATADOG))
                )
                    .setSdkInstanceName(stubSdkCore.name)
                    .setTraceContextInjection(TraceContextInjection.ALL)
                    .build()
            )
            .build()

        // Simulate what Apollo interceptor would do - add GraphQL headers
        val requestWithGraphQLHeaders = Request.Builder()
            .url(mockServer.url("/graphql"))
            .addHeader(GraphQLHeaders.DD_GRAPHQL_NAME_HEADER.headerValue, fakeOperationName)
            .addHeader(GraphQLHeaders.DD_GRAPHQL_TYPE_HEADER.headerValue, fakeOperationType)
            .addHeader(GraphQLHeaders.DD_GRAPHQL_VARIABLES_HEADER.headerValue, fakeVariables)
            .addHeader(GraphQLHeaders.DD_GRAPHQL_PAYLOAD_HEADER.headerValue, fakePayload)
            .build()

        // When
        okHttpClient.newCall(requestWithGraphQLHeaders).execute()

        // Then - Verify headers are removed from outgoing request
        val requestSent = mockServer.takeRequest()
        assertThat(requestSent.getHeader(GraphQLHeaders.DD_GRAPHQL_NAME_HEADER.headerValue)).isNull()
        assertThat(requestSent.getHeader(GraphQLHeaders.DD_GRAPHQL_TYPE_HEADER.headerValue)).isNull()
        assertThat(requestSent.getHeader(GraphQLHeaders.DD_GRAPHQL_VARIABLES_HEADER.headerValue)).isNull()
        assertThat(requestSent.getHeader(GraphQLHeaders.DD_GRAPHQL_PAYLOAD_HEADER.headerValue)).isNull()
    }

    @Test
    fun `M not affect regular requests W DatadogInterceptor { without GraphQL headers }`() {
        // Given
        mockServer.enqueue(MockResponse().setResponseCode(200))

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(
                DatadogInterceptor.Builder(
                    tracedHostsWithHeaderType = mapOf(mockServer.hostName to setOf(TracingHeaderType.DATADOG))
                )
                    .setSdkInstanceName(stubSdkCore.name)
                    .setTraceContextInjection(TraceContextInjection.ALL)
                    .build()
            )
            .build()

        val regularRequest = Request.Builder()
            .url(mockServer.url("/api/users"))
            .addHeader("X-Custom-Header", "test-value")
            .build()

        // When
        okHttpClient.newCall(regularRequest).execute()

        // Then - Verify custom headers are preserved
        val requestSent = mockServer.takeRequest()
        assertThat(requestSent.getHeader("X-Custom-Header")).isEqualTo("test-value")

        // Verify GraphQL headers are not present (they weren't added)
        assertThat(requestSent.getHeader(GraphQLHeaders.DD_GRAPHQL_NAME_HEADER.headerValue)).isNull()
        assertThat(requestSent.getHeader(GraphQLHeaders.DD_GRAPHQL_TYPE_HEADER.headerValue)).isNull()
        assertThat(requestSent.getHeader(GraphQLHeaders.DD_GRAPHQL_VARIABLES_HEADER.headerValue)).isNull()
        assertThat(requestSent.getHeader(GraphQLHeaders.DD_GRAPHQL_PAYLOAD_HEADER.headerValue)).isNull()
    }

    @Test
    fun `M remove partial GraphQL headers W DatadogInterceptor { with only some GraphQL headers }`(
        @StringForgery fakeOperationName: String
    ) {
        // Given
        mockServer.enqueue(MockResponse().setResponseCode(200))

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(
                DatadogInterceptor.Builder(
                    tracedHostsWithHeaderType = mapOf(mockServer.hostName to setOf(TracingHeaderType.DATADOG))
                )
                    .setSdkInstanceName(stubSdkCore.name)
                    .setTraceContextInjection(TraceContextInjection.ALL)
                    .build()
            )
            .build()

        // Simulate partial GraphQL headers (only operation name)
        val requestWithPartialHeaders = Request.Builder()
            .url(mockServer.url("/graphql"))
            .addHeader(GraphQLHeaders.DD_GRAPHQL_NAME_HEADER.headerValue, fakeOperationName)
            .addHeader("X-Custom-Header", "should-remain")
            .build()

        // When
        okHttpClient.newCall(requestWithPartialHeaders).execute()

        // Then - Verify GraphQL headers are removed but other headers remain
        val requestSent = mockServer.takeRequest()
        assertThat(requestSent.getHeader(GraphQLHeaders.DD_GRAPHQL_NAME_HEADER.headerValue)).isNull()
        assertThat(requestSent.getHeader(GraphQLHeaders.DD_GRAPHQL_TYPE_HEADER.headerValue)).isNull()
        assertThat(requestSent.getHeader(GraphQLHeaders.DD_GRAPHQL_VARIABLES_HEADER.headerValue)).isNull()
        assertThat(requestSent.getHeader(GraphQLHeaders.DD_GRAPHQL_PAYLOAD_HEADER.headerValue)).isNull()
        assertThat(requestSent.getHeader("X-Custom-Header")).isEqualTo("should-remain")
    }

    // endregion

    // region RumMonitor

    @Test
    fun `M call RumMonitor with GraphQL attributes W DatadogInterceptor { with GraphQL headers }`(
        @StringForgery fakeOperationName: String,
        @StringForgery fakeOperationType: String,
        @StringForgery fakeVariables: String,
        @StringForgery fakePayload: String
    ) {
        // Given
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("response"))

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(
                DatadogInterceptor.Builder(
                    tracedHostsWithHeaderType = mapOf(mockServer.hostName to setOf(TracingHeaderType.DATADOG))
                )
                    .setSdkInstanceName(stubSdkCore.name)
                    .setTraceContextInjection(TraceContextInjection.ALL)
                    .build()
            )
            .build()

        // Simulate what Apollo interceptor would do - add GraphQL headers
        val requestWithGraphQLHeaders = Request.Builder()
            .url(mockServer.url("/graphql"))
            .addHeader(GraphQLHeaders.DD_GRAPHQL_NAME_HEADER.headerValue, fakeOperationName)
            .addHeader(GraphQLHeaders.DD_GRAPHQL_TYPE_HEADER.headerValue, fakeOperationType)
            .addHeader(GraphQLHeaders.DD_GRAPHQL_VARIABLES_HEADER.headerValue, fakeVariables)
            .addHeader(GraphQLHeaders.DD_GRAPHQL_PAYLOAD_HEADER.headerValue, fakePayload)
            .build()

        // When
        okHttpClient.newCall(requestWithGraphQLHeaders).execute()

        // Then - Verify RumMonitor calls with GraphQL attributes
        inOrder(mockRumMonitor) {
            argumentCaptor<ResourceId> {
                verify(mockRumMonitor).startResource(
                    capture(),
                    eq(RumResourceMethod.GET),
                    eq(mockServer.url("/graphql").toString()),
                    eq(emptyMap())
                )

                // Capture the actual attributes passed to stopResource
                val stopAttrsCaptor = argumentCaptor<Map<String, Any?>>()
                verify(mockRumMonitor).stopResource(
                    capture(),
                    eq(200),
                    eq(8L), // "response".length
                    any(),
                    stopAttrsCaptor.capture()
                )

                val actualStopAttrs = stopAttrsCaptor.firstValue

                // Verify GraphQL attributes are present in RUM
                assertThat(actualStopAttrs[RumAttributes.GRAPHQL_OPERATION_NAME]).isEqualTo(fakeOperationName)
                assertThat(actualStopAttrs[RumAttributes.GRAPHQL_OPERATION_TYPE]).isEqualTo(fakeOperationType)
                assertThat(actualStopAttrs[RumAttributes.GRAPHQL_VARIABLES]).isEqualTo(fakeVariables)
                assertThat(actualStopAttrs[RumAttributes.GRAPHQL_PAYLOAD]).isEqualTo(fakePayload)

                // Verify resource IDs match
                assertThat(firstValue).isEqualTo(secondValue)
            }
        }

        // Also verify headers are removed from outgoing request
        val requestSent = mockServer.takeRequest()
        assertThat(requestSent.getHeader(GraphQLHeaders.DD_GRAPHQL_NAME_HEADER.headerValue)).isNull()
        assertThat(requestSent.getHeader(GraphQLHeaders.DD_GRAPHQL_TYPE_HEADER.headerValue)).isNull()
        assertThat(requestSent.getHeader(GraphQLHeaders.DD_GRAPHQL_VARIABLES_HEADER.headerValue)).isNull()
        assertThat(requestSent.getHeader(GraphQLHeaders.DD_GRAPHQL_PAYLOAD_HEADER.headerValue)).isNull()
    }

    @Test
    fun `M call RumMonitor without GraphQL attributes W DatadogInterceptor { without GraphQL headers }`() {
        // Given
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("response"))

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(
                DatadogInterceptor.Builder(
                    tracedHostsWithHeaderType = mapOf(mockServer.hostName to setOf(TracingHeaderType.DATADOG))
                )
                    .setSdkInstanceName(stubSdkCore.name)
                    .setTraceContextInjection(TraceContextInjection.ALL)
                    .build()
            )
            .build()

        val regularRequest = Request.Builder()
            .url(mockServer.url("/api/users"))
            .build()

        // When
        okHttpClient.newCall(regularRequest).execute()

        // Then - Verify RumMonitor calls without GraphQL attributes
        inOrder(mockRumMonitor) {
            argumentCaptor<ResourceId> {
                verify(mockRumMonitor).startResource(
                    capture(),
                    eq(RumResourceMethod.GET),
                    eq(mockServer.url("/api/users").toString()),
                    eq(emptyMap())
                )

                // Capture the actual attributes passed to stopResource
                val stopAttrsCaptor = argumentCaptor<Map<String, Any?>>()
                verify(mockRumMonitor).stopResource(
                    capture(),
                    eq(200),
                    eq(8L), // "response".length
                    any(),
                    stopAttrsCaptor.capture()
                )

                val actualStopAttrs = stopAttrsCaptor.firstValue

                // Verify no GraphQL attributes are present in RUM
                assertThat(actualStopAttrs).doesNotContainKey(RumAttributes.GRAPHQL_OPERATION_NAME)
                assertThat(actualStopAttrs).doesNotContainKey(RumAttributes.GRAPHQL_OPERATION_TYPE)
                assertThat(actualStopAttrs).doesNotContainKey(RumAttributes.GRAPHQL_VARIABLES)
                assertThat(actualStopAttrs).doesNotContainKey(RumAttributes.GRAPHQL_PAYLOAD)

                // Verify resource IDs match
                assertThat(firstValue).isEqualTo(secondValue)
            }
        }
    }

    // endregion

    // region TracingInterceptor

    @Test
    fun `M preserve trace context headers and preserve GraphQL headers W TracingInterceptor { with GraphQL headers }`(
        @StringForgery fakeOperationName: String,
        @StringForgery fakeOperationType: String,
        @StringForgery fakeVariables: String,
        @StringForgery fakePayload: String
    ) {
        // Given
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("response"))

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(
                TracingInterceptor.Builder(
                    tracedHostsWithHeaderType = mapOf(mockServer.hostName to setOf(TracingHeaderType.DATADOG))
                )
                    .setTraceContextInjection(TraceContextInjection.ALL)
                    .setSdkInstanceName(stubSdkCore.name)
                    .setTraceSampleRate(100f)
                    .build()
            )
            .build()

        // Simulate what Apollo interceptor would do - add GraphQL headers
        val requestWithGraphQLHeaders = Request.Builder()
            .url(mockServer.url("/graphql"))
            .addHeader(GraphQLHeaders.DD_GRAPHQL_NAME_HEADER.headerValue, fakeOperationName)
            .addHeader(GraphQLHeaders.DD_GRAPHQL_TYPE_HEADER.headerValue, fakeOperationType)
            .addHeader(GraphQLHeaders.DD_GRAPHQL_VARIABLES_HEADER.headerValue, fakeVariables)
            .addHeader(GraphQLHeaders.DD_GRAPHQL_PAYLOAD_HEADER.headerValue, fakePayload)
            .addHeader("X-Custom-Header", "should-remain")
            .build()

        // When
        okHttpClient.newCall(requestWithGraphQLHeaders).execute()

        // Then - Verify trace context headers are present and GraphQL headers are preserved
        val requestSent = mockServer.takeRequest()

        // Trace context headers should be present
        assertThat(requestSent.getHeader(DATADOG_TRACE_ID_HEADER)).isNotNull()
        assertThat(requestSent.getHeader(DATADOG_SPAN_ID_HEADER)).isNotNull()
        assertThat(requestSent.getHeader(DATADOG_SAMPLING_PRIORITY_HEADER)).isNotNull()
        assertThat(requestSent.getHeader(DATADOG_TAGS_HEADER)).isNotNull()

        // GraphQL headers should be preserved (TracingInterceptor does NOT remove them)
        assertThat(
            requestSent.getHeader(GraphQLHeaders.DD_GRAPHQL_NAME_HEADER.headerValue)
        ).isEqualTo(fakeOperationName)
        assertThat(
            requestSent.getHeader(GraphQLHeaders.DD_GRAPHQL_TYPE_HEADER.headerValue)
        ).isEqualTo(fakeOperationType)
        assertThat(
            requestSent.getHeader(GraphQLHeaders.DD_GRAPHQL_VARIABLES_HEADER.headerValue)
        ).isEqualTo(fakeVariables)
        assertThat(requestSent.getHeader(GraphQLHeaders.DD_GRAPHQL_PAYLOAD_HEADER.headerValue)).isEqualTo(fakePayload)

        // Other headers should remain
        assertThat(requestSent.getHeader("X-Custom-Header")).isEqualTo("should-remain")

        // Verify tracing events are written
        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
    }

    @Test
    fun `M preserve trace context headers W TracingInterceptor { without GraphQL headers }`() {
        // Given
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("response"))

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(
                TracingInterceptor.Builder(
                    tracedHostsWithHeaderType = mapOf(mockServer.hostName to setOf(TracingHeaderType.DATADOG))
                )
                    .setTraceContextInjection(TraceContextInjection.ALL)
                    .setSdkInstanceName(stubSdkCore.name)
                    .setTraceSampleRate(100f)
                    .build()
            )
            .build()

        val regularRequest = Request.Builder()
            .url(mockServer.url("/api/users"))
            .addHeader("X-Custom-Header", "should-remain")
            .build()

        // When
        okHttpClient.newCall(regularRequest).execute()

        // Then - Verify trace context headers are present
        val requestSent = mockServer.takeRequest()

        // Trace context headers should be present
        assertThat(requestSent.getHeader(DATADOG_TRACE_ID_HEADER)).isNotNull()
        assertThat(requestSent.getHeader(DATADOG_SPAN_ID_HEADER)).isNotNull()
        assertThat(requestSent.getHeader(DATADOG_SAMPLING_PRIORITY_HEADER)).isNotNull()
        assertThat(requestSent.getHeader(DATADOG_TAGS_HEADER)).isNotNull()

        // Other headers should remain
        assertThat(requestSent.getHeader("X-Custom-Header")).isEqualTo("should-remain")

        // Verify tracing events are written
        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
    }

    @Test
    @Suppress("MaxLineLength", "ktlint:standard:max-line-length")
    fun `M combine TracingInterceptor and DatadogInterceptor W both interceptors { GraphQL headers removed, trace headers preserved, RUM tracked }`(
        @StringForgery fakeOperationName: String,
        @StringForgery fakeOperationType: String,
        @StringForgery fakeVariables: String,
        @StringForgery fakePayload: String
    ) {
        // Given
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("response"))

        val okHttpClient = OkHttpClient.Builder()
            // Add TracingInterceptor first
            .addInterceptor(
                TracingInterceptor.Builder(
                    tracedHostsWithHeaderType = mapOf(mockServer.hostName to setOf(TracingHeaderType.DATADOG))
                )
                    .setTraceContextInjection(TraceContextInjection.ALL)
                    .setSdkInstanceName(stubSdkCore.name)
                    .setTraceSampleRate(100f)
                    .build()
            )
            // Add DatadogInterceptor second
            .addInterceptor(
                DatadogInterceptor.Builder(
                    tracedHostsWithHeaderType = mapOf(mockServer.hostName to setOf(TracingHeaderType.DATADOG))
                )
                    .setSdkInstanceName(stubSdkCore.name)
                    .setTraceContextInjection(TraceContextInjection.ALL)
                    .build()
            )
            .build()

        // Simulate what Apollo interceptor would do - add GraphQL headers
        val requestWithGraphQLHeaders = Request.Builder()
            .url(mockServer.url("/graphql"))
            .addHeader(GraphQLHeaders.DD_GRAPHQL_NAME_HEADER.headerValue, fakeOperationName)
            .addHeader(GraphQLHeaders.DD_GRAPHQL_TYPE_HEADER.headerValue, fakeOperationType)
            .addHeader(GraphQLHeaders.DD_GRAPHQL_VARIABLES_HEADER.headerValue, fakeVariables)
            .addHeader(GraphQLHeaders.DD_GRAPHQL_PAYLOAD_HEADER.headerValue, fakePayload)
            .addHeader("X-Custom-Header", "should-remain")
            .build()

        // When
        okHttpClient.newCall(requestWithGraphQLHeaders).execute()

        // Then - Verify trace context headers are present and GraphQL headers are removed
        val requestSent = mockServer.takeRequest()

        // Trace context headers should be present (from TracingInterceptor)
        assertThat(requestSent.getHeader(DATADOG_TRACE_ID_HEADER)).isNotNull()
        assertThat(requestSent.getHeader(DATADOG_SPAN_ID_HEADER)).isNotNull()
        assertThat(requestSent.getHeader(DATADOG_SAMPLING_PRIORITY_HEADER)).isNotNull()
        assertThat(requestSent.getHeader(DATADOG_TAGS_HEADER)).isNotNull()

        // GraphQL headers should be removed (by DatadogInterceptor)
        assertThat(requestSent.getHeader(GraphQLHeaders.DD_GRAPHQL_NAME_HEADER.headerValue)).isNull()
        assertThat(requestSent.getHeader(GraphQLHeaders.DD_GRAPHQL_TYPE_HEADER.headerValue)).isNull()
        assertThat(requestSent.getHeader(GraphQLHeaders.DD_GRAPHQL_VARIABLES_HEADER.headerValue)).isNull()
        assertThat(requestSent.getHeader(GraphQLHeaders.DD_GRAPHQL_PAYLOAD_HEADER.headerValue)).isNull()

        // Other headers should remain
        assertThat(requestSent.getHeader("X-Custom-Header")).isEqualTo("should-remain")

        // Verify both tracing and RUM events are written
        val tracingEvents = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(tracingEvents).hasSize(1)

        // Verify RumMonitor was called - TracingInterceptor does not extract GraphQL attributes
        // Only DatadogInterceptor extracts GraphQL headers into RUM attributes
        inOrder(mockRumMonitor) {
            argumentCaptor<ResourceId> {
                verify(mockRumMonitor).startResource(
                    capture(),
                    eq(RumResourceMethod.GET),
                    eq(mockServer.url("/graphql").toString()),
                    eq(emptyMap()) // TracingInterceptor doesn't extract GraphQL attributes
                )
                // stopResource is called with trace attributes (from DatadogInterceptor)
                verify(mockRumMonitor).stopResource(
                    eq(firstValue),
                    eq(200),
                    any(), // size
                    eq(RumResourceKind.NATIVE),
                    argThat { attributes ->
                        // Verify that GraphQL attributes are present in stopResource (from DatadogInterceptor)
                        attributes[RumAttributes.GRAPHQL_OPERATION_NAME] == fakeOperationName &&
                            attributes[RumAttributes.GRAPHQL_OPERATION_TYPE] == fakeOperationType &&
                            attributes[RumAttributes.GRAPHQL_VARIABLES] == fakeVariables &&
                            attributes[RumAttributes.GRAPHQL_PAYLOAD] == fakePayload
                    }
                )
            }
        }
    }

    // endregion

    companion object {
        private const val DATADOG_TAGS_HEADER = "x-datadog-tags"
        private const val DATADOG_TRACE_ID_HEADER = "x-datadog-trace-id"
        private const val DATADOG_SPAN_ID_HEADER = "x-datadog-parent-id"
        private const val DATADOG_SAMPLING_PRIORITY_HEADER = "x-datadog-sampling-priority"
    }
}

internal interface FakeRumMonitor : RumMonitor, AdvancedNetworkRumMonitor
