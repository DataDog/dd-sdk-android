/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.integration

import com.datadog.android.api.feature.Feature
import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags.integration.tests.elmyr.FlagsIntegrationForgeConfigurator
import com.datadog.android.flags.integration.tests.utils.FlagsTelemetryTestFixture
import com.datadog.android.flags.integration.tests.utils.MainLooperTestConfiguration
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.openfeature.ExposureEvaluation
import com.datadog.android.flags.openfeature.ExposureHook
import com.datadog.android.flags.openfeature.asOpenFeatureProvider
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import dev.openfeature.kotlin.sdk.ImmutableContext
import dev.openfeature.kotlin.sdk.OpenFeatureAPI
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * A peeking client evaluates flags without exposures or evaluation telemetry, while other clients of the same SDK
 * instance keep both.
 */
@ExtendWith(ForgeExtension::class, TestConfigurationExtension::class)
@ForgeConfiguration(FlagsIntegrationForgeConfigurator::class)
internal class PeekingFlagsClientTest {
    private lateinit var fixture: FlagsTelemetryTestFixture

    @BeforeEach
    fun `set up`(forge: Forge) {
        fixture = FlagsTelemetryTestFixture(forge)
    }

    @AfterEach
    fun `tear down`() {
        runBlocking { OpenFeatureAPI.shutdown() }
        fixture.close()
    }

    @Test
    fun `M send nothing and resolve the assignment W resolve() {peeking client}`() {
        // Given
        val testedClient = peekingClient()

        // When
        val details = testedClient.resolve(FLAG, false)
        fixture.flush()

        // Then
        assertThat(details.value).isTrue()
        assertThat(details.variant).isEqualTo("on")
        assertThat(fixture.events(Feature.FLAGS_FEATURE_NAME)).isEmpty()
        assertThat(fixture.evaluationCount()).isZero()
        assertThat(fixture.stubCore.eventsReceived(Feature.RUM_FEATURE_NAME)).isEmpty()
    }

    @Test
    fun `M send the exposure through the default client W resolve() {peeking client evaluated first}`() {
        // Given
        val peekingClient = peekingClient()
        val defaultClient = FlagsClient.Builder("default", fixture.core).build()
        defaultClient.setEvaluationContext(EvaluationContext(SUBJECT))
        val peeked = peekingClient.resolve(FLAG, false)

        // When
        val resolved = defaultClient.resolve(FLAG, false)
        fixture.flush()

        // Then
        assertThat(resolved).isEqualTo(peeked)
        val exposure = fixture.events(Feature.FLAGS_FEATURE_NAME).single()
        assertThat(exposure["flag"].asJsonObject["key"].asString).isEqualTo(FLAG)
        assertThat(exposure["subject"].asJsonObject["id"].asString).isEqualTo(SUBJECT)
        assertThat(fixture.evaluationCount()).isEqualTo(1)
        assertThat(fixture.stubCore.eventsReceived(Feature.RUM_FEATURE_NAME)).hasSize(1)
    }

    @Test
    fun `M report a non-exposure W getBooleanValue() {peeking client as OpenFeature provider}`() {
        // Given
        val evaluations = mutableListOf<ExposureEvaluation>()
        OpenFeatureAPI.addHooks(listOf(ExposureHook { evaluations.add(it) }))
        val peekingClient = FlagsClient.Builder("peek", fixture.core)
            .trackExposures(false)
            .trackEvaluations(false)
            .build()
        runBlocking {
            OpenFeatureAPI.setProviderAndWait(
                peekingClient.asOpenFeatureProvider(),
                ImmutableContext(SUBJECT),
                Dispatchers.Unconfined
            )
        }
        val defaultClient = FlagsClient.Builder("default", fixture.core).build()
        defaultClient.setEvaluationContext(EvaluationContext(SUBJECT))

        // When
        val peeked = OpenFeatureAPI.getClient().getBooleanValue(FLAG, false)
        defaultClient.resolveBooleanValue(FLAG, false)
        fixture.flush()

        // Then
        assertThat(peeked).isTrue()
        assertThat(evaluations.single().isExposure).isFalse()
        assertThat(evaluations.single().shouldSend()).isFalse()
        assertThat(fixture.events(Feature.FLAGS_FEATURE_NAME)).hasSize(1)
        assertThat(fixture.evaluationCount()).isEqualTo(1)
        assertThat(fixture.stubCore.eventsReceived(Feature.RUM_FEATURE_NAME)).hasSize(1)
    }

    private fun peekingClient(): FlagsClient = FlagsClient.Builder("peek", fixture.core)
        .trackExposures(false)
        .trackEvaluations(false)
        .build()
        .apply { setEvaluationContext(EvaluationContext(SUBJECT)) }

    companion object {
        private const val FLAG = "enabled"
        private const val SUBJECT = "subject"

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> = listOf(MainLooperTestConfiguration())
    }
}
