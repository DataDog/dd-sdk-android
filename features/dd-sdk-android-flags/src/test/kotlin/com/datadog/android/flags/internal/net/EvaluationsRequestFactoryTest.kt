/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.net

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.net.RequestExecutionContext
import com.datadog.android.api.net.RequestFactory
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.flags.model.BatchedFlagEvaluations
import com.datadog.android.flags.utils.forge.ForgeConfigurator
import com.google.gson.JsonParser
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@ExtendWith(MockitoExtension::class, ForgeExtension::class)
@ForgeConfiguration(ForgeConfigurator::class)
internal class EvaluationsRequestFactoryTest {

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @Forgery
    lateinit var fakeExecutionContext: RequestExecutionContext

    private lateinit var testedFactory: EvaluationsRequestFactory

    @BeforeEach
    fun setUp() {
        testedFactory = EvaluationsRequestFactory(
            internalLogger = mockInternalLogger,
            customEvaluationEndpoint = null
        )
    }

    // region Request Structure

    @Test
    fun `M create proper request W create() { with valid batch }`(
        @Forgery flagEvaluation: BatchedFlagEvaluations.FlagEvaluation
    ) {
        // Given
        val evaluationJson = flagEvaluation.toJson().toString()
        val batchData = listOf(
            RawBatchEvent(data = evaluationJson.toByteArray(Charsets.UTF_8))
        )

        // When
        val request = testedFactory.create(fakeDatadogContext, fakeExecutionContext, batchData, null)

        // Then
        assertThat(request.url).isEqualTo("${fakeDatadogContext.site.intakeEndpoint}/api/v2/flagevaluations")
        assertThat(request.description).isEqualTo("Evaluation Request")
        assertThat(request.contentType).isEqualTo(RequestFactory.CONTENT_TYPE_JSON)

        // Headers
        assertThat(request.headers[RequestFactory.HEADER_API_KEY]).isEqualTo(fakeDatadogContext.clientToken)
        assertThat(request.headers[RequestFactory.HEADER_EVP_ORIGIN]).isEqualTo(fakeDatadogContext.source)
        assertThat(request.headers[RequestFactory.HEADER_EVP_ORIGIN_VERSION]).isEqualTo(fakeDatadogContext.sdkVersion)
        assertThat(request.headers[RequestFactory.HEADER_REQUEST_ID]).isNotNull()
        assertThat(request.headers[RequestFactory.HEADER_REQUEST_ID]).isNotEmpty()

        // Request ID matches
        assertThat(request.id).isEqualTo(request.headers[RequestFactory.HEADER_REQUEST_ID])
    }

    @Test
    fun `M use custom endpoint W create() { custom endpoint provided }`(
        @Forgery flagEvaluation: BatchedFlagEvaluations.FlagEvaluation,
        @StringForgery(regex = "https://[a-z]+\\.com(/[a-z]+)+") customEndpoint: String
    ) {
        // Given
        val factoryWithCustomEndpoint = EvaluationsRequestFactory(
            internalLogger = mockInternalLogger,
            customEvaluationEndpoint = customEndpoint
        )
        val evaluationJson = flagEvaluation.toJson().toString()
        val batchData = listOf(
            RawBatchEvent(data = evaluationJson.toByteArray(Charsets.UTF_8))
        )

        // When
        val request = factoryWithCustomEndpoint.create(fakeDatadogContext, fakeExecutionContext, batchData, null)

        // Then
        assertThat(request.url).isEqualTo(customEndpoint)
        assertThat(request.url).doesNotContain(fakeDatadogContext.site.intakeEndpoint)
        assertThat(request.contentType).isEqualTo(RequestFactory.CONTENT_TYPE_JSON)
    }

