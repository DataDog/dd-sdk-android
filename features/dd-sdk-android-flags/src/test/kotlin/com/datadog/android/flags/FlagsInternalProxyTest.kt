/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.flags.internal.DatadogFlagsClient
import com.datadog.android.flags.internal.EvaluationsFeature
import com.datadog.android.flags.internal.EventsProcessor
import com.datadog.android.flags.internal.FlagsStateManager
import com.datadog.android.flags.internal.NoOpFlagsClient
import com.datadog.android.flags.internal.RumEvaluationLogger
import com.datadog.android.flags.internal.evaluation.EvaluationsManager
import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.datadog.android.flags.internal.repository.FlagsRepository
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.utils.forge.ForgeConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class FlagsInternalProxyTest {

    @Mock
    lateinit var mockFeatureSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockFlagsRepository: FlagsRepository

    @Mock
    lateinit var mockEvaluationsManager: EvaluationsManager

    @Mock
    lateinit var mockExposureProcessor: EventsProcessor

    @Mock
    lateinit var mockEvaluationsFeature: EvaluationsFeature

    @Mock
    lateinit var mockRumEvaluationLogger: RumEvaluationLogger

    @Mock
    lateinit var mockFlagsStateManager: FlagsStateManager

    @StringForgery
    lateinit var fakeFlagKey: String

    private lateinit var fakeFlag: PrecomputedFlag
    private lateinit var fakeContext: EvaluationContext
    private lateinit var fakeConfiguration: FlagsConfiguration
    private lateinit var testedProxy: _FlagsInternalProxy

    @BeforeEach
    fun `set up`(forge: Forge) {
        // Given
        fakeFlag = forge.getForgery<PrecomputedFlag>().copy(doLog = true)
        fakeContext = forge.getForgery()
        fakeConfiguration = forge.getForgery<FlagsConfiguration>().copy(
            trackExposures = true,
            trackEvaluations = true,
            rumIntegrationEnabled = true
        )
        testedProxy = newProxy()
    }

    @AfterEach
    fun `verify tracking never fetches or replaces configuration`() {
        // Then
        verifyNoInteractions(mockFlagsRepository, mockEvaluationsManager, mockFlagsStateManager, mockFeatureSdkCore)
        verifyNoMoreInteractions(mockExposureProcessor, mockEvaluationsFeature, mockRumEvaluationLogger)
    }

    @Test
    fun `M record only exposure W trackFlagExposure()`() {
        // When
        testedProxy.trackFlagExposure(fakeFlagKey, fakeFlag, fakeContext)

        // Then
        verify(mockExposureProcessor).processEvent(fakeFlagKey, fakeContext, fakeFlag)
    }

    @Test
    fun `M record only evaluation W trackFlagEvaluationEvent() {doLog false}`() {
        // When
        testedProxy.trackFlagEvaluationEvent(fakeFlagKey, fakeFlag.copy(doLog = false), fakeContext, null)

        // Then
        verify(mockEvaluationsFeature).processEvaluation(
            fakeFlagKey,
            fakeContext,
            fakeFlag.variationKey,
            fakeFlag.allocationKey,
            null,
            null
        )
    }

    @ParameterizedTest
    @CsvSource("false, false", "false, true", "true, false", "true, true")
    fun `M respect each tracking setting W trackFlagExposure() + trackFlagEvaluationEvent()`(
        trackExposures: Boolean,
        trackEvaluations: Boolean
    ) {
        // Given
        testedProxy = newProxy(
            fakeConfiguration.copy(trackExposures = trackExposures, trackEvaluations = trackEvaluations)
        )

        // When
        testedProxy.trackFlagExposure(fakeFlagKey, fakeFlag, fakeContext)
        testedProxy.trackFlagEvaluationEvent(fakeFlagKey, fakeFlag, fakeContext, null)
        testedProxy.trackFlagEvaluationEvent(fakeFlagKey, null, fakeContext, "FLAG_NOT_FOUND")

        // Then
        if (trackExposures) {
            verify(mockExposureProcessor).processEvent(fakeFlagKey, fakeContext, fakeFlag)
        } else {
            verifyNoInteractions(mockExposureProcessor)
        }
        if (trackEvaluations) {
            verify(mockEvaluationsFeature).processEvaluation(
                fakeFlagKey,
                fakeContext,
                fakeFlag.variationKey,
                fakeFlag.allocationKey,
                null,
                null
            )
            verify(mockEvaluationsFeature).processEvaluation(
                fakeFlagKey,
                fakeContext,
                null,
                null,
                "FLAG_NOT_FOUND",
                "FLAG_NOT_FOUND"
            )
        } else {
            verifyNoInteractions(mockEvaluationsFeature)
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M respect RUM setting W trackFlagRumEvaluation()`(enabled: Boolean) {
        // Given
        testedProxy = newProxy(fakeConfiguration.copy(rumIntegrationEnabled = enabled))

        // When
        testedProxy.trackFlagRumEvaluation(fakeFlagKey, fakeFlag.variationKey, fakeContext)

        // Then
        if (enabled) {
            verify(mockRumEvaluationLogger).logEvaluation(fakeFlagKey, fakeFlag.variationKey)
        } else {
            verifyNoInteractions(mockRumEvaluationLogger)
        }
        verifyNoInteractions(mockExposureProcessor, mockEvaluationsFeature)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " "])
    fun `M skip RUM W trackFlagRumEvaluation() {blank variant}`(variant: String) {
        // When
        testedProxy.trackFlagRumEvaluation(fakeFlagKey, variant, fakeContext)

        // Then
        verifyNoInteractions(mockRumEvaluationLogger, mockExposureProcessor, mockEvaluationsFeature)
    }

    @Test
    fun `M retain combined tracking W trackFlagSnapshotEvaluation()`() {
        // When
        testedProxy.trackFlagSnapshotEvaluation(fakeFlagKey, fakeFlag, fakeContext)

        // Then
        verify(mockExposureProcessor).processEvent(fakeFlagKey, fakeContext, fakeFlag)
        verify(mockEvaluationsFeature).processEvaluation(
            fakeFlagKey,
            fakeContext,
            fakeFlag.variationKey,
            fakeFlag.allocationKey,
            null,
            null
        )
        verify(mockRumEvaluationLogger).logEvaluation(fakeFlagKey, fakeFlag.variationKey)
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(
        strings = [
            "PROVIDER_NOT_READY", "PROVIDER_FATAL", "FLAG_NOT_FOUND", "PARSE_ERROR", "TYPE_MISMATCH",
            "TARGETING_KEY_MISSING", "INVALID_CONTEXT", "GENERAL"
        ]
    )
    fun `M record runtime default W trackFlagEvaluationEvent() {missing assignment}`(errorCode: String?) {
        // When
        testedProxy.trackFlagEvaluationEvent(fakeFlagKey, null, fakeContext, errorCode)

        // Then
        verify(mockEvaluationsFeature).processEvaluation(fakeFlagKey, fakeContext, null, null, errorCode, errorCode)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "customer-sensitive-error"])
    fun `M sanitize error W trackFlagEvaluationEvent() {unknown error code}`(errorCode: String) {
        // When
        testedProxy.trackFlagEvaluationEvent(fakeFlagKey, null, fakeContext, errorCode)

        // Then
        verify(mockEvaluationsFeature).processEvaluation(fakeFlagKey, fakeContext, null, null, "GENERAL", "GENERAL")
    }

    @Test
    fun `M omit assignment metadata W trackFlagEvaluationEvent() {evaluation error}`() {
        // When
        testedProxy.trackFlagEvaluationEvent(fakeFlagKey, fakeFlag, fakeContext, "TYPE_MISMATCH")

        // Then
        verify(mockEvaluationsFeature).processEvaluation(
            fakeFlagKey,
            fakeContext,
            null,
            null,
            "TYPE_MISMATCH",
            "TYPE_MISMATCH"
        )
    }

    @Test
    fun `M skip exposure W trackFlagExposure() {doLog false}`() {
        // When
        testedProxy.trackFlagExposure(fakeFlagKey, fakeFlag.copy(doLog = false), fakeContext)

        // Then
        verifyNoInteractions(mockExposureProcessor, mockEvaluationsFeature, mockRumEvaluationLogger)
    }

    @Test
    fun `M skip evaluation W trackFlagEvaluationEvent() {feature unavailable}`() {
        // Given
        testedProxy = newProxy(evaluationsFeature = null)

        // When
        testedProxy.trackFlagEvaluationEvent(fakeFlagKey, fakeFlag, fakeContext, null)

        // Then
        verifyNoInteractions(mockExposureProcessor, mockEvaluationsFeature, mockRumEvaluationLogger)
    }

    @Test
    fun `M no-op on fallback W trackFlagExposure() + trackFlagEvaluationEvent() + trackFlagRumEvaluation()`() {
        // Given
        testedProxy = _FlagsInternalProxy(NoOpFlagsClient("test", "not initialized") { _, _ -> })

        // When / Then
        assertDoesNotThrow {
            testedProxy.trackFlagExposure(fakeFlagKey, fakeFlag, fakeContext)
            testedProxy.trackFlagEvaluationEvent(fakeFlagKey, fakeFlag, fakeContext, null)
            testedProxy.trackFlagEvaluationEvent(fakeFlagKey, null, fakeContext, "PROVIDER_NOT_READY")
            testedProxy.trackFlagRumEvaluation(fakeFlagKey, fakeFlag.variationKey, fakeContext)
        }
    }

    private fun newProxy(
        configuration: FlagsConfiguration = fakeConfiguration,
        evaluationsFeature: EvaluationsFeature? = mockEvaluationsFeature
    ): _FlagsInternalProxy = _FlagsInternalProxy(
        DatadogFlagsClient(
            featureSdkCore = mockFeatureSdkCore,
            evaluationsManager = mockEvaluationsManager,
            flagsRepository = mockFlagsRepository,
            flagsConfiguration = configuration,
            rumEvaluationLogger = mockRumEvaluationLogger,
            exposureProcessor = mockExposureProcessor,
            evaluationsFeature = evaluationsFeature,
            flagStateManager = mockFlagsStateManager,
            evaluationTelemetryOptOutParameterEnabled = false
        )
    )
}
