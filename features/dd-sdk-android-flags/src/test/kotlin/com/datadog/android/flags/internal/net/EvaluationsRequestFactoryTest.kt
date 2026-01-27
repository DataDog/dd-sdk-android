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

@ExtendWith(MockitoExtension::class, ForgeExtension::class)
@ForgeConfiguration(ForgeConfigurator::class)
internal class EvaluationsRequestFactoryTest {

    private lateinit var testedFactory: EvaluationsRequestFactory

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @StringForgery
    lateinit var fakeCustomEndpoint: String

    @BeforeEach
    fun setUp() {
        testedFactory = EvaluationsRequestFactory(
            internalLogger = mockInternalLogger,
            customEvaluationEndpoint = null
        )
    }

    // region create

    @Test
    fun `M create proper batched request W create()`(
        @Forgery executionContext: RequestExecutionContext,
        @Forgery flagEvaluation: BatchedFlagEvaluations.FlagEvaluation
    ) {
        // Given
        val evaluationJson = flagEvaluation.toJson().toString()
        val batchEvents = listOf(
            RawBatchEvent(data = evaluationJson.toByteArray(Charsets.UTF_8))
        )

        // When
        val request = testedFactory.create(fakeDatadogContext, executionContext, batchEvents, null)

        // Then
        requireNotNull(request)
        assertThat(request.url).isEqualTo("${fakeDatadogContext.site.intakeEndpoint}/api/v2/flagevaluations")
        assertThat(request.contentType).isEqualTo(RequestFactory.CONTENT_TYPE_JSON)
        assertThat(request.headers.minus(RequestFactory.HEADER_REQUEST_ID)).isEqualTo(
            mapOf(
                RequestFactory.HEADER_API_KEY to fakeDatadogContext.clientToken,
                RequestFactory.HEADER_EVP_ORIGIN to fakeDatadogContext.source,
                RequestFactory.HEADER_EVP_ORIGIN_VERSION to fakeDatadogContext.sdkVersion
            )
        )
        assertThat(request.headers[RequestFactory.HEADER_REQUEST_ID]).isNotEmpty()
        assertThat(request.id).isEqualTo(request.headers[RequestFactory.HEADER_REQUEST_ID])
        assertThat(request.description).isEqualTo("Evaluation Request")
    }

    @Test
    fun `M create batched payload with context W create() { EVALLOG1 compliance }`(
        @Forgery executionContext: RequestExecutionContext,
        @Forgery flagEvaluation: BatchedFlagEvaluations.FlagEvaluation
    ) {
        // Given
        val evaluationJson = flagEvaluation.toJson().toString()
        val batchEvents = listOf(
            RawBatchEvent(data = evaluationJson.toByteArray(Charsets.UTF_8))
        )

        // When
        val request = testedFactory.create(fakeDatadogContext, executionContext, batchEvents, null)

        // Then - verify batched structure
        val bodyString = String(request.body, Charsets.UTF_8)
        val bodyJson = JsonParser.parseString(bodyString).asJsonObject

        // EVALLOG.1: Must have top-level context wrapper
        assertThat(bodyJson.has("context")).isTrue()
        assertThat(bodyJson.has("flagEvaluations")).isTrue()

        // Verify top-level context
        val context = bodyJson.getAsJsonObject("context")
        assertThat(context.get("service").asString).isEqualTo(fakeDatadogContext.service)
        assertThat(context.get("version").asString).isEqualTo(fakeDatadogContext.version)
        assertThat(context.get("env").asString).isEqualTo(fakeDatadogContext.env)

        // Verify evaluations array
        val evaluations = bodyJson.getAsJsonArray("flagEvaluations")
        assertThat(evaluations.size()).isEqualTo(1)
    }

    @Test
    fun `M include device context W create() { complete device info }`(
        @Forgery executionContext: RequestExecutionContext,
        @Forgery flagEvaluation: BatchedFlagEvaluations.FlagEvaluation
    ) {
        // Given
        val evaluationJson = flagEvaluation.toJson().toString()
        val batchEvents = listOf(
            RawBatchEvent(data = evaluationJson.toByteArray(Charsets.UTF_8))
        )

        // When
        val request = testedFactory.create(fakeDatadogContext, executionContext, batchEvents, null)

        // Then
        val bodyString = String(request.body, Charsets.UTF_8)
        val bodyJson = JsonParser.parseString(bodyString).asJsonObject
        val context = bodyJson.getAsJsonObject("context")

        assertThat(context.has("device")).isTrue()
        val device = context.getAsJsonObject("device")
        assertThat(device.get("name").asString).isEqualTo(fakeDatadogContext.deviceInfo.deviceName)
        assertThat(device.get("brand").asString).isEqualTo(fakeDatadogContext.deviceInfo.deviceBrand)
        assertThat(device.get("model").asString).isEqualTo(fakeDatadogContext.deviceInfo.deviceModel)
        assertThat(device.get("type").asString).isEqualTo(
            fakeDatadogContext.deviceInfo.deviceType.toString().lowercase()
        )
    }

