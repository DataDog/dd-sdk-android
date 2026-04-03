/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.apollographql.apollo.network.okHttpClient
import com.datadog.android.Datadog
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.Feature
import com.datadog.android.apollo.DatadogApolloInterceptor
import com.datadog.android.core.stub.StubEvent
import com.datadog.android.core.stub.StubSDKCore
import com.datadog.android.internal.network.GraphQLHeaders
import com.datadog.android.internal.network.HttpSpec
import com.datadog.android.okhttp.tests.elmyr.OkHttpConfigurator
import com.datadog.android.okhttp.tests.utils.MainLooperTestConfiguration
import com.datadog.android.okhttp.trace.TracingInterceptor
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.rum.RumMonitor
import com.datadog.android.testgraphql.FakeMutation
import com.datadog.android.testgraphql.FakeQuery
import com.datadog.android.testgraphql.type.UserInput
import com.datadog.android.trace.GlobalDatadogTracer
import com.datadog.android.trace.Trace
import com.datadog.android.trace.TraceConfiguration
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.getFieldValue
import com.datadog.tools.unit.getStaticValue
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
    lateinit var fakeHeaderName: String

    @StringForgery
    lateinit var fakeHeaderValue: String

    @StringForgery
    lateinit var fakeResponseBody: String

    @StringForgery
    lateinit var fakeViewKey: String

    @StringForgery
    lateinit var fakeViewName: String

    private lateinit var stubSdkCore: StubSDKCore
    private lateinit var mockServer: MockWebServer
    private lateinit var apolloClient: ApolloClient
    private lateinit var rumMonitor: RumMonitor

    @BeforeEach
    fun `set up`(forge: Forge) {
        stubSdkCore = StubSDKCore(forge)
        val registry: Any = Datadog::class.java.getStaticValue("registry")
        val instances: MutableMap<String, SdkCore> = registry.getFieldValue("instances")
        instances += stubSdkCore.name to stubSdkCore
        mockServer = MockWebServer()

        val fakeApplicationId = forge.anAlphabeticalString()
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
            .serverUrl(mockServer.url("/").toString())
            .okHttpClient(okHttpClient)
            .addInterceptor(DatadogApolloInterceptor(sendGraphQLPayloads = true))
            .build()

        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(fakeResponseBody))
        rumMonitor = GlobalRumMonitor.get(stubSdkCore)
    }

    private fun createDatadogInterceptor(): DatadogInterceptor {
        return DatadogInterceptor
            .Builder(tracedHosts = listOf(mockServer.hostName))
            .setSdkInstanceName(stubSdkCore.name)
            .build()
    }

    private fun createTracingInterceptor(): TracingInterceptor {
        return TracingInterceptor
            .Builder(tracedHosts = listOf(mockServer.hostName))
            .setSdkInstanceName(stubSdkCore.name)
            .build()
    }

    @AfterEach
    fun `tear down`() {
        GlobalDatadogTracer.clear()
        Datadog.stopInstance(stubSdkCore.name)
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
        GraphQLHeaders.values().forEach {
            val header = requestSent.getHeader(it.headerValue)
            assertThat(header).isNull()
        }
    }

    @Test
    fun `M remove GraphQL headers W DatadogInterceptor { with mutation and Apollo headers }`() = runBlocking {
        // When
        apolloClient.mutation(FakeMutation(input = UserInput(name = fakeUserName, email = fakeUserEmail))).execute()

        // Then
        val requestSent = mockServer.takeRequest()
        GraphQLHeaders.values().forEach {
            val header = requestSent.getHeader(it.headerValue)
            assertThat(header).isNull()
        }
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
        GraphQLHeaders.values().forEach {
            val header = requestSent.getHeader(it.headerValue)
            assertThat(header).isNull()
        }
    }

    // endregion

    // region RUM Resource Creation

    @Test
    fun `M create RUM resource events W DatadogApolloInterceptor { with GraphQL query }`() = runBlocking {
        // Given
        rumMonitor.startView(fakeViewKey, fakeViewName)

        // When
        apolloClient.query(
            FakeQuery(
                userId = fakeUserId,
                filters = Optional.present(listOf(fakeFilter1, fakeFilter2))
            )
        ).execute()

        // Then
        val resourceEvents = getResourceEvents()

        resourceEvents.forEach { event ->
            val eventResourceJson = JsonParser.parseString(event.eventData).asJsonObject
            assertThat(eventResourceJson.has("resource")).isTrue()

            val resourceSection = eventResourceJson.getAsJsonObject("resource")
            assertThat(resourceSection.has("url")).isTrue()
            assertThat(resourceSection.get("url").asString).contains(mockServer.hostName)
        }
    }

    @Test
    fun `M create RUM resource events W DatadogApolloInterceptor { with GraphQL mutation }`() = runBlocking {
        // Given
        rumMonitor.startView(fakeViewKey, fakeViewName)

        // When
        apolloClient.mutation(
            FakeMutation(input = UserInput(name = fakeUserName, email = fakeUserEmail))
        ).execute()

        // Then
        val resourceEvents = getResourceEvents()

        resourceEvents.forEach { resourceEvent ->
            val eventJson = JsonParser.parseString(resourceEvent.eventData).asJsonObject
            assertThat(eventJson.has("resource")).isTrue()

            val resourceSection = eventJson.getAsJsonObject("resource")
            assertThat(resourceSection.has("url")).isTrue()
            assertThat(resourceSection.get("url").asString).contains(mockServer.hostName)
        }
    }

    @Test
    fun `M create resource events with GraphQL attributes W DatadogApolloInterceptor`() {
        runBlocking {
            // Given
            rumMonitor.startView(fakeViewKey, fakeViewName)

            // When
            apolloClient.query(
                FakeQuery(
                    userId = fakeUserId,
                    filters = Optional.present(listOf(fakeFilter1, fakeFilter2))
                )
            ).execute()

            // Then
            val resourceEvents = getResourceEvents()

            val resourceEvent = resourceEvents.first()
            val resourceJson = JsonParser.parseString(resourceEvent.eventData).asJsonObject
            val resourceData = resourceJson.get("resource")?.asJsonObject

            assertThat(resourceData?.get("url")?.asString).contains(mockServer.hostName)
            assertThat(resourceData?.get("method")?.asString).isEqualTo("POST")
            assertThat(resourceData?.get("status_code")?.asInt).isEqualTo(200)
        }
    }

    @Test
    fun `M track RUM resource timing W DatadogApolloInterceptor { resource duration tracked }`() {
        runBlocking {
            // Given
            rumMonitor.startView(fakeViewKey, fakeViewName)

            // When
            apolloClient.query(
                FakeQuery(
                    userId = fakeUserId,
                    filters = Optional.present(listOf(fakeFilter1, fakeFilter2))
                )
            ).execute()

            // Then
            val resourceEvents = getResourceEvents()

            val resourceEvent = resourceEvents.first()
            val resourceJson = JsonParser.parseString(resourceEvent.eventData).asJsonObject
            val resourceData = resourceJson.get("resource")?.asJsonObject

            assertThat(resourceData?.has("duration")).isTrue()
            assertThat(resourceData?.get("duration")?.asLong).isGreaterThan(0)
        }
    }

    // endregion

    // region GraphQL errors

    @Test
    fun `M extract GraphQL errors W DatadogInterceptor { response body contains errors }`() {
        runBlocking {
            // Given
            rumMonitor.startView(fakeViewKey, fakeViewName)
            // Drain the default response enqueued in setUp, then enqueue our GraphQL error response
            apolloClient.query(FakeQuery(userId = fakeUserId, filters = Optional.present(emptyList()))).execute()
            mockServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", HttpSpec.ContentType.APPLICATION_JSON)
                    .setBody(GRAPHQL_ERROR_RESPONSE_BODY)
            )

            // When
            apolloClient.query(
                FakeQuery(
                    userId = fakeUserId,
                    filters = Optional.present(listOf(fakeFilter1))
                )
            ).execute()

            // Then
            val graphQL = getGraphQLData()
            assertThat(graphQL.get("operationType")?.asString).isEqualTo("query")
            assertThat(graphQL.get("operationName")?.asString).isEqualTo("FakeQuery")

            val errors = graphQL.getAsJsonArray("errors")
            assertThat(errors).hasSize(1)
            val error = errors.get(0).asJsonObject
            assertThat(error.get("message")?.asString).isEqualTo("User not found")
            assertThat(error.get("code")?.asString).isEqualTo("NOT_FOUND")
        }
    }

    @Test
    fun `M extract multiple GraphQL errors W DatadogInterceptor { response body contains multiple errors }`() {
        runBlocking {
            // Given
            rumMonitor.startView(fakeViewKey, fakeViewName)
            apolloClient.query(FakeQuery(userId = fakeUserId, filters = Optional.present(emptyList()))).execute()
            mockServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/graphql-response+json")
                    .setBody(GRAPHQL_MULTIPLE_ERRORS_RESPONSE_BODY)
            )

            // When
            apolloClient.query(
                FakeQuery(
                    userId = fakeUserId,
                    filters = Optional.present(listOf(fakeFilter1))
                )
            ).execute()

            // Then
            val graphQLData = getGraphQLData()

            val errors = graphQLData.getAsJsonArray("errors")
            assertThat(errors).hasSize(2)
            val firstError = errors.get(0).asJsonObject
            val secondError = errors.get(1).asJsonObject

            assertThat(firstError.get("message")?.asString).isEqualTo("Unauthorized")
            assertThat(firstError.get("code")?.asString).isEqualTo("AUTH_ERROR")
            assertThat(secondError.get("message")?.asString).isEqualTo("Rate limited")
        }
    }

    @Test
    fun `M not extract GraphQL errors W DatadogInterceptor { response body has no errors }`() {
        runBlocking {
            // Given
            rumMonitor.startView(fakeViewKey, fakeViewName)
            apolloClient.query(FakeQuery(userId = fakeUserId, filters = Optional.present(emptyList()))).execute()
            mockServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", HttpSpec.ContentType.APPLICATION_JSON)
                    .setBody(GRAPHQL_SUCCESS_RESPONSE_BODY)
            )

            // When
            apolloClient.query(
                FakeQuery(
                    userId = fakeUserId,
                    filters = Optional.present(listOf(fakeFilter1))
                )
            ).execute()

            // Then
            val graphQLData = getGraphQLData()
            assertThat(graphQLData.get("operationType")?.asString).isEqualTo("query")
            assertThat(graphQLData.getAsJsonArray("errors")).isNull()
        }
    }

    // endregion

    // region Base64 encoding/decoding

    @Test
    fun `M correctly decode non-ASCII characters W DatadogInterceptor { base64 encoded GraphQL variables }`(
        forge: Forge
    ) {
        runBlocking {
            // Given
            rumMonitor.startView(fakeViewKey, fakeViewName)
            val nonAsciiName = forgeNonAsciiString(forge)
            val nonAsciiEmail = forgeNonAsciiString(forge)

            // When
            apolloClient.mutation(
                FakeMutation(input = UserInput(name = nonAsciiName, email = nonAsciiEmail))
            ).execute()

            // Then
            val graphQLData = getGraphQLData(last = false)
            assertThat(graphQLData.get("operationType")?.asString).isEqualTo("mutation")
            assertThat(graphQLData.get("operationName")?.asString).isEqualTo("FakeMutation")

            val variables = graphQLData.get("variables")?.asString
            assertThat(variables).contains(nonAsciiName)
            assertThat(variables).contains(nonAsciiEmail)
        }
    }

    @Test
    fun `M correctly decode non-ASCII characters W DatadogInterceptor { base64 encoded GraphQL payload }`(
        forge: Forge
    ) {
        runBlocking {
            // Given
            rumMonitor.startView(fakeViewKey, fakeViewName)
            val nonAsciiFilter = forgeNonAsciiString(forge)

            // When
            apolloClient.query(
                FakeQuery(
                    userId = fakeUserId,
                    filters = Optional.present(listOf(nonAsciiFilter))
                )
            ).execute()

            // Then
            val graphQLData = getGraphQLData(last = false)
            assertThat(graphQLData.get("operationType")?.asString).isEqualTo("query")
            assertThat(graphQLData.get("operationName")?.asString).isEqualTo("FakeQuery")

            val payload = graphQLData.get("payload")?.asString
            assertThat(payload).contains(nonAsciiFilter)
        }
    }

    private fun forgeNonAsciiString(forge: Forge): String {
        val asciiPart = forge.aString(size = forge.anInt(min = 3, max = 10))

        val accentedStart = 0x0100
        val accentedEnd = 0x017F
        val accentedPart = (1..forge.anInt(min = 2, max = 5)).map {
            Char(forge.anInt(min = accentedStart, max = accentedEnd))
        }.joinToString("")

        val cjkStart = 0x4E00
        val cjkEnd = 0x9FFF
        val cjkPart = (1..forge.anInt(min = 2, max = 5)).map {
            Char(forge.anInt(min = cjkStart, max = cjkEnd))
        }.joinToString("")

        val emojiStart = 0x1F300
        val emojiEnd = 0x1F5FF
        val emojiPart = (1..forge.anInt(min = 1, max = 3)).joinToString("") {
            String(Character.toChars(forge.anInt(min = emojiStart, max = emojiEnd)))
        }

        return asciiPart + accentedPart + cjkPart + emojiPart
    }

    // endregion

    private fun getResourceEvents(): List<StubEvent> {
        val events = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(events).isNotEmpty()
        return events.filter { event ->
            val json = JsonParser.parseString(event.eventData).asJsonObject
            json.get("type")?.asString == "resource"
        }.also { assertThat(it).isNotEmpty() }
    }

    private fun getGraphQLData(last: Boolean = true): JsonObject {
        val resourceEvents = getResourceEvents()
        val event = if (last) {
            assertThat(resourceEvents.size).isGreaterThanOrEqualTo(2)
            resourceEvents.last()
        } else {
            resourceEvents.first()
        }
        val resourceJson = JsonParser.parseString(event.eventData).asJsonObject
        val graphQLData = resourceJson
            .getAsJsonObject("resource")
            ?.getAsJsonObject("graphql")
        assertThat(graphQLData).isNotNull
        return graphQLData!!
    }

    companion object {
        private val mainLooper = MainLooperTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        @Suppress("Unused")
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(mainLooper)
        }

        private const val GRAPHQL_ERROR_RESPONSE_BODY = """
            {
                "data": null,
                "errors": [
                    {
                        "message": "User not found",
                        "extensions": { "code": "NOT_FOUND" }
                    }
                ]
            }
        """

        private const val GRAPHQL_MULTIPLE_ERRORS_RESPONSE_BODY = """
            {
                "data": null,
                "errors": [
                    {
                        "message": "Unauthorized",
                        "extensions": { "code": "AUTH_ERROR" }
                    },
                    {
                        "message": "Rate limited"
                    }
                ]
            }
        """

        private const val GRAPHQL_SUCCESS_RESPONSE_BODY = """
            {
                "data": { "user": { "id": "123", "name": "Test" } }
            }
        """
    }
}