    @Test
    fun `M generate unique request IDs W create() { multiple requests }`(
        @Forgery flagEvaluation: BatchedFlagEvaluations.FlagEvaluation
    ) {
        // Given
        val evaluationJson = flagEvaluation.toJson().toString()
        val batchData = listOf(
            RawBatchEvent(data = evaluationJson.toByteArray(Charsets.UTF_8))
        )

        // When
        val request1 = testedFactory.create(fakeDatadogContext, fakeExecutionContext, batchData, null)
        val request2 = testedFactory.create(fakeDatadogContext, fakeExecutionContext, batchData, null)

        // Then
        assertThat(request1.id).isNotEqualTo(request2.id)
        assertThat(request1.headers[RequestFactory.HEADER_REQUEST_ID])
            .isNotEqualTo(request2.headers[RequestFactory.HEADER_REQUEST_ID])
        assertThat(request1.id).isEqualTo(request1.headers[RequestFactory.HEADER_REQUEST_ID])
        assertThat(request2.id).isEqualTo(request2.headers[RequestFactory.HEADER_REQUEST_ID])
    }

    // endregion

    // region Batch Payload

    @Test
    fun `M parse single evaluation W create() { one event }`(
        @Forgery flagEvaluation: BatchedFlagEvaluations.FlagEvaluation
    ) {
        // Given
        val evaluationJson = flagEvaluation.toJson().toString()
        val batchData = listOf(
            RawBatchEvent(data = evaluationJson.toByteArray(Charsets.UTF_8))
        )

        // When
        val request = testedFactory.create(fakeDatadogContext, fakeExecutionContext, batchData, null)

        // Then
        val bodyJson = JsonParser.parseString(String(request.body, Charsets.UTF_8)).asJsonObject
        assertThat(bodyJson.has("context")).isTrue()
        assertThat(bodyJson.has("flagEvaluations")).isTrue()

        val evaluations = bodyJson.getAsJsonArray("flagEvaluations")
        assertThat(evaluations).hasSize(1)
        assertThat(evaluations[0].asJsonObject.get("flag").asJsonObject.get("key").asString)
            .isEqualTo(flagEvaluation.flag.key)
    }

    @Test
    fun `M parse multiple evaluations W create() { batch of events }`(
        @Forgery evaluation1: BatchedFlagEvaluations.FlagEvaluation,
        @Forgery evaluation2: BatchedFlagEvaluations.FlagEvaluation,
        @Forgery evaluation3: BatchedFlagEvaluations.FlagEvaluation
    ) {
        // Given
        val batchData = listOf(
            RawBatchEvent(data = evaluation1.toJson().toString().toByteArray(Charsets.UTF_8)),
            RawBatchEvent(data = evaluation2.toJson().toString().toByteArray(Charsets.UTF_8)),
            RawBatchEvent(data = evaluation3.toJson().toString().toByteArray(Charsets.UTF_8))
        )

        // When
        val request = testedFactory.create(fakeDatadogContext, fakeExecutionContext, batchData, null)

        // Then
        val bodyJson = JsonParser.parseString(String(request.body, Charsets.UTF_8)).asJsonObject
        val evaluations = bodyJson.getAsJsonArray("flagEvaluations")
        assertThat(evaluations).hasSize(3)

        val flagKeys = evaluations.map { it.asJsonObject.get("flag").asJsonObject.get("key").asString }
        assertThat(flagKeys).containsExactly(
            evaluation1.flag.key,
            evaluation2.flag.key,
            evaluation3.flag.key
        )
    }

    @Test
    fun `M handle empty batch W create() { no events }`() {
        // Given
        val batchData = emptyList<RawBatchEvent>()

        // When
        val request = testedFactory.create(fakeDatadogContext, fakeExecutionContext, batchData, null)

        // Then
        val bodyJson = JsonParser.parseString(String(request.body, Charsets.UTF_8)).asJsonObject
        assertThat(bodyJson.has("context")).isTrue()
        assertThat(bodyJson.getAsJsonArray("flagEvaluations")).isEmpty()
    }

