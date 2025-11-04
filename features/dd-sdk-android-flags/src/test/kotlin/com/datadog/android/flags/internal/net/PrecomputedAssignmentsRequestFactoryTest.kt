/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.net

import com.datadog.android.DatadogSite
import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.internal.model.FlagsContext
import com.datadog.android.flags.model.EvaluationContext
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.Request
import okio.Buffer
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class, ForgeExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class PrecomputedAssignmentsRequestFactoryTest {

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    private lateinit var testedFactory: PrecomputedAssignmentsRequestFactory

    @BeforeEach
    fun `set up`() {
        testedFactory = PrecomputedAssignmentsRequestFactory(mockInternalLogger)
    }

    // region create() - Success cases

    @Test
    fun `M create valid request W create() { all fields present }`(
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeClientToken: String,
        @StringForgery fakeEnv: String,
        @StringForgery fakeTargetingKey: String
    ) {
        // Given
        val context = EvaluationContext(
            targetingKey = fakeTargetingKey,
            attributes = mapOf("attr1" to "value1", "attr2" to "value2")
        )
        val flagsContext = FlagsContext(
            applicationId = fakeApplicationId,
            clientToken = fakeClientToken,
            site = DatadogSite.US1,
            env = fakeEnv
        )

        // When
        val request = testedFactory.create(context, flagsContext)

        // Then
        checkNotNull(request)
        assertThat(request.url.toString()).isEqualTo("https://preview.ff-cdn.datadoghq.com/precompute-assignments")
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.header("dd-client-token")).isEqualTo(fakeClientToken)
        assertThat(request.header("dd-application-id")).isEqualTo(fakeApplicationId)
        assertThat(request.header("Content-Type")).isEqualTo("application/vnd.api+json")
    }

    @Test
    fun `M create valid request W create() { no application id }`(
        @StringForgery fakeClientToken: String,
        @StringForgery fakeEnv: String,
        @StringForgery fakeTargetingKey: String
    ) {
        // Given
        val context = EvaluationContext(
            targetingKey = fakeTargetingKey,
            attributes = emptyMap()
        )
        val flagsContext = FlagsContext(
            applicationId = null,
            clientToken = fakeClientToken,
            site = DatadogSite.US1,
            env = fakeEnv
        )

        // When
        val request = testedFactory.create(context, flagsContext)

        // Then
        checkNotNull(request)
        assertThat(request.header("dd-client-token")).isEqualTo(fakeClientToken)
        assertThat(request.header("dd-application-id")).isNull()
        assertThat(request.header("Content-Type")).isEqualTo("application/vnd.api+json")
    }

    @Test
    fun `M use custom endpoint W create() { custom endpoint configured }`(
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeClientToken: String,
        @StringForgery fakeEnv: String,
        @StringForgery fakeTargetingKey: String
    ) {
        // Given
        val fakeCustomEndpoint = "https://custom-proxy.example.com/flags/assignments"
        val context = EvaluationContext(
            targetingKey = fakeTargetingKey,
            attributes = emptyMap()
        )
        val flagsContext = FlagsContext(
            applicationId = fakeApplicationId,
            clientToken = fakeClientToken,
            site = DatadogSite.US1,
            env = fakeEnv,
            customFlagEndpoint = fakeCustomEndpoint
        )

        // When
        val request = testedFactory.create(context, flagsContext)

        checkNotNull(request)
        assertThat(request.url.toString()).isEqualTo(fakeCustomEndpoint)
    }

    // endregion

    // region create() - Request body validation

    @Test
    fun `M create correct JSON body W create() { with attributes }`(
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeClientToken: String,
        @StringForgery fakeEnv: String,
        @StringForgery fakeTargetingKey: String
    ) {
        // Given
        val context = EvaluationContext(
            targetingKey = fakeTargetingKey,
            attributes = mapOf(
                "userId" to "user123",
                "plan" to "premium",
                "age" to "25"
            )
        )
        val flagsContext = FlagsContext(
            applicationId = fakeApplicationId,
            clientToken = fakeClientToken,
            site = DatadogSite.US1,
            env = fakeEnv
        )

        // When
        val request = testedFactory.create(context, flagsContext)

        // Then
        checkNotNull(request)
        val bodyString = extractRequestBodyAsString(request)
        val bodyJson = JSONObject(bodyString)

        // Validate top-level structure
        assertThat(bodyJson.has("data")).isTrue()
        val data = bodyJson.getJSONObject("data")
        assertThat(data.getString("type")).isEqualTo("precompute-assignments-request")

        // Validate attributes structure
        val attributes = data.getJSONObject("attributes")
        assertThat(attributes.has("env")).isTrue()
        assertThat(attributes.has("subject")).isTrue()

        // Validate subject
        val subject = attributes.getJSONObject("subject")
        assertThat(subject.getString("targeting_key")).isEqualTo(fakeTargetingKey)
        assertThat(subject.has("targeting_attributes")).isTrue()

        // Validate targeting attributes
        val targetingAttributes = subject.getJSONObject("targeting_attributes")
        assertThat(targetingAttributes.getString("userId")).isEqualTo("user123")
        assertThat(targetingAttributes.getString("plan")).isEqualTo("premium")
        assertThat(targetingAttributes.getString("age")).isEqualTo("25")

        // Validate env
        val env = attributes.getJSONObject("env")
        assertThat(env.getString("dd_env")).isEqualTo(fakeEnv)
    }

    @Test
    fun `M create correct JSON body W create() { empty attributes }`(
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeClientToken: String,
        @StringForgery fakeEnv: String,
        @StringForgery fakeTargetingKey: String
    ) {
        // Given
        val context = EvaluationContext(
            targetingKey = fakeTargetingKey,
            attributes = emptyMap()
        )
        val flagsContext = FlagsContext(
            applicationId = fakeApplicationId,
            clientToken = fakeClientToken,
            site = DatadogSite.US1,
            env = fakeEnv
        )

        // When
        val request = testedFactory.create(context, flagsContext)

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

    @Test
    fun `M handle various attributes W create() { multiple string attributes }`(
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeClientToken: String,
        @StringForgery fakeEnv: String,
        @StringForgery fakeTargetingKey: String
    ) {
        // Given
        val context = EvaluationContext(
            targetingKey = fakeTargetingKey,
            attributes = mapOf(
                "attr1" to "value1",
                "attr2" to "value2",
                "attr3" to "value3",
                "attr4" to "value4"
            )
        )
        val flagsContext = FlagsContext(
            applicationId = fakeApplicationId,
            clientToken = fakeClientToken,
            site = DatadogSite.US1,
            env = fakeEnv
        )

        // When
        val request = testedFactory.create(context, flagsContext)

        // Then
        checkNotNull(request)
        val bodyString = extractRequestBodyAsString(request)
        val bodyJson = JSONObject(bodyString)

        val targetingAttributes = bodyJson
            .getJSONObject("data")
            .getJSONObject("attributes")
            .getJSONObject("subject")
            .getJSONObject("targeting_attributes")

        assertThat(targetingAttributes.getString("attr1")).isEqualTo("value1")
        assertThat(targetingAttributes.getString("attr2")).isEqualTo("value2")
        assertThat(targetingAttributes.getString("attr3")).isEqualTo("value3")
        assertThat(targetingAttributes.getString("attr4")).isEqualTo("value4")
    }

    // endregion

    // region create() - Error cases

    @Test
    fun `M return null W create() { unsupported site and no custom endpoint }`(
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeClientToken: String,
        @StringForgery fakeEnv: String,
        @StringForgery fakeTargetingKey: String
    ) {
        // Given
        val context = EvaluationContext(
            targetingKey = fakeTargetingKey,
            attributes = emptyMap()
        )
        val flagsContext = FlagsContext(
            applicationId = fakeApplicationId,
            clientToken = fakeClientToken,
            site = DatadogSite.US1_FED,
            env = fakeEnv
        )

        // When
        val request = testedFactory.create(context, flagsContext)

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
