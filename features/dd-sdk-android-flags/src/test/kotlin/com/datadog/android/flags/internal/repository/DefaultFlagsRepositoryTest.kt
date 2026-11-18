/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.repository

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.datastore.DataStoreHandler
import com.datadog.android.api.storage.datastore.DataStoreReadCallback
import com.datadog.android.core.persistence.datastore.DataStoreContent
import com.datadog.android.flags.ExperimentationOptOutMode
import com.datadog.android.flags.internal.aggregation.EvaluationPrivacy
import com.datadog.android.flags.internal.model.FlagEvaluationSnapshot
import com.datadog.android.flags.internal.model.FlagsStateEntry
import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.datadog.android.flags.internal.selection.CanonicalFlagSelection
import com.datadog.android.flags.internal.selection.FlagSelection
import com.datadog.android.flags.internal.selection.FlagSelectionCanonicalizer
import com.datadog.android.flags.internal.selection.FlagSelectionResult
import com.datadog.android.flags.model.EvaluationContext
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
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class DefaultFlagsRepositoryTest {

    @Mock
    lateinit var mockFeatureSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockDataStore: DataStoreHandler

    private lateinit var testedRepository: DefaultFlagsRepository

    private lateinit var testContext: EvaluationContext
    private lateinit var singleFlagMap: Map<String, PrecomputedFlag>
    private lateinit var multipleFlagsMap: Map<String, PrecomputedFlag>

    @BeforeEach
    fun `set up`(forge: Forge) {
        whenever(mockFeatureSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockFeatureSdkCore.timeProvider) doReturn mock()
        whenever(
            mockDataStore.value<FlagsStateEntry>(
                key = any(),
                version = anyOrNull(),
                callback = any(),
                deserializer = any()
            )
        ) doAnswer {
            val callback = it.getArgument<DataStoreReadCallback<FlagsStateEntry>>(2)
            callback.onFailure()
            null
        }

        testedRepository = DefaultFlagsRepository(
            featureSdkCore = mockFeatureSdkCore,
            dataStore = mockDataStore,
            flagSelection = null,
            instanceName = "default"
        )

        // Setup test fixtures for hasConfiguration tests
        testContext = EvaluationContext(forge.anAlphabeticalString(), emptyMap())

        singleFlagMap = mapOf(
            forge.anAlphabeticalString() to PrecomputedFlag(
                variationType = "string",
                variationValue = forge.anAlphabeticalString(),
                doLog = false,
                allocationKey = forge.anAlphabeticalString(),
                variationKey = forge.anAlphabeticalString(),
                extraLogging = JSONObject(),
                reason = "DEFAULT"
            )
        )

        multipleFlagsMap = mapOf(
            forge.anAlphabeticalString() to PrecomputedFlag(
                variationType = "string",
                variationValue = forge.anAlphabeticalString(),
                doLog = false,
                allocationKey = forge.anAlphabeticalString(),
                variationKey = forge.anAlphabeticalString(),
                extraLogging = JSONObject(),
                reason = "DEFAULT"
            ),
            forge.anAlphabeticalString() to PrecomputedFlag(
                variationType = "boolean",
                variationValue = "true",
                doLog = false,
                allocationKey = forge.anAlphabeticalString(),
                variationKey = forge.anAlphabeticalString(),
                extraLogging = JSONObject(),
                reason = "TARGETING_MATCH"
            )
        )
    }

    @Test
    fun `M return snapshot without waiting W getEvaluationSnapshot() {persistence pending}`(forge: Forge) {
        // Given
        doAnswer { null }.whenever(mockDataStore).value<FlagsStateEntry>(
            key = any(),
            version = anyOrNull(),
            callback = any(),
            deserializer = any()
        )
        val testedRepository = DefaultFlagsRepository(
            featureSdkCore = mockFeatureSdkCore,
            dataStore = mockDataStore,
            flagSelection = null,
            instanceName = forge.anAlphabeticalString(),
            persistenceLoadTimeoutMs = TimeUnit.SECONDS.toMillis(10)
        )
        val fakeKey = forge.anAlphabeticalString()
        val executor = Executors.newSingleThreadExecutor()

        try {
            // When
            val snapshot = executor.submit<FlagEvaluationSnapshot?> {
                testedRepository.getEvaluationSnapshot(fakeKey)
            }

            // Then
            assertThat(snapshot.get(1, TimeUnit.SECONDS)).isNull()
        } finally {
            executor.shutdownNow()
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue()
        }
    }

    @Test
    fun `M retain context W getEvaluationSnapshot() {flag missing}`(forge: Forge) {
        // Given
        testedRepository.installFlagsAndContext(testContext, emptyMap()).complete {}

        // When
        val result = testedRepository.getEvaluationSnapshot(forge.anAlphabeticalString())

        // Then
        assertThat(result?.flag).isNull()
        assertThat(result?.context).isEqualTo(testContext)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M reuse telemetry identity across flags W getEvaluationSnapshot()`(fakeConsent: Boolean, forge: Forge) {
        // Given
        testedRepository.installFlagsAndContext(
            testContext,
            multipleFlagsMap,
            observeFullEvaluationData = fakeConsent
        ).complete {
        }
        val fakeMissingKey = forge.anAlphabeticalString() + multipleFlagsMap.keys.joinToString()

        // When
        val snapshots = (multipleFlagsMap.keys + fakeMissingKey).map {
            checkNotNull(testedRepository.getEvaluationSnapshot(it))
        }
        val repeated = checkNotNull(testedRepository.getEvaluationSnapshot(multipleFlagsMap.keys.first()))

        // Then
        val preparedIdentity = snapshots.first().telemetryTargetingKey
        assertThat(preparedIdentity.value)
            .isEqualTo(EvaluationPrivacy.targetingKey(testContext.targetingKey, fakeConsent))
        snapshots.forEach { assertThat(it.telemetryTargetingKey).isSameAs(preparedIdentity) }
        assertThat(repeated.telemetryTargetingKey).isSameAs(preparedIdentity)
        assertThat(snapshots.last().flag).isNull()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M replace telemetry identity and retain old snapshots W installFlagsAndContext() + complete()`(
        fakeConsent: Boolean,
        forge: Forge
    ) {
        // Given
        val fakeKey = multipleFlagsMap.keys.first()
        val fakeNextContext = testContext.copy(targetingKey = testContext.targetingKey + forge.anAlphabeticalString())
        testedRepository.installFlagsAndContext(
            testContext,
            multipleFlagsMap,
            observeFullEvaluationData = fakeConsent
        ).complete {
        }
        val initial = checkNotNull(testedRepository.getEvaluationSnapshot(fakeKey))

        // When
        testedRepository.installFlagsAndContext(
            testContext,
            multipleFlagsMap,
            observeFullEvaluationData = !fakeConsent
        ).complete {
        }
        val consentChanged = checkNotNull(testedRepository.getEvaluationSnapshot(fakeKey))
        testedRepository.installFlagsAndContext(
            fakeNextContext,
            multipleFlagsMap,
            observeFullEvaluationData = !fakeConsent
        ).complete {
        }
        val contextChanged = checkNotNull(testedRepository.getEvaluationSnapshot(fakeKey))

        // Then
        assertThat(initial.telemetryTargetingKey.value)
            .isEqualTo(EvaluationPrivacy.targetingKey(testContext.targetingKey, fakeConsent))
        assertThat(initial.observeFullEvaluationData).isEqualTo(fakeConsent)
        assertThat(consentChanged.telemetryTargetingKey.value)
            .isEqualTo(EvaluationPrivacy.targetingKey(testContext.targetingKey, !fakeConsent))
        assertThat(consentChanged.telemetryTargetingKey).isNotSameAs(initial.telemetryTargetingKey)
        assertThat(contextChanged.telemetryTargetingKey.value)
            .isEqualTo(EvaluationPrivacy.targetingKey(fakeNextContext.targetingKey, !fakeConsent))
        assertThat(contextChanged.telemetryTargetingKey).isNotSameAs(consentChanged.telemetryTargetingKey)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M prepare reusable restored identity W getEvaluationSnapshot() {cached state}`(
        fakeConsent: Boolean,
        forge: Forge
    ) {
        // Given
        val callback = preparePersistenceLoad(forge)
        val fakeEntry = FlagsStateEntry(testContext, multipleFlagsMap, forge.aLong(), fakeConsent)
        callback.onSuccess(DataStoreContent(forge.anInt(), fakeEntry))

        // When
        val snapshots = multipleFlagsMap.keys.map { checkNotNull(testedRepository.getEvaluationSnapshot(it)) }

        // Then
        val identity = snapshots.first().telemetryTargetingKey
        assertThat(identity.value).isEqualTo(EvaluationPrivacy.targetingKey(testContext.targetingKey, fakeConsent))
        snapshots.forEach {
            assertThat(it.telemetryTargetingKey).isSameAs(identity)
            assertThat(it.observeFullEvaluationData).isEqualTo(fakeConsent)
            assertThat(checkNotNull(it.flag).reason).isEqualTo(ResolutionReason.CACHED.name)
        }
    }

    @Test
    fun `M return stored context W getEvaluationContext() { after setting context }`(forge: Forge) {
        // Given
        val context = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = mapOf("env" to "test")
        )
        testedRepository.installFlagsAndContext(context, emptyMap()).complete {}

        // When
        val result = testedRepository.getEvaluationContext()

        // Then
        assertThat(result).isEqualTo(context)
    }

    @Test
    fun `M not wait for persistence W hasConfigurationForContext() { persistence is loading }`(forge: Forge) {
        // Given
        doAnswer { null }.whenever(mockDataStore).value<FlagsStateEntry>(
            key = any(),
            version = anyOrNull(),
            callback = any(),
            deserializer = any()
        )
        val testedRepository = DefaultFlagsRepository(
            featureSdkCore = mockFeatureSdkCore,
            dataStore = mockDataStore,
            flagSelection = null,
            instanceName = "loading",
            persistenceLoadTimeoutMs = TimeUnit.SECONDS.toMillis(10)
        )
        val result = AtomicReference<Boolean>()
        val completed = CountDownLatch(1)
        val context = EvaluationContext(forge.anAlphabeticalString(), emptyMap())
        val readThread = Thread {
            result.set(testedRepository.hasConfigurationForContext(context))
            completed.countDown()
        }.apply { isDaemon = true }

        // When
        readThread.start()

        // Then
        assertThat(completed.await(1, TimeUnit.SECONDS)).isTrue()
        readThread.join(TimeUnit.SECONDS.toMillis(1))
        assertThat(result.get()).isFalse()
    }

    @Test
    fun `M mark restored assignments cached W initial disk read completes`(forge: Forge) {
        val context = forge.getForgery<EvaluationContext>()
        val flags = forge.aList(size = forge.anInt(min = 1, max = 5)) {
            anAlphabeticalString() to getForgery<PrecomputedFlag>()
        }.toMap()
        val callback = preparePersistenceLoad(forge)

        callback.onSuccess(DataStoreContent(forge.anInt(), FlagsStateEntry(context, flags, forge.aLong())))

        val snapshot = checkNotNull(testedRepository.getAssignmentsSnapshot()).flags
        flags.forEach { (key, originalFlag) ->
            assertThat(snapshot[key]).isEqualTo(originalFlag.copy(reason = ResolutionReason.CACHED.name))
            assertThat(testedRepository.getEvaluationSnapshot(key))
                .isEqualTo(FlagEvaluationSnapshot(snapshot[key], context, false))
        }
        assertThat(testedRepository.getEvaluationContext()).isEqualTo(context)
        assertThat(flags.values.map { it.reason }).doesNotContain(ResolutionReason.CACHED.name)
        verify(mockDataStore, never()).setValue<FlagsStateEntry>(
            key = any(),
            data = any(),
            version = any(),
            callback = anyOrNull(),
            serializer = any()
        )
    }

    @Test
    fun `M replace cached assignments with original network reasons W network response arrives`(forge: Forge) {
        val context = forge.getForgery<EvaluationContext>()
        val flags = forge.aList(size = forge.anInt(min = 1, max = 5)) {
            anAlphabeticalString() to getForgery<PrecomputedFlag>()
        }.toMap()
        val callback = preparePersistenceLoad(forge)
        callback.onSuccess(DataStoreContent(forge.anInt(), FlagsStateEntry(context, flags, forge.aLong())))
        val cachedSnapshot = checkNotNull(testedRepository.getAssignmentsSnapshot()).flags

        testedRepository.installFlagsAndContext(context, flags).complete {}

        assertThat(checkNotNull(testedRepository.getAssignmentsSnapshot()).flags).isSameAs(flags)
        assertThat(cachedSnapshot.values.map { it.reason }).containsOnly(ResolutionReason.CACHED.name)
        val entry = argumentCaptor<FlagsStateEntry>()
        verify(mockDataStore).setValue(
            key = any(),
            data = entry.capture(),
            version = any(),
            callback = anyOrNull(),
            serializer = any()
        )
        assertThat(entry.firstValue.flags).isSameAs(flags)
    }

    @Test
    fun `M preserve fresh assignments W disk read finishes after network response`(forge: Forge) {
        val context = forge.getForgery<EvaluationContext>()
        val flags = forge.aList(size = forge.anInt(min = 1, max = 5)) {
            anAlphabeticalString() to getForgery<PrecomputedFlag>()
        }.toMap()
        val callback = preparePersistenceLoad(forge)
        val networkContext = forge.getForgery<EvaluationContext>()
        val networkFlags = mapOf(forge.anAlphabeticalString() to forge.getForgery<PrecomputedFlag>())
        testedRepository.installFlagsAndContext(networkContext, networkFlags).complete {}

        callback.onSuccess(DataStoreContent(forge.anInt(), FlagsStateEntry(context, flags, forge.aLong())))

        assertThat(checkNotNull(testedRepository.getAssignmentsSnapshot()).flags).isSameAs(networkFlags)
        assertThat(testedRepository.getEvaluationContext()).isEqualTo(networkContext)
    }

    @Test
    fun `M retain installed context W initial disk read completes { empty assignments }`(forge: Forge) {
        // Given
        val context = forge.getForgery<EvaluationContext>()
        val callback = preparePersistenceLoad(forge)

        // When
        callback.onSuccess(DataStoreContent(forge.anInt(), FlagsStateEntry(context, emptyMap(), forge.aLong())))

        // Then
        assertThat(testedRepository.getEvaluationContext()).isEqualTo(context)
        assertThat(checkNotNull(testedRepository.getAssignmentsSnapshot()).flags).isEmpty()
        assertThat(testedRepository.hasConfiguration()).isTrue()
        verify(mockDataStore, never()).setValue<FlagsStateEntry>(
            key = any(),
            data = any(),
            version = any(),
            callback = anyOrNull(),
            serializer = any()
        )
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `M retain empty network assignments W disk and network complete`(diskFirst: Boolean, forge: Forge) {
        // Given
        val diskContext = forge.getForgery<EvaluationContext>()
        val networkContext = diskContext.copy(targetingKey = diskContext.targetingKey + "-network")
        val diskFlags = mapOf(forge.anAlphabeticalString() to forge.getForgery<PrecomputedFlag>())
        val callback = preparePersistenceLoad(forge)
        val diskContent = DataStoreContent(forge.anInt(), FlagsStateEntry(diskContext, diskFlags, forge.aLong()))

        // When
        if (diskFirst) {
            callback.onSuccess(diskContent)
        }
        testedRepository.installFlagsAndContext(networkContext, emptyMap()).complete {}
        if (!diskFirst) {
            callback.onSuccess(diskContent)
        }

        // Then
        assertThat(testedRepository.getEvaluationContext()).isEqualTo(networkContext)
        assertThat(checkNotNull(testedRepository.getAssignmentsSnapshot()).flags).isEmpty()
        assertThat(testedRepository.getEvaluationSnapshot(diskFlags.keys.single())?.flag).isNull()
        val entry = argumentCaptor<FlagsStateEntry>()
        verify(mockDataStore).setValue(
            key = any(),
            data = entry.capture(),
            version = any(),
            callback = anyOrNull(),
            serializer = any()
        )
        assertThat(entry.firstValue.evaluationContext).isEqualTo(networkContext)
        assertThat(entry.firstValue.flags).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `M ignore persisted state W initial disk read completes { different flag selection }`(
        configureSelection: Boolean,
        forge: Forge
    ) {
        // Given
        val configuredSelection = CanonicalFlagSelection(listOf("mobile"), emptyList()).takeIf { configureSelection }
        val persistedSelection = CanonicalFlagSelection(listOf("web"), emptyList())
        val flags = mapOf(forge.anAlphabeticalString() to forge.getForgery<PrecomputedFlag>())
        val callback = preparePersistenceLoad(forge, configuredSelection)

        // When
        callback.onSuccess(
            DataStoreContent(
                forge.anInt(),
                FlagsStateEntry(forge.getForgery(), flags, forge.aLong(), flagSelection = persistedSelection)
            )
        )

        // Then
        assertThat(testedRepository.getEvaluationContext()).isNull()
        assertThat(testedRepository.hasConfiguration()).isFalse()
    }

    @Test
    fun `M ignore persisted state W initial disk read completes { state saved without a flag selection }`(
        forge: Forge
    ) {
        // Given
        val flags = mapOf(forge.anAlphabeticalString() to forge.getForgery<PrecomputedFlag>())
        val callback = preparePersistenceLoad(forge, CanonicalFlagSelection(listOf("mobile"), emptyList()))

        // When
        callback.onSuccess(
            DataStoreContent(forge.anInt(), FlagsStateEntry(forge.getForgery(), flags, forge.aLong()))
        )

        // Then
        assertThat(testedRepository.getEvaluationContext()).isNull()
        assertThat(testedRepository.hasConfiguration()).isFalse()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M install persisted state W initial disk read completes { same flag selection }`(
        fakeConsent: Boolean,
        forge: Forge
    ) {
        // Given
        val context = forge.getForgery<EvaluationContext>()
        val flagKey = forge.anAlphabeticalString()
        val callback = preparePersistenceLoad(forge, CanonicalFlagSelection(listOf("mobile"), listOf("checkout-*")))

        // When
        callback.onSuccess(
            DataStoreContent(
                forge.anInt(),
                FlagsStateEntry(
                    context,
                    mapOf(flagKey to forge.getForgery<PrecomputedFlag>()),
                    forge.aLong(),
                    observeFullEvaluationData = fakeConsent,
                    flagSelection = CanonicalFlagSelection(listOf("mobile"), listOf("checkout-*"))
                )
            )
        )

        // Then
        assertThat(testedRepository.getEvaluationContext()).isEqualTo(context)
        assertThat(testedRepository.getEvaluationSnapshot(flagKey)?.flag).isNotNull()
        val snapshot = checkNotNull(testedRepository.getEvaluationSnapshot(flagKey))
        assertThat(snapshot.observeFullEvaluationData).isEqualTo(fakeConsent)
        assertThat(snapshot.telemetryTargetingKey.value)
            .isEqualTo(EvaluationPrivacy.targetingKey(context.targetingKey, fakeConsent))
    }

    @Test
    fun `M install persisted state W initial disk read completes { equivalent configured selection }`(forge: Forge) {
        // Given
        val context = forge.getForgery<EvaluationContext>()
        val flagKey = forge.anAlphabeticalString()
        val persistedSelection = canonical(FlagSelection(tags = listOf("checkout"), keyPatterns = null))
        val configuredSelection = canonical(FlagSelection(tags = listOf(" checkout", "checkout"), keyPatterns = null))
        val callback = preparePersistenceLoad(forge, configuredSelection)

        // When
        callback.onSuccess(
            DataStoreContent(
                forge.anInt(),
                FlagsStateEntry(
                    context,
                    mapOf(flagKey to forge.getForgery<PrecomputedFlag>()),
                    forge.aLong(),
                    flagSelection = persistedSelection
                )
            )
        )

        // Then
        assertThat(testedRepository.getEvaluationContext()).isEqualTo(context)
        assertThat(testedRepository.getEvaluationSnapshot(flagKey)?.flag).isNotNull()
    }

    @Test
    fun `M persist the state under the selection key W installFlagsAndContext() + complete() { flag selection }`(
        @StringForgery(regex = "fake-[a-z]{4,8}") fakeInstanceName: String,
        forge: Forge
    ) {
        // Given
        testedRepository = DefaultFlagsRepository(
            mockFeatureSdkCore,
            fakeInstanceName,
            mockDataStore,
            CanonicalFlagSelection(listOf("mobile"), emptyList())
        )

        // When
        testedRepository.installFlagsAndContext(forge.getForgery(), emptyMap()).complete {}

        // Then
        verify(mockDataStore).setValue<FlagsStateEntry>(
            key = eq("flags-selection-state-$fakeInstanceName"),
            data = any(),
            version = any(),
            callback = anyOrNull(),
            serializer = any()
        )
        verify(mockDataStore, never()).setValue<FlagsStateEntry>(
            key = eq("flags-state-$fakeInstanceName"),
            data = any(),
            version = any(),
            callback = anyOrNull(),
            serializer = any()
        )
    }

    @Test
    fun `M persist the state under the unselected key W installFlagsAndContext() + complete() { no flag selection }`(
        @StringForgery(regex = "fake-[a-z]{4,8}") fakeInstanceName: String,
        forge: Forge
    ) {
        // Given
        testedRepository = DefaultFlagsRepository(mockFeatureSdkCore, fakeInstanceName, mockDataStore, null)

        // When
        testedRepository.installFlagsAndContext(forge.getForgery(), emptyMap()).complete {}

        // Then
        verify(mockDataStore).setValue<FlagsStateEntry>(
            key = eq("flags-state-$fakeInstanceName"),
            data = any(),
            version = any(),
            callback = anyOrNull(),
            serializer = any()
        )
    }

    @Test
    fun `M read the selection key and remove the unselected state W init { flag selection }`(
        @StringForgery(regex = "fake-[a-z]{4,8}") fakeInstanceName: String
    ) {
        // When
        testedRepository = DefaultFlagsRepository(
            mockFeatureSdkCore,
            fakeInstanceName,
            mockDataStore,
            CanonicalFlagSelection(listOf("mobile"), emptyList())
        )

        // Then
        verify(mockDataStore).value<FlagsStateEntry>(
            key = eq("flags-selection-state-$fakeInstanceName"),
            version = anyOrNull(),
            callback = any(),
            deserializer = any()
        )
        verify(mockDataStore).removeValue(eq("flags-state-$fakeInstanceName"), anyOrNull())
    }

    @Test
    fun `M read the unselected key and remove the selected state W init { no flag selection }`(
        @StringForgery(regex = "fake-[a-z]{4,8}") fakeInstanceName: String
    ) {
        // When
        testedRepository = DefaultFlagsRepository(mockFeatureSdkCore, fakeInstanceName, mockDataStore, null)

        // Then
        verify(mockDataStore).value<FlagsStateEntry>(
            key = eq("flags-state-$fakeInstanceName"),
            version = anyOrNull(),
            callback = any(),
            deserializer = any()
        )
        verify(mockDataStore).removeValue(eq("flags-selection-state-$fakeInstanceName"), anyOrNull())
    }

    @ParameterizedTest
    @CsvSource(
        "DISABLED,false,flags-state,flags-selection-state",
        "DISABLED,true,flags-selection-state,flags-state",
        "IMMEDIATE,false,flags-immediate-v1-state,flags-selection-immediate-v1-state",
        "IMMEDIATE,true,flags-selection-immediate-v1-state,flags-immediate-v1-state",
        "FUTURE_EXPERIMENTS_ONLY,false,flags-future-v1-state,flags-selection-future-v1-state",
        "FUTURE_EXPERIMENTS_ONLY,true,flags-selection-future-v1-state,flags-future-v1-state"
    )
    fun `M use the opt-out mode key for the selection W init and installFlagsAndContext() + complete()`(
        mode: ExperimentationOptOutMode,
        selected: Boolean,
        expectedKeyPrefix: String,
        siblingKeyPrefix: String,
        @StringForgery(regex = "fake-[a-z]{4,8}") fakeInstanceName: String,
        forge: Forge
    ) {
        // Given
        val expectedKey = "$expectedKeyPrefix-$fakeInstanceName"
        val siblingKey = "$siblingKeyPrefix-$fakeInstanceName"
        testedRepository = DefaultFlagsRepository(
            featureSdkCore = mockFeatureSdkCore,
            instanceName = fakeInstanceName,
            dataStore = mockDataStore,
            flagSelection = if (selected) CanonicalFlagSelection(listOf("mobile"), emptyList()) else null,
            experimentationOptOutMode = mode
        )

        // When
        testedRepository.installFlagsAndContext(forge.getForgery(), emptyMap()).complete {}

        // Then
        verify(mockDataStore).value<FlagsStateEntry>(
            key = eq(expectedKey),
            version = anyOrNull(),
            callback = any(),
            deserializer = any()
        )
        verify(mockDataStore).removeValue(eq(siblingKey), anyOrNull())
        verify(mockDataStore).setValue<FlagsStateEntry>(
            key = eq(expectedKey),
            data = any(),
            version = any(),
            callback = anyOrNull(),
            serializer = any()
        )
        verify(mockDataStore, never()).setValue<FlagsStateEntry>(
            key = eq(siblingKey),
            data = any(),
            version = any(),
            callback = anyOrNull(),
            serializer = any()
        )
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M persist the configured flag selection and consent W installFlagsAndContext() + complete()`(
        fakeConsent: Boolean,
        forge: Forge
    ) {
        // Given
        val flagSelection = CanonicalFlagSelection(listOf("mobile"), listOf("checkout-*"))
        preparePersistenceLoad(forge, flagSelection)

        // When
        testedRepository.installFlagsAndContext(
            forge.getForgery(),
            emptyMap(),
            observeFullEvaluationData = fakeConsent
        ).complete {
        }

        // Then
        val entry = argumentCaptor<FlagsStateEntry>()
        verify(mockDataStore).setValue(
            key = any(),
            data = entry.capture(),
            version = any(),
            callback = anyOrNull(),
            serializer = any()
        )
        assertThat(entry.firstValue.flagSelection).isEqualTo(flagSelection)
        assertThat(entry.firstValue.observeFullEvaluationData).isEqualTo(fakeConsent)
    }

    @Test
    fun `M return no flag with the context W getEvaluationSnapshot() { empty flags map }`(forge: Forge) {
        // Given
        val context = forge.getForgery<EvaluationContext>()
        testedRepository.installFlagsAndContext(context, emptyMap()).complete {}

        // When
        val result = testedRepository.getEvaluationSnapshot(forge.anAlphabeticalString())

        // Then
        assertThat(result).isEqualTo(FlagEvaluationSnapshot(null, context, false))
    }

    @Test
    fun `M return true W hasConfigurationForContext() { empty flags map for the same context }`(forge: Forge) {
        // Given
        val context = forge.getForgery<EvaluationContext>()
        testedRepository.installFlagsAndContext(context, emptyMap()).complete {}

        // When / Then
        assertThat(testedRepository.hasConfigurationForContext(context)).isTrue()
    }

    @Test
    fun `M return false W hasConfigurationForContext() { different context }`(forge: Forge) {
        // Given
        val context = forge.getForgery<EvaluationContext>()
        testedRepository.installFlagsAndContext(context, singleFlagMap).complete {}

        // When / Then
        assertThat(
            testedRepository.hasConfigurationForContext(context.copy(targetingKey = context.targetingKey + "-other"))
        ).isFalse()
    }

    private fun canonical(selection: FlagSelection): CanonicalFlagSelection =
        checkNotNull(FlagSelectionCanonicalizer.canonicalize(selection) as? FlagSelectionResult.Selected).selection

    private fun preparePersistenceLoad(
        forge: Forge,
        flagSelection: CanonicalFlagSelection? = null
    ): DataStoreReadCallback<FlagsStateEntry> {
        lateinit var callback: DataStoreReadCallback<FlagsStateEntry>
        doAnswer {
            callback = it.getArgument(2)
            null
        }.whenever(mockDataStore).value<FlagsStateEntry>(any(), anyOrNull(), any(), any())
        testedRepository = DefaultFlagsRepository(
            mockFeatureSdkCore,
            forge.anAlphabeticalString(),
            mockDataStore,
            flagSelection
        )
        return callback
    }

    @Test
    fun `M accept pending cache W retargetInvalidatedContext() {previous request failed}`(forge: Forge) {
        // Given
        val callback = preparePersistenceLoad(forge)
        testedRepository.clearFlagsIfContextChanged(EvaluationContext(forge.anAlphabeticalString()))

        // When
        testedRepository.retargetInvalidatedContext(testContext)
        callback.onSuccess(DataStoreContent(0, FlagsStateEntry(testContext, singleFlagMap, 0)))

        // Then
        assertThat(testedRepository.hasConfigurationForContext(testContext)).isTrue()
        assertThat(testedRepository.getAssignmentsSnapshot()?.flags?.keys).isEqualTo(singleFlagMap.keys)
    }

    @Test
    fun `M retain accepted assignments W retargetInvalidatedContext()`(forge: Forge) {
        // Given
        testedRepository.installFlagsAndContext(testContext, singleFlagMap).complete {}

        // When
        testedRepository.retargetInvalidatedContext(EvaluationContext(forge.anAlphabeticalString()))

        // Then
        assertThat(testedRepository.hasConfigurationForContext(testContext)).isTrue()
    }

    @Test
    fun `M signal completed read W onFailure()`(forge: Forge) {
        // Given
        val callback = preparePersistenceLoad(forge)
        var completed = false
        testedRepository.cachedFlagsLoaded.whenComplete { completed = true }

        // When
        callback.onFailure()

        // Then
        assertThat(completed).isTrue()
        assertThat(testedRepository.hasConfiguration()).isFalse()
    }

    @Test
    fun `M report no pending read W whenComplete() {storage unavailable}`() {
        // Given
        val testedNoOp = NoOpFlagsRepository()
        var cacheCompleted = false
        var flagsPublished = false

        // When
        testedNoOp.cachedFlagsLoaded.whenComplete { cacheCompleted = true }
        testedNoOp.firstFlags.whenComplete { flagsPublished = true }

        // Then
        assertThat(cacheCompleted).isTrue()
        assertThat(flagsPublished).isFalse()
    }

    // region hasConfiguration

    @Test
    fun `M return false W hasConfiguration() { no state set }`() {
        // When + Then
        assertThat(testedRepository.hasConfiguration()).isFalse()
    }

    @Test
    fun `M return true W hasConfiguration() { empty flags map }`(forge: Forge) {
        // Given
        testedRepository.installFlagsAndContext(
            EvaluationContext(forge.anAlphabeticalString(), emptyMap()),
            emptyMap()
        ).complete {}

        // When + Then
        assertThat(testedRepository.hasConfiguration()).isTrue()
    }

    @Test
    fun `M return true W hasConfiguration() { single flag }`() {
        // Given
        testedRepository.installFlagsAndContext(testContext, singleFlagMap).complete {}

        // When + Then
        assertThat(testedRepository.hasConfiguration()).isTrue()
    }

    @Test
    fun `M return true W hasConfiguration() { multiple flags }`() {
        // Given
        testedRepository.installFlagsAndContext(testContext, multipleFlagsMap).complete {}

        // When + Then
        assertThat(testedRepository.hasConfiguration()).isTrue()
    }

    @Test
    fun `M return true W hasConfiguration() { persistence loads with non-empty flags }`() {
        // Given
        val persistedEntry = FlagsStateEntry(
            flags = singleFlagMap,
            evaluationContext = testContext,
            lastUpdateTimestamp = 0L,
            flagSelection = null
        )
        doAnswer {
            it.getArgument<DataStoreReadCallback<FlagsStateEntry>>(2)
                .onSuccess(DataStoreContent(versionCode = 0, data = persistedEntry))
            null
        }.whenever(mockDataStore).value<FlagsStateEntry>(
            key = any(),
            version = anyOrNull(),
            callback = any(),
            deserializer = any()
        )
        val repository = DefaultFlagsRepository(
            featureSdkCore = mockFeatureSdkCore,
            dataStore = mockDataStore,
            flagSelection = null,
            instanceName = "with-flags"
        )

        // When + Then
        assertThat(repository.hasConfiguration()).isTrue()
    }

    @Test
    fun `M return false W hasConfiguration() { persistence loads with no data }`() {
        // Given
        doAnswer {
            it.getArgument<DataStoreReadCallback<FlagsStateEntry>>(2)
                .onSuccess(DataStoreContent(versionCode = 0, data = null))
            null
        }.whenever(mockDataStore).value<FlagsStateEntry>(
            key = any(),
            version = anyOrNull(),
            callback = any(),
            deserializer = any()
        )
        val repository = DefaultFlagsRepository(
            featureSdkCore = mockFeatureSdkCore,
            dataStore = mockDataStore,
            flagSelection = null,
            instanceName = "no-data"
        )

        // When + Then
        assertThat(repository.hasConfiguration()).isFalse()
    }

    @Test
    fun `M return false W hasConfiguration() { persistence callback never fires within timeout }`() {
        // Given
        doAnswer {
            // Never call the callback
            null
        }.whenever(mockDataStore).value<FlagsStateEntry>(
            key = any(),
            version = anyOrNull(),
            callback = any(),
            deserializer = any()
        )
        val timeoutRepository = DefaultFlagsRepository(
            featureSdkCore = mockFeatureSdkCore,
            dataStore = mockDataStore,
            flagSelection = null,
            instanceName = "timeout",
            persistenceLoadTimeoutMs = 1L
        )

        // When
        val result = timeoutRepository.hasConfiguration()

        // Then
        assertThat(result).isFalse()
    }

    // endregion

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M reject invalidated snapshots W clearFlagsIfContextChanged()`(acceptedBefore: Boolean, forge: Forge) {
        // Given
        if (acceptedBefore) testedRepository.installFlagsAndContext(testContext, multipleFlagsMap).complete {}
        val nextContext = EvaluationContext(forge.anAlphabeticalString())

        // When
        testedRepository.clearFlagsIfContextChanged(nextContext)

        // Then
        assertThat(testedRepository.getAssignmentsSnapshot()).isNull()
        assertThat(testedRepository.getEvaluationContext()).isNull()
        assertThat(testedRepository.getEvaluationSnapshot(singleFlagMap.keys.first())).isNull()
    }

    @Test
    fun `M expose accepted empty snapshot W installFlagsAndContext()`() {
        // Given
        testedRepository.clearFlagsIfContextChanged(testContext)

        // When
        testedRepository.installFlagsAndContext(testContext, emptyMap()).complete {}

        // Then
        val snapshot = checkNotNull(testedRepository.getAssignmentsSnapshot())
        assertThat(snapshot.context).isEqualTo(testContext)
        assertThat(snapshot.flags).isEmpty()
    }

    // region getFlagsSnapshot

    @Test
    fun `M return flags map W getAssignmentsSnapshot() { flags state set }`() {
        // Given
        testedRepository.installFlagsAndContext(testContext, multipleFlagsMap).complete {}

        // When
        val result = testedRepository.getAssignmentsSnapshot()?.flags

        // Then
        assertThat(result).isEqualTo(multipleFlagsMap)
    }

    // endregion
}
