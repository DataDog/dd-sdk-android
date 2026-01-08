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
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.CountDownLatch

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
                version = any(),
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
            version = any(),
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
            version = any(),
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
    fun `M not block W hasFlags() { persistence still loading }`() {
        // Given
        doAnswer {
            // Never call the callback - simulate slow persistence
            null
        }.whenever(mockDataStore).value<FlagsStateEntry>(
            key = any(),
            version = any(),
            callback = any(),
            deserializer = any()
        )
        val slowRepository = DefaultFlagsRepository(
            featureSdkCore = mockFeatureSdkCore,
            dataStore = mockDataStore,
            instanceName = "slow",
            persistenceLoadTimeoutMs = 1000L // Long timeout
        )

        // When
        val startTime = System.currentTimeMillis()
        val result = slowRepository.hasFlags()
        val elapsedTime = System.currentTimeMillis() - startTime

        // Then
        assertThat(result).isFalse
        assertThat(elapsedTime).isLessThan(100L) // Should not wait for persistence
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
