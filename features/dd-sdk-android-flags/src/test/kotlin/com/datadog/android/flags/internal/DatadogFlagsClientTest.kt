/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature.Companion.RUM_FEATURE_NAME
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.flags.EvaluationContextCallback
import com.datadog.android.flags.FlagsConfiguration
import com.datadog.android.flags.FlagsStateListener
import com.datadog.android.flags._FlagsInternalProxy
import com.datadog.android.flags.internal.evaluation.EvaluationsManager
import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.datadog.android.flags.internal.model.VariationType
import com.datadog.android.flags.internal.repository.FirstFlagsLatch
import com.datadog.android.flags.internal.repository.FlagsRepository
import com.datadog.android.flags.model.ErrorCode
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.FlagsClientEvent
import com.datadog.android.flags.model.ResolutionReason
import com.datadog.android.flags.utils.forge.ForgeConfigurator
import fr.xgouchet.elmyr.Forge
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
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class DatadogFlagsClientTest {

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
    lateinit var mockRumEvaluationLogger: RumEvaluationLogger

    @Mock
    lateinit var mockExecutorService: ExecutorService

    @Mock
    lateinit var mockFlagsStateManager: FlagsStateManager

    private lateinit var testedClient: DatadogFlagsClient

    @StringForgery
    lateinit var fakeDefaultValue: String

    @StringForgery
    lateinit var fakeJsonKey: String

    lateinit var fakeJsonDefaultValue: JSONObject

    private lateinit var fakeDefaultMapValue: Map<String, Any?>

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeDefaultMapValue = mapOf(
            "name" to forge.anAlphabeticalString(),
            "age" to forge.anInt()
        )

        fakeJsonDefaultValue = JSONObject().apply {
            put(fakeJsonKey, forge.anAlphabeticalString())
        }

        whenever(mockFeatureSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockFeatureSdkCore.getFeature(RUM_FEATURE_NAME)) doReturn mock()

        // Mock executor to run tasks synchronously for testing
        whenever(mockExecutorService.execute(any())).thenAnswer { invocation ->
            val runnable = invocation.getArgument<Runnable>(0)
            runnable.run()
        }

        // Mock evaluation context as ready by default
        // Tests that need to test "not ready" state should override this
        val defaultContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )

        whenever(mockFlagsRepository.getEvaluationContext()) doReturn defaultContext

        testedClient = DatadogFlagsClient(
            featureSdkCore = mockFeatureSdkCore,
            evaluationsManager = mockEvaluationsManager,
            flagsRepository = mockFlagsRepository,
            flagsConfiguration = forge.getForgery<FlagsConfiguration>().copy(
                trackExposures = true,
                rumIntegrationEnabled = true
            ),
            rumEvaluationLogger = mockRumEvaluationLogger,
            exposureProcessor = mockProcessor,
            evaluationsFeature = null,
            flagStateManager = mockFlagsStateManager,
            evaluationTelemetryOptOutParameterEnabled = false
        )
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M isolate event keys W subscriber mutates delivered list`(completeBeforeRegistration: Boolean) {
        // Given
        val latch = FirstFlagsLatch()
        whenever(mockFlagsRepository.firstFlags) doReturn latch
        val fakeKeys = listOf(fakeDefaultValue, fakeJsonKey, fakeDefaultValue)
        val events = mutableListOf<FlagsClientEvent>()
        if (completeBeforeRegistration) latch.complete(fakeKeys)

        // When
        testedClient.onFirstFlags { event ->
            events.add(event)
            // Simulate Java or cast-based mutation of a delivered multi-key list.
            @Suppress("DontDowncastCollectionTypes")
            val mutableKeys = event.flagsChanged as MutableList<String>
            mutableKeys.clear()
        }
        testedClient.onFirstFlags { events.add(it) }
        if (!completeBeforeRegistration) latch.complete(fakeKeys)
        testedClient.onFirstFlags { events.add(it) }

        // Then
        assertThat(events).hasSize(3)
        assertThat(events[0].flagsChanged).isEmpty()
        assertThat(events[1].flagsChanged).containsExactlyElementsOf(fakeKeys)
        assertThat(events[2].flagsChanged).containsExactlyElementsOf(fakeKeys)
        assertThat(events[1]).isNotSameAs(events[0])
        assertThat(events[2]).isNotSameAs(events[1])
        verifyNoInteractions(mockInternalLogger)
    }

    // region resolveBooleanValue()

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M allow concurrent waits and context updates W resolve() {persistence pending}`(
        enabled: Boolean,
        forge: Forge
    ) {
        // Given
        val fakeKey = forge.anAlphabeticalString()
        val fakeContext = forge.getForgery<EvaluationContext>()
        val firstWaitEntered = CountDownLatch(1)
        val bothWaitsEntered = CountDownLatch(2)
        val persistenceFinished = CountDownLatch(1)
        whenever(mockFlagsRepository.waitForPersistenceLoad()).thenAnswer {
            firstWaitEntered.countDown()
            bothWaitsEntered.countDown()
            check(persistenceFinished.await(5, TimeUnit.SECONDS))
        }
        testedClient = DatadogFlagsClient(
            mockFeatureSdkCore, mockEvaluationsManager, mockFlagsRepository,
            FlagsConfiguration.Builder().build(), mockRumEvaluationLogger, mockProcessor,
            null, mockFlagsStateManager,
            evaluationTelemetryOptOutParameterEnabled = enabled
        )
        val executor = Executors.newFixedThreadPool(3)

        try {
            // When
            val first = executor.submit<Boolean> { testedClient.resolveBooleanValue(fakeKey, false) }
            assertThat(firstWaitEntered.await(1, TimeUnit.SECONDS)).isTrue()
            val second = executor.submit<Boolean> { testedClient.resolveBooleanValue(fakeKey, false) }
            val update = executor.submit { testedClient.setEvaluationContext(fakeContext, null, true) }

            // Then: both readers wait independently, without blocking a policy update.
            assertThat(bothWaitsEntered.await(1, TimeUnit.SECONDS)).isTrue()
            update.get(1, TimeUnit.SECONDS)
            persistenceFinished.countDown()
            assertThat(first.get(1, TimeUnit.SECONDS)).isFalse()
            assertThat(second.get(1, TimeUnit.SECONDS)).isFalse()
        } finally {
            persistenceFinished.countDown()
            executor.shutdownNow()
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue()
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M gate evaluation telemetry W resolve() {opt-out set}`(enabled: Boolean, forge: Forge) {
        // Given
        val fakeKey = forge.anAlphabeticalString()
        val fakeContext = forge.getForgery<EvaluationContext>()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.BOOLEAN.value,
            variationValue = "true",
            reason = ResolutionReason.TARGETING_MATCH.name,
            doLog = true
        )
        val mockEvaluationsFeature = mock<EvaluationsFeature>()
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeKey)) doReturn (fakeFlag to fakeContext)
        testedClient = DatadogFlagsClient(
            mockFeatureSdkCore, mockEvaluationsManager, mockFlagsRepository,
            FlagsConfiguration.Builder().build(), mockRumEvaluationLogger, mockProcessor,
            mockEvaluationsFeature, mockFlagsStateManager,
            evaluationTelemetryOptOutParameterEnabled = enabled
        )

        testedClient.setEvaluationTelemetryOptOut(true)

        // When
        val result = testedClient.resolve(fakeKey, false)

        // Then
        assertThat(result.value).isTrue()
        verify(mockProcessor).processEvent(fakeKey, fakeContext, fakeFlag)
        verifyNoInteractions(mockEvaluationsManager)
        if (enabled) {
            verifyNoInteractions(mockRumEvaluationLogger, mockEvaluationsFeature)
        } else {
            verify(mockEvaluationsFeature).processEvaluation(
                fakeKey,
                fakeContext,
                fakeFlag.variationKey,
                fakeFlag.allocationKey,
                null,
                null
            )
        }
    }

    @ParameterizedTest
    @CsvSource(
        "false, false, false",
        "false, false, true",
        "false, true, false",
        "false, true, true",
        "true, false, false",
        "true, false, true",
        "true, true, false",
        "true, true, true"
    )
    fun `M respect opt-out W trackFlagExposure() + trackFlagEvaluationEvent() + trackFlagRumEvaluation()`(
        enabled: Boolean,
        telemetryOptedOut: Boolean,
        perSubject: Boolean,
        forge: Forge
    ) {
        // Given
        val fakeKey = forge.anAlphabeticalString()
        val fakeContext = forge.getForgery<EvaluationContext>()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(doLog = true)
        val mockEvaluationsFeature = mock<EvaluationsFeature>()
        testedClient = DatadogFlagsClient(
            mockFeatureSdkCore, mockEvaluationsManager, mockFlagsRepository,
            FlagsConfiguration.Builder().build(), mockRumEvaluationLogger, mockProcessor,
            mockEvaluationsFeature, mockFlagsStateManager,
            evaluationTelemetryOptOutParameterEnabled = enabled
        )
        val testedProxy = _FlagsInternalProxy(testedClient)
        if (perSubject) {
            testedProxy.setEvaluationContext(fakeContext, null, telemetryOptedOut)
        } else {
            testedClient.setEvaluationTelemetryOptOut(telemetryOptedOut)
        }

        // When
        testedProxy.trackFlagExposure(fakeKey, fakeFlag, fakeContext)
        testedProxy.trackFlagEvaluationEvent(fakeKey, fakeFlag, fakeContext, null)
        testedProxy.trackFlagEvaluationEvent(fakeKey, null, fakeContext, null)
        testedProxy.trackFlagEvaluationEvent(fakeKey, null, fakeContext, "FLAG_NOT_FOUND")
        testedProxy.trackFlagRumEvaluation(fakeKey, fakeFlag.variationKey, fakeContext)

        // Then
        verify(mockProcessor).processEvent(fakeKey, fakeContext, fakeFlag)
        if (enabled && telemetryOptedOut) {
            verifyNoInteractions(mockEvaluationsFeature, mockRumEvaluationLogger)
        } else {
            verify(mockRumEvaluationLogger).logEvaluation(fakeKey, fakeFlag.variationKey)
            verify(mockEvaluationsFeature).processEvaluation(
                fakeKey,
                fakeContext,
                fakeFlag.variationKey,
                fakeFlag.allocationKey,
                null,
                null
            )
            verify(mockEvaluationsFeature).processEvaluation(fakeKey, fakeContext, null, null, null, null)
            verify(mockEvaluationsFeature).processEvaluation(
                fakeKey,
                fakeContext,
                null,
                null,
                "FLAG_NOT_FOUND",
                "FLAG_NOT_FOUND"
            )
        }
        verifyNoMoreInteractions(mockProcessor, mockEvaluationsFeature, mockRumEvaluationLogger)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M opt out W trackFlagSnapshotEvaluation() + trackFlagEvaluationEvent() + trackFlagRumEvaluation()`(
        requestedSubjectOptedOut: Boolean,
        forge: Forge
    ) {
        // Given
        val fakeKey = forge.anAlphabeticalString()
        val fakeContext = forge.getForgery<EvaluationContext>()
        val fakeNextContext = forge.getForgery<EvaluationContext>().copy(
            targetingKey = "${fakeContext.targetingKey}-next"
        )
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(doLog = true)
        val mockEvaluationsFeature = mock<EvaluationsFeature>()
        testedClient = DatadogFlagsClient(
            mockFeatureSdkCore, mockEvaluationsManager, mockFlagsRepository,
            FlagsConfiguration.Builder().build(), mockRumEvaluationLogger, mockProcessor,
            mockEvaluationsFeature, mockFlagsStateManager,
            evaluationTelemetryOptOutParameterEnabled = true
        )
        val testedProxy = _FlagsInternalProxy(testedClient)
        testedProxy.setEvaluationContext(fakeContext, null, !requestedSubjectOptedOut)
        testedProxy.setEvaluationContext(fakeNextContext, null, requestedSubjectOptedOut)

        // When
        testedProxy.trackFlagSnapshotEvaluation(fakeKey, fakeFlag, fakeContext)
        testedProxy.trackFlagRumEvaluation(fakeKey, fakeFlag.variationKey, fakeContext)
        testedProxy.trackFlagEvaluationEvent(fakeKey, fakeFlag, fakeContext, null)
        testedProxy.trackFlagEvaluationEvent(fakeKey, null, fakeContext, "FLAG_NOT_FOUND")

        // Then
        verify(mockProcessor).processEvent(fakeKey, fakeContext, fakeFlag)
        verifyNoInteractions(mockEvaluationsFeature, mockRumEvaluationLogger)
        verifyNoMoreInteractions(mockProcessor)
    }

    @Test
    fun `M return default value W resolveBooleanValue() { flag exists with invalid boolean string }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aBool()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.BOOLEAN.value,
            variationValue = "not-a-boolean"
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn (fakeFlag to fakeContext)

        // When
        val result = testedClient.resolveBooleanValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    @Test
    fun `M return default value W resolveBooleanValue() { flag does not exist }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aBool()
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn null

        // When
        val result = testedClient.resolveBooleanValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    @Test
    fun `M return default value W resolveBooleanValue() { provider not ready }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aBool()
        whenever(mockFlagsRepository.getEvaluationContext()) doReturn null

        // When
        val result = testedClient.resolveBooleanValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
        verifyNoInteractions(mockProcessor)
        verifyNoInteractions(mockRumEvaluationLogger)
    }

    @Test
    fun `M not log RUM evaluation W resolveBooleanValue() { flag does not exist }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aBool()
        val fakeEvaluationContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn null
        whenever(mockFlagsRepository.getEvaluationContext()) doReturn fakeEvaluationContext

        // When
        val result = testedClient.resolveBooleanValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
        verifyNoInteractions(mockRumEvaluationLogger)
    }

    @Test
    fun `M not log RUM evaluation W resolveBooleanValue() { flag has invalid boolean value }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aBool()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.BOOLEAN.value,
            variationValue = "not-a-boolean"
        )
        val fakeEvaluationContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn
            (fakeFlag to fakeEvaluationContext)

        // When
        val result = testedClient.resolveBooleanValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
        verifyNoInteractions(mockRumEvaluationLogger)
    }

    @Test
    fun `M log RUM evaluation W resolveBooleanValue() { evaluation context is null }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeFlagValue = forge.aBool()
        val fakeDefaultValue = !fakeFlagValue
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.BOOLEAN.value,
            variationValue = fakeFlagValue.toString(),
            doLog = true
        )
        val fakeEvaluationContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn
            (fakeFlag to fakeEvaluationContext)

        // When
        val result = testedClient.resolveBooleanValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeFlagValue)
        verify(mockRumEvaluationLogger).logEvaluation(
            flagKey = fakeFlagKey,
            value = fakeFlag.variationKey
        )
    }

    // endregion

    // region resolveStringValue()

    @Test
    fun `M return default value W resolveStringValue() { flag does not exist }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anAlphabeticalString()
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn null

        // When
        val result = testedClient.resolveStringValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    @Test
    fun `M return default value W resolveStringValue() { provider not ready }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anAlphabeticalString()
        whenever(mockFlagsRepository.getEvaluationContext()) doReturn null

        // When
        val result = testedClient.resolveStringValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
        verifyNoInteractions(mockProcessor)
        verifyNoInteractions(mockRumEvaluationLogger)
    }

    // endregion

    // region resolveIntValue()

    @Test
    fun `M return default value W resolveIntValue() { flag exists with invalid integer string }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anInt()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.INTEGER.value,
            variationValue = "not-an-integer"
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn (fakeFlag to fakeContext)

        // When
        val result = testedClient.resolveIntValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    @Test
    fun `M return default value W resolveIntValue() {flag does not exist}`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anInt()
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn null

        // When
        val result = testedClient.resolveIntValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    @Test
    fun `M return default value W resolveIntValue() { provider not ready }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anInt()
        whenever(mockFlagsRepository.getEvaluationContext()) doReturn null

        // When
        val result = testedClient.resolveIntValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
        verifyNoInteractions(mockProcessor)
        verifyNoInteractions(mockRumEvaluationLogger)
    }

    // endregion

    // region resolveDoubleValue()

    @Test
    fun `M return default value W resolveDoubleValue() {flag does not exist}`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aDouble()
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn null

        // When
        val result = testedClient.resolveDoubleValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    @Test
    fun `M return default value W resolveDoubleValue() { provider not ready }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aDouble()
        whenever(mockFlagsRepository.getEvaluationContext()) doReturn null

        // When
        val result = testedClient.resolveDoubleValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
        verifyNoInteractions(mockProcessor)
        verifyNoInteractions(mockRumEvaluationLogger)
    }

    // endregion

    // region resolveStructureValue()

    @Test
    fun `M return default value W resolveStructureValue() { flag exists with invalid JSON }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.OBJECT.value,
            variationValue = "invalid json {"
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn (fakeFlag to fakeContext)

        // When
        val result = testedClient.resolveStructureValue(fakeFlagKey, fakeJsonDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeJsonDefaultValue)
        // Parse errors are not logged - they're expected in normal operation
    }

    @Test
    fun `M return default value W resolveStructureValue() { flag exists with malformed JSON }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.OBJECT.value,
            variationValue = "{\"unclosed\": \"quote"
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn (fakeFlag to fakeContext)

        // When
        val result = testedClient.resolveStructureValue(fakeFlagKey, fakeJsonDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeJsonDefaultValue)
        // Parse errors are not logged - they're expected in normal operation
    }

    @Test
    fun `M return default value W resolveStructureValue() { flag exists with completely invalid JSON }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.OBJECT.value,
            variationValue = "not json at all!"
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn (fakeFlag to fakeContext)

        // When
        val result = testedClient.resolveStructureValue(fakeFlagKey, fakeJsonDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeJsonDefaultValue)
        // Parse errors are not logged - they're expected in normal operation
    }

    @Test
    fun `M return default value W resolveStructureValue() {flag does not exist}`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn null

        // When
        val result = testedClient.resolveStructureValue(fakeFlagKey, fakeJsonDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeJsonDefaultValue)
    }

    @Test
    fun `M return default value W resolveStructureValue() { provider not ready }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        whenever(mockFlagsRepository.getEvaluationContext()) doReturn null

        // When
        val result = testedClient.resolveStructureValue(fakeFlagKey, fakeJsonDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeJsonDefaultValue)
        verifyNoInteractions(mockProcessor)
        verifyNoInteractions(mockRumEvaluationLogger)
    }

    // endregion

    // region resolveStructureValue() - Map overload

    @Test
    fun `M return default map W resolveStructureValue() {map default, flag does not exist}`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn null

        // When
        val result = testedClient.resolveStructureValue(fakeFlagKey, fakeDefaultMapValue)

        // Then
        assertThat(result).isSameAs(fakeDefaultMapValue)
        verifyNoInteractions(mockProcessor)
    }

    @Test
    fun `M return default map W resolveStructureValue() {map default, provider not ready}`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        whenever(mockFlagsRepository.getEvaluationContext()) doReturn null

        // When
        val result = testedClient.resolveStructureValue(fakeFlagKey, fakeDefaultMapValue)

        // Then
        assertThat(result).isSameAs(fakeDefaultMapValue)
        verifyNoInteractions(mockProcessor)
        verifyNoInteractions(mockRumEvaluationLogger)
    }

    @Test
    fun `M return empty map W resolveStructureValue() {empty map default, flag not found}`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = emptyMap<String, Any?>()
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn null

        // When
        val result = testedClient.resolveStructureValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEmpty()
        assertThat(result).isSameAs(fakeDefaultValue) // Preserves original reference
        verifyNoInteractions(mockProcessor)
    }

    @Test
    fun `M return default map W resolveStructureValue() {map default, type mismatch}`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.STRING.value,
            variationValue = forge.anAlphabeticalString()
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn (fakeFlag to fakeContext)

        // When
        val result = testedClient.resolveStructureValue(fakeFlagKey, fakeDefaultMapValue)

        // Then
        assertThat(result).isSameAs(fakeDefaultMapValue)
        verifyNoInteractions(mockProcessor) // No exposure tracked for type mismatch
    }

    // endregion

    // region Constructor Logic

    @Test
    fun `M not create DefaultFlagsRepository W constructor { custom repository provided }`(forge: Forge) {
        // Given
        val customRepository = mockFlagsRepository
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeFlagValue = forge.anAlphabeticalString()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.STRING.value,
            variationValue = fakeFlagValue
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )
        whenever(customRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn (fakeFlag to fakeContext)

        testedClient = DatadogFlagsClient(
            featureSdkCore = mockFeatureSdkCore,
            evaluationsManager = mockEvaluationsManager,
            flagsRepository = customRepository,
            flagsConfiguration = forge.getForgery(),
            rumEvaluationLogger = mockRumEvaluationLogger,
            exposureProcessor = mockProcessor,
            evaluationsFeature = null,
            flagStateManager = mockFlagsStateManager,
            evaluationTelemetryOptOutParameterEnabled = false
        )

        // When
        val result = testedClient.resolveStringValue(fakeFlagKey, fakeDefaultValue)

        // Then

        assertThat(result).isEqualTo(fakeFlagValue)

        verify(customRepository).getPrecomputedFlagWithContext(fakeFlagKey)
    }

    // endregion

    // region resolve() - Generic Resolution with ResolutionDetails

    @Test
    fun `M typed allocationKey wins W resolve() { extraLogging also contains allocationKey }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aBool()
        val fakeFlagValue = !fakeDefaultValue
        val fakeAllocationKey = forge.anAlphabeticalString()
        val fakeExtraLoggingAllocationKey = forge.anAlphabeticalString()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.BOOLEAN.value,
            variationValue = fakeFlagValue.toString(),
            allocationKey = fakeAllocationKey,
            extraLogging = JSONObject().apply {
                put("allocationKey", fakeExtraLoggingAllocationKey)
            }
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn (fakeFlag to fakeContext)

        // When
        val result = testedClient.resolve(fakeFlagKey, fakeDefaultValue)

        // Then - typed allocationKey wins over any "allocationKey" entry from extraLogging
        assertThat(result.flagMetadata["allocationKey"]).isEqualTo(fakeAllocationKey)
    }

    @Test
    fun `M allocationKey excluded from metadata W resolve() { empty allocationKey }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aBool()
        val fakeFlagValue = !fakeDefaultValue
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.BOOLEAN.value,
            variationValue = fakeFlagValue.toString(),
            allocationKey = "",
            extraLogging = JSONObject()
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn (fakeFlag to fakeContext)

        // When
        val result = testedClient.resolve(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result.value).isEqualTo(fakeFlagValue)
        assertThat(result.flagMetadata).doesNotContainKey("allocationKey")
    }

    @Test
    fun `M allocationKey excluded from metadata W resolve() { whitespace allocationKey }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aBool()
        val fakeFlagValue = !fakeDefaultValue
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.BOOLEAN.value,
            variationValue = fakeFlagValue.toString(),
            allocationKey = "   ",
            extraLogging = JSONObject()
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn (fakeFlag to fakeContext)

        // When
        val result = testedClient.resolve(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result.value).isEqualTo(fakeFlagValue)
        assertThat(result.flagMetadata).doesNotContainKey("allocationKey")
    }

    @Test
    fun `M null extraLogging value excluded from metadata W resolve() { extraLogging has null value }`(
        forge: Forge
    ) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aBool()
        val fakeFlagValue = !fakeDefaultValue
        val fakeValidValue = forge.anAlphabeticalString()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.BOOLEAN.value,
            variationValue = fakeFlagValue.toString(),
            extraLogging = JSONObject().apply {
                put("nullKey", JSONObject.NULL) // JSONObject.NULL is not String/Number/Boolean
                put("validKey", fakeValidValue)
            }
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn (fakeFlag to fakeContext)

        // When
        val result = testedClient.resolve(fakeFlagKey, fakeDefaultValue)

        // Then - JSONObject.NULL is dropped; only primitive values pass through
        assertThat(result.flagMetadata).doesNotContainKey("nullKey")
        assertThat(result.flagMetadata["validKey"]).isEqualTo(fakeValidValue)
    }

    @Test
    fun `M return ResolutionDetails with STATIC reason W resolve() { flag has STATIC reason }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aBool()
        val fakeFlagValue = !fakeDefaultValue
        val fakeVariationKey = forge.anAlphabeticalString()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.BOOLEAN.value,
            variationValue = fakeFlagValue.toString(),
            variationKey = fakeVariationKey,
            reason = "STATIC"
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn (fakeFlag to fakeContext)

        // When
        val result = testedClient.resolve(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result.value).isEqualTo(fakeFlagValue)
        assertThat(result.variant).isEqualTo(fakeVariationKey)
        assertThat(result.reason).isEqualTo(ResolutionReason.STATIC)
        assertThat(result.errorCode).isNull()
        assertThat(result.errorMessage).isNull()
    }

    @Test
    fun `M return ResolutionDetails with parse error W resolve() { invalid JSON object }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = JSONObject().apply {
            put("default", "value")
        }
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.OBJECT.value,
            variationValue = "not valid json{"
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn (fakeFlag to fakeContext)

        // When
        val result = testedClient.resolve(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result.value.toString()).isEqualTo(fakeDefaultValue.toString())
        assertThat(result.variant).isNull()
        assertThat(result.reason).isEqualTo(ResolutionReason.ERROR)
        assertThat(result.errorCode).isEqualTo(ErrorCode.PARSE_ERROR)
        assertThat(result.errorMessage).contains("Flag '$fakeFlagKey'")
        assertThat(result.errorMessage).contains("Failed to parse value")
        assertThat(result.flagMetadata).isEmpty()

        // Verify no exposure tracked for parse error
        verifyNoInteractions(mockProcessor)
    }

    @Test
    fun `M track error evaluation W resolve() { flag not found and trackEvaluations enabled }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anInt()
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )
        val mockEvaluationsFeature = mock<EvaluationsFeature>()

        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn (null to fakeContext)

        val clientWithEvaluationTracking = DatadogFlagsClient(
            featureSdkCore = mockFeatureSdkCore,
            evaluationsManager = mockEvaluationsManager,
            flagsRepository = mockFlagsRepository,
            flagsConfiguration = forge.getForgery<FlagsConfiguration>().copy(
                trackEvaluations = true
            ),
            rumEvaluationLogger = mockRumEvaluationLogger,
            exposureProcessor = mockProcessor,
            evaluationsFeature = mockEvaluationsFeature,
            flagStateManager = mockFlagsStateManager,
            evaluationTelemetryOptOutParameterEnabled = false
        )

        // When
        val result = clientWithEvaluationTracking.resolve(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result.value).isEqualTo(fakeDefaultValue)
        assertThat(result.errorCode).isEqualTo(ErrorCode.FLAG_NOT_FOUND)

        // Verify error evaluation was tracked
        verify(mockEvaluationsFeature).processEvaluation(
            flagKey = eq(fakeFlagKey),
            context = eq(fakeContext),
            variantKey = isNull(),
            allocationKey = isNull(),
            errorCode = eq(ErrorCode.FLAG_NOT_FOUND.name),
            errorMessage = eq("Flag not found")
        )
    }

    @Test
    fun `M track exposure W resolve() { successful resolution and trackExposures enabled }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anAlphabeticalString()
        val fakeFlagValue = forge.anAlphabeticalString()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.STRING.value,
            variationValue = fakeFlagValue,
            doLog = true
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn (fakeFlag to fakeContext)

        // When
        val result = testedClient.resolve(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result.value).isEqualTo(fakeFlagValue)
        // Verify exposure tracked for successful resolution
        verify(mockProcessor).processEvent(
            flagKey = eq(fakeFlagKey),
            context = eq(fakeContext),
            data = eq(fakeFlag)
        )
    }

    // endregion

    @Test
    fun `M read repository once W resolveBooleanValue() {repository not ready}`(forge: Forge) {
        // Given
        val key = forge.anAlphabeticalString()
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(key)) doReturn null

        // When
        assertThat(testedClient.resolveBooleanValue(key, false)).isFalse()

        // Then: no second disk wait or context from a later repository state.
        verify(mockFlagsRepository).waitForPersistenceLoad()
        verify(mockFlagsRepository).getPrecomputedFlagWithContext(key)
        verify(mockFlagsRepository, never()).getEvaluationContext()
    }

    // region setEvaluationContext()

    @Test
    fun `M call evaluations manager W setEvaluationContext() { valid targeting key and attributes }`(forge: Forge) {
        // Given
        val fakeTargetingKey = forge.anAlphabeticalString()
        val fakeAttributes = mapOf(
            "plan" to forge.anElementFrom("free", "premium", "enterprise"),
            "region" to forge.anElementFrom("us-east-1", "eu-west-1"),
            "user_id" to forge.anInt().toString(),
            "is_beta" to forge.aBool().toString()
        )
        val fakeContext = EvaluationContext(fakeTargetingKey, fakeAttributes)

        // When
        testedClient.setEvaluationContext(fakeContext)

        // Then
        val contextCaptor = argumentCaptor<EvaluationContext>()
        verify(mockEvaluationsManager).updateEvaluationsForContext(
            contextCaptor.capture(),
            anyOrNull(),
            any()
        )

        val capturedContext = contextCaptor.firstValue
        assertThat(capturedContext.targetingKey).isEqualTo(fakeTargetingKey)
        assertThat(capturedContext.attributes).hasSize(4)
        assertThat(capturedContext.attributes).containsOnlyKeys("plan", "region", "user_id", "is_beta")
        assertThat(capturedContext.attributes["user_id"]).isEqualTo(fakeAttributes["user_id"])
        assertThat(capturedContext.attributes["is_beta"]).isEqualTo(fakeAttributes["is_beta"])
    }

    @Test
    fun `M call evaluations manager W setEvaluationContext() { blank targeting key }`() {
        // Given
        val blankTargetingKey = ""
        val fakeAttributes = mapOf("test" to "value")
        val contextWithBlankKey = EvaluationContext(blankTargetingKey, fakeAttributes)

        // When
        testedClient.setEvaluationContext(contextWithBlankKey)

        // Then
        // Verify that blank targeting keys are allowed and processed
        val contextCaptor = argumentCaptor<EvaluationContext>()
        verify(mockEvaluationsManager).updateEvaluationsForContext(
            contextCaptor.capture(),
            anyOrNull(),
            any()
        )

        val capturedContext = contextCaptor.firstValue
        assertThat(capturedContext.targetingKey).isEmpty()
        assertThat(capturedContext.attributes).isEqualTo(fakeAttributes)
    }

    @Test
    fun `M call evaluations manager W setEvaluationContext() { Empty Evaluation Context }`() {
        // Given
        val emptyContext = EvaluationContext.EMPTY

        // When
        testedClient.setEvaluationContext(emptyContext)

        // Then
        val contextCaptor = argumentCaptor<EvaluationContext>()
        verify(mockEvaluationsManager).updateEvaluationsForContext(
            contextCaptor.capture(),
            anyOrNull(),
            any()
        )

        val capturedContext = contextCaptor.firstValue
        assertThat(capturedContext.targetingKey).isEmpty()
        assertThat(capturedContext.attributes).isEqualTo(emptyMap<String, String>())
    }

    @Test
    fun `M delegate callback W setEvaluationContext() { with callback }`(forge: Forge) {
        // Given
        val fakeContext = EvaluationContext(forge.anAlphabeticalString(), emptyMap())
        val mockCallback = mock<EvaluationContextCallback>()

        // When
        testedClient.setEvaluationContext(fakeContext, mockCallback)

        // Then
        verify(mockEvaluationsManager).updateEvaluationsForContext(
            fakeContext,
            mockCallback
        )
    }

    @Test
    fun `M delegate with null callback W setEvaluationContext() { without callback }`(forge: Forge) {
        // Given
        val fakeContext = EvaluationContext(forge.anAlphabeticalString(), emptyMap())

        // When
        testedClient.setEvaluationContext(fakeContext)

        // Then
        verify(mockEvaluationsManager).updateEvaluationsForContext(
            fakeContext,
            null
        )
    }

    // endregion

    // region exposure event logging configuration

    // endregion

    // region rumIntegrationEnabled

    // endregion

    // region Type Mismatch Tests

    @Test
    fun `M return default value and not track exposure W resolveBooleanValue() { type mismatch - string flag }`(
        forge: Forge
    ) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aBool()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.STRING.value,
            variationValue = "some-string"
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn (fakeFlag to fakeContext)

        // When
        val result = testedClient.resolveBooleanValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
        // Verify no exposure tracked for type mismatch
        verifyNoInteractions(mockProcessor)

        // Verify warning was logged
        argumentCaptor<() -> String> {
            verify(mockInternalLogger).log(
                eq(InternalLogger.Level.WARN),
                eq(InternalLogger.Target.USER),
                capture(),
                eq(null),
                eq(false),
                eq(null)
            )
            val message = lastValue.invoke()
            assertThat(message).contains("Flag '$fakeFlagKey'")
            assertThat(message).contains("has type 'string' but Boolean was requested")
        }
    }

    @Test
    fun `M return default value and not track exposure W resolveIntValue() { type mismatch - boolean flag }`(
        forge: Forge
    ) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anInt()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.BOOLEAN.value,
            variationValue = "true"
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn (fakeFlag to fakeContext)

        // When
        val result = testedClient.resolveIntValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
        // Verify no exposure tracked for type mismatch
        verifyNoInteractions(mockProcessor)

        // Verify warning was logged
        argumentCaptor<() -> String> {
            verify(mockInternalLogger).log(
                eq(InternalLogger.Level.WARN),
                eq(InternalLogger.Target.USER),
                capture(),
                eq(null),
                eq(false),
                eq(null)
            )
            val message = lastValue.invoke()
            assertThat(message).contains("Flag '$fakeFlagKey'")
            assertThat(message).contains("has type 'boolean' but Int was requested")
        }
    }

    @Test
    fun `M return default value and not track exposure W resolveStructureValue() { type mismatch - string flag }`(
        forge: Forge
    ) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = JSONObject().apply {
            put("default", "value")
        }
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.STRING.value,
            variationValue = "just a string"
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn (fakeFlag to fakeContext)

        // When
        val result = testedClient.resolveStructureValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
        // Verify no exposure tracked for type mismatch
        verifyNoInteractions(mockProcessor)

        // Verify warning was logged
        argumentCaptor<() -> String> {
            verify(mockInternalLogger).log(
                eq(InternalLogger.Level.WARN),
                eq(InternalLogger.Target.USER),
                capture(),
                eq(null),
                eq(false),
                eq(null)
            )
            val message = lastValue.invoke()
            assertThat(message).contains("Flag '$fakeFlagKey'")
            assertThat(message).contains("has type 'string' but JSONObject was requested")
        }
    }

    @Test
    fun `M return default value and not track exposure W resolveStringValue() { type mismatch - number flag }`(
        forge: Forge
    ) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anAlphabeticalString()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.NUMBER.value,
            variationValue = "42.5"
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn (fakeFlag to fakeContext)

        // When
        val result = testedClient.resolveStringValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
        // Verify no exposure tracked for type mismatch
        verifyNoInteractions(mockProcessor)

        // Verify warning was logged
        argumentCaptor<() -> String> {
            verify(mockInternalLogger).log(
                eq(InternalLogger.Level.WARN),
                eq(InternalLogger.Target.USER),
                capture(),
                eq(null),
                eq(false),
                eq(null)
            )
            val message = lastValue.invoke()
            assertThat(message).contains("Flag '$fakeFlagKey'")
            assertThat(message).contains("has type 'number' but String was requested")
        }
    }

    @Test
    fun `M accept number, float and int W resolveDoubleValue() { compatible types }`(forge: Forge) {
        // Given
        val fakeFlagKey1 = forge.anAlphabeticalString()
        val fakeFlagKey2 = forge.anAlphabeticalString()
        val fakeFlagKey3 = forge.anAlphabeticalString()
        val fakeFlagValue = forge.aDouble()
        val fakeFlagIntValue = forge.anInt()
        val fakeDefaultValue = forge.aDouble()

        val numberFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.NUMBER.value,
            variationValue = fakeFlagValue.toString()
        )
        val floatFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.FLOAT.value,
            variationValue = fakeFlagValue.toString()
        )
        val intFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.INTEGER.value,
            variationValue = fakeFlagIntValue.toString()
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )

        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey1)) doReturn (numberFlag to fakeContext)
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey2)) doReturn (floatFlag to fakeContext)
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey3)) doReturn (intFlag to fakeContext)

        // When
        val result1 = testedClient.resolveDoubleValue(fakeFlagKey1, fakeDefaultValue)
        val result2 = testedClient.resolveDoubleValue(fakeFlagKey2, fakeDefaultValue)
        val result3 = testedClient.resolveDoubleValue(fakeFlagKey3, fakeDefaultValue)

        // Then
        assertThat(result1).isEqualTo(fakeFlagValue)
        assertThat(result2).isEqualTo(fakeFlagValue)
        assertThat(result3).isEqualTo(fakeFlagIntValue.toDouble())
    }

    @Test
    fun `M return default value W resolveIntValue() { value exceeds Int MAX_VALUE }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anInt()
        // Value that's too large for Int
        val oversizedValue = Int.MAX_VALUE.toLong() + 1L
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.INTEGER.value,
            variationValue = oversizedValue.toString()
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn (fakeFlag to fakeContext)

        // When
        val result = testedClient.resolveIntValue(fakeFlagKey, fakeDefaultValue)

        // Then
        // toIntOrNull() returns null for values outside Int range, so default is returned
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    // endregion

    // region State Management

    @Test
    fun `M delegate to state manager W state_addListener()`() {
        // Given
        val mockListener = mock<FlagsStateListener>()

        // When
        testedClient.state.addListener(mockListener)

        // Then
        // Verify delegation to state manager
        verify(mockFlagsStateManager).addListener(mockListener)
    }

    @Test
    fun `M delegate to state manager W state_removeListener()`() {
        // Given
        val mockListener = mock<FlagsStateListener>()

        // When
        testedClient.state.removeListener(mockListener)

        // Then
        // Verify delegation to state manager
        verify(mockFlagsStateManager).removeListener(mockListener)
    }

    // Note: updateState() tests removed - this is now an internal method called only by
    // EvaluationsManager. State notification testing is covered in FlagsStateManagerTest.

    // endregion

    // region Internal APIs exposed through _FlagsInternalProxy

    @Test
    fun `M delegate to repository W getFlagAssignmentsSnapshot()`() {
        // Given
        val mockSnapshot = mapOf<String, PrecomputedFlag>(
            "flag1" to mock(),
            "flag2" to mock()
        )
        whenever(mockFlagsRepository.getFlagsSnapshot()) doReturn mockSnapshot

        // When
        val result = testedClient.getFlagAssignmentsSnapshot()

        // Then
        assertThat(result).isSameAs(mockSnapshot)
        verify(mockFlagsRepository).getFlagsSnapshot()
    }

    @Test
    fun `M track evaluation W trackFlagSnapshotEvaluation() { boolean flag }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeFlagValue = forge.aBool()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.BOOLEAN.value,
            variationValue = fakeFlagValue.toString(),
            doLog = true
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )

        // When
        testedClient.trackFlagSnapshotEvaluation(fakeFlagKey, fakeFlag, fakeContext)

        // Then
        verify(mockProcessor).processEvent(
            flagKey = fakeFlagKey,
            context = fakeContext,
            data = fakeFlag
        )
        verify(mockRumEvaluationLogger).logEvaluation(
            flagKey = fakeFlagKey,
            value = fakeFlag.variationKey
        )
    }

    @Test
    fun `M track evaluation W trackFlagSnapshotEvaluation() { string flag }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeFlagValue = forge.anAlphabeticalString()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.STRING.value,
            variationValue = fakeFlagValue,
            doLog = true
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )

        // When
        testedClient.trackFlagSnapshotEvaluation(fakeFlagKey, fakeFlag, fakeContext)

        // Then
        verify(mockProcessor).processEvent(
            flagKey = fakeFlagKey,
            context = fakeContext,
            data = fakeFlag
        )
        verify(mockRumEvaluationLogger).logEvaluation(
            flagKey = fakeFlagKey,
            value = fakeFlag.variationKey
        )
    }

    @Test
    fun `M track evaluation W trackFlagSnapshotEvaluation() { integer flag }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeFlagValue = forge.anInt()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.INTEGER.value,
            variationValue = fakeFlagValue.toString(),
            doLog = true
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )

        // When
        testedClient.trackFlagSnapshotEvaluation(fakeFlagKey, fakeFlag, fakeContext)

        // Then
        verify(mockProcessor).processEvent(
            flagKey = fakeFlagKey,
            context = fakeContext,
            data = fakeFlag
        )
        verify(mockRumEvaluationLogger).logEvaluation(
            flagKey = fakeFlagKey,
            value = fakeFlag.variationKey
        )
    }

    @Test
    fun `M track evaluation W trackFlagSnapshotEvaluation() { number flag }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeFlagValue = forge.aDouble()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.NUMBER.value,
            variationValue = fakeFlagValue.toString(),
            doLog = true
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )

        // When
        testedClient.trackFlagSnapshotEvaluation(fakeFlagKey, fakeFlag, fakeContext)

        // Then
        verify(mockProcessor).processEvent(
            flagKey = fakeFlagKey,
            context = fakeContext,
            data = fakeFlag
        )
        verify(mockRumEvaluationLogger).logEvaluation(
            flagKey = fakeFlagKey,
            value = fakeFlag.variationKey
        )
    }

    @Test
    fun `M track evaluation W trackFlagSnapshotEvaluation() { object flag }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeFlagValue = JSONObject().apply {
            put("key", forge.anAlphabeticalString())
        }
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.OBJECT.value,
            variationValue = fakeFlagValue.toString(),
            doLog = true
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )

        // When
        testedClient.trackFlagSnapshotEvaluation(fakeFlagKey, fakeFlag, fakeContext)

        // Then
        verify(mockProcessor).processEvent(
            flagKey = fakeFlagKey,
            context = fakeContext,
            data = fakeFlag
        )
        verify(mockRumEvaluationLogger).logEvaluation(
            flagKey = fakeFlagKey,
            value = fakeFlag.variationKey
        )
    }

    @Test
    fun `M not track exposure but still log RUM W trackFlagSnapshotEvaluation() { doLog is false }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeFlagValue = forge.aBool()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.BOOLEAN.value,
            variationValue = fakeFlagValue.toString(),
            doLog = false
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )

        // When
        testedClient.trackFlagSnapshotEvaluation(fakeFlagKey, fakeFlag, fakeContext)

        // Then
        verifyNoInteractions(mockProcessor)
        verify(mockRumEvaluationLogger).logEvaluation(
            flagKey = fakeFlagKey,
            value = fakeFlag.variationKey
        )
    }

    @Test
    fun `M not track exposure to backend W trackFlagSnapshotEvaluation() { trackExposures is false }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeFlagValue = forge.aBool()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.BOOLEAN.value,
            variationValue = fakeFlagValue.toString(),
            doLog = true
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )

        testedClient = DatadogFlagsClient(
            featureSdkCore = mockFeatureSdkCore,
            evaluationsManager = mockEvaluationsManager,
            flagsRepository = mockFlagsRepository,
            flagsConfiguration = forge.getForgery<FlagsConfiguration>().copy(
                trackExposures = false,
                rumIntegrationEnabled = true
            ),
            rumEvaluationLogger = mockRumEvaluationLogger,
            exposureProcessor = mockProcessor,
            evaluationsFeature = null,
            flagStateManager = mockFlagsStateManager,
            evaluationTelemetryOptOutParameterEnabled = false
        )

        // When
        testedClient.trackFlagSnapshotEvaluation(fakeFlagKey, fakeFlag, fakeContext)

        // Then
        verifyNoInteractions(mockProcessor)
        verify(mockRumEvaluationLogger).logEvaluation(
            flagKey = fakeFlagKey,
            value = fakeFlag.variationKey
        )
    }

    @Test
    fun `M not log to RUM W trackFlagSnapshotEvaluation() { rumIntegrationEnabled is false }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeFlagValue = forge.aBool()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.BOOLEAN.value,
            variationValue = fakeFlagValue.toString(),
            doLog = true
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )

        testedClient = DatadogFlagsClient(
            featureSdkCore = mockFeatureSdkCore,
            evaluationsManager = mockEvaluationsManager,
            flagsRepository = mockFlagsRepository,
            flagsConfiguration = forge.getForgery<FlagsConfiguration>().copy(
                trackExposures = true,
                rumIntegrationEnabled = false
            ),
            rumEvaluationLogger = mockRumEvaluationLogger,
            exposureProcessor = mockProcessor,
            evaluationsFeature = null,
            flagStateManager = mockFlagsStateManager,
            evaluationTelemetryOptOutParameterEnabled = false
        )

        // When
        testedClient.trackFlagSnapshotEvaluation(fakeFlagKey, fakeFlag, fakeContext)

        // Then
        verify(mockProcessor).processEvent(
            flagKey = fakeFlagKey,
            context = fakeContext,
            data = fakeFlag
        )
        verifyNoInteractions(mockRumEvaluationLogger)
    }

    // endregion
}