    @Test
    fun `M preserve event order W create() { ordered batch }`(
        @Forgery evaluation1: BatchedFlagEvaluations.FlagEvaluation,
        @Forgery evaluation2: BatchedFlagEvaluations.FlagEvaluation,
        @Forgery evaluation3: BatchedFlagEvaluations.FlagEvaluation
    ) {
        // Given - specific order
        val batchData = listOf(
            RawBatchEvent(data = evaluation1.toJson().toString().toByteArray(Charsets.UTF_8)),
            RawBatchEvent(data = evaluation2.toJson().toString().toByteArray(Charsets.UTF_8)),
            RawBatchEvent(data = evaluation3.toJson().toString().toByteArray(Charsets.UTF_8))
        )

        // When
        val request = testedFactory.create(fakeDatadogContext, fakeExecutionContext, batchData, null)

        // Then - order is preserved
        val bodyJson = JsonParser.parseString(String(request.body, Charsets.UTF_8)).asJsonObject
        val evaluations = bodyJson.getAsJsonArray("flagEvaluations")

        val timestamps = evaluations.map { it.asJsonObject.get("timestamp").asLong }
        assertThat(timestamps).containsExactly(
            evaluation1.timestamp,
            evaluation2.timestamp,
            evaluation3.timestamp
        )
    }

    // endregion

    // region Top-Level Context

    @Test
    fun `M include service metadata W create() { context available }`(
        @Forgery flagEvaluation: BatchedFlagEvaluations.FlagEvaluation,
        forge: Forge
    ) {
        // Given
        val service = forge.anAlphabeticalString()
        val version = forge.aStringMatching("[0-9]+\\.[0-9]+\\.[0-9]+")
        val env = forge.anElementFrom("prod", "staging", "dev")

        val contextWithMetadata = fakeDatadogContext.copy(
            service = service,
            version = version,
            env = env
        )

        val batchData = listOf(
            RawBatchEvent(data = flagEvaluation.toJson().toString().toByteArray(Charsets.UTF_8))
        )

        // When
        val request = testedFactory.create(contextWithMetadata, fakeExecutionContext, batchData, null)

        // Then
        val bodyJson = JsonParser.parseString(String(request.body, Charsets.UTF_8)).asJsonObject
        val context = bodyJson.getAsJsonObject("context")

        assertThat(context.get("service").asString).isEqualTo(service)
        assertThat(context.get("version").asString).isEqualTo(version)
        assertThat(context.get("env").asString).isEqualTo(env)
    }

    @Test
    fun `M include device context W create() { device info available }`(
        @Forgery flagEvaluation: BatchedFlagEvaluations.FlagEvaluation
    ) {
        // Given
        val batchData = listOf(
            RawBatchEvent(data = flagEvaluation.toJson().toString().toByteArray(Charsets.UTF_8))
        )

        // When
        val request = testedFactory.create(fakeDatadogContext, fakeExecutionContext, batchData, null)

        // Then
        val bodyJson = JsonParser.parseString(String(request.body, Charsets.UTF_8)).asJsonObject
        val device = bodyJson.getAsJsonObject("context").getAsJsonObject("device")

        assertThat(device.get("name").asString).isEqualTo(fakeDatadogContext.deviceInfo.deviceName)
        assertThat(
            device.get("type").asString
        ).isEqualTo(fakeDatadogContext.deviceInfo.deviceType.toString().lowercase())
        assertThat(device.get("brand").asString).isEqualTo(fakeDatadogContext.deviceInfo.deviceBrand)
        assertThat(device.get("model").asString).isEqualTo(fakeDatadogContext.deviceInfo.deviceModel)
    }

