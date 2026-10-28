/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.net

import com.datadog.android.DatadogSite
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.flags.internal.model.PrecomputedAssignmentsRequest
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.utils.forge.ForgeConfigurator
import com.datadog.android.internal.rum.RumContext
import com.datadog.android.utils.withRumContext
import com.google.gson.JsonParser
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.Request
import okio.Buffer
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class, ForgeExtension::class)
@ForgeConfiguration(ForgeConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class PrecomputedAssignmentsRequestFactoryTest {

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @Forgery
    lateinit var fakeRumContext: RumContext

    private lateinit var testedFactory: PrecomputedAssignmentsRequestFactory

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeDatadogContext = fakeDatadogContext.copy(
            site = forge.aValueFrom(
                DatadogSite::class.java,
                exclude = listOf(DatadogSite.US1_FED, DatadogSite.US2_FED)
            )
        ).withRumContext(fakeRumContext)

        testedFactory = PrecomputedAssignmentsRequestFactory(mockInternalLogger, null)
    }

    // region create() - Success cases

    @Test
    fun `M advertise encoding capability W create() { native source }`(
        @Forgery fakeEvaluationContext: EvaluationContext
    ) {
        // Given
        val context = fakeDatadogContext.copy(source = "android")

        // When
        val request = checkNotNull(testedFactory.create(fakeEvaluationContext, context))
        val buffer = Buffer()
        checkNotNull(request.body).writeTo(buffer)
        val attributes = JSONObject(buffer.readUtf8()).getJSONObject("data").getJSONObject("attributes")

        // Then
        assertThat(request.header("X-DD-FEATURE-FLAGS-CAPABILITIES"))
            .isEqualTo("assignment-encoding-flag-key-256-v1")
        assertThat(attributes.has("supported_capabilities")).isFalse()
        assertThat(attributes.getJSONObject("source").getString("sdk_name")).isEqualTo("dd-sdk-android")
        assertThat(attributes.getJSONObject("source").getString("sdk_version")).isEqualTo(context.sdkVersion)
    }

    @Test
    fun `M omit encoding capability W create() { bridge sources }`(
        @Forgery fakeEvaluationContext: EvaluationContext
    ) {
        for (source in listOf("react-native", "flutter", "unknown")) {
            // Given
            val context = fakeDatadogContext.copy(source = source)

            // When
            val request = checkNotNull(testedFactory.create(fakeEvaluationContext, context))
            val buffer = Buffer()
            checkNotNull(request.body).writeTo(buffer)
            val attributes = JSONObject(buffer.readUtf8()).getJSONObject("data").getJSONObject("attributes")

            // Then
            assertThat(attributes.has("supported_capabilities")).isFalse()
            assertThat(request.header("X-DD-FEATURE-FLAGS-CAPABILITIES")).isNull()
            assertThat(attributes.getJSONObject("source").getString("sdk_name")).isEqualTo(source)
            assertThat(attributes.getJSONObject("source").getString("sdk_version")).isEqualTo(context.sdkVersion)
        }
    }

    @Test
    fun `M create valid request W create() { all fields present }`(
        @StringForgery fakeTargetingKey: String
    ) {
        // Given
        val context = EvaluationContext(
            targetingKey = fakeTargetingKey,
            attributes = mapOf("attr1" to "value1", "attr2" to "value2")
        )
        fakeDatadogContext = fakeDatadogContext.copy(
            site = DatadogSite.US1
        )

        // When
        val request = testedFactory.create(context, fakeDatadogContext)

        // Then
        checkNotNull(request)
        assertThat(request.url.toString()).isEqualTo("https://preview.ff-cdn.datadoghq.com/precompute-assignments")
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.header("dd-client-token")).isEqualTo(fakeDatadogContext.clientToken)
        assertThat(request.header("dd-application-id")).isEqualTo(fakeRumContext.applicationId)
        assertThat(request.header("Content-Type")).isEqualTo("application/vnd.api+json")
    }

    @Test
    fun `M create valid request W create() { no application id }`(
        @StringForgery fakeTargetingKey: String
    ) {
        // Given
        val context = EvaluationContext(
            targetingKey = fakeTargetingKey,
            attributes = emptyMap()
        )
        fakeDatadogContext = fakeDatadogContext.copy(
            featuresContext = fakeDatadogContext.featuresContext
                .toMutableMap()
                .apply { remove(Feature.RUM_FEATURE_NAME) }
        )

        // When
        val request = testedFactory.create(context, fakeDatadogContext)

        // Then
        checkNotNull(request)
        assertThat(request.header("dd-client-token")).isEqualTo(fakeDatadogContext.clientToken)
        assertThat(request.header("dd-application-id")).isNull()
        assertThat(request.header("Content-Type")).isEqualTo("application/vnd.api+json")
    }

    @Test
    fun `M use custom endpoint W create() { custom endpoint configured }`(
        @StringForgery fakeTargetingKey: String
    ) {
        // Given
        val fakeCustomEndpoint = "https://custom-proxy.example.com/flags/assignments"
        testedFactory = PrecomputedAssignmentsRequestFactory(mockInternalLogger, fakeCustomEndpoint)
        val context = EvaluationContext(
            targetingKey = fakeTargetingKey,
            attributes = emptyMap()
        )

        // When
        val request = testedFactory.create(context, fakeDatadogContext)

        checkNotNull(request)
        assertThat(request.url.toString()).isEqualTo(fakeCustomEndpoint)
    }

    // endregion

    // region create() - Request body validation

    @Test
    fun `M create correct JSON body W create() { with attributes }`(
        @StringForgery fakeTargetingKey: String,
        forge: Forge
    ) {
        // Given
        val fakeAttributes = forge.aMap(size = forge.anInt(min = 1, max = 5)) {
            anAlphabeticalString() to anAlphabeticalString()
        }
        val fakeContext = EvaluationContext(fakeTargetingKey, fakeAttributes)

        // When
        val request = testedFactory.create(fakeContext, fakeDatadogContext)

        // Then
        checkNotNull(request)
        val bodyJson = JsonParser.parseString(extractRequestBodyAsString(request)).asJsonObject
        assertThat(bodyJson.keySet()).containsExactly("data")
        assertThat(bodyJson.getAsJsonObject("data").get("type").asString)
            .isEqualTo("precompute-assignments-request")
        val attributes = PrecomputedAssignmentsRequest.fromJsonObject(bodyJson).data.attributes
        assertThat(attributes.subject.targetingKey).isEqualTo(fakeContext.targetingKey)
        assertThat(attributes.subject.targetingAttributes.additionalProperties).isEqualTo(fakeAttributes)
        assertThat(attributes.env.ddEnv).isEqualTo(fakeDatadogContext.env)
        assertThat(attributes.source.sdkName).isEqualTo(fakeDatadogContext.source)
        assertThat(attributes.source.sdkVersion).isEqualTo(fakeDatadogContext.sdkVersion)
    }

    @Test
    fun `M create correct JSON body W create() { empty attributes }`(
        @StringForgery fakeTargetingKey: String
    ) {
        // Given
        val context = EvaluationContext(
            targetingKey = fakeTargetingKey,
            attributes = emptyMap()
        )

        // When
        val request = testedFactory.create(context, fakeDatadogContext)

        // Then
        checkNotNull(request)
        val bodyString = extractRequestBodyAsString(request)
        val bodyJson = JSONObject(bodyString)

        val data = bodyJson.getJSONObject("data")
        val attributes = data.getJSONObject("attributes")
        val subject = attributes.getJSONObject("subject")
        assertThat(subject.getString("targeting_key")).isEqualTo(fakeTargetingKey)

        val targetingAttributes = subject.getJSONObject("targeting_attributes")
        assertThat(targetingAttributes.length()).isEqualTo(0)
    }

    // endregion

    // region create() - Error cases

    @Test
    fun `M omit null attribute keys W create() { null attribute key from Java }`(
        @StringForgery fakeTargetingKey: String,
        @StringForgery fakeValue: String
    ) {
        // Given
        @Suppress("UNCHECKED_CAST")
        val fakeAttributes = mapOf(null to fakeValue, fakeTargetingKey to fakeValue) as Map<String, String>
        val fakeContext = EvaluationContext(fakeTargetingKey, fakeAttributes)

        // When
        val request = testedFactory.create(fakeContext, fakeDatadogContext)

        // Then
        val json = JSONObject(extractRequestBodyAsString(checkNotNull(request)))
            .getJSONObject("data").getJSONObject("attributes")
            .getJSONObject("subject").getJSONObject("targeting_attributes")
        assertThat(json.length()).isEqualTo(1)
        assertThat(json.getString(fakeTargetingKey)).isEqualTo(fakeValue)
    }

    @Test
    fun `M omit null attribute values W create() { null attribute value from Java }`(
        @StringForgery fakeTargetingKey: String,
        @StringForgery fakeKey: String,
        @StringForgery fakeValue: String
    ) {
        // Given
        @Suppress("UNCHECKED_CAST")
        val fakeAttributes = mapOf(fakeKey to null, "$fakeKey-valid" to fakeValue) as Map<String, String>
        val fakeContext = EvaluationContext(fakeTargetingKey, fakeAttributes)

        // When
        val request = testedFactory.create(fakeContext, fakeDatadogContext)

        // Then
        val json = JSONObject(extractRequestBodyAsString(checkNotNull(request)))
            .getJSONObject("data").getJSONObject("attributes")
            .getJSONObject("subject").getJSONObject("targeting_attributes")
        assertThat(json.has(fakeKey)).isFalse()
        assertThat(json.getString("$fakeKey-valid")).isEqualTo(fakeValue)
    }

    @ParameterizedTest
    @EnumSource(DatadogSite::class, names = ["US1_FED", "US2_FED"])
    fun `M return null W create() { unsupported site and no custom endpoint }`(
        site: DatadogSite,
        @StringForgery fakeTargetingKey: String
    ) {
        // Given
        val context = EvaluationContext(
            targetingKey = fakeTargetingKey,
            attributes = emptyMap()
        )
        fakeDatadogContext = fakeDatadogContext.copy(
            site = site
        )

        // When
        val request = testedFactory.create(context, fakeDatadogContext)

        // Then
        assertThat(request).isNull()
    }

    // endregion

    // region Helper methods

    private fun extractRequestBodyAsString(request: Request): String {
        val buffer = Buffer()
        request.body?.writeTo(buffer)
        return buffer.readUtf8()
    }

    // endregion
}