    @Test
    fun `M include os context W create() { complete os info }`(
        @Forgery executionContext: RequestExecutionContext,
        @Forgery flagEvaluation: BatchedFlagEvaluations.FlagEvaluation
    ) {
        // Given
        val evaluationJson = flagEvaluation.toJson().toString()
        val batchEvents = listOf(
            RawBatchEvent(data = evaluationJson.toByteArray(Charsets.UTF_8))
        )

        // When
        val request = testedFactory.create(fakeDatadogContext, executionContext, batchEvents, null)

        // Then
        val bodyString = String(request.body, Charsets.UTF_8)
        val bodyJson = JsonParser.parseString(bodyString).asJsonObject
        val context = bodyJson.getAsJsonObject("context")

        assertThat(context.has("os")).isTrue()
        val os = context.getAsJsonObject("os")
        assertThat(os.get("name").asString).isEqualTo(fakeDatadogContext.deviceInfo.osName)
        assertThat(os.get("version").asString).isEqualTo(fakeDatadogContext.deviceInfo.osVersion)
    }

    @Test
    fun `M batch multiple evaluations W create() { multiple events }`(
        @Forgery executionContext: RequestExecutionContext,
        forge: Forge
    ) {
        // Given
        val eval1 = forge.getForgery<BatchedFlagEvaluations.FlagEvaluation>()
        val eval2 = forge.getForgery<BatchedFlagEvaluations.FlagEvaluation>()
        val eval3 = forge.getForgery<BatchedFlagEvaluations.FlagEvaluation>()
        val batchEvents = listOf(
            RawBatchEvent(data = eval1.toJson().toString().toByteArray(Charsets.UTF_8)),
            RawBatchEvent(data = eval2.toJson().toString().toByteArray(Charsets.UTF_8)),
            RawBatchEvent(data = eval3.toJson().toString().toByteArray(Charsets.UTF_8))
        )

        // When
        val request = testedFactory.create(fakeDatadogContext, executionContext, batchEvents, null)

        // Then
        val bodyString = String(request.body, Charsets.UTF_8)
        val bodyJson = JsonParser.parseString(bodyString).asJsonObject
        val evaluations = bodyJson.getAsJsonArray("flagEvaluations")

        assertThat(evaluations.size()).isEqualTo(3)
        assertThat(evaluations[0].asJsonObject.get("flag").asJsonObject.get("key").asString)
            .isEqualTo(eval1.flag.key)
        assertThat(evaluations[1].asJsonObject.get("flag").asJsonObject.get("key").asString)
            .isEqualTo(eval2.flag.key)
        assertThat(evaluations[2].asJsonObject.get("flag").asJsonObject.get("key").asString)
            .isEqualTo(eval3.flag.key)
    }

    @Test
    fun `M use custom endpoint W create() { custom endpoint configured }`(
        @Forgery executionContext: RequestExecutionContext,
        @Forgery flagEvaluation: BatchedFlagEvaluations.FlagEvaluation
    ) {
        // Given
        val customFactory = EvaluationsRequestFactory(
            internalLogger = mockInternalLogger,
            customEvaluationEndpoint = fakeCustomEndpoint
        )
        val evaluationJson = flagEvaluation.toJson().toString()
        val batchEvents = listOf(
            RawBatchEvent(data = evaluationJson.toByteArray(Charsets.UTF_8))
        )

        // When
        val request = customFactory.create(fakeDatadogContext, executionContext, batchEvents, null)

        // Then
        assertThat(request.url).isEqualTo(fakeCustomEndpoint)
    }

    @Test
    fun `M generate unique request IDs W create() { multiple calls }`(
        @Forgery executionContext: RequestExecutionContext,
        @Forgery flagEvaluation: BatchedFlagEvaluations.FlagEvaluation
    ) {
        // Given
        val evaluationJson = flagEvaluation.toJson().toString()
        val batchEvents = listOf(
            RawBatchEvent(data = evaluationJson.toByteArray(Charsets.UTF_8))
        )

        // When
        val request1 = testedFactory.create(fakeDatadogContext, executionContext, batchEvents, null)
        val request2 = testedFactory.create(fakeDatadogContext, executionContext, batchEvents, null)

        // Then
        assertThat(request1.id).isNotEqualTo(request2.id)
        assertThat(request1.headers[RequestFactory.HEADER_REQUEST_ID])
            .isNotEqualTo(request2.headers[RequestFactory.HEADER_REQUEST_ID])
    }