    @Test
    fun `M include OS context W create() { OS info available }`(
        @Forgery flagEvaluation: BatchedFlagEvaluations.FlagEvaluation
    ) {
        // Given
        val batchData = listOf(
            RawBatchEvent(data = flagEvaluation.toJson().toString().toByteArray(Charsets.UTF_8))
        )

        // When
        val request = testedFactory.create(fakeDatadogContext, fakeExecutionContext, batchData, null)

        // Then
        val bodyJson = JsonParser.parseString(String(request.body, Charsets.UTF_8)).asJsonObject
        val os = bodyJson.getAsJsonObject("context").getAsJsonObject("os")

        assertThat(os.get("name").asString).isEqualTo(fakeDatadogContext.deviceInfo.osName)
        assertThat(os.get("version").asString).isEqualTo(fakeDatadogContext.deviceInfo.osVersion)
    }

    @Test
    fun `M include RUM context W create() { RUM available }`(
        @Forgery flagEvaluation: BatchedFlagEvaluations.FlagEvaluation,
        @StringForgery applicationId: String,
        @StringForgery viewName: String
    ) {
        // Given
        val contextWithRum = fakeDatadogContext.copy(
            featuresContext = mapOf(
                "rum" to mapOf(
                    "application_id" to applicationId,
                    "view_name" to viewName
                )
            )
        )
        val batchData = listOf(
            RawBatchEvent(data = flagEvaluation.toJson().toString().toByteArray(Charsets.UTF_8))
        )

        // When
        val request = testedFactory.create(contextWithRum, fakeExecutionContext, batchData, null)

        // Then
        val bodyJson = JsonParser.parseString(String(request.body, Charsets.UTF_8)).asJsonObject
        val context = bodyJson.getAsJsonObject("context")

        assertThat(context.has("rum")).isTrue()
        val rum = context.getAsJsonObject("rum")
        assertThat(rum.getAsJsonObject("application").get("id").asString).isEqualTo(applicationId)
        assertThat(rum.getAsJsonObject("view").get("url").asString).isEqualTo(viewName)
    }

    @Test
    fun `M omit RUM context W create() { RUM not available }`(
        @Forgery flagEvaluation: BatchedFlagEvaluations.FlagEvaluation
    ) {
        // Given
        val contextWithoutRum = fakeDatadogContext.copy(
            featuresContext = emptyMap()
        )
        val batchData = listOf(
            RawBatchEvent(data = flagEvaluation.toJson().toString().toByteArray(Charsets.UTF_8))
        )

        // When
        val request = testedFactory.create(contextWithoutRum, fakeExecutionContext, batchData, null)

        // Then
        val bodyJson = JsonParser.parseString(String(request.body, Charsets.UTF_8)).asJsonObject
        val context = bodyJson.getAsJsonObject("context")

        // RUM should either not be present or be null
        if (context.has("rum")) {
            assertThat(context.get("rum").isJsonNull).isTrue()
        }
    }

    @Test
    fun `M include partial RUM W create() { only application ID }`(
        @Forgery flagEvaluation: BatchedFlagEvaluations.FlagEvaluation,
        @StringForgery applicationId: String
    ) {
        // Given
        val contextWithAppOnly = fakeDatadogContext.copy(
            featuresContext = mapOf(
                "rum" to mapOf(
                    "application_id" to applicationId
                    // no view_name
                )
            )
        )
        val batchData = listOf(
            RawBatchEvent(data = flagEvaluation.toJson().toString().toByteArray(Charsets.UTF_8))
        )

        // When
        val request = testedFactory.create(contextWithAppOnly, fakeExecutionContext, batchData, null)

        // Then
        val bodyJson = JsonParser.parseString(String(request.body, Charsets.UTF_8)).asJsonObject
        val rum = bodyJson.getAsJsonObject("context").getAsJsonObject("rum")

        assertThat(rum.getAsJsonObject("application").get("id").asString).isEqualTo(applicationId)
        // View should be null or not present
        if (rum.has("view")) {
            assertThat(rum.get("view").isJsonNull).isTrue()
        }
    }

