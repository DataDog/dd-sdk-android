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
import com.datadog.android.flags.internal.model.FlagsStateEntry
import com.datadog.android.flags.internal.model.PrecomputedAssignments
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
    fun `M store generated assignments W setFlagsAndContext() { forged assignments }`(forge: Forge) {
        // Given
        val assignments = forge.getForgery<PrecomputedAssignments>()

        // When
        testedRepository.setFlagsAndContext(testContext, assignments.flags, assignments.obfuscation)

        // Then
        assertThat(testedRepository.getFlagsSnapshot()).isEqualTo(assignments.flags)
    }

    @Test
    fun `M return snapshot without waiting W getPrecomputedFlagWithContext() {persistence pending}`(forge: Forge) {
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
            val snapshot = executor.submit<Pair<PrecomputedFlag?, EvaluationContext>?> {
                testedRepository.getPrecomputedFlagWithContext(fakeKey)
            }

            // Then
            assertThat(snapshot.get(1, TimeUnit.SECONDS)).isNull()
        } finally {
            executor.shutdownNow()
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue()
        }
    }

    @Test
    fun `M retain context W getPrecomputedFlagWithContext() {flag missing}`(forge: Forge) {
        // Given
        testedRepository.setFlagsAndContext(testContext, emptyMap())

        // When
        val result = testedRepository.getPrecomputedFlagWithContext(forge.anAlphabeticalString())

        // Then
        assertThat(result?.first).isNull()
        assertThat(result?.second).isEqualTo(testContext)
    }

    @Test
    fun `M return stored context W getEvaluationContext() { after setting context }`(forge: Forge) {
        // Given
        val context = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = mapOf("env" to "test")
        )
        testedRepository.setFlagsAndContext(context, emptyMap())

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
    fun `M wait for async persistence callback W getPrecomputedFlag() { persistence fails within timeout }`(
        forge: Forge
    ) {
        // Given
        val flagKey = forge.anAlphabeticalString()
        val callbackBarrier = CountDownLatch(1)
        var capturedCallback: DataStoreReadCallback<FlagsStateEntry>? = null
        doAnswer {
            capturedCallback = it.getArgument(2)
            null
        }.whenever(mockDataStore).value<FlagsStateEntry>(
            key = any(),
            version = anyOrNull(),
            callback = any(),
            deserializer = any()
        )
        val testedRepository = DefaultFlagsRepository(
            featureSdkCore = mockFeatureSdkCore,
            dataStore = mockDataStore,
            flagSelection = null,
            instanceName = "async"
        )
        val asyncThread = Thread {
            callbackBarrier.await()
            capturedCallback?.onFailure()
        }

        // When
        asyncThread.start()
        callbackBarrier.countDown()
        val result = testedRepository.getPrecomputedFlag(flagKey)
        asyncThread.join()

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W getPrecomputedFlag() { persistence never loads }`(forge: Forge) {
        // Given
        val flagKey = forge.anAlphabeticalString()
        doAnswer {
            null
        }.whenever(mockDataStore).value<FlagsStateEntry>(
            key = any(),
            version = anyOrNull(),
            callback = any(),
            deserializer = any()
        )
        val testedRepository = DefaultFlagsRepository(
            featureSdkCore = mockFeatureSdkCore,
            dataStore = mockDataStore,
            flagSelection = null,
            instanceName = "timeout",
            persistenceLoadTimeoutMs = 1L
        )

        // When
        val result = testedRepository.getPrecomputedFlag(flagKey)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return immediately without blocking W getPrecomputedFlag() { setFlagsAndContext already called }`(
        forge: Forge
    ) {
        // Given
        val flagKey = forge.anAlphabeticalString()
        val flagValue = forge.anAlphabeticalString()
        val context = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = mapOf("env" to "test")
        )
        val flags = mapOf(
            flagKey to PrecomputedFlag(
                variationType = "string",
                variationValue = flagValue,
                doLog = false,
                allocationKey = forge.anAlphabeticalString(),
                variationKey = forge.anAlphabeticalString(),
                extraLogging = JSONObject(),
                reason = "DEFAULT"
            )
        )
        testedRepository.setFlagsAndContext(context, flags)

        // When
        val result = testedRepository.getPrecomputedFlag(flagKey)

        // Then
        assertThat(result?.variationValue).isEqualTo(flagValue)
    }

    @Test
    fun `M mark restored assignments cached W initial disk read completes`(forge: Forge) {
        val context = forge.getForgery<EvaluationContext>()
        val flags = forge.aList(size = forge.anInt(min = 1, max = 5)) {
            anAlphabeticalString() to getForgery<PrecomputedFlag>()
        }.toMap()
        val callback = preparePersistenceLoad(forge)

        callback.onSuccess(DataStoreContent(forge.anInt(), FlagsStateEntry(context, flags, forge.aLong())))

        val snapshot = testedRepository.getFlagsSnapshot()
        flags.forEach { (key, originalFlag) ->
            assertThat(snapshot[key]).isEqualTo(originalFlag.copy(reason = ResolutionReason.CACHED.name))
            assertThat(testedRepository.getPrecomputedFlag(key)).isSameAs(snapshot[key])
            assertThat(testedRepository.getPrecomputedFlagWithContext(key))
                .isEqualTo(snapshot[key] to context)
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
        val cachedSnapshot = testedRepository.getFlagsSnapshot()

        testedRepository.setFlagsAndContext(context, flags)

        assertThat(testedRepository.getFlagsSnapshot()).isSameAs(flags)
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
        testedRepository.setFlagsAndContext(networkContext, networkFlags)

        callback.onSuccess(DataStoreContent(forge.anInt(), FlagsStateEntry(context, flags, forge.aLong())))

        assertThat(testedRepository.getFlagsSnapshot()).isSameAs(networkFlags)
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
        assertThat(testedRepository.getFlagsSnapshot()).isEmpty()
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
        testedRepository.setFlagsAndContext(networkContext, emptyMap())
        if (!diskFirst) {
            callback.onSuccess(diskContent)
        }

        // Then
        assertThat(testedRepository.getEvaluationContext()).isEqualTo(networkContext)
        assertThat(testedRepository.getFlagsSnapshot()).isEmpty()
        assertThat(testedRepository.getPrecomputedFlag(diskFlags.keys.single())).isNull()
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

    @Test
    fun `M install persisted state W initial disk read completes { same flag selection }`(forge: Forge) {
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
                    flagSelection = CanonicalFlagSelection(listOf("mobile"), listOf("checkout-*"))
                )
            )
        )

        // Then
        assertThat(testedRepository.getEvaluationContext()).isEqualTo(context)
        assertThat(testedRepository.getPrecomputedFlag(flagKey)).isNotNull()
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
        assertThat(testedRepository.getPrecomputedFlag(flagKey)).isNotNull()
    }

    @Test
    fun `M persist the state under the selection key W setFlagsAndContext() { flag selection }`(
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
        testedRepository.setFlagsAndContext(forge.getForgery(), emptyMap())

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
    fun `M persist the state under the unselected key W setFlagsAndContext() { no flag selection }`(
        @StringForgery(regex = "fake-[a-z]{4,8}") fakeInstanceName: String,
        forge: Forge
    ) {
        // Given
        testedRepository = DefaultFlagsRepository(mockFeatureSdkCore, fakeInstanceName, mockDataStore, null)

        // When
        testedRepository.setFlagsAndContext(forge.getForgery(), emptyMap())

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

    @Test
    fun `M persist the configured flag selection W setFlagsAndContext()`(forge: Forge) {
        // Given
        val flagSelection = CanonicalFlagSelection(listOf("mobile"), listOf("checkout-*"))
        preparePersistenceLoad(forge, flagSelection)

        // When
        testedRepository.setFlagsAndContext(forge.getForgery(), emptyMap())

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
    }

    @Test
    fun `M return no flag with the context W getPrecomputedFlagWithContext() { empty flags map }`(forge: Forge) {
        // Given
        val context = forge.getForgery<EvaluationContext>()
        testedRepository.setFlagsAndContext(context, emptyMap())

        // When
        val result = testedRepository.getPrecomputedFlagWithContext(forge.anAlphabeticalString())

        // Then
        assertThat(result).isEqualTo(null to context)
    }

    @Test
    fun `M return true W hasConfigurationForContext() { empty flags map for the same context }`(forge: Forge) {
        // Given
        val context = forge.getForgery<EvaluationContext>()
        testedRepository.setFlagsAndContext(context, emptyMap())

        // When / Then
        assertThat(testedRepository.hasConfigurationForContext(context)).isTrue()
    }

    @Test
    fun `M return false W hasConfigurationForContext() { different context }`(forge: Forge) {
        // Given
        val context = forge.getForgery<EvaluationContext>()
        testedRepository.setFlagsAndContext(context, singleFlagMap)

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

    // region hasConfiguration

    @Test
    fun `M return false W hasConfiguration() { no state set }`() {
        // When + Then
        assertThat(testedRepository.hasConfiguration()).isFalse()
    }

    @Test
    fun `M return true W hasConfiguration() { empty flags map }`(forge: Forge) {
        // Given
        testedRepository.setFlagsAndContext(
            EvaluationContext(forge.anAlphabeticalString(), emptyMap()),
            emptyMap()
        )

        // When + Then
        assertThat(testedRepository.hasConfiguration()).isTrue()
    }

    @Test
    fun `M return true W hasConfiguration() { single flag }`() {
        // Given
        testedRepository.setFlagsAndContext(testContext, singleFlagMap)

        // When + Then
        assertThat(testedRepository.hasConfiguration()).isTrue()
    }

    @Test
    fun `M return true W hasConfiguration() { multiple flags }`() {
        // Given
        testedRepository.setFlagsAndContext(testContext, multipleFlagsMap)

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

    // region getFlagsSnapshot

    @Test
    fun `M return flags map W getFlagsSnapshot() { flags state set }`() {
        // Given
        testedRepository.setFlagsAndContext(testContext, multipleFlagsMap)

        // When
        val result = testedRepository.getFlagsSnapshot()

        // Then
        assertThat(result).isEqualTo(multipleFlagsMap)
    }

    // endregion
}
