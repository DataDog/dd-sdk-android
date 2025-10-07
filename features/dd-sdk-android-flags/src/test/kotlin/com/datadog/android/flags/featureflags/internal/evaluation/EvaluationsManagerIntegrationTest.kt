/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal.evaluation

import com.datadog.android.DatadogSite
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.flags.featureflags.internal.model.FlagsContext
import com.datadog.android.flags.featureflags.internal.model.PrecomputedFlag
import com.datadog.android.flags.featureflags.internal.repository.FlagsRepository
import com.datadog.android.flags.featureflags.internal.repository.net.DefaultPrecomputedAssignmentsRequestFactory
import com.datadog.android.flags.featureflags.internal.repository.net.PrecomputeMapper
import com.datadog.android.flags.featureflags.internal.repository.net.PrecomputedAssignmentsDownloader
import com.datadog.android.flags.featureflags.model.EvaluationContext
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.Executors

@ExtendWith(MockitoExtension::class, ForgeExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class EvaluationsManagerIntegrationTest {

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockSdkCore: InternalSdkCore

    @Mock
    lateinit var mockFlagsRepository: FlagsRepository

    private lateinit var mockWebServer: MockWebServer
    private lateinit var evaluationsManager: EvaluationsManager
    private lateinit var requestFactory: DefaultPrecomputedAssignmentsRequestFactory
    private lateinit var downloader: PrecomputedAssignmentsDownloader
    private lateinit var mapper: PrecomputeMapper
    private val executorService = Executors.newSingleThreadExecutor()

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        // Mock the createOkHttpCallFactory to return a real OkHttpClient for integration testing
        whenever(mockSdkCore.createOkHttpCallFactory(any())).thenAnswer { invocation ->
            val block = invocation.getArgument<okhttp3.OkHttpClient.Builder.() -> Unit>(0)
            okhttp3.Call.Factory { request ->
                okhttp3.OkHttpClient.Builder()
                    .apply(block)
                    .build()
                    .newCall(request)
            }
        }

        // Create real instances for integration testing
        requestFactory = DefaultPrecomputedAssignmentsRequestFactory(mockInternalLogger)
        mapper = PrecomputeMapper(mockInternalLogger)
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
        executorService.shutdown()
    }

    @Test
    fun `M successfully fetch and store flags W updateEvaluationsForContext() { valid server response }`(
        @StringForgery fakeClientToken: String,
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeTargetingKey: String
    ) {
        // Given - Create flags context with custom endpoint pointing to mock server
        val customEndpoint = mockWebServer.url("/precompute-assignments").toString()
        val flagsContext = FlagsContext(
            clientToken = fakeClientToken,
            applicationId = fakeApplicationId,
            site = DatadogSite.US1,
            env = "test",
            customFlagEndpoint = customEndpoint
        )

        // Create real downloader with real factory
        downloader = PrecomputedAssignmentsDownloader(
            sdkCore = mockSdkCore,
            internalLogger = mockInternalLogger,
            flagsContext = flagsContext,
            requestFactory = requestFactory
        )

        // Create evaluations manager with real components
        evaluationsManager = EvaluationsManager(
            executorService = executorService,
            internalLogger = mockInternalLogger,
            flagsRepository = mockFlagsRepository,
            flagsNetworkManager = downloader,
            precomputeMapper = mapper
        )

        // Create evaluation context
        val evaluationContext = EvaluationContext(
            targetingKey = fakeTargetingKey,
            attributes = mapOf("plan" to "premium", "region" to "us-east")
        )

        // Enqueue mock response
        val mockResponseBody = """
        {
            "data": {
                "attributes": {
                    "flags": {
                        "test-flag": {
                            "variationType": "boolean",
                            "variationValue": true,
                            "doLog": true,
                            "allocationKey": "test-allocation",
                            "variationKey": "test-variation",
                            "extraLogging": {},
                            "reason": "test-reason"
                        },
                        "string-flag": {
                            "variationType": "string",
                            "variationValue": "hello",
                            "doLog": false,
                            "allocationKey": "string-allocation",
                            "variationKey": "string-variation",
                            "extraLogging": {},
                            "reason": "default"
                        }
                    }
                }
            }
        }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(mockResponseBody)
                .setHeader("Content-Type", "application/json")
        )

        // When
        evaluationsManager.updateEvaluationsForContext(evaluationContext)

        // Wait for async execution to complete
        Thread.sleep(500)

        // Then - Verify request was made correctly
        val recordedRequest = mockWebServer.takeRequest()
        assertThat(recordedRequest.method).isEqualTo("POST")
        assertThat(recordedRequest.path).isEqualTo("/precompute-assignments")
        assertThat(recordedRequest.getHeader("dd-client-token")).isEqualTo(fakeClientToken)
        assertThat(recordedRequest.getHeader("dd-application-id")).isEqualTo(fakeApplicationId)
        assertThat(recordedRequest.getHeader("Content-Type")).isEqualTo("application/vnd.api+json")

        // Verify request body contains evaluation context
        val requestBody = recordedRequest.body.readUtf8()
        val requestJson = JSONObject(requestBody)
        val subject = requestJson
            .getJSONObject("data")
            .getJSONObject("attributes")
            .getJSONObject("subject")

        assertThat(subject.getString("targeting_key")).isEqualTo(fakeTargetingKey)
        val targetingAttributes = subject.getJSONObject("targeting_attributes")
        assertThat(targetingAttributes.getString("plan")).isEqualTo("premium")
        assertThat(targetingAttributes.getString("region")).isEqualTo("us-east")

        // Verify repository was updated with parsed flags
        argumentCaptor<Map<String, PrecomputedFlag>> {
            verify(mockFlagsRepository).setFlagsAndContext(eq(evaluationContext), capture())

            assertThat(lastValue).hasSize(2)
            assertThat(lastValue).containsKey("test-flag")
            assertThat(lastValue).containsKey("string-flag")

            val testFlag = lastValue["test-flag"]!!
            assertThat(testFlag.variationType).isEqualTo("boolean")
            assertThat(testFlag.variationValue).isEqualTo("true")
            assertThat(testFlag.doLog).isTrue()
            assertThat(testFlag.allocationKey).isEqualTo("test-allocation")

            val stringFlag = lastValue["string-flag"]!!
            assertThat(stringFlag.variationType).isEqualTo("string")
            assertThat(stringFlag.variationValue).isEqualTo("hello")
            assertThat(stringFlag.doLog).isFalse()
        }
    }

    @Test
    fun `M handle server error gracefully W updateEvaluationsForContext() { 500 response }`(
        @StringForgery fakeClientToken: String,
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeTargetingKey: String
    ) {
        // Given
        val customEndpoint = mockWebServer.url("/precompute-assignments").toString()
        val flagsContext = FlagsContext(
            clientToken = fakeClientToken,
            applicationId = fakeApplicationId,
            site = DatadogSite.US1,
            env = "test",
            customFlagEndpoint = customEndpoint
        )

        downloader = PrecomputedAssignmentsDownloader(
            sdkCore = mockSdkCore,
            internalLogger = mockInternalLogger,
            flagsContext = flagsContext,
            requestFactory = requestFactory
        )

        evaluationsManager = EvaluationsManager(
            executorService = executorService,
            internalLogger = mockInternalLogger,
            flagsRepository = mockFlagsRepository,
            flagsNetworkManager = downloader,
            precomputeMapper = mapper
        )

        val evaluationContext = EvaluationContext(
            targetingKey = fakeTargetingKey,
            attributes = emptyMap()
        )

        // Enqueue error response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error")
        )

        // When
        evaluationsManager.updateEvaluationsForContext(evaluationContext)

        // Wait for async execution to complete
        Thread.sleep(500)

        // Then - Request was made
        val recordedRequest = mockWebServer.takeRequest()
        assertThat(recordedRequest.method).isEqualTo("POST")

        // Verify repository was updated with empty flags (graceful degradation)
        verify(mockFlagsRepository).setFlagsAndContext(eq(evaluationContext), eq(emptyMap()))
    }

    @Test
    fun `M handle network timeout gracefully W updateEvaluationsForContext() { no response }`(
        @StringForgery fakeClientToken: String,
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeTargetingKey: String
    ) {
        // Given
        val customEndpoint = mockWebServer.url("/precompute-assignments").toString()
        val flagsContext = FlagsContext(
            clientToken = fakeClientToken,
            applicationId = fakeApplicationId,
            site = DatadogSite.US1,
            env = "test",
            customFlagEndpoint = customEndpoint
        )

        downloader = PrecomputedAssignmentsDownloader(
            sdkCore = mockSdkCore,
            internalLogger = mockInternalLogger,
            flagsContext = flagsContext,
            requestFactory = requestFactory
        )

        evaluationsManager = EvaluationsManager(
            executorService = executorService,
            internalLogger = mockInternalLogger,
            flagsRepository = mockFlagsRepository,
            flagsNetworkManager = downloader,
            precomputeMapper = mapper
        )

        val evaluationContext = EvaluationContext(
            targetingKey = fakeTargetingKey,
            attributes = emptyMap()
        )

        // Shutdown server to simulate network failure
        mockWebServer.shutdown()

        // When
        evaluationsManager.updateEvaluationsForContext(evaluationContext)

        // Wait for async execution to complete
        Thread.sleep(500)

        // Then - Verify repository was updated with empty flags (graceful degradation)
        verify(mockFlagsRepository).setFlagsAndContext(eq(evaluationContext), eq(emptyMap()))
    }

    @Test
    fun `M send correct request structure W updateEvaluationsForContext() { empty attributes }`(
        @StringForgery fakeClientToken: String,
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeTargetingKey: String,
        @StringForgery fakeEnv: String
    ) {
        // Given
        val customEndpoint = mockWebServer.url("/precompute-assignments").toString()
        val flagsContext = FlagsContext(
            clientToken = fakeClientToken,
            applicationId = fakeApplicationId,
            site = DatadogSite.US1,
            env = fakeEnv,
            customFlagEndpoint = customEndpoint
        )

        downloader = PrecomputedAssignmentsDownloader(
            sdkCore = mockSdkCore,
            internalLogger = mockInternalLogger,
            flagsContext = flagsContext,
            requestFactory = requestFactory
        )

        evaluationsManager = EvaluationsManager(
            executorService = executorService,
            internalLogger = mockInternalLogger,
            flagsRepository = mockFlagsRepository,
            flagsNetworkManager = downloader,
            precomputeMapper = mapper
        )

        val evaluationContext = EvaluationContext(
            targetingKey = fakeTargetingKey,
            attributes = emptyMap()
        )

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"data": {"attributes": {"flags": {}}}}""")
        )

        // When
        evaluationsManager.updateEvaluationsForContext(evaluationContext)

        // Wait for async execution to complete
        Thread.sleep(500)

        // Then - Verify request body structure with empty attributes
        val recordedRequest = mockWebServer.takeRequest()
        val requestBody = recordedRequest.body.readUtf8()
        val requestJson = JSONObject(requestBody)

        // Verify JSON-API structure
        assertThat(requestJson.has("data")).isTrue()
        val data = requestJson.getJSONObject("data")
        assertThat(data.getString("type")).isEqualTo("precompute-assignments-request")

        val attributes = data.getJSONObject("attributes")
        assertThat(attributes.has("subject")).isTrue()
        assertThat(attributes.has("env")).isTrue()

        val subject = attributes.getJSONObject("subject")
        assertThat(subject.getString("targeting_key")).isEqualTo(fakeTargetingKey)
        assertThat(subject.getJSONObject("targeting_attributes").length()).isEqualTo(0)

        val env = attributes.getJSONObject("env")
        assertThat(env.getString("dd_env")).isEqualTo(fakeEnv)
    }
}