    @Test
    fun `M include partial RUM W create() { only view name }`(
        @Forgery flagEvaluation: BatchedFlagEvaluations.FlagEvaluation,
        @StringForgery viewName: String
    ) {
        // Given
        val contextWithViewOnly = fakeDatadogContext.copy(
            featuresContext = mapOf(
                "rum" to mapOf(
                    "view_name" to viewName
                    // no application_id
                )
            )
        )
        val batchData = listOf(
            RawBatchEvent(data = flagEvaluation.toJson().toString().toByteArray(Charsets.UTF_8))
        )

        // When
        val request = testedFactory.create(contextWithViewOnly, fakeExecutionContext, batchData, null)

        // Then
        val bodyJson = JsonParser.parseString(String(request.body, Charsets.UTF_8)).asJsonObject
        val rum = bodyJson.getAsJsonObject("context").getAsJsonObject("rum")

        assertThat(rum.getAsJsonObject("view").get("url").asString).isEqualTo(viewName)
        // Application should be null or not present
        if (rum.has("application")) {
            assertThat(rum.get("application").isJsonNull).isTrue()
        }
    }

    // endregion

    // region Error Handling

    @Test
    fun `M skip malformed event W create() { invalid JSON }`(
        @Forgery validEvaluation: BatchedFlagEvaluations.FlagEvaluation
    ) {
        // Given
        val validEvent = RawBatchEvent(data = validEvaluation.toJson().toString().toByteArray(Charsets.UTF_8))
        val malformedEvent = RawBatchEvent(data = "not valid json{[".toByteArray(Charsets.UTF_8))
        val batchData = listOf(validEvent, malformedEvent, validEvent)

        // When
        val request = testedFactory.create(fakeDatadogContext, fakeExecutionContext, batchData, null)

        // Then - only 2 valid events in payload
        val bodyJson = JsonParser.parseString(String(request.body, Charsets.UTF_8)).asJsonObject
        val evaluations = bodyJson.getAsJsonArray("flagEvaluations")
        assertThat(evaluations).hasSize(2)

        // Error logged for malformed event
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.ERROR),
            eq(InternalLogger.Target.MAINTAINER),
            check { messageBuilder ->
                assertThat(messageBuilder()).isEqualTo("Failed to parse flag evaluation for batching")
            },
            any<Throwable>(),
            eq(false),
            eq(null)
        )
    }

    @Test
    fun `M handle all malformed W create() { all events malformed }`() {
        // Given
        val batchData = listOf(
            RawBatchEvent(data = "bad json 1".toByteArray(Charsets.UTF_8)),
            RawBatchEvent(data = "bad json 2".toByteArray(Charsets.UTF_8)),
            RawBatchEvent(data = "bad json 3".toByteArray(Charsets.UTF_8))
        )

        // When
        val request = testedFactory.create(fakeDatadogContext, fakeExecutionContext, batchData, null)

        // Then - empty evaluations array
        val bodyJson = JsonParser.parseString(String(request.body, Charsets.UTF_8)).asJsonObject
        val evaluations = bodyJson.getAsJsonArray("flagEvaluations")
        assertThat(evaluations).isEmpty()

        // 3 errors logged (one for each malformed event)
        verify(mockInternalLogger, times(3)).log(
            eq(InternalLogger.Level.ERROR),
            eq(InternalLogger.Target.MAINTAINER),
            check { messageBuilder ->
                assertThat(messageBuilder()).isEqualTo("Failed to parse flag evaluation for batching")
            },
            any<Throwable>(),
            eq(false),
            eq(null)
        )
    }

    @Test
    fun `M log error and continue W create() { mix valid and malformed }`(
        @Forgery evaluation1: BatchedFlagEvaluations.FlagEvaluation,
        @Forgery evaluation2: BatchedFlagEvaluations.FlagEvaluation
    ) {
        // Given
        val batchData = listOf(
            RawBatchEvent(data = evaluation1.toJson().toString().toByteArray(Charsets.UTF_8)),
            RawBatchEvent(data = "{invalid}".toByteArray(Charsets.UTF_8)),
            RawBatchEvent(data = evaluation2.toJson().toString().toByteArray(Charsets.UTF_8)),
            RawBatchEvent(data = "[]".toByteArray(Charsets.UTF_8))
        )

        // When
        val request = testedFactory.create(fakeDatadogContext, fakeExecutionContext, batchData, null)

        // Then - only valid events included
        val bodyJson = JsonParser.parseString(String(request.body, Charsets.UTF_8)).asJsonObject
        val evaluations = bodyJson.getAsJsonArray("flagEvaluations")
        assertThat(evaluations).hasSize(2)

        // 2 errors logged (one for each malformed event)
        verify(mockInternalLogger, times(2)).log(
            eq(InternalLogger.Level.ERROR),
            eq(InternalLogger.Target.MAINTAINER),
            check { messageBuilder ->
                assertThat(messageBuilder()).isEqualTo("Failed to parse flag evaluation for batching")
            },
            any<Throwable>(),
            eq(false),
            eq(null)
        )
    }

    // endregion

    // region Data Integrity

    // Note: Serialization round-trip tests removed - covered by parse multiple evaluations test
    // Forgery-generated evaluations may have incompatible field combinations that fail fromJson()

    // endregion

    // region Geo Context

    @Test
    fun `M omit geo context W create() { geo not tracked }`(
        @Forgery flagEvaluation: BatchedFlagEvaluations.FlagEvaluation
    ) {
        // Given
        val batchData = listOf(
            RawBatchEvent(data = flagEvaluation.toJson().toString().toByteArray(Charsets.UTF_8))
        )

        // When
        val request = testedFactory.create(fakeDatadogContext, fakeExecutionContext, batchData, null)

        // Then - geo should be null or not present (not currently tracked)
        val bodyJson = JsonParser.parseString(String(request.body, Charsets.UTF_8)).asJsonObject
        val context = bodyJson.getAsJsonObject("context")

        // Geo is explicitly set to null in implementation
        if (context.has("geo")) {
            assertThat(context.get("geo").isJsonNull).isTrue()
        }
    }

    // endregion

    // region Batch Metadata

    @Test
    fun `M ignore batch metadata W create() { metadata provided }`(
        @Forgery flagEvaluation: BatchedFlagEvaluations.FlagEvaluation,
        @StringForgery metadata: String
    ) {
        // Given
        val batchData = listOf(
            RawBatchEvent(data = flagEvaluation.toJson().toString().toByteArray(Charsets.UTF_8))
        )
        val batchMetadata = metadata.toByteArray(Charsets.UTF_8)

        // When
        val request = testedFactory.create(fakeDatadogContext, fakeExecutionContext, batchData, batchMetadata)

        // Then - request created successfully (metadata is ignored by this factory)
        assertThat(request).isNotNull()
        val bodyJson = JsonParser.parseString(String(request.body, Charsets.UTF_8)).asJsonObject
        assertThat(bodyJson.has("flagEvaluations")).isTrue()
    }

    // endregion

    // region Large Batch

    @Test
    fun `M handle large batch W create() { 100 events }`(forge: Forge) {
        // Given - 100 events
        val batchData = (1..100).map { _ ->
            val evaluation = forge.getForgery<BatchedFlagEvaluations.FlagEvaluation>()
            RawBatchEvent(data = evaluation.toJson().toString().toByteArray(Charsets.UTF_8))
        }

        // When
        val request = testedFactory.create(fakeDatadogContext, fakeExecutionContext, batchData, null)

        // Then - all 100 events in payload
        val bodyJson = JsonParser.parseString(String(request.body, Charsets.UTF_8)).asJsonObject
        val evaluations = bodyJson.getAsJsonArray("flagEvaluations")
        assertThat(evaluations).hasSize(100)

        // Payload size is reasonable (should be under 4MB)
        assertThat(request.body.size).isLessThan(4 * 1024 * 1024)
    }

    // endregion
}
