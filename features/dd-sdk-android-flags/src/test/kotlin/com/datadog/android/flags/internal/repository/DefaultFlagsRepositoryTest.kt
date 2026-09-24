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
import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.utils.forge.ForgeConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.CountDownLatch
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
            instanceName = "default"
        )

        // Setup test fixtures for hasFlags tests
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
    fun `M not wait for persistence W hasLoadedFlagsForContext() { persistence is loading }`(forge: Forge) {
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
            instanceName = "loading",
            persistenceLoadTimeoutMs = TimeUnit.SECONDS.toMillis(10)
        )
        val result = AtomicReference<Boolean>()
        val completed = CountDownLatch(1)
        val context = EvaluationContext(forge.anAlphabeticalString(), emptyMap())
        val readThread = Thread {
            result.set(testedRepository.hasLoadedFlagsForContext(context))
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
    fun `M return original assignments without changing persisted metadata W disk hydration completes`() {
        val callback = preparePersistenceLoad()
        callback.onSuccess(DataStoreContent(0, FlagsStateEntry(testContext, multipleFlagsMap, 0L)))

        val snapshot = checkNotNull(testedRepository.getFlagsSnapshot())

        assertThat(snapshot.flags).isSameAs(multipleFlagsMap)
        assertThat(snapshot.context).isEqualTo(testContext)
        assertThat(snapshot.requestedContext).isNull()
        assertThat(snapshot.restoredFromDisk).isTrue()
        multipleFlagsMap.forEach { (key, flag) ->
            assertThat(snapshot.flags[key]).isSameAs(flag)
            assertThat(testedRepository.getPrecomputedFlag(key)).isSameAs(flag)
        }
        assertThat(multipleFlagsMap.values.map { it.reason }).doesNotContain("CACHED")
    }

    @Test
    fun `M retain network reasons W disk read finishes after network response`() {
        val callback = preparePersistenceLoad()
        val networkContext = EvaluationContext("new-user")
        testedRepository.setFlagsAndContext(networkContext, singleFlagMap)

        callback.onSuccess(DataStoreContent(0, FlagsStateEntry(testContext, multipleFlagsMap, 0L)))

        assertThat(testedRepository.getFlagsSnapshot()?.flags).isSameAs(singleFlagMap)
        assertThat(testedRepository.getFlagsSnapshot()?.restoredFromDisk).isFalse()
        assertThat(testedRepository.getEvaluationContext()).isEqualTo(networkContext)
    }

    @Test
    fun `M replace disk provenance W network response replaces persisted assignments`() {
        val callback = preparePersistenceLoad()
        callback.onSuccess(DataStoreContent(0, FlagsStateEntry(testContext, multipleFlagsMap, 0L)))
        val initialSnapshot = testedRepository.getFlagsSnapshot()

        testedRepository.setFlagsAndContext(testContext, multipleFlagsMap)

        assertThat(testedRepository.getFlagsSnapshot()?.flags).isSameAs(multipleFlagsMap)
        assertThat(testedRepository.getFlagsSnapshot()?.restoredFromDisk).isFalse()
        assertThat(initialSnapshot?.restoredFromDisk).isTrue()
    }

    @Test
    fun `M retain original assignments and capture requested context W context changes before network resolution`() {
        val callback = preparePersistenceLoad()
        callback.onSuccess(DataStoreContent(0, FlagsStateEntry(testContext, multipleFlagsMap, 0L)))
        val contexts = listOf(
            testContext,
            testContext.copy(attributes = mapOf("plan" to "premium")),
            testContext.copy(targetingKey = "different-user"),
            testContext
        )

        contexts.forEach { requestedContext ->
            testedRepository.setRequestedContext(requestedContext)
            val snapshot = checkNotNull(testedRepository.getFlagsSnapshot())
            assertThat(snapshot.flags).isSameAs(multipleFlagsMap)
            assertThat(snapshot.context).isEqualTo(testContext)
            assertThat(snapshot.requestedContext).isEqualTo(requestedContext)
            assertThat(snapshot.restoredFromDisk).isTrue()
            multipleFlagsMap.forEach { (key, flag) ->
                assertThat(testedRepository.getPrecomputedFlag(key)).isSameAs(flag)
            }
        }
    }

    @Test
    fun `M preserve requested context W request starts before disk hydration`() {
        val callback = preparePersistenceLoad()
        testedRepository.setRequestedContext(EvaluationContext("different-user"))

        callback.onSuccess(DataStoreContent(0, FlagsStateEntry(testContext, multipleFlagsMap, 0L)))

        assertThat(testedRepository.getFlagsSnapshot()?.requestedContext).isEqualTo(EvaluationContext("different-user"))
        assertThat(testedRepository.getFlagsSnapshot()?.flags).isSameAs(multipleFlagsMap)
        assertThat(testedRepository.getEvaluationContext()).isEqualTo(testContext)
    }

    @Test
    fun `M leave fresh assignments unchanged W another context is requested`() {
        testedRepository.setFlagsAndContext(testContext, multipleFlagsMap)

        testedRepository.setRequestedContext(EvaluationContext("different-user"))

        assertThat(testedRepository.getFlagsSnapshot()?.flags).isSameAs(multipleFlagsMap)
    }

    @Test
    fun `M persist original network reasons only W cached assignments are read and replaced`() {
        val callback = preparePersistenceLoad()
        callback.onSuccess(DataStoreContent(0, FlagsStateEntry(testContext, multipleFlagsMap, 0L)))
        testedRepository.setRequestedContext(EvaluationContext("different-user"))

        testedRepository.getFlagsSnapshot()
        multipleFlagsMap.keys.forEach { testedRepository.getPrecomputedFlag(it) }

        verify(mockDataStore, never()).setValue<FlagsStateEntry>(
            key = any(),
            data = any(),
            version = any(),
            callback = anyOrNull(),
            serializer = any()
        )

        testedRepository.setFlagsAndContext(testContext, multipleFlagsMap)

        val entry = argumentCaptor<FlagsStateEntry>()
        verify(mockDataStore).setValue(
            key = any(),
            data = entry.capture(),
            version = any(),
            callback = anyOrNull(),
            serializer = any()
        )
        assertThat(entry.firstValue.flags).isEqualTo(multipleFlagsMap)
        assertThat(entry.firstValue.evaluationContext).isEqualTo(testContext)
    }

    private fun preparePersistenceLoad(): DataStoreReadCallback<FlagsStateEntry> {
        lateinit var callback: DataStoreReadCallback<FlagsStateEntry>
        doAnswer {
            callback = it.getArgument(2)
            null
        }.whenever(mockDataStore).value<FlagsStateEntry>(
            key = any(),
            version = anyOrNull(),
            callback = any(),
            deserializer = any()
        )
        testedRepository = DefaultFlagsRepository(mockFeatureSdkCore, "cached", mockDataStore)
        return callback
    }

    // region hasFlags

    @Test
    fun `M return false W hasFlags() { no state set }`() {
        // When + Then
        assertThat(testedRepository.hasFlags()).isFalse()
    }

    @Test
    fun `M return false W hasFlags() { empty flags map }`(forge: Forge) {
        // Given
        testedRepository.setFlagsAndContext(
            EvaluationContext(forge.anAlphabeticalString(), emptyMap()),
            emptyMap()
        )

        // When + Then
        assertThat(testedRepository.hasFlags()).isFalse()
    }

    @Test
    fun `M return true W hasFlags() { single flag }`() {
        // Given
        testedRepository.setFlagsAndContext(testContext, singleFlagMap)

        // When + Then
        assertThat(testedRepository.hasFlags()).isTrue()
    }

    @Test
    fun `M return true W hasFlags() { multiple flags }`() {
        // Given
        testedRepository.setFlagsAndContext(testContext, multipleFlagsMap)

        // When + Then
        assertThat(testedRepository.hasFlags()).isTrue()
    }

    @Test
    fun `M return true W hasFlags() { persistence loads with non-empty flags }`() {
        // Given
        val persistedEntry = FlagsStateEntry(
            flags = singleFlagMap,
            evaluationContext = testContext,
            lastUpdateTimestamp = 0L
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
            instanceName = "with-flags"
        )

        // When + Then
        assertThat(repository.hasFlags()).isTrue()
    }

    @Test
    fun `M return false W hasFlags() { persistence loads with no data }`() {
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
            instanceName = "no-data"
        )

        // When + Then
        assertThat(repository.hasFlags()).isFalse()
    }

    @Test
    fun `M return false W hasFlags() { persistence callback never fires within timeout }`() {
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
            instanceName = "timeout",
            persistenceLoadTimeoutMs = 1L
        )

        // When
        val result = timeoutRepository.hasFlags()

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
        assertThat(result?.flags).isSameAs(multipleFlagsMap)
    }

    // endregion
}