    @Test
    fun `M skip malformed events W create() { invalid JSON }`(
        @Forgery executionContext: RequestExecutionContext,
        @Forgery flagEvaluation: BatchedFlagEvaluations.FlagEvaluation
    ) {
        // Given
        val validJson = flagEvaluation.toJson().toString()
        val batchEvents = listOf(
            RawBatchEvent(data = "invalid json {{{".toByteArray(Charsets.UTF_8)),
            RawBatchEvent(data = validJson.toByteArray(Charsets.UTF_8)),
            RawBatchEvent(data = "more invalid".toByteArray(Charsets.UTF_8))
        )

        // When
        val request = testedFactory.create(fakeDatadogContext, executionContext, batchEvents, null)

        // Then - should only include valid event
        val bodyString = String(request.body, Charsets.UTF_8)
        val bodyJson = JsonParser.parseString(bodyString).asJsonObject
        val evaluations = bodyJson.getAsJsonArray("flagEvaluations")

        assertThat(evaluations.size()).isEqualTo(1)
        assertThat(evaluations[0].asJsonObject.get("flag").asJsonObject.get("key").asString)
            .isEqualTo(flagEvaluation.flag.key)
    }

    @Test
    fun `M include RUM context W create() { rum context available }`(
        @Forgery executionContext: RequestExecutionContext,
        @Forgery flagEvaluation: BatchedFlagEvaluations.FlagEvaluation,
        @StringForgery applicationId: String,
        @StringForgery viewUrl: String
    ) {
        // Given
        val contextWithRum = fakeDatadogContext.copy(
            featuresContext = mapOf(
                "rum" to mapOf(
                    "application_id" to applicationId,
                    "view_name" to viewUrl
                )
            )
        )
        val evaluationJson = flagEvaluation.toJson().toString()
        val batchEvents = listOf(
            RawBatchEvent(data = evaluationJson.toByteArray(Charsets.UTF_8))
        )

        // When
        val request = testedFactory.create(contextWithRum, executionContext, batchEvents, null)

        // Then
        val bodyString = String(request.body, Charsets.UTF_8)
        val bodyJson = JsonParser.parseString(bodyString).asJsonObject
        val context = bodyJson.getAsJsonObject("context")

        assertThat(context.has("rum")).isTrue()
        val rum = context.getAsJsonObject("rum")
        assertThat(rum.getAsJsonObject("application").get("id").asString).isEqualTo(applicationId)
        assertThat(rum.getAsJsonObject("view").get("url").asString).isEqualTo(viewUrl)
    }

    @Test
    fun `M omit RUM context W create() { rum context not available }`(
        @Forgery executionContext: RequestExecutionContext,
        @Forgery flagEvaluation: BatchedFlagEvaluations.FlagEvaluation
    ) {
        // Given
        val contextWithoutRum = fakeDatadogContext.copy(
            featuresContext = emptyMap()
        )
        val evaluationJson = flagEvaluation.toJson().toString()
        val batchEvents = listOf(
            RawBatchEvent(data = evaluationJson.toByteArray(Charsets.UTF_8))
        )

        // When
        val request = testedFactory.create(contextWithoutRum, executionContext, batchEvents, null)

        // Then
        val bodyString = String(request.body, Charsets.UTF_8)
        val bodyJson = JsonParser.parseString(bodyString).asJsonObject
        val context = bodyJson.getAsJsonObject("context")

        // RUM context should be omitted if not available
        assertThat(context.has("rum")).isFalse()
    }

    @Test
    fun `M create valid JSON structure W create() { verify schema compliance }`(
        @Forgery executionContext: RequestExecutionContext,
        forge: Forge
    ) {
        // Given
        val eval1 = forge.getForgery<BatchedFlagEvaluations.FlagEvaluation>()
        val eval2 = forge.getForgery<BatchedFlagEvaluations.FlagEvaluation>()
        val batchEvents = listOf(
            RawBatchEvent(data = eval1.toJson().toString().toByteArray(Charsets.UTF_8)),
            RawBatchEvent(data = eval2.toJson().toString().toByteArray(Charsets.UTF_8))
        )

        // When
        val request = testedFactory.create(fakeDatadogContext, executionContext, batchEvents, null)

        // Then - verify structure matches BatchedFlagEvaluations schema
        val bodyString = String(request.body, Charsets.UTF_8)
        val bodyJson = JsonParser.parseString(bodyString).asJsonObject

        // Top-level structure
        assertThat(bodyJson.has("context")).isTrue()
        assertThat(bodyJson.has("flagEvaluations")).isTrue()

        // Context structure
        val context = bodyJson.getAsJsonObject("context")
        assertThat(context.has("service")).isTrue()
        assertThat(context.has("version")).isTrue()
        assertThat(context.has("env")).isTrue()
        assertThat(context.has("device")).isTrue()
        assertThat(context.has("os")).isTrue()

        // Evaluations array
        val evaluations = bodyJson.getAsJsonArray("flagEvaluations")
        assertThat(evaluations.size()).isEqualTo(2)

        // Each evaluation should have required fields
        evaluations.forEach { evalElement ->
            val eval = evalElement.asJsonObject
            assertThat(eval.has("timestamp")).isTrue()
            assertThat(eval.has("flag")).isTrue()
            assertThat(eval.has("evaluation_count")).isTrue()
            assertThat(eval.has("first_evaluation")).isTrue()
            assertThat(eval.has("last_evaluation")).isTrue()
            assertThat(eval.has("runtime_default_used")).isTrue()
        }
    }

    // endregion
}
