/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.apollographql.apollo.network.okHttpClient
import com.datadog.android.api.feature.Feature
import com.datadog.android.apollo.DatadogApolloInterceptor
import com.datadog.android.core.stub.StubSDKCore
import com.datadog.android.internal.network.GraphQLHeaders
import com.datadog.android.okhttp.tests.elmyr.OkHttpConfigurator
import com.datadog.android.okhttp.tests.utils.MainLooperTestConfiguration
import com.datadog.android.okhttp.trace.TracingInterceptor
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.testgraphql.FakeMutation
import com.datadog.android.testgraphql.FakeQuery
import com.datadog.android.testgraphql.type.UserInput
import com.datadog.android.trace.DatadogTracing
import com.datadog.android.trace.GlobalDatadogTracer
import com.datadog.android.trace.Trace
import com.datadog.android.trace.TraceConfiguration
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import kotlinx.coroutines.runBlocking
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
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@ForgeConfiguration(OkHttpConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApolloIntegrationTest {

    @StringForgery
    lateinit var fakeUserId: String

    @StringForgery
    lateinit var fakeUserName: String

    @StringForgery
    lateinit var fakeUserEmail: String

    @StringForgery
    lateinit var fakeFilter1: String

    @StringForgery
    lateinit var fakeFilter2: String

    @StringForgery
    lateinit var fakeOperationName: String

    @StringForgery
    lateinit var fakeHeaderName: String

    @StringForgery
    lateinit var fakeHeaderValue: String

    @StringForgery
    lateinit var fakeResponseBody: String

    private lateinit var stubSdkCore: StubSDKCore
    private lateinit var mockServer: MockWebServer
    private lateinit var apolloClient: ApolloClient

    @BeforeEach
    fun `set up`(forge: Forge) {
        mockServer = MockWebServer()

        val fakeApplicationId = forge.anAlphabeticalString()
        stubSdkCore = StubSDKCore(forge)

        val rumConfiguration = RumConfiguration.Builder(fakeApplicationId)
            .trackNonFatalAnrs(false)
            .build()
        Rum.enable(rumConfiguration, stubSdkCore)

        val fakeTraceConfiguration = TraceConfiguration.Builder().build()
        Trace.enable(fakeTraceConfiguration, stubSdkCore)

        val tracingInterceptor = createTracingInterceptor()
        val datadogInterceptor = createDatadogInterceptor()
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(datadogInterceptor)
            .addInterceptor(tracingInterceptor)
            .build()

        apolloClient = ApolloClient
            .Builder()
            .addInterceptor(DatadogApolloInterceptor(sendGraphQLPayloads = true))
            .serverUrl(mockServer.url("/").toString())
            .okHttpClient(okHttpClient)
            .build()

        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(fakeResponseBody))
    }

    private fun createDatadogInterceptor(): DatadogInterceptor {
        return DatadogInterceptor.Builder(tracedHosts = listOf(mockServer.hostName)).build()
    }

    private fun createTracingInterceptor(): TracingInterceptor {
        return TracingInterceptor.Builder(tracedHosts = listOf(mockServer.hostName)).build()
    }

    @AfterEach
    fun `tear down`() {
        GlobalDatadogTracer.clear()
        mockServer.shutdown()
    }

    // region graphQL headers

    @Test
    fun `M remove GraphQL headers W DatadogInterceptor { with query and Apollo headers }`() = runBlocking {
        // When
        apolloClient.query(
            FakeQuery(
                userId = fakeUserId,
                filters = Optional.present(listOf(fakeFilter1, fakeFilter2))
            )
        ).execute()

        // Then
        val requestSent = mockServer.takeRequest()
        assertThat(requestSent.getHeader(GraphQLHeaders.DD_GRAPHQL_NAME_HEADER.headerValue)).isNull()
        assertThat(requestSent.getHeader(GraphQLHeaders.DD_GRAPHQL_TYPE_HEADER.headerValue)).isNull()
        assertThat(requestSent.getHeader(GraphQLHeaders.DD_GRAPHQL_VARIABLES_HEADER.headerValue)).isNull()
        assertThat(requestSent.getHeader(GraphQLHeaders.DD_GRAPHQL_PAYLOAD_HEADER.headerValue)).isNull()
    }

    @Test
    fun `M remove GraphQL headers W DatadogInterceptor { with mutation and Apollo headers }`() = runBlocking {
        // When
        apolloClient.mutation(FakeMutation(input = UserInput(name = fakeUserName, email = fakeUserEmail))).execute()

        // Then
        val requestSent = mockServer.takeRequest()
        assertThat(requestSent.getHeader(GraphQLHeaders.DD_GRAPHQL_NAME_HEADER.headerValue)).isNull()
        assertThat(requestSent.getHeader(GraphQLHeaders.DD_GRAPHQL_TYPE_HEADER.headerValue)).isNull()
        assertThat(requestSent.getHeader(GraphQLHeaders.DD_GRAPHQL_VARIABLES_HEADER.headerValue)).isNull()
        assertThat(requestSent.getHeader(GraphQLHeaders.DD_GRAPHQL_PAYLOAD_HEADER.headerValue)).isNull()
    }

    @Test
    fun `M not affect regular requests W DatadogInterceptor { without GraphQL headers }`() {
        // Given
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(createDatadogInterceptor())
            .build()

        val regularRequest = Request.Builder()
            .url(mockServer.url("/api/users"))
            .addHeader(fakeHeaderName, fakeHeaderValue)
            .build()

        // When
        okHttpClient.newCall(regularRequest).execute()

        // Then
        val requestSent = mockServer.takeRequest()
        assertThat(requestSent.getHeader(fakeHeaderName)).isEqualTo(fakeHeaderValue)
    }

    @Test
    fun `M remove partial GraphQL headers W DatadogInterceptor { query, some GraphQL headers }`() = runBlocking {
        // Given
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(createDatadogInterceptor())
            .build()

        val partialHeadersApolloClient = ApolloClient
            .Builder()
            .addInterceptor(DatadogApolloInterceptor(sendGraphQLPayloads = false))
            .serverUrl(mockServer.url("/").toString())
            .okHttpClient(okHttpClient)
            .build()

        // When
        partialHeadersApolloClient.query(
            FakeQuery(userId = fakeUserId, filters = Optional.present(listOf(fakeFilter1)))
        ).execute()

        // Then
        val requestSent = mockServer.takeRequest()
        assertThat(requestSent.getHeader(GraphQLHeaders.DD_GRAPHQL_NAME_HEADER.headerValue)).isNull()
        assertThat(requestSent.getHeader(GraphQLHeaders.DD_GRAPHQL_TYPE_HEADER.headerValue)).isNull()
        assertThat(requestSent.getHeader(GraphQLHeaders.DD_GRAPHQL_VARIABLES_HEADER.headerValue)).isNull()
        assertThat(requestSent.getHeader(GraphQLHeaders.DD_GRAPHQL_PAYLOAD_HEADER.headerValue)).isNull()
    }

    // endregion

    // region TracingInterceptor

    @Test
    fun `M preserve trace headers and GraphQL headers W TracingInterceptor { with GraphQL headers }`() = runBlocking {
        // Given
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(createTracingInterceptor())
            .build()

        val apolloClientWithHeaders = ApolloClient
            .Builder()
            .addInterceptor(DatadogApolloInterceptor(sendGraphQLPayloads = true))
            .serverUrl(mockServer.url("/").toString())
            .okHttpClient(okHttpClient)
            .build()

        // When
        apolloClientWithHeaders.query(
            FakeQuery(
                userId = fakeUserId,
                filters = Optional.present(listOf(fakeFilter1, fakeFilter2))
            )
        ).execute()

        // Then
        val requestSent = mockServer.takeRequest()

        assertThat(requestSent.getHeader("x-datadog-trace-id")).isNotNull()
        assertThat(requestSent.getHeader("x-datadog-parent-id")).isNotNull()
        assertThat(requestSent.getHeader("x-datadog-sampling-priority")).isNotNull()
        assertThat(requestSent.getHeader("x-datadog-tags")).isNotNull()

        assertThat(requestSent.getHeader(GraphQLHeaders.DD_GRAPHQL_NAME_HEADER.headerValue)).isNotNull()
        assertThat(requestSent.getHeader(GraphQLHeaders.DD_GRAPHQL_TYPE_HEADER.headerValue)).isNotNull()
        assertThat(requestSent.getHeader(GraphQLHeaders.DD_GRAPHQL_VARIABLES_HEADER.headerValue)).isNotNull()
        assertThat(requestSent.getHeader(GraphQLHeaders.DD_GRAPHQL_PAYLOAD_HEADER.headerValue)).isNotNull()

        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
    }

    @Test
    fun `M preserve trace context headers W TracingInterceptor { without GraphQL headers }`() {
        // Given
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(createTracingInterceptor())
            .build()

        val tracer = DatadogTracing.newTracerBuilder(stubSdkCore).build()
        GlobalDatadogTracer.registerIfAbsent(tracer)

        val span = tracer.buildSpan(fakeOperationName).start()
        val regularRequest = Request.Builder()
            .url(mockServer.url("/api/users"))
            .addHeader(fakeHeaderName, fakeHeaderValue)
            .apply {
                tracer.propagate().inject(span.context(), this) { builder, key, value ->
                    builder.addHeader(key, value)
                }
            }
            .build()

        // When
        okHttpClient.newCall(regularRequest).execute()
        span.finish()

        // Then
        val requestSent = mockServer.takeRequest()

        assertThat(requestSent.getHeader("x-datadog-trace-id")).isNotNull()
        assertThat(requestSent.getHeader("x-datadog-parent-id")).isNotNull()
        assertThat(requestSent.getHeader("x-datadog-sampling-priority")).isNotNull()
        assertThat(requestSent.getHeader("x-datadog-tags")).isNotNull()

        assertThat(requestSent.getHeader(fakeHeaderName)).isEqualTo(fakeHeaderValue)

        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
    }

    // endregion

    companion object {
        private val mainLooper = MainLooperTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        @Suppress("Unused")
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(mainLooper)
        }
    }
}
