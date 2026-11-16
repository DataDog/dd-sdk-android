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
import com.datadog.android.flags.ExperimentationOptOutMode
import com.datadog.android.flags.internal.evaluation.ExperimentationOptOut
import com.datadog.android.flags.internal.model.PrecomputedAssignmentsRequest
import com.datadog.android.flags.internal.model.PrecomputedAssignmentsRequest.Diagnostics
import com.datadog.android.flags.internal.model.PrecomputedAssignmentsRequest.Trigger
import com.datadog.android.flags.internal.selection.CanonicalFlagSelection
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
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
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

    @Forgery
    lateinit var fakeDiagnostics: Diagnostics

    private lateinit var testedFactory: PrecomputedAssignmentsRequestFactory

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeDatadogContext = fakeDatadogContext.copy(
            site = forge.aValueFrom(
                DatadogSite::class.java,
                exclude = listOf(DatadogSite.US1_FED, DatadogSite.US2_FED)
            )
        ).withRumContext(fakeRumContext)

        testedFactory = PrecomputedAssignmentsRequestFactory(mockInternalLogger, null, null)
    }

    @Test
    fun `M include diagnostics in request body W create()`(
        @Forgery fakeEvaluationContext: EvaluationContext
    ) {
        // Given
        val diagnostics = fakeDiagnostics
        val expectedTrigger = when (diagnostics.fetch.trigger) {
            Trigger.INITIALIZATION -> "initialization"
            Trigger.CONTEXT_CHANGE -> "context_change"
        }
        val expectedDiagnostics = JsonParser.parseString(
            """
            {
              "sdk": {"instance_id": "${diagnostics.sdk.instanceId}"},
              "fetch": {"sequence": ${diagnostics.fetch.sequence}, "trigger": "$expectedTrigger"}
            }
            """.trimIndent()
        )

        // When
        val request = testedFactory.create(fakeEvaluationContext, fakeDatadogContext, diagnostics)

        // Then
        val actualRequest = checkNotNull(request) { "Expected request with diagnostics" }
        val body = JsonParser.parseString(extractRequestBodyAsString(actualRequest)).asJsonObject
        assertThat(body.remove("diagnostics")).isEqualTo(expectedDiagnostics)
    }

    // region create() - Success cases

    @Test
    fun `M advertise encoding capability W create() { native source }`(
        @Forgery fakeEvaluationContext: EvaluationContext
    ) {
        // Given
        val context = fakeDatadogContext.copy(source = "android")

        // When
        val request = checkNotNull(testedFactory.create(fakeEvaluationContext, context, fakeDiagnostics))
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
            val request = checkNotNull(testedFactory.create(fakeEvaluationContext, context, fakeDiagnostics))
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
        val request = testedFactory.create(context, fakeDatadogContext, fakeDiagnostics)

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
        val request = testedFactory.create(context, fakeDatadogContext, fakeDiagnostics)

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
        testedFactory = PrecomputedAssignmentsRequestFactory(mockInternalLogger, fakeCustomEndpoint, null)
        val context = EvaluationContext(
            targetingKey = fakeTargetingKey,
            attributes = emptyMap()
        )

        // When
        val request = testedFactory.create(context, fakeDatadogContext, fakeDiagnostics)

        checkNotNull(request)
        assertThat(request.url.toString()).isEqualTo(fakeCustomEndpoint)
    }

    // endregion

    // region create() - Request body validation

    @ParameterizedTest
    @EnumSource(ExperimentationOptOutMode::class)
    fun `M omit evaluation options W create() {timestamp absent}`(mode: ExperimentationOptOutMode) {
        // Given
        testedFactory = PrecomputedAssignmentsRequestFactory(mockInternalLogger, null, null, mode)
        val context = EvaluationContext("subject")

        // When
        val request = checkNotNull(testedFactory.create(context, fakeDatadogContext, fakeDiagnostics))

        // Then
        val attributes = JSONObject(extractRequestBodyAsString(request))
            .getJSONObject("data").getJSONObject("attributes")
        assertThat(attributes.has("evaluation_options")).isFalse()
    }

    @ParameterizedTest
    @EnumSource(ExperimentationOptOutMode::class)
    fun `M include evaluation options only for enabled mode W create() {valid timestamp}`(
        mode: ExperimentationOptOutMode
    ) {
        // Given
        testedFactory = PrecomputedAssignmentsRequestFactory(mockInternalLogger, null, null, mode)
        val context = EvaluationContext(
            "subject",
            mapOf(ExperimentationOptOut.ATTRIBUTE to "2026-10-01T00:00:00Z")
        )

        // When
        val request = checkNotNull(testedFactory.create(context, fakeDatadogContext, fakeDiagnostics))

        // Then
        val body = JSONObject(extractRequestBodyAsString(request))
        val attributes = body.getJSONObject("data").getJSONObject("attributes")
        assertThat(attributes.has("evaluation_options")).isEqualTo(mode != ExperimentationOptOutMode.DISABLED)
        assertThat(
            attributes.getJSONObject("subject").getJSONObject("targeting_attributes")
                .getString(ExperimentationOptOut.ATTRIBUTE)
        ).isEqualTo("2026-10-01T00:00:00Z")
        if (mode != ExperimentationOptOutMode.DISABLED) {
            val expectedMode = when (mode) {
                ExperimentationOptOutMode.IMMEDIATE -> "immediate"
                else -> "future_experiments_only"
            }
            assertThat(attributes.getJSONObject("evaluation_options").getString("experimentation_opt_out_mode"))
                .isEqualTo(expectedMode)
        }
        assertThat(body.getJSONObject("diagnostics").getJSONObject("sdk").getString("instance_id"))
            .isEqualTo(fakeDiagnostics.sdk.instanceId)
        assertThat(body.getJSONObject("diagnostics").getJSONObject("fetch").getLong("sequence"))
            .isEqualTo(fakeDiagnostics.fetch.sequence)
    }

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
        val request = testedFactory.create(fakeContext, fakeDatadogContext, fakeDiagnostics)

        // Then
        checkNotNull(request)
        val bodyJson = JsonParser.parseString(extractRequestBodyAsString(request)).asJsonObject
        assertThat(bodyJson.keySet()).containsExactlyInAnyOrder("data", "diagnostics")
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
        val request = testedFactory.create(context, fakeDatadogContext, fakeDiagnostics)

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

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `M add flag_selection to attributes W create() { flag selection configured }`(
        useCustomEndpoint: Boolean,
        @StringForgery fakeTargetingKey: String
    ) {
        // Given
        val fakeCustomEndpoint = "https://custom-proxy.example.com/flags/assignments".takeIf { useCustomEndpoint }
        testedFactory = PrecomputedAssignmentsRequestFactory(
            mockInternalLogger,
            fakeCustomEndpoint,
            CanonicalFlagSelection(tags = listOf("mobile", "web"), keyPatterns = listOf("checkout-*"))
        )
        val context = EvaluationContext(targetingKey = fakeTargetingKey, attributes = emptyMap())

        // When
        val request = testedFactory.create(context, fakeDatadogContext, fakeDiagnostics)

        // Then
        checkNotNull(request)
        val flagSelection = JSONObject(extractRequestBodyAsString(request))
            .getJSONObject("data")
            .getJSONObject("attributes")
            .getJSONObject("flag_selection")
        assertThat(flagSelection.keys().asSequence().toList()).containsExactlyInAnyOrder("tags", "key_patterns")
        assertThat(flagSelection.getJSONArray("tags").toString()).isEqualTo("""["mobile","web"]""")
        assertThat(flagSelection.getJSONArray("key_patterns").toString()).isEqualTo("""["checkout-*"]""")
        assertThat(testedFactory.sendsFlagSelection).isTrue()
    }

    @ParameterizedTest
    @CsvSource("IMMEDIATE,immediate", "FUTURE_EXPERIMENTS_ONLY,future_experiments_only")
    fun `M send flag_selection and evaluation_options W create() { selection and opt-out }`(
        mode: ExperimentationOptOutMode,
        expectedWireMode: String
    ) {
        // Given
        testedFactory = PrecomputedAssignmentsRequestFactory(
            mockInternalLogger,
            null,
            CanonicalFlagSelection(tags = listOf("mobile"), keyPatterns = listOf("checkout-*")),
            mode
        )
        val context = EvaluationContext(
            "subject",
            mapOf(ExperimentationOptOut.ATTRIBUTE to "2026-10-01T00:00:00Z")
        )

        // When
        val request = checkNotNull(testedFactory.create(context, fakeDatadogContext, fakeDiagnostics))

        // Then
        val attributes = JSONObject(extractRequestBodyAsString(request))
            .getJSONObject("data").getJSONObject("attributes")
        val flagSelection = attributes.getJSONObject("flag_selection")
        assertThat(flagSelection.getJSONArray("tags").toString()).isEqualTo("""["mobile"]""")
        assertThat(flagSelection.getJSONArray("key_patterns").toString()).isEqualTo("""["checkout-*"]""")
        assertThat(attributes.getJSONObject("evaluation_options").getString("experimentation_opt_out_mode"))
            .isEqualTo(expectedWireMode)
    }

    @Test
    fun `M send both lists W create() { flag selection with key patterns only }`(
        @StringForgery fakeTargetingKey: String
    ) {
        // Given
        testedFactory = PrecomputedAssignmentsRequestFactory(
            mockInternalLogger,
            null,
            CanonicalFlagSelection(tags = emptyList(), keyPatterns = listOf("checkout-*"))
        )
        val context = EvaluationContext(targetingKey = fakeTargetingKey, attributes = emptyMap())

        // When
        val request = testedFactory.create(context, fakeDatadogContext, fakeDiagnostics)

        // Then
        checkNotNull(request)
        val flagSelection = JSONObject(extractRequestBodyAsString(request))
            .getJSONObject("data")
            .getJSONObject("attributes")
            .getJSONObject("flag_selection")
        assertThat(flagSelection.getJSONArray("tags").length()).isZero()
        assertThat(flagSelection.getJSONArray("key_patterns").toString()).isEqualTo("""["checkout-*"]""")
    }

    @Test
    fun `M not add flag_selection W create() { no flag selection }`(
        @StringForgery fakeTargetingKey: String
    ) {
        // Given
        val context = EvaluationContext(targetingKey = fakeTargetingKey, attributes = emptyMap())

        // When
        val request = testedFactory.create(context, fakeDatadogContext, fakeDiagnostics)

        // Then
        checkNotNull(request)
        val attributes = JSONObject(extractRequestBodyAsString(request))
            .getJSONObject("data")
            .getJSONObject("attributes")
        assertThat(attributes.keys().asSequence().toList()).containsExactlyInAnyOrder("env", "source", "subject")
        assertThat(testedFactory.sendsFlagSelection).isFalse()
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
        val request = testedFactory.create(fakeContext, fakeDatadogContext, fakeDiagnostics)

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
        val request = testedFactory.create(fakeContext, fakeDatadogContext, fakeDiagnostics)

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
        val request = testedFactory.create(context, fakeDatadogContext, fakeDiagnostics)

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
