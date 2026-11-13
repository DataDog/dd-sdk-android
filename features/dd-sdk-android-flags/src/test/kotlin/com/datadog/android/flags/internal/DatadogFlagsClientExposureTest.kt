/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.flags.ExperimentationOptOutMode
import com.datadog.android.flags.FlagsConfiguration
import com.datadog.android.flags.internal.evaluation.EvaluationsManager
import com.datadog.android.flags.internal.evaluation.ExperimentationOptOut
import com.datadog.android.flags.internal.model.FlagEvaluationSnapshot
import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.datadog.android.flags.internal.model.SplitFeature
import com.datadog.android.flags.internal.model.VariationType
import com.datadog.android.flags.internal.repository.FlagsRepository
import com.datadog.android.flags.model.ErrorCode
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.utils.forge.ForgeConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class DatadogFlagsClientExposureTest {

    @Mock
    lateinit var mockFeatureSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockFlagsRepository: FlagsRepository

    @Mock
    lateinit var mockEvaluationsManager: EvaluationsManager

    @Mock
    lateinit var mockProcessor: EventsProcessor

    @Mock
    lateinit var mockEvaluationsFeature: EvaluationsFeature

    @Mock
    lateinit var mockRumEvaluationLogger: RumEvaluationLogger

    @Mock
    lateinit var mockFlagsStateManager: FlagsStateManager

    @StringForgery
    lateinit var fakeFlagKey: String

    private lateinit var fakeContext: EvaluationContext
    private lateinit var fakeFlag: PrecomputedFlag
    private lateinit var testedClient: DatadogFlagsClient

    @BeforeEach
    fun `set up`(forge: Forge) {
        whenever(mockFeatureSdkCore.internalLogger) doReturn mockInternalLogger
        fakeContext = EvaluationContext(forge.anAlphabeticalString())
        fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.BOOLEAN.value,
            variationValue = "true",
            allocationKey = forge.anAlphabeticalString(),
            variationKey = forge.anAlphabeticalString(),
            extraLogging = JSONObject(),
            doLog = true,
            serialId = forge.aLong(min = 0L, max = Int.MAX_VALUE.toLong())
        )
        testedClient = newClient(ExperimentationOptOut())
    }

    @Test
    fun `M write one entry per feature and known destination W resolve() {split features}`() {
        // Given
        install(
            fakeFlag.copy(
                features = listOf(
                    SplitFeature("holdout.key", "holdout-1", listOf("HOOK")),
                    SplitFeature("holdout.should_include_in_holdout_analysis", false, listOf("HOOK", "FUTURE")),
                    SplitFeature("exposure", 2.0, listOf("EXPOSURE")),
                    SplitFeature("evaluation", "e", listOf("EVALUATION", "HOOK")),
                    SplitFeature("nowhere", "n", listOf("FUTURE"))
                )
            )
        )

        // When
        val result = testedClient.resolve(fakeFlagKey, false)

        // Then
        assertThat(result.flagMetadata).isEqualTo(
            mapOf(
                "allocationKey" to fakeFlag.allocationKey,
                "__dd_observe_full_evaluation_data" to false,
                "__dd_feature.holdout.key" to "holdout-1",
                "__dd_feature.holdout.should_include_in_holdout_analysis" to false,
                "__dd_feature.evaluation" to "e"
            )
        )
    }

    @Test
    fun `M add no metadata entries W resolve() {no features}`() {
        // Given
        install(fakeFlag)

        // When
        val result = testedClient.resolve(fakeFlagKey, false)

        // Then
        assertThat(result.flagMetadata).containsOnlyKeys("allocationKey", "__dd_observe_full_evaluation_data")
    }

    @Test
    fun `M count EVALUATION features W resolve() {split features}`() {
        // Given
        install(
            fakeFlag.copy(
                features = listOf(
                    SplitFeature("hook", "h", listOf("HOOK")),
                    SplitFeature("evaluation", 1.0, listOf("EVALUATION"))
                )
            )
        )

        // When
        testedClient.resolve(fakeFlagKey, false)

        // Then
        verify(mockEvaluationsFeature).processEvaluation(
            flagKey = eq(fakeFlagKey),
            context = eq(fakeContext),
            variantKey = eq(fakeFlag.variationKey),
            allocationKey = eq(fakeFlag.allocationKey),
            errorCode = isNull(),
            errorMessage = isNull(),
            observeFullEvaluationData = eq(false),
            telemetryTargetingKey = eq(fakeContext.targetingKey),
            preparedTargetingKey = anyOrNull(),
            features = eq(mapOf("evaluation" to 1.0))
        )
    }

    @Test
    fun `M record the exposure W resolve()`() {
        // Given
        install(fakeFlag)

        // When
        testedClient.resolve(fakeFlagKey, false)

        // Then
        verify(mockProcessor).processEvent(fakeFlagKey, fakeContext, fakeFlag)
    }

    @Test
    fun `M defer the exposure W resolveDeferringExposure()`() {
        // Given
        install(fakeFlag)

        // When
        val result = testedClient.resolveDeferringExposure(fakeFlagKey, false)

        // Then
        assertThat(result.details.value).isTrue()
        assertThat(result.details.flagMetadata).isEqualTo(
            mapOf(
                "allocationKey" to fakeFlag.allocationKey,
                "__dd_observe_full_evaluation_data" to false
            )
        )
        assertThat(result.recordExposure as Any?).isNotNull()
        verifyNoInteractions(mockProcessor)
    }

    @Test
    fun `M record on request W resolveDeferringExposure() + recordExposure()`(@BoolForgery fakeIsRecorded: Boolean) {
        // Given
        install(fakeFlag)
        whenever(mockProcessor.processEvent(fakeFlagKey, fakeContext, fakeFlag)) doReturn fakeIsRecorded
        val resolution = testedClient.resolveDeferringExposure(fakeFlagKey, false)

        // When
        val isRecorded = checkNotNull(resolution.recordExposure).invoke()

        // Then
        assertThat(isRecorded).isEqualTo(fakeIsRecorded)
        verify(mockProcessor).processEvent(fakeFlagKey, fakeContext, fakeFlag)
    }

    @Test
    fun `M record nothing W resolveDeferringExposure() + recordExposure() {newer context replaced it}`() {
        // Given
        val policy = ExperimentationOptOut(ExperimentationOptOutMode.IMMEDIATE)
        policy.prepare(fakeContext)
        testedClient = newClient(policy)
        install(fakeFlag)
        val resolution = testedClient.resolveDeferringExposure(fakeFlagKey, false)
        policy.prepare(EvaluationContext(fakeContext.targetingKey + "-other"))

        // When
        val isRecorded = checkNotNull(resolution.recordExposure).invoke()

        // Then
        assertThat(isRecorded).isNull()
        verify(mockProcessor, never()).processEvent(any(), any(), any())
    }

    @Test
    fun `M track evaluation telemetry with consent W resolveDeferringExposure()`(
        @BoolForgery fakeConsent: Boolean
    ) {
        // Given
        install(fakeFlag.copy(observeFullEvaluationData = fakeConsent))

        // When
        val result = testedClient.resolveDeferringExposure(fakeFlagKey, false)

        // Then
        assertThat(result.details.flagMetadata).containsEntry("__dd_observe_full_evaluation_data", fakeConsent)
        verify(mockEvaluationsFeature).processEvaluation(
            eq(fakeFlagKey),
            eq(fakeContext),
            eq(fakeFlag.variationKey),
            eq(fakeFlag.allocationKey),
            isNull(),
            isNull(),
            eq(fakeConsent),
            eq(fakeContext.targetingKey),
            anyOrNull(),
            eq(emptyMap())
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["variationKey", "allocationKey"])
    fun `M return no exposure W resolveDeferringExposure() {blank assignment key}`(blankKey: String) {
        // Given
        install(
            if (blankKey == "variationKey") fakeFlag.copy(variationKey = " ") else fakeFlag.copy(allocationKey = "")
        )

        // When
        val result = testedClient.resolveDeferringExposure(fakeFlagKey, false)

        // Then
        assertThat(result.recordExposure as Any?).isNull()
        verifyNoInteractions(mockProcessor)
    }

    @Test
    fun `M return the assignment context W resolveDeferringExposure()`() {
        // Given
        install(fakeFlag)

        // When
        val result = testedClient.resolveDeferringExposure(fakeFlagKey, false)

        // Then
        assertThat(result.context).isEqualTo(fakeContext)
    }

    @Test
    fun `M drop reserved keys from extra logging W resolve()`() {
        // Given
        install(
            fakeFlag.copy(
                doLog = false,
                extraLogging = JSONObject()
                    .put("__dd_do_log", true)
                    .put("__dd_exposure_cache_hit", false)
                    .put("__dd_feature.holdout.key", "forged")
                    .put("custom", "kept")
            )
        )

        // When
        val result = testedClient.resolve(fakeFlagKey, false)

        // Then
        assertThat(result.flagMetadata).containsEntry("custom", "kept")
            .doesNotContainKeys("__dd_do_log", "__dd_exposure_cache_hit", "__dd_feature.holdout.key")
    }

    @Test
    fun `M return no exposure W resolveDeferringExposure() {doLog false}`() {
        // Given
        install(fakeFlag.copy(doLog = false))

        // When
        val result = testedClient.resolveDeferringExposure(fakeFlagKey, false)

        // Then
        assertThat(result.recordExposure as Any?).isNull()
        assertThat(result.details.flagMetadata).doesNotContainKeys("__dd_do_log", "__dd_exposure_cache_hit")
        verifyNoInteractions(mockProcessor)
    }

    @Test
    fun `M return no exposure W resolveDeferringExposure() {type mismatch}`() {
        // Given
        install(fakeFlag)

        // When
        val result = testedClient.resolveDeferringExposure(fakeFlagKey, "default")

        // Then
        assertThat(result.details.errorCode).isEqualTo(ErrorCode.TYPE_MISMATCH)
        assertThat(result.recordExposure as Any?).isNull()
        verify(mockEvaluationsFeature).processEvaluation(
            eq(fakeFlagKey),
            eq(fakeContext),
            isNull(),
            isNull(),
            eq(ErrorCode.TYPE_MISMATCH.name),
            anyOrNull(),
            eq(false),
            anyOrNull(),
            anyOrNull(),
            eq(emptyMap())
        )
        verifyNoInteractions(mockProcessor)
    }

    private fun newClient(experimentationOptOut: ExperimentationOptOut) = DatadogFlagsClient(
        featureSdkCore = mockFeatureSdkCore,
        evaluationsManager = mockEvaluationsManager,
        flagsRepository = mockFlagsRepository,
        flagsConfiguration = FlagsConfiguration.Builder().trackEvaluations(true).rumIntegrationEnabled(false)
            .build(),
        rumEvaluationLogger = mockRumEvaluationLogger,
        exposureProcessor = mockProcessor,
        evaluationsFeature = mockEvaluationsFeature,
        flagStateManager = mockFlagsStateManager,
        evaluationTelemetryOptOutParameterEnabled = false,
        experimentationOptOut = experimentationOptOut
    )

    private fun install(flag: PrecomputedFlag) {
        fakeFlag = flag
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn
            FlagEvaluationSnapshot(flag, fakeContext, flag.observeFullEvaluationData)
    }
}
