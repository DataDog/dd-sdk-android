/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.integration

import com.datadog.android.api.feature.Feature
import com.datadog.android.flags.Flags
import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags.FlagsConfiguration
import com.datadog.android.flags.integration.FlagsClientTest.Companion.createStubSdkCore
import com.datadog.android.flags.integration.tests.elmyr.FlagsIntegrationForgeConfigurator
import com.datadog.android.flags.integration.tests.utils.httpResponse
import com.datadog.android.flags.model.ErrorCode
import com.datadog.android.flags.model.EvaluationContext
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

/**
 * Plaintext flag keys are compared as exact code-point sequences, without Unicode normalization.
 */
@ExtendWith(MockitoExtension::class, ForgeExtension::class)
@ForgeConfiguration(FlagsIntegrationForgeConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class FlagsKeyIdentityTest {
    @Test
    fun `M not find the decomposed key W resolve() {response serves only the composed key}`(forge: Forge) {
        // Given
        val core = createStubSdkCore(forge)
        core.stubDataStore(Feature.FLAGS_FEATURE_NAME)
        val body = "{\"data\":{\"type\":\"precomputed-assignments\",\"attributes\":{\"flags\":{" +
            "\"$COMPOSED_ESCAPED\":{\"variationType\":\"boolean\",\"variationValue\":true,\"doLog\":true," +
            "\"allocationKey\":\"allocation\",\"variationKey\":\"variant\",\"reason\":\"TARGETING_MATCH\"}}}}}"
        core.stubNetworkResponses { httpResponse(it, body = body) }
        Flags.enable(
            FlagsConfiguration.Builder().useCustomFlagEndpoint("https://fixture.test/precompute-assignments").build(),
            core
        )
        val client = FlagsClient.Builder(sdkCore = core).build()

        try {
            // When
            client.setEvaluationContext(EvaluationContext(forge.anAlphabeticalString()))
            val decomposed = client.resolve(DECOMPOSED, false)
            val composed = client.resolve(COMPOSED, false)
            core.stopFeatures()

            // Then
            assertThat(DECOMPOSED).isNotEqualTo(COMPOSED)
            assertThat(decomposed.value).isFalse()
            assertThat(decomposed.errorCode).isEqualTo(ErrorCode.FLAG_NOT_FOUND)
            assertThat(composed.value).isTrue()
            assertThat(composed.errorCode).isNull()
            val exposureKeys = core.eventsWritten(Feature.FLAGS_FEATURE_NAME).map { flagKey(it.eventData) }
            assertThat(exposureKeys).containsExactly(COMPOSED)
            val evaluationKeys = core.eventsWritten(Feature.FLAGS_EVALUATIONS_FEATURE_NAME)
                .map { flagKey(it.eventData) }
            assertThat(evaluationKeys).containsExactlyInAnyOrder(COMPOSED, DECOMPOSED)
        } finally {
            core.stopFeatures()
        }
    }

    private fun flagKey(eventData: String) = JSONObject(eventData).getJSONObject("flag").getString("key")

    private companion object {
        const val COMPOSED = "café"
        const val COMPOSED_ESCAPED = "caf\\u00e9"
        const val DECOMPOSED = "café"
    }
}
