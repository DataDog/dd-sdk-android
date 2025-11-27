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
    lateinit var mockDataStore: DataStoreHandler

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    private lateinit var testedRepository: DefaultFlagsRepository

    @BeforeEach
    fun `set up`() {
        whenever(mockFeatureSdkCore.internalLogger) doReturn mockInternalLogger
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
    fun `M return expected value W hasFlags() { for various states }`(forge: Forge) {
        data class TestCase(val given: () -> Unit, val then: Boolean)

        val testCases = listOf(
            TestCase(
                given = { /* no state set */ },
                then = false
            ),
            TestCase(
                given = {
                    testedRepository.setFlagsAndContext(
                        EvaluationContext(forge.anAlphabeticalString(), emptyMap()),
                        emptyMap()
                    )
                },
                then = false
            ),
            TestCase(
                given = {
                    testedRepository.setFlagsAndContext(
                        EvaluationContext(forge.anAlphabeticalString(), emptyMap()),
                        mapOf(
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
                    )
                },
                then = true
            ),
            TestCase(
                given = {
                    testedRepository.setFlagsAndContext(
                        EvaluationContext(forge.anAlphabeticalString(), emptyMap()),
                        mapOf(
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
                    )
                },
                then = true
            )
        )

        testCases.forEach { testCase ->
            // Given
            testCase.given()

            // When
            val result = testedRepository.hasFlags()

            // Then
            assertThat(result).isEqualTo(testCase.then)
        }
    }

    @Test
    fun `M not block W hasFlags() { persistence still loading }`() {
        // Given
        val startTime = System.currentTimeMillis()
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
        val result = slowRepository.hasFlags()
        val elapsedTime = System.currentTimeMillis() - startTime

        // Then
        assertThat(result).isFalse
        assertThat(elapsedTime).isLessThan(100L) // Should not wait for persistence
    }

    // endregion
}
