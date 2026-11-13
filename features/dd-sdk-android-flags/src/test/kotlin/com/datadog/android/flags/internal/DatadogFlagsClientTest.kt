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
import com.datadog.android.flags.ExperimentationOptOutMode
import com.datadog.android.flags.FlagsConfiguration
import com.datadog.android.flags.FlagsStateListener
import com.datadog.android.flags._FlagsInternalProxy
import com.datadog.android.flags.internal.aggregation.EvaluationTargetingKey
import com.datadog.android.flags.internal.evaluation.AssignmentAuthorization
import com.datadog.android.flags.internal.evaluation.EvaluationContextOutcomeCallback
import com.datadog.android.flags.internal.evaluation.EvaluationsManager
import com.datadog.android.flags.internal.evaluation.ExperimentationOptOut
import com.datadog.android.flags.internal.model.FlagEvaluationSnapshot
import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.datadog.android.flags.internal.model.VariationType
import com.datadog.android.flags.internal.repository.DefaultFlagsRepository
import com.datadog.android.flags.internal.repository.FirstFlagsLatch
import com.datadog.android.flags.internal.repository.FlagsAssignmentsSnapshot
import com.datadog.android.flags.internal.repository.FlagsRepository
import com.datadog.android.flags.model.ErrorCode
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.FlagsClientEvent
import com.datadog.android.flags.model.FlagsClientState
import com.datadog.android.flags.model.ResolutionDetails
import com.datadog.android.flags.model.ResolutionReason
import com.datadog.android.flags.utils.forge.ForgeConfigurator
import com.datadog.android.utils.verifyLog
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
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.NullAndEmptySource
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
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
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

    @ParameterizedTest
    @EnumSource(ExperimentationOptOutMode::class, names = ["IMMEDIATE", "FUTURE_EXPERIMENTS_ONLY"])
    fun `M not track W trackFlagSnapshotEvaluation and granular tracking {context superseded by an opt-out}`(
        mode: ExperimentationOptOutMode,
        forge: Forge
    ) {
        // Given
        val fakeKey = forge.anAlphabeticalString()
        val fakePreviousContext = EvaluationContext(forge.anAlphabeticalString())
        val fakeOptedOutContext = EvaluationContext(
            fakePreviousContext.targetingKey,
            mapOf(ExperimentationOptOut.ATTRIBUTE to "2026-10-19T12:00:00Z")
        )
        val policy = ExperimentationOptOut(mode)
        policy.prepare(fakeOptedOutContext)
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(doLog = true)
        val mockEvaluationsFeature = mock<EvaluationsFeature>()
        testedClient = DatadogFlagsClient(
            featureSdkCore = mockFeatureSdkCore,
            evaluationsManager = mockEvaluationsManager,
            flagsRepository = mockFlagsRepository,
            flagsConfiguration = FlagsConfiguration.Builder().trackEvaluations(true)
                .trackExposures(true).rumIntegrationEnabled(true).build(),
            rumEvaluationLogger = mockRumEvaluationLogger,
            exposureProcessor = mockProcessor,
            evaluationsFeature = mockEvaluationsFeature,
            flagStateManager = mockFlagsStateManager,
            experimentationOptOut = policy,
            evaluationTelemetryOptOutParameterEnabled = false
        )

        // When
        testedClient.trackFlagSnapshotEvaluation(fakeKey, fakeFlag, fakePreviousContext)
        testedClient.trackFlagExposure(fakeKey, fakeFlag, fakePreviousContext)
        testedClient.trackFlagEvaluationEvent(fakeKey, fakeFlag, fakePreviousContext, null)
        testedClient.trackFlagRumEvaluation(fakeKey, fakeFlag.variationKey, fakePreviousContext)

        // Then
        verifyNoInteractions(mockProcessor, mockEvaluationsFeature, mockRumEvaluationLogger)
    }

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
    private lateinit var fakeDefaultContext: EvaluationContext

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
        fakeDefaultContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )

        whenever(mockFlagsRepository.getEvaluationSnapshot(any())) doReturn
            FlagEvaluationSnapshot(null, fakeDefaultContext, false)

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
            evaluationTelemetryOptOutParameterEnabled = false,
            experimentationOptOut = ExperimentationOptOut()
        )
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M read persistence once W resolveBooleanValue() { cold client }`(trackEvaluations: Boolean) {
        // Given
        val mockRepository = spy(
            DefaultFlagsRepository(
                featureSdkCore = mockFeatureSdkCore,
                instanceName = fakeJsonKey,
                dataStore = mock(),
                flagSelection = null,
                persistenceLoadTimeoutMs = 0
            )
        )
        val mockEvaluationsFeature = mock<EvaluationsFeature>()
        testedClient = DatadogFlagsClient(
            featureSdkCore = mockFeatureSdkCore,
            evaluationsManager = mockEvaluationsManager,
            flagsRepository = mockRepository,
            flagsConfiguration = FlagsConfiguration.Builder()
                .trackEvaluations(trackEvaluations).trackExposures(false).rumIntegrationEnabled(false).build(),
            rumEvaluationLogger = mockRumEvaluationLogger,
            exposureProcessor = mockProcessor,
            evaluationsFeature = mockEvaluationsFeature,
            flagStateManager = mockFlagsStateManager,
            evaluationTelemetryOptOutParameterEnabled = false,
            experimentationOptOut = ExperimentationOptOut()
        )

        // When
        val value = testedClient.resolveBooleanValue(fakeJsonKey, false)

        // Then
        assertThat(value).isFalse()
        verify(mockRepository).getEvaluationSnapshot(fakeJsonKey)
        verify(mockRepository, never()).getEvaluationContext()
        verify(mockRepository).waitForPersistenceLoad()
    }

    @Test
    fun `M return general error and default W resolve() {protected cross subject assignments}`(forge: Forge) {
        // Given
        val flag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.BOOLEAN.value,
            variationValue = "true",
            doLog = true
        )
        val installedContext = EvaluationContext("installed")
        val experimentationOptOut = ExperimentationOptOut()
        val assignmentAuthorization = AssignmentAuthorization(
            mockInternalLogger,
            experimentationOptOut.stateLock
        )
        assignmentAuthorization.prepare(
            EvaluationContext(
                "requested",
                mapOf(AssignmentAuthorization.ATTRIBUTE to "header.payload.signature")
            )
        )
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeJsonKey)) doReturn
            FlagEvaluationSnapshot(flag, installedContext, false)
        testedClient = DatadogFlagsClient(
            mockFeatureSdkCore,
            mockEvaluationsManager,
            mockFlagsRepository,
            FlagsConfiguration.Builder().trackExposures(true).build(),
            mockRumEvaluationLogger,
            mockProcessor,
            null,
            mockFlagsStateManager,
            false,
            experimentationOptOut,
            assignmentAuthorization
        )

        // When
        val result = testedClient.resolve(fakeJsonKey, false)

        // Then
        assertThat(result.value).isFalse()
        assertThat(result.errorCode).isEqualTo(ErrorCode.GENERAL)
        assertThat(result.errorMessage)
            .isEqualTo("Flag '$fakeJsonKey': ${AssignmentAuthorization.AUTHORIZATION_FAILED}")
        verifyNoInteractions(mockProcessor)
    }

    @Test
    fun `M not return previous subject value W resolve() {authorized subject switch completes during read}`(
        forge: Forge
    ) {
        // Given
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.BOOLEAN.value,
            variationValue = "true"
        )
        val fakeInstalledContext = EvaluationContext("installed-subject")
        val experimentationOptOut = ExperimentationOptOut()
        val assignmentAuthorization = AssignmentAuthorization(
            mockInternalLogger,
            experimentationOptOut.stateLock
        )
        val authorizationRequest = assignmentAuthorization.prepare(
            EvaluationContext(
                "requested-subject",
                mapOf(AssignmentAuthorization.ATTRIBUTE to "header.payload.signature")
            )
        )
        val snapshotRead = CountDownLatch(1)
        val releaseSnapshot = CountDownLatch(1)
        val switchStarted = CountDownLatch(1)
        val switchCompleted = CountDownLatch(1)
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeJsonKey)).thenAnswer {
            snapshotRead.countDown()
            check(releaseSnapshot.await(5, TimeUnit.SECONDS))
            FlagEvaluationSnapshot(fakeFlag, fakeInstalledContext, false)
        }
        testedClient = DatadogFlagsClient(
            mockFeatureSdkCore,
            mockEvaluationsManager,
            mockFlagsRepository,
            FlagsConfiguration.Builder().build(),
            mockRumEvaluationLogger,
            mockProcessor,
            null,
            mockFlagsStateManager,
            false,
            experimentationOptOut,
            assignmentAuthorization
        )
        val executor = Executors.newFixedThreadPool(2)

        try {
            // When
            val resolution = executor.submit<ResolutionDetails<Boolean>> {
                testedClient.resolve(fakeJsonKey, false)
            }
            assertThat(snapshotRead.await(5, TimeUnit.SECONDS)).isTrue()
            val subjectSwitch = executor.submit {
                switchStarted.countDown()
                assignmentAuthorization.onAssignmentsAccepted(authorizationRequest)
                switchCompleted.countDown()
            }
            assertThat(switchStarted.await(5, TimeUnit.SECONDS)).isTrue()
            val switchCompletedBeforeSnapshot = switchCompleted.await(250, TimeUnit.MILLISECONDS)
            releaseSnapshot.countDown()
            val result = resolution.get(5, TimeUnit.SECONDS)
            subjectSwitch.get(5, TimeUnit.SECONDS)

            // Then
            assertThat(switchCompletedBeforeSnapshot).isFalse()
            assertThat(result.value).isFalse()
            assertThat(result.errorCode).isEqualTo(ErrorCode.GENERAL)
        } finally {
            releaseSnapshot.countDown()
            executor.shutdownNow()
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue()
        }
    }

    @Test
    fun `M exclude assignment token W trackFlagExposure()`(forge: Forge) {
        // Given
        val token = "header.payload.signature"
        val context = EvaluationContext(
            "subject",
            mapOf(AssignmentAuthorization.ATTRIBUTE to token, "plan" to "premium")
        )
        val flag = forge.getForgery<PrecomputedFlag>().copy(doLog = true)
        val capturedContext = argumentCaptor<EvaluationContext>()

        // When
        testedClient.trackFlagExposure(fakeJsonKey, flag, context)

        // Then
        verify(mockProcessor).processEvent(eq(fakeJsonKey), capturedContext.capture(), eq(flag))
        assertThat(capturedContext.firstValue.attributes).isEqualTo(mapOf("plan" to "premium"))
    }

    @Test
    fun `M exclude assignment token W trackFlagEvaluationEvent()`(forge: Forge) {
        // Given
        val token = "header.payload.signature"
        val context = EvaluationContext(
            "subject",
            mapOf(AssignmentAuthorization.ATTRIBUTE to token, "plan" to "premium")
        )
        val flag = forge.getForgery<PrecomputedFlag>()
        val mockEvaluationsFeature = mock<EvaluationsFeature>()
        val capturedContext = argumentCaptor<EvaluationContext>()
        testedClient = DatadogFlagsClient(
            mockFeatureSdkCore,
            mockEvaluationsManager,
            mockFlagsRepository,
            FlagsConfiguration.Builder().trackEvaluations(true).build(),
            mockRumEvaluationLogger,
            mockProcessor,
            mockEvaluationsFeature,
            mockFlagsStateManager,
            false,
            ExperimentationOptOut()
        )

        // When
        testedClient.trackFlagEvaluationEvent(fakeJsonKey, flag, context, null)

        // Then
        verify(mockEvaluationsFeature).processEvaluation(
            eq(fakeJsonKey),
            capturedContext.capture(),
            eq(flag.variationKey),
            eq(flag.allocationKey),
            isNull(),
            isNull(),
            eq(flag.observeFullEvaluationData),
            eq(context.targetingKey),
            any()
        )
        assertThat(capturedContext.firstValue.attributes).isEqualTo(mapOf("plan" to "premium"))
    }

    @ParameterizedTest
    @CsvSource(
        "IMMEDIATE,invalid,false",
        "IMMEDIATE,invalid,true",
        "IMMEDIATE,invalid,",
        "IMMEDIATE,2026-10-21T12:00:00Z,false",
        "IMMEDIATE,2026-10-21T12:00:00Z,true",
        "IMMEDIATE,2026-10-21T12:00:00Z,",
        "FUTURE_EXPERIMENTS_ONLY,invalid,false",
        "FUTURE_EXPERIMENTS_ONLY,invalid,true",
        "FUTURE_EXPERIMENTS_ONLY,invalid,",
        "FUTURE_EXPERIMENTS_ONLY,2026-10-21T12:00:00Z,false",
        "FUTURE_EXPERIMENTS_ONLY,2026-10-21T12:00:00Z,true",
        "FUTURE_EXPERIMENTS_ONLY,2026-10-21T12:00:00Z,"
    )
    fun `M respect response consent and reuse requested identity W resolve() { current assignments unavailable }`(
        mode: ExperimentationOptOutMode,
        optOutAt: String,
        fakeConsent: Boolean?,
        forge: Forge
    ) {
        // Given
        val fakePreviousContext = EvaluationContext(forge.anAlphabeticalString())
        val fakeRequestedContext = EvaluationContext(
            "jane.doe@datadoghq.com",
            mapOf(ExperimentationOptOut.ATTRIBUTE to optOutAt)
        )
        val policy = ExperimentationOptOut(mode)
        policy.prepare(fakeRequestedContext)
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeJsonKey)) doReturn
            fakeConsent?.let { FlagEvaluationSnapshot(null, fakePreviousContext, it) }
        val expectedConsent = fakeConsent == true
        val mockEvaluationsFeature = mock<EvaluationsFeature>()
        testedClient = DatadogFlagsClient(
            featureSdkCore = mockFeatureSdkCore,
            evaluationsManager = mockEvaluationsManager,
            flagsRepository = mockFlagsRepository,
            flagsConfiguration = FlagsConfiguration.Builder().trackEvaluations(true)
                .trackExposures(false).rumIntegrationEnabled(false).build(),
            rumEvaluationLogger = mockRumEvaluationLogger,
            exposureProcessor = mockProcessor,
            evaluationsFeature = mockEvaluationsFeature,
            flagStateManager = mockFlagsStateManager,
            experimentationOptOut = policy,
            evaluationTelemetryOptOutParameterEnabled = false
        )
        val expectedError = if (optOutAt == "invalid") ErrorCode.INVALID_CONTEXT else ErrorCode.PROVIDER_NOT_READY

        // When
        val results = List(2) { testedClient.resolve(fakeJsonKey, false) }

        // Then
        results.forEach {
            assertThat(it.errorCode).isEqualTo(expectedError)
            assertThat(it.flagMetadata).containsEntry("__dd_observe_full_evaluation_data", expectedConsent)
        }
        val contextCaptor = argumentCaptor<EvaluationContext>()
        val identityCaptor = argumentCaptor<EvaluationTargetingKey>()
        verify(mockEvaluationsFeature, times(2)).processEvaluation(
            eq(fakeJsonKey),
            contextCaptor.capture(),
            isNull(),
            isNull(),
            eq(expectedError.name),
            anyOrNull(),
            eq(expectedConsent),
            eq(fakeRequestedContext.targetingKey),
            identityCaptor.capture()
        )
        assertThat(contextCaptor.allValues).containsExactly(fakeRequestedContext, fakeRequestedContext)
        assertThat(identityCaptor.firstValue.value).isEqualTo(
            if (expectedConsent) {
                fakeRequestedContext.targetingKey
            } else {
                "sha256_b4698f9b6d186781fa8dc59e533578fa2d8379a46b1cf6db85cda6aa9c99e51b"
            }
        )
        assertThat(identityCaptor.firstValue).isSameAs(identityCaptor.lastValue)
        verify(mockFlagsRepository, never()).getEvaluationContext()
        verifyNoInteractions(mockProcessor, mockRumEvaluationLogger)
    }

    @Test
    fun `M own context attributes W setEvaluationContext()`(forge: Forge) {
        // Given
        val fakeAttributes = mutableMapOf("email" to forge.anAlphabeticalString())
        val fakeContext = EvaluationContext(forge.anAlphabeticalString(), fakeAttributes)
        val fakeOriginalAttributes = fakeAttributes.toMap()

        // When
        testedClient.setEvaluationContext(fakeContext)
        fakeAttributes.clear()

        // Then
        val contextCaptor = argumentCaptor<EvaluationContext>()
        verify(mockEvaluationsManager).updateEvaluationsForContext(contextCaptor.capture(), isNull(), any())
        assertThat(contextCaptor.firstValue.attributes).isEqualTo(fakeOriginalAttributes)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M use only captured consent W resolve() {success and error}`(fakeConsent: Boolean, forge: Forge) {
        // Given
        val fakeContext = forge.getForgery<EvaluationContext>()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = "boolean",
            variationValue = "true",
            observeFullEvaluationData = !fakeConsent
        )
        whenever(mockFlagsRepository.getEvaluationSnapshot("assigned")) doReturn
            FlagEvaluationSnapshot(fakeFlag, fakeContext, fakeConsent)
        whenever(mockFlagsRepository.getEvaluationSnapshot("missing")) doReturn
            FlagEvaluationSnapshot(null, fakeContext, fakeConsent)

        // When
        val success = testedClient.resolve("assigned", false)
        val mismatch = testedClient.resolve("assigned", "default")
        val missing = testedClient.resolve("missing", false)

        // Then
        listOf(success, mismatch, missing).forEach {
            assertThat(it.flagMetadata).containsEntry("__dd_observe_full_evaluation_data", fakeConsent)
        }
        verify(mockFlagsRepository, never()).getEvaluationContext()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M reuse captured identity W resolve() {success missing and invalid flags}`(
        fakeConsent: Boolean,
        forge: Forge
    ) {
        // Given
        val fakeContext = forge.getForgery<EvaluationContext>()
        val fakeIdentity = EvaluationTargetingKey.create(fakeContext.targetingKey, fakeConsent)
        val fakeBooleanFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = "boolean",
            variationValue = "true",
            observeFullEvaluationData = !fakeConsent
        )
        val fakeInvalidFlag = fakeBooleanFlag.copy(variationType = "object", variationValue = "{invalid")
        whenever(mockFlagsRepository.getEvaluationSnapshot("assigned")) doReturn
            FlagEvaluationSnapshot(fakeBooleanFlag, fakeContext, fakeConsent, fakeIdentity)
        whenever(mockFlagsRepository.getEvaluationSnapshot("invalid")) doReturn
            FlagEvaluationSnapshot(fakeInvalidFlag, fakeContext, fakeConsent, fakeIdentity)
        whenever(mockFlagsRepository.getEvaluationSnapshot("missing")) doReturn
            FlagEvaluationSnapshot(null, fakeContext, fakeConsent, fakeIdentity)
        val mockEvaluationsFeature = mock<EvaluationsFeature>()
        testedClient = DatadogFlagsClient(
            mockFeatureSdkCore, mockEvaluationsManager, mockFlagsRepository,
            FlagsConfiguration.Builder().build(), mockRumEvaluationLogger, mockProcessor,
            mockEvaluationsFeature, mockFlagsStateManager,
            evaluationTelemetryOptOutParameterEnabled = false,
            experimentationOptOut = ExperimentationOptOut()
        )

        // When
        testedClient.resolve("assigned", false)
        testedClient.resolve("assigned", "default")
        testedClient.resolve("invalid", JSONObject())
        testedClient.resolve("missing", false)

        // Then
        val keyCaptor = argumentCaptor<String>()
        val variantCaptor = argumentCaptor<String>()
        val allocationCaptor = argumentCaptor<String>()
        val errorCaptor = argumentCaptor<String>()
        val messageCaptor = argumentCaptor<String>()
        val identityCaptor = argumentCaptor<EvaluationTargetingKey>()
        verify(mockEvaluationsFeature, times(4)).processEvaluation(
            keyCaptor.capture(), eq(fakeContext), variantCaptor.capture(), allocationCaptor.capture(),
            errorCaptor.capture(), messageCaptor.capture(), eq(fakeConsent), eq(fakeContext.targetingKey),
            identityCaptor.capture()
        )
        assertThat(keyCaptor.allValues).containsExactly("assigned", "assigned", "invalid", "missing")
        assertThat(variantCaptor.allValues).containsExactly(fakeBooleanFlag.variationKey, null, null, null)
        assertThat(allocationCaptor.allValues).containsExactly(fakeBooleanFlag.allocationKey, null, null, null)
        assertThat(errorCaptor.allValues).containsExactly(null, "TYPE_MISMATCH", "PARSE_ERROR", "FLAG_NOT_FOUND")
        assertThat(messageCaptor.allValues.first()).isNull()
        assertThat(messageCaptor.allValues.drop(1)).allSatisfy { assertThat(it).isNotBlank() }
        identityCaptor.allValues.forEach { assertThat(it).isSameAs(fakeIdentity) }
        verify(mockFlagsRepository, never()).getEvaluationContext()
    }

    @Test
    fun `M retain bridge identity W resolveBooleanValue() {native provider not ready}`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeContext = EvaluationContext("jane.doe@datadoghq.com")
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(observeFullEvaluationData = false)
        val mockEvaluationsFeature = mock<EvaluationsFeature>()
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn null
        testedClient = DatadogFlagsClient(
            mockFeatureSdkCore, mockEvaluationsManager, mockFlagsRepository,
            FlagsConfiguration.Builder().build(), mockRumEvaluationLogger, mockProcessor,
            mockEvaluationsFeature, mockFlagsStateManager,
            evaluationTelemetryOptOutParameterEnabled = false,
            experimentationOptOut = ExperimentationOptOut()
        )
        val testedProxy = _FlagsInternalProxy(testedClient)

        // When
        testedProxy.trackFlagSnapshotEvaluation(fakeFlagKey, fakeFlag, fakeContext)
        testedClient.resolveBooleanValue(fakeFlagKey, false)
        testedProxy.trackFlagSnapshotEvaluation(fakeFlagKey, fakeFlag, fakeContext)

        // Then
        val bridgeIdentityCaptor = argumentCaptor<EvaluationTargetingKey>()
        verify(mockEvaluationsFeature, times(2)).processEvaluation(
            eq(fakeFlagKey), eq(fakeContext), eq(fakeFlag.variationKey), eq(fakeFlag.allocationKey),
            isNull(), isNull(), eq(false), eq(fakeContext.targetingKey), bridgeIdentityCaptor.capture()
        )
        val missingIdentityCaptor = argumentCaptor<EvaluationTargetingKey>()
        verify(mockEvaluationsFeature).processEvaluation(
            eq(fakeFlagKey), isNull(), isNull(), isNull(), eq("PROVIDER_NOT_READY"),
            eq("Provider not ready - call setEvaluationContext() before resolving flags"),
            eq(false), isNull(), missingIdentityCaptor.capture()
        )
        assertThat(missingIdentityCaptor.firstValue.value).isNull()
        assertThat(bridgeIdentityCaptor.firstValue.value)
            .isEqualTo("sha256_b4698f9b6d186781fa8dc59e533578fa2d8379a46b1cf6db85cda6aa9c99e51b")
        assertThat(bridgeIdentityCaptor.firstValue).isSameAs(bridgeIdentityCaptor.lastValue)
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

    @Test
    fun `M avoid persistence wait W setEvaluationContext() {telemetry request cleanup}`() {
        // Given
        whenever(mockEvaluationsManager.updatePreparedEvaluations(any(), anyOrNull(), any())).thenAnswer {
            it.getArgument<() -> Unit>(2).invoke()
            null
        }
        testedClient = DatadogFlagsClient(
            mockFeatureSdkCore, mockEvaluationsManager, mockFlagsRepository,
            FlagsConfiguration.Builder().build(), mockRumEvaluationLogger, mockProcessor,
            null, mockFlagsStateManager,
            evaluationTelemetryOptOutParameterEnabled = true,
            experimentationOptOut = ExperimentationOptOut()
        )

        // When
        testedClient.setEvaluationContext(fakeDefaultContext, null, true)

        // Then
        verify(mockFlagsRepository).getAssignmentsSnapshot()
        verify(mockFlagsRepository, never()).getEvaluationContext()
        verify(mockFlagsRepository, never()).waitForPersistenceLoad()
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
            evaluationTelemetryOptOutParameterEnabled = enabled,
            experimentationOptOut = ExperimentationOptOut()
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
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeKey)) doReturn
            FlagEvaluationSnapshot(fakeFlag, fakeContext, false)
        testedClient = DatadogFlagsClient(
            mockFeatureSdkCore, mockEvaluationsManager, mockFlagsRepository,
            FlagsConfiguration.Builder().build(), mockRumEvaluationLogger, mockProcessor,
            mockEvaluationsFeature, mockFlagsStateManager,
            evaluationTelemetryOptOutParameterEnabled = enabled,
            experimentationOptOut = ExperimentationOptOut()
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
                null,
                false,
                fakeContext.targetingKey,
                EvaluationTargetingKey.create(fakeContext.targetingKey, false)
            )
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = ["different-telemetry-subject"])
    fun `M retain subject opt-out and exposure context W trackFlagSnapshotEvaluation`(
        fakeTelemetryTargetingKey: String?,
        forge: Forge
    ) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeContext = forge.getForgery<EvaluationContext>()
        val fakeOtherContext = EvaluationContext(forge.anAlphabeticalString())
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(doLog = true)
        val mockEvaluationsFeature = mock<EvaluationsFeature>()
        testedClient = DatadogFlagsClient(
            mockFeatureSdkCore, mockEvaluationsManager, mockFlagsRepository,
            FlagsConfiguration.Builder().build(), mockRumEvaluationLogger, mockProcessor,
            mockEvaluationsFeature, mockFlagsStateManager,
            evaluationTelemetryOptOutParameterEnabled = true,
            experimentationOptOut = ExperimentationOptOut()
        )
        val proxy = _FlagsInternalProxy(testedClient)
        proxy.setEvaluationContext(fakeContext, null, true)
        proxy.setEvaluationContext(fakeOtherContext, null, false)

        // When
        proxy.trackFlagSnapshotEvaluation(fakeFlagKey, fakeFlag, fakeContext, fakeTelemetryTargetingKey)

        // Then
        verify(mockProcessor).processEvent(fakeFlagKey, fakeContext, fakeFlag)
        verifyNoInteractions(mockRumEvaluationLogger, mockEvaluationsFeature)
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
            evaluationTelemetryOptOutParameterEnabled = enabled,
            experimentationOptOut = ExperimentationOptOut()
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
                null,
                false,
                fakeContext.targetingKey,
                EvaluationTargetingKey.create(fakeContext.targetingKey, false)
            )
            verify(mockEvaluationsFeature).processEvaluation(
                fakeKey, fakeContext, null, null, null, null,
                false, fakeContext.targetingKey, EvaluationTargetingKey.create(fakeContext.targetingKey, false)
            )
            verify(mockEvaluationsFeature).processEvaluation(
                fakeKey,
                fakeContext,
                null,
                null,
                "FLAG_NOT_FOUND",
                "FLAG_NOT_FOUND",
                false,
                fakeContext.targetingKey,
                EvaluationTargetingKey.create(fakeContext.targetingKey, false)
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
            evaluationTelemetryOptOutParameterEnabled = true,
            experimentationOptOut = ExperimentationOptOut()
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
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn
            FlagEvaluationSnapshot(fakeFlag, fakeContext, false)

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
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn
            FlagEvaluationSnapshot(null, fakeDefaultContext, false)

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
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn null

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
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn
            FlagEvaluationSnapshot(null, fakeEvaluationContext, false)

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
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn
            FlagEvaluationSnapshot(fakeFlag, fakeEvaluationContext, false)

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
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn
            FlagEvaluationSnapshot(fakeFlag, fakeEvaluationContext, false)

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
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn
            FlagEvaluationSnapshot(null, fakeDefaultContext, false)

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
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn null

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
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn
            FlagEvaluationSnapshot(fakeFlag, fakeContext, false)

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
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn
            FlagEvaluationSnapshot(null, fakeDefaultContext, false)

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
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn null

        // When
        val result = testedClient.resolveIntValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
        verifyNoInteractions(mockProcessor)
        verifyNoInteractions(mockRumEvaluationLogger)
    }

    @ParameterizedTest
    @CsvSource(
        "number, 5, 5",
        "number, 5.0, 5",
        "number, 1e3, 1000",
        "number, -7.00, -7",
        "number, 0.0, 0",
        "number, -0.0, 0",
        "number, 2147483647.0, 2147483647",
        "number, -2147483648, -2147483648",
        "number, -2147483648.0, -2147483648",
        "integer, 7, 7",
        "integer, 5.0, 5",
        "float, 5, 5",
        "float, 1e3, 1000",
        "number, 0e5, 0",
        "number, 10e-1, 1",
        "number, 1.5e1, 15",
        "number, 5.00000000000000000000000000000000000000000000000000000000000000, 5",
        "number, 00000000000000000000000000000000000000000000000000000000000000005, 5"
    )
    fun `M return value W resolve() { numeric flag, whole value, Int default }`(
        fakeVariationType: String,
        fakeVariationValue: String,
        expectedValue: Int,
        forge: Forge
    ) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anInt(min = 2000)
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = fakeVariationType,
            variationValue = fakeVariationValue
        )
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn
            FlagEvaluationSnapshot(fakeFlag, fakeDefaultContext, false)

        // When
        val result = testedClient.resolve(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result.value).isEqualTo(expectedValue)
        assertThat(result.errorCode).isNull()
    }

    @ParameterizedTest
    @CsvSource(
        "number, 1.2",
        "number, -0.5",
        "number, 5.000001",
        "number, 999999999999999.99",
        "number, 1e-400",
        "float, 1.2",
        "integer, 1.2"
    )
    fun `M return default with TYPE_MISMATCH W resolve() { numeric flag, fractional value, Int default }`(
        fakeVariationType: String,
        fakeVariationValue: String,
        forge: Forge
    ) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anInt()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = fakeVariationType,
            variationValue = fakeVariationValue,
            doLog = true
        )
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn
            FlagEvaluationSnapshot(fakeFlag, fakeDefaultContext, false)

        // When
        val result = testedClient.resolve(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result.value).isEqualTo(fakeDefaultValue)
        assertThat(result.errorCode).isEqualTo(ErrorCode.TYPE_MISMATCH)
        assertThat(result.errorMessage).isEqualTo(
            "Flag '$fakeFlagKey': Flag has type '$fakeVariationType' with fractional value " +
                "'$fakeVariationValue' but Int was requested"
        )
        verifyNoInteractions(mockProcessor)
    }

    @ParameterizedTest
    @CsvSource(
        "number, 2147483648",
        "number, 3000000000",
        "number, 1e20",
        "number, -2147483649",
        "number, -1e20",
        "number, 100e2147483647",
        "number, 1e2147483648",
        "integer, 2147483648",
        "float, 2147483648.0"
    )
    fun `M return default with TYPE_MISMATCH W resolve() { numeric flag, value outside Int range, Int default }`(
        fakeVariationType: String,
        fakeVariationValue: String,
        forge: Forge
    ) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anInt()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = fakeVariationType,
            variationValue = fakeVariationValue,
            doLog = true
        )
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn
            FlagEvaluationSnapshot(fakeFlag, fakeDefaultContext, false)

        // When
        val result = testedClient.resolve(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result.value).isEqualTo(fakeDefaultValue)
        assertThat(result.errorCode).isEqualTo(ErrorCode.TYPE_MISMATCH)
        assertThat(result.errorMessage).isEqualTo(
            "Flag '$fakeFlagKey': Flag has type '$fakeVariationType' with value " +
                "'$fakeVariationValue' outside Int range but Int was requested"
        )
        verifyNoInteractions(mockProcessor)
    }

    @ParameterizedTest
    @CsvSource(
        "number, not-a-number",
        "integer, not-a-number",
        "float, not-a-number",
        "number, ''",
        "number, ' 5'",
        "number, NaN",
        "number, Infinity",
        "number, 5f",
        "number, 0x10",
        "number, 1e99999999999",
        "number, 1e-2147483648",
        "number, 1e2147483649",
        "number, 10000000000000000000000000000000000000000000000000000000000000000.0",
        "number, 5.000000000000000000000000000000000000000000000000000000000000000",
        "float, NaN"
    )
    fun `M return default with PARSE_ERROR W resolve() { malformed numeric value, Int default }`(
        fakeVariationType: String,
        fakeVariationValue: String,
        forge: Forge
    ) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anInt()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = fakeVariationType,
            variationValue = fakeVariationValue,
            doLog = true
        )
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn
            FlagEvaluationSnapshot(fakeFlag, fakeDefaultContext, false)

        // When
        val result = testedClient.resolve(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result.value).isEqualTo(fakeDefaultValue)
        assertThat(result.errorCode).isEqualTo(ErrorCode.PARSE_ERROR)
        assertThat(result.errorMessage)
            .isEqualTo("Flag '$fakeFlagKey': Failed to parse value '$fakeVariationValue' as Int")
        verifyNoInteractions(mockProcessor)
    }

    @ParameterizedTest
    @ValueSource(strings = ["boolean", "string", "object"])
    fun `M return default with TYPE_MISMATCH W resolve() { non-numeric flag, Int default }`(
        fakeVariationType: String,
        forge: Forge
    ) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anInt(min = 6)
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = fakeVariationType,
            variationValue = "5",
            doLog = true
        )
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn
            FlagEvaluationSnapshot(fakeFlag, fakeDefaultContext, false)

        // When
        val result = testedClient.resolve(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result.value).isEqualTo(fakeDefaultValue)
        assertThat(result.errorCode).isEqualTo(ErrorCode.TYPE_MISMATCH)
        assertThat(result.errorMessage)
            .isEqualTo("Flag '$fakeFlagKey': Flag has type '$fakeVariationType' but Int was requested")
        verifyNoInteractions(mockProcessor)
    }

    // endregion

    // region resolveDoubleValue()

    @Test
    fun `M return default value W resolveDoubleValue() {flag does not exist}`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aDouble()
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn
            FlagEvaluationSnapshot(null, fakeDefaultContext, false)

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
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn null

        // When
        val result = testedClient.resolveDoubleValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
        verifyNoInteractions(mockProcessor)
        verifyNoInteractions(mockRumEvaluationLogger)
    }

    @ParameterizedTest
    @CsvSource(
        "number, 5, 5.0",
        "number, 1.5, 1.5",
        "integer, 7, 7.0",
        "integer, 5.0, 5.0",
        "float, 5, 5.0",
        "number, 9007199254740992, 9007199254740992.0",
        "number, -9223372036854775808, -9223372036854775808.0",
        "float, 18446744073709551616, 18446744073709551616.0",
        "number, 1.20000000000000001, 1.2",
        "number, 4.9e-324, 4.9e-324",
        "number, 0e-400, 0.0",
        "number, -0.0, -0.0",
        "float, 9007199254740993.5, 9007199254740994.0",
        "number, 9007199254740992.00000000000000000000000000000000000000000000000, 9007199254740992.0",
        "number, 1.000000000000000000000000000000000000000000000000000000000000000, 1.0"
    )
    fun `M return value W resolve() { numeric flag, Double default }`(
        fakeVariationType: String,
        fakeVariationValue: String,
        expectedValue: Double,
        forge: Forge
    ) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aDouble(min = 100.0)
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = fakeVariationType,
            variationValue = fakeVariationValue
        )
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn
            FlagEvaluationSnapshot(fakeFlag, fakeDefaultContext, false)

        // When
        val result = testedClient.resolve(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result.value).isEqualTo(expectedValue)
        assertThat(result.errorCode).isNull()
    }

    @ParameterizedTest
    @CsvSource(
        "number, 9007199254740993",
        "number, -9007199254740993",
        "number, 9223372036854775807",
        "number, 9007199254740993.0",
        "number, 1e400",
        "number, 1e2147483648",
        "number, 1e300",
        "integer, 9007199254740993",
        "float, 9007199254740993",
        "float, 1e300"
    )
    fun `M return default with TYPE_MISMATCH W resolve() { whole value Double can't hold, Double default }`(
        fakeVariationType: String,
        fakeVariationValue: String,
        forge: Forge
    ) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aDouble()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = fakeVariationType,
            variationValue = fakeVariationValue,
            doLog = true
        )
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn
            FlagEvaluationSnapshot(fakeFlag, fakeDefaultContext, false)

        // When
        val result = testedClient.resolve(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result.value).isEqualTo(fakeDefaultValue)
        assertThat(result.errorCode).isEqualTo(ErrorCode.TYPE_MISMATCH)
        assertThat(result.errorMessage).isEqualTo(
            "Flag '$fakeFlagKey': Flag has type '$fakeVariationType' with whole value " +
                "'$fakeVariationValue' that Double can't hold exactly but Double was requested"
        )
        verifyNoInteractions(mockProcessor)
    }

    @ParameterizedTest
    @CsvSource(
        "number, 1e-400",
        "number, -1e-400",
        "float, 2e-324",
        "integer, 1e-400"
    )
    fun `M return default with TYPE_MISMATCH W resolve() { nonzero value that rounds to zero, Double default }`(
        fakeVariationType: String,
        fakeVariationValue: String,
        forge: Forge
    ) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aDouble()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = fakeVariationType,
            variationValue = fakeVariationValue,
            doLog = true
        )
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn
            FlagEvaluationSnapshot(fakeFlag, fakeDefaultContext, false)

        // When
        val result = testedClient.resolve(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result.value).isEqualTo(fakeDefaultValue)
        assertThat(result.errorCode).isEqualTo(ErrorCode.TYPE_MISMATCH)
        assertThat(result.errorMessage).isEqualTo(
            "Flag '$fakeFlagKey': Flag has type '$fakeVariationType' with nonzero value " +
                "'$fakeVariationValue' that Double rounds to zero but Double was requested"
        )
        verifyNoInteractions(mockProcessor)
    }

    @ParameterizedTest
    @CsvSource(
        "number, not-a-number",
        "number, NaN",
        "number, Infinity",
        "float, -Infinity",
        "number, 10000000000000000000000000000000000000000000000000000000000000000.0",
        "number, 9007199254740992.0000000000000000000000000000000000000000000000001",
        "number, 1e2147483649",
        "number, 0.000000000000000000000000000000000000000000000000000000000000000"
    )
    fun `M return default with PARSE_ERROR W resolve() { malformed numeric value, Double default }`(
        fakeVariationType: String,
        fakeVariationValue: String,
        forge: Forge
    ) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aDouble()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = fakeVariationType,
            variationValue = fakeVariationValue,
            doLog = true
        )
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn
            FlagEvaluationSnapshot(fakeFlag, fakeDefaultContext, false)

        // When
        val result = testedClient.resolve(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result.value).isEqualTo(fakeDefaultValue)
        assertThat(result.errorCode).isEqualTo(ErrorCode.PARSE_ERROR)
        assertThat(result.errorMessage)
            .isEqualTo("Flag '$fakeFlagKey': Failed to parse value '$fakeVariationValue' as Double")
        verifyNoInteractions(mockProcessor)
    }

    @ParameterizedTest
    @ValueSource(strings = ["boolean", "string", "object"])
    fun `M return default with TYPE_MISMATCH W resolve() { non-numeric flag, Double default }`(
        fakeVariationType: String,
        forge: Forge
    ) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aDouble(min = 6.0)
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = fakeVariationType,
            variationValue = "5",
            doLog = true
        )
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn
            FlagEvaluationSnapshot(fakeFlag, fakeDefaultContext, false)

        // When
        val result = testedClient.resolve(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result.value).isEqualTo(fakeDefaultValue)
        assertThat(result.errorCode).isEqualTo(ErrorCode.TYPE_MISMATCH)
        assertThat(result.errorMessage)
            .isEqualTo("Flag '$fakeFlagKey': Flag has type '$fakeVariationType' but Double was requested")
        verifyNoInteractions(mockProcessor)
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
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn
            FlagEvaluationSnapshot(fakeFlag, fakeContext, false)

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
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn
            FlagEvaluationSnapshot(fakeFlag, fakeContext, false)

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
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn
            FlagEvaluationSnapshot(fakeFlag, fakeContext, false)

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
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn
            FlagEvaluationSnapshot(null, fakeDefaultContext, false)

        // When
        val result = testedClient.resolveStructureValue(fakeFlagKey, fakeJsonDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeJsonDefaultValue)
    }

    @Test
    fun `M return default value W resolveStructureValue() { provider not ready }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn null

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
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn
            FlagEvaluationSnapshot(null, fakeDefaultContext, false)

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
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn null

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
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn
            FlagEvaluationSnapshot(null, fakeDefaultContext, false)

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
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn
            FlagEvaluationSnapshot(fakeFlag, fakeContext, false)

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
        whenever(customRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn
            FlagEvaluationSnapshot(fakeFlag, fakeContext, false)

        testedClient = DatadogFlagsClient(
            featureSdkCore = mockFeatureSdkCore,
            evaluationsManager = mockEvaluationsManager,
            flagsRepository = customRepository,
            flagsConfiguration = forge.getForgery(),
            rumEvaluationLogger = mockRumEvaluationLogger,
            exposureProcessor = mockProcessor,
            evaluationsFeature = null,
            flagStateManager = mockFlagsStateManager,
            evaluationTelemetryOptOutParameterEnabled = false,
            experimentationOptOut = ExperimentationOptOut()
        )

        // When
        val result = testedClient.resolveStringValue(fakeFlagKey, fakeDefaultValue)

        // Then

        assertThat(result).isEqualTo(fakeFlagValue)

        verify(customRepository).getEvaluationSnapshot(fakeFlagKey)
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
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn
            FlagEvaluationSnapshot(fakeFlag, fakeContext, false)

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
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn
            FlagEvaluationSnapshot(fakeFlag, fakeContext, false)

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
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn
            FlagEvaluationSnapshot(fakeFlag, fakeContext, false)

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
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn
            FlagEvaluationSnapshot(fakeFlag, fakeContext, false)

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
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn
            FlagEvaluationSnapshot(fakeFlag, fakeContext, false)

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
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn
            FlagEvaluationSnapshot(fakeFlag, fakeContext, false)

        // When
        val result = testedClient.resolve(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result.value.toString()).isEqualTo(fakeDefaultValue.toString())
        assertThat(result.variant).isNull()
        assertThat(result.reason).isEqualTo(ResolutionReason.ERROR)
        assertThat(result.errorCode).isEqualTo(ErrorCode.PARSE_ERROR)
        assertThat(result.errorMessage).contains("Flag '$fakeFlagKey'")
        assertThat(result.errorMessage).contains("Failed to parse value")
        assertThat(result.flagMetadata).containsEntry("__dd_observe_full_evaluation_data", false)

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

        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn
            FlagEvaluationSnapshot(null, fakeContext, false)

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
            evaluationTelemetryOptOutParameterEnabled = false,
            experimentationOptOut = ExperimentationOptOut()
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
            errorMessage = eq("Flag not found"),
            observeFullEvaluationData = eq(false),
            telemetryTargetingKey = eq(fakeContext.targetingKey),
            preparedTargetingKey = eq(EvaluationTargetingKey.create(fakeContext.targetingKey, false))
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
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn
            FlagEvaluationSnapshot(fakeFlag, fakeContext, false)

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
        whenever(mockFlagsRepository.getEvaluationSnapshot(key)) doReturn null

        // When
        assertThat(testedClient.resolveBooleanValue(key, false)).isFalse()

        // Then: no second disk wait or context from a later repository state.
        verify(mockFlagsRepository).waitForPersistenceLoad()
        verify(mockFlagsRepository).getEvaluationSnapshot(key)
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
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn
            FlagEvaluationSnapshot(fakeFlag, fakeContext, false)

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
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn
            FlagEvaluationSnapshot(fakeFlag, fakeContext, false)

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
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn
            FlagEvaluationSnapshot(fakeFlag, fakeContext, false)

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
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn
            FlagEvaluationSnapshot(fakeFlag, fakeContext, false)

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
        val fakeFlagValue = forge.aDouble(min = -1_000_000.0, max = 1_000_000.0)
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

        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey1)) doReturn
            FlagEvaluationSnapshot(numberFlag, fakeContext, false)
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey2)) doReturn
            FlagEvaluationSnapshot(floatFlag, fakeContext, false)
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey3)) doReturn
            FlagEvaluationSnapshot(intFlag, fakeContext, false)

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
    fun `M log warning and return default W resolveIntValue() { number flag, fractional value }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anInt()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.NUMBER.value,
            variationValue = "1.2",
            doLog = true
        )
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeFlagKey)) doReturn
            FlagEvaluationSnapshot(fakeFlag, fakeDefaultContext, false)

        // When
        val result = testedClient.resolveIntValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
        verifyNoInteractions(mockProcessor)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            "Flag '$fakeFlagKey': Flag has type 'number' with fractional value '1.2' but Int was requested"
        )
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
    fun `M report request outcome W setEvaluationContext() {internal proxy}`(forge: Forge) {
        // Given
        val fakeContext = EvaluationContext(forge.anAlphabeticalString())
        val fakeError = RuntimeException(forge.anAlphabeticalString())
        var completionState: FlagsClientState? = null
        var completionError: Throwable? = null
        val testedProxy = _FlagsInternalProxy(testedClient)

        // When
        testedProxy.setEvaluationContext(fakeContext, null) { state, error ->
            completionState = state
            completionError = error
        }
        val callbackCaptor = argumentCaptor<EvaluationContextCallback>()
        verify(mockEvaluationsManager).updateEvaluationsForContext(
            eq(fakeContext),
            callbackCaptor.capture(),
            any()
        )
        val callback = checkNotNull(callbackCaptor.firstValue as? EvaluationContextOutcomeCallback) {
            "Expected a state-aware callback"
        }
        callback.onFailure(fakeError, FlagsClientState.Stale)

        // Then
        assertThat(completionState).isEqualTo(FlagsClientState.Stale)
        assertThat(completionError).isSameAs(fakeError)
    }

    @ParameterizedTest
    @EnumSource(ExperimentationOptOutMode::class)
    fun `M validate one assignment snapshot W getFlagAssignmentsSnapshot() {context changes during read}`(
        mode: ExperimentationOptOutMode
    ) {
        // Given
        val policy = ExperimentationOptOut(mode)
        policy.prepare(fakeDefaultContext)
        val fakeFlags = mapOf(fakeJsonKey to mock<PrecomputedFlag>())
        whenever(mockFlagsRepository.getAssignmentsSnapshot()).thenAnswer {
            policy.prepare(EvaluationContext("new-subject"))
            FlagsAssignmentsSnapshot(fakeFlags, fakeDefaultContext)
        }
        testedClient = DatadogFlagsClient(
            mockFeatureSdkCore, mockEvaluationsManager, mockFlagsRepository, FlagsConfiguration.Builder().build(),
            mockRumEvaluationLogger, mockProcessor, null, mockFlagsStateManager, true, policy
        )

        // When
        val snapshot = testedClient.getFlagAssignmentsSnapshot()

        // Then
        assertThat(snapshot).isEqualTo(if (mode == ExperimentationOptOutMode.DISABLED) fakeFlags else emptyMap())
        verify(mockFlagsRepository).waitForPersistenceLoad()
        verify(mockFlagsRepository).getAssignmentsSnapshot()
        verify(mockFlagsRepository, never()).getEvaluationContext()
    }

    @Test
    fun `M delegate to repository W getFlagAssignmentsSnapshot()`() {
        // Given
        val mockSnapshot = mapOf<String, PrecomputedFlag>(
            "flag1" to mock(),
            "flag2" to mock()
        )
        whenever(mockFlagsRepository.getAssignmentsSnapshot()) doReturn
            FlagsAssignmentsSnapshot(mockSnapshot, fakeDefaultContext)

        // When
        val result = testedClient.getFlagAssignmentsSnapshot()

        // Then
        assertThat(result).isSameAs(mockSnapshot)
        verify(mockFlagsRepository).getAssignmentsSnapshot()
        verify(mockFlagsRepository).waitForPersistenceLoad()
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
            evaluationTelemetryOptOutParameterEnabled = false,
            experimentationOptOut = ExperimentationOptOut()
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
            evaluationTelemetryOptOutParameterEnabled = false,
            experimentationOptOut = ExperimentationOptOut()
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
    @ParameterizedTest
    @EnumSource(ExperimentationOptOutMode::class, names = ["IMMEDIATE", "FUTURE_EXPERIMENTS_ONLY"])
    fun `M track invalid context error W trackFlagEvaluationEvent() {requested context invalid}`(
        mode: ExperimentationOptOutMode,
        forge: Forge
    ) {
        // Given
        val fakeKey = forge.anAlphabeticalString()
        val fakeRequested = EvaluationContext(
            forge.anAlphabeticalString(),
            mapOf(ExperimentationOptOut.ATTRIBUTE to "invalid")
        )
        val policy = ExperimentationOptOut(mode)
        policy.prepare(fakeRequested)
        val mockEvaluationsFeature = mock<EvaluationsFeature>()
        testedClient = DatadogFlagsClient(
            featureSdkCore = mockFeatureSdkCore,
            evaluationsManager = mockEvaluationsManager,
            flagsRepository = mockFlagsRepository,
            flagsConfiguration = FlagsConfiguration.Builder().trackEvaluations(true).build(),
            rumEvaluationLogger = mockRumEvaluationLogger,
            exposureProcessor = mockProcessor,
            evaluationsFeature = mockEvaluationsFeature,
            flagStateManager = mockFlagsStateManager,
            evaluationTelemetryOptOutParameterEnabled = false,
            experimentationOptOut = policy
        )

        // When
        testedClient.trackFlagEvaluationEvent(fakeKey, null, fakeRequested, "INVALID_CONTEXT")

        // Then
        verify(mockEvaluationsFeature).processEvaluation(
            eq(fakeKey),
            eq(fakeRequested),
            isNull(),
            isNull(),
            eq("INVALID_CONTEXT"),
            eq("INVALID_CONTEXT"),
            eq(false),
            eq(fakeRequested.targetingKey),
            eq(EvaluationTargetingKey.create(fakeRequested.targetingKey, false))
        )
    }

    @ParameterizedTest
    @CsvSource("IMMEDIATE,false", "IMMEDIATE,true", "FUTURE_EXPERIMENTS_ONLY,false", "FUTURE_EXPERIMENTS_ONLY,true")
    fun `M serve and track W bridge read and track paths {enabled mode, current context installed}`(
        mode: ExperimentationOptOutMode,
        fakeConsent: Boolean,
        forge: Forge
    ) {
        // Given
        val fakeKey = forge.anAlphabeticalString()
        val fakeContext = EvaluationContext(forge.anAlphabeticalString())
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(doLog = true, observeFullEvaluationData = fakeConsent)
        val policy = ExperimentationOptOut(mode)
        policy.prepare(fakeContext)
        whenever(mockFlagsRepository.getAssignmentsSnapshot()) doReturn
            FlagsAssignmentsSnapshot(mapOf(fakeKey to fakeFlag), fakeContext)
        val mockEvaluationsFeature = mock<EvaluationsFeature>()
        testedClient = DatadogFlagsClient(
            featureSdkCore = mockFeatureSdkCore,
            evaluationsManager = mockEvaluationsManager,
            flagsRepository = mockFlagsRepository,
            flagsConfiguration = FlagsConfiguration.Builder().trackEvaluations(true)
                .trackExposures(true).rumIntegrationEnabled(true).build(),
            rumEvaluationLogger = mockRumEvaluationLogger,
            exposureProcessor = mockProcessor,
            evaluationsFeature = mockEvaluationsFeature,
            flagStateManager = mockFlagsStateManager,
            experimentationOptOut = policy,
            evaluationTelemetryOptOutParameterEnabled = false
        )

        // When
        val snapshot = testedClient.getFlagAssignmentsSnapshot()
        testedClient.trackFlagExposure(fakeKey, fakeFlag, fakeContext)
        testedClient.trackFlagEvaluationEvent(fakeKey, fakeFlag, fakeContext, null)
        testedClient.trackFlagRumEvaluation(fakeKey, fakeFlag.variationKey, fakeContext)

        // Then
        assertThat(snapshot).containsEntry(fakeKey, fakeFlag)
        verify(mockProcessor).processEvent(fakeKey, fakeContext, fakeFlag)
        verify(mockEvaluationsFeature).processEvaluation(
            eq(fakeKey),
            eq(fakeContext),
            eq(fakeFlag.variationKey),
            eq(fakeFlag.allocationKey),
            isNull(),
            isNull(),
            eq(fakeFlag.observeFullEvaluationData),
            eq(fakeContext.targetingKey),
            eq(EvaluationTargetingKey.create(fakeContext.targetingKey, fakeFlag.observeFullEvaluationData))
        )
        verify(mockRumEvaluationLogger).logEvaluation(fakeKey, fakeFlag.variationKey)
    }

    @ParameterizedTest
    @EnumSource(ExperimentationOptOutMode::class)
    fun `M return assignments only for their context W getFlagAssignmentsSnapshot(context)`(
        mode: ExperimentationOptOutMode,
        forge: Forge
    ) {
        // Given
        val fakeRequestedContext = EvaluationContext("requested-" + forge.anAlphabeticalString())
        val fakeNewerContext = EvaluationContext("newer-" + forge.anAlphabeticalString())
        val fakeFlags = mapOf(forge.anAlphabeticalString() to forge.getForgery<PrecomputedFlag>())
        val policy = ExperimentationOptOut(mode)
        policy.prepare(fakeNewerContext)
        whenever(mockFlagsRepository.getAssignmentsSnapshot()) doReturn
            FlagsAssignmentsSnapshot(fakeFlags, fakeNewerContext)
        testedClient = DatadogFlagsClient(
            featureSdkCore = mockFeatureSdkCore,
            evaluationsManager = mockEvaluationsManager,
            flagsRepository = mockFlagsRepository,
            flagsConfiguration = FlagsConfiguration.Builder().build(),
            rumEvaluationLogger = mockRumEvaluationLogger,
            exposureProcessor = mockProcessor,
            evaluationsFeature = null,
            flagStateManager = mockFlagsStateManager,
            experimentationOptOut = policy,
            evaluationTelemetryOptOutParameterEnabled = false
        )

        // When
        val replacedResult = testedClient.getFlagAssignmentsSnapshot(fakeRequestedContext)
        val currentResult = testedClient.getFlagAssignmentsSnapshot(fakeNewerContext)

        // Then
        assertThat(replacedResult).isNull()
        assertThat(currentResult).isEqualTo(fakeFlags)
    }

    @ParameterizedTest
    @EnumSource(ExperimentationOptOutMode::class)
    fun `M return assignments only while their request is current W getFlagAssignmentsSnapshot(context)`(
        mode: ExperimentationOptOutMode,
        forge: Forge
    ) {
        // Given
        val fakeRequestedContext = EvaluationContext("requested-" + forge.anAlphabeticalString())
        val fakeNewerContext = EvaluationContext("newer-" + forge.anAlphabeticalString())
        val fakeFlags = mapOf(forge.anAlphabeticalString() to forge.getForgery<PrecomputedFlag>())
        val policy = ExperimentationOptOut(mode)
        policy.prepare(fakeRequestedContext)
        whenever(mockFlagsRepository.getAssignmentsSnapshot()) doReturn
            FlagsAssignmentsSnapshot(fakeFlags, fakeRequestedContext)
        testedClient = DatadogFlagsClient(
            featureSdkCore = mockFeatureSdkCore,
            evaluationsManager = mockEvaluationsManager,
            flagsRepository = mockFlagsRepository,
            flagsConfiguration = FlagsConfiguration.Builder().build(),
            rumEvaluationLogger = mockRumEvaluationLogger,
            exposureProcessor = mockProcessor,
            evaluationsFeature = null,
            flagStateManager = mockFlagsStateManager,
            experimentationOptOut = policy,
            evaluationTelemetryOptOutParameterEnabled = false
        )
        policy.prepare(fakeNewerContext)

        // When
        val result = testedClient.getFlagAssignmentsSnapshot(fakeRequestedContext)

        // Then
        if (mode == ExperimentationOptOutMode.DISABLED) {
            assertThat(result).isEqualTo(fakeFlags)
        } else {
            assertThat(result).isNull()
        }
    }

    @ParameterizedTest
    @EnumSource(ExperimentationOptOutMode::class)
    fun `M preserve not ready message W resolve() {mode varies}`(mode: ExperimentationOptOutMode) {
        // Given
        whenever(mockFlagsRepository.getEvaluationSnapshot(fakeJsonKey)) doReturn null
        testedClient = DatadogFlagsClient(
            mockFeatureSdkCore, mockEvaluationsManager, mockFlagsRepository,
            FlagsConfiguration.Builder().build(), mockRumEvaluationLogger, mockProcessor,
            null, mockFlagsStateManager, false, ExperimentationOptOut(mode)
        )

        // When
        val result = testedClient.resolve(fakeJsonKey, false)

        // Then
        val expectedMessage = if (mode == ExperimentationOptOutMode.DISABLED) {
            "Provider not ready - call setEvaluationContext() before resolving flags"
        } else {
            "Assignments for the current evaluation context are not available."
        }
        assertThat(result.errorCode).isEqualTo(ErrorCode.PROVIDER_NOT_READY)
        assertThat(result.errorMessage).isEqualTo("Flag '$fakeJsonKey': $expectedMessage")
    }
}
