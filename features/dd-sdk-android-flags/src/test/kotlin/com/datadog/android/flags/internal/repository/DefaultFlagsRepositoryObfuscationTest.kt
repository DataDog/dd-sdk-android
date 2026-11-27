/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.repository

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.storage.datastore.DataStoreHandler
import com.datadog.android.api.storage.datastore.DataStoreReadCallback
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.persistence.datastore.DataStoreContent
import com.datadog.android.flags.internal.model.FlagKeyObfuscation
import com.datadog.android.flags.internal.model.FlagsStateEntry
import com.datadog.android.flags.internal.model.PrecomputedDependencyEvaluation
import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.datadog.android.flags.internal.model.PrecomputedFlagEvaluationError
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.ResolutionReason
import com.datadog.android.flags.utils.forge.ForgeConfigurator
import com.datadog.android.internal.time.TimeProvider
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class, ForgeExtension::class)
@ForgeConfiguration(ForgeConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class DefaultFlagsRepositoryObfuscationTest {
    private lateinit var testedRepository: DefaultFlagsRepository
    private lateinit var encoding: FlagKeyObfuscation

    @StringForgery(regex = "[0-9a-f]{32}")
    lateinit var fakeSalt: String
    private lateinit var encodedKey: String

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockSdkCore: InternalSdkCore

    @Mock
    lateinit var mockDataStore: DataStoreHandler

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @Forgery
    lateinit var fakeContext: EvaluationContext

    @Forgery
    lateinit var fakeFlag: PrecomputedFlag

    @StringForgery
    lateinit var fakeInstanceName: String

    @StringForgery
    lateinit var fakeFlagKey: String

    @LongForgery(min = 0L)
    var fakeTimestamp = 0L

    @BeforeEach
    fun setUp() {
        encoding = FlagKeyObfuscation.fromSalt(fakeSalt)
        encodedKey = checkNotNull(encoding.encode(fakeFlagKey))
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockSdkCore.timeProvider) doReturn mockTimeProvider
        whenever(mockSdkCore.source) doReturn "android"
        doAnswer { it.getArgument<DataStoreReadCallback<FlagsStateEntry>>(2).onFailure() }
            .whenever(mockDataStore).value<FlagsStateEntry>(any(), anyOrNull(), any(), any())
        testedRepository = DefaultFlagsRepository(mockSdkCore, fakeInstanceName, mockDataStore, null)
    }

    @Test
    fun `M restore encoded cache W DefaultFlagsRepository() { native consumer }`() {
        // Given
        val entry = cachedEntry(encoded = true)
        doAnswer { it.getArgument<DataStoreReadCallback<FlagsStateEntry>>(2).onSuccess(DataStoreContent(0, entry)) }
            .whenever(mockDataStore).value<FlagsStateEntry>(any(), anyOrNull(), any(), any())

        // When
        val testedRepository = DefaultFlagsRepository(mockSdkCore, fakeInstanceName, mockDataStore, null)

        // Then
        val (flag, context) = checkNotNull(testedRepository.getEvaluationSnapshot(fakeFlagKey))
        assertThat(context).isEqualTo(fakeContext)
        checkNotNull(flag)
        assertThat(flag.variationValue).isEqualTo(fakeFlag.variationValue)
        assertThat(flag.reason).isEqualTo(ResolutionReason.CACHED.name)
        assertThat(testedRepository.getEvaluationSnapshot(encodedKey)?.flag).isNull()
    }

    @ParameterizedTest
    @MethodSource("cacheInitializations")
    fun `M gate cached assignments W getEvaluationSnapshot() {synchronous source and delayed disk}`(
        source: String,
        encoded: Boolean
    ) {
        // Given
        var dependentCallback: DataStoreReadCallback<FlagsStateEntry>? = null
        var legacyCallback: DataStoreReadCallback<FlagsStateEntry>? = null
        doAnswer {
            if (it.getArgument<String>(0).contains("dependent-v1")) {
                dependentCallback = it.getArgument(2)
            } else {
                legacyCallback = it.getArgument(2)
            }
        }
            .whenever(mockDataStore).value<FlagsStateEntry>(any(), anyOrNull(), any(), any())
        whenever(mockSdkCore.source) doReturn source
        val entry = cachedEntry(encoded)
        testedRepository = DefaultFlagsRepository(mockSdkCore, fakeInstanceName, mockDataStore, null)
        val firstFlags = mutableListOf<List<String>>()
        testedRepository.firstFlags.whenComplete { firstFlags.add(it) }

        // When
        checkNotNull(dependentCallback).onSuccess(DataStoreContent(0, entry))
        checkNotNull(legacyCallback).onFailure()
        val result = testedRepository.getEvaluationSnapshot(fakeFlagKey)?.flag

        // Then
        val readable = !encoded || source == "android"
        assertThat(result?.variationValue).isEqualTo(if (readable) fakeFlag.variationValue else null)
        assertThat(testedRepository.hasConfiguration()).isEqualTo(readable)
        assertThat(testedRepository.hasConfigurationForContext(fakeContext)).isEqualTo(readable)
        assertThat(testedRepository.getEvaluationContext()).isEqualTo(if (readable) fakeContext else null)
        assertThat(testedRepository.getEvaluationSnapshot(fakeFlagKey) != null).isEqualTo(readable)
        val snapshot = testedRepository.getAssignmentsSnapshot()
        if (readable) {
            assertThat(checkNotNull(snapshot).context).isEqualTo(fakeContext)
            assertThat(snapshot.flags.isEmpty()).isEqualTo(encoded)
        } else {
            assertThat(snapshot).isNull()
        }
        assertThat(firstFlags).hasSize(if (readable) 1 else 0)
        if (readable) {
            assertThat(firstFlags.single()).isEqualTo(if (encoded) emptyList() else listOf(fakeFlagKey))
        }
        verify(mockSdkCore, never()).getFeature(Feature.FLAGS_FEATURE_NAME)
    }

    @Test
    fun `M notify first flags W installFlagsAndContext() + complete() { encoded network response }`() {
        // Given
        val firstFlags = mutableListOf<List<String>>()
        testedRepository.firstFlags.whenComplete { firstFlags.add(it) }

        // When
        install(encoded = true)
        install(encoded = true)

        // Then
        assertThat(firstFlags).containsExactly(emptyList())
        assertThat(testedRepository.getEvaluationSnapshot(fakeFlagKey)?.flag != null).isTrue()
    }

    @Test
    fun `M decrypt hash and resolve dependency W getEvaluationSnapshot() {canonical encrypted key}`() {
        // Given
        val rootKey = "checkout-redesign"
        val dependencyKey = "new-payment-flow"
        val canonicalEncoding = FlagKeyObfuscation.fromSalt(
            "000102030405060708090a0b0c0d0e0f",
            dependencyKeyEncryption = true
        )
        val root = fakeFlag.copy(
            dependencyEvaluations = listOf(
                PrecomputedDependencyEvaluation.Success(
                    "tATBHdKpF-zxZ2Fr70UGwTMfPcXSdulh78uKYPpNzVBoSlNnB6qMCTtI1o8",
                    true
                )
            )
        )
        val encodedRoot = checkNotNull(canonicalEncoding.encode(rootKey))
        val encodedDependency = checkNotNull(canonicalEncoding.encode(dependencyKey))
        testedRepository.installFlagsAndContext(
            fakeContext,
            mapOf(encodedRoot to root, encodedDependency to fakeFlag),
            canonicalEncoding
        ).complete {}

        // When
        val snapshot = checkNotNull(testedRepository.getEvaluationSnapshot(rootKey))

        // Then
        assertThat(snapshot.dependencyResolutionError).isNull()
        val dependency = snapshot.dependencyEvaluations.single()
        assertThat(dependency.flagKey).isEqualTo(dependencyKey)
        assertThat(dependency.flag).isEqualTo(fakeFlag)
        assertThat(dependency.isPresentInAssignment).isTrue()
    }

    @Test
    fun `M retain plaintext dependent roots W installFlagsAndContext() {native source}`() {
        // Given
        val parentKey = "parent"
        val rootKey = "root"
        val errorKey = "failed-root"
        val parent = fakeFlag.copy(dependencyEvaluations = emptyList())
        val root = fakeFlag.copy(
            dependencyEvaluations = listOf(PrecomputedDependencyEvaluation.Success(parentKey, true))
        )
        val error = PrecomputedFlagEvaluationError(
            errorCode = "GENERAL",
            dependencyEvaluations = listOf(PrecomputedDependencyEvaluation.Success(parentKey, false))
        )

        // When
        testedRepository.installFlagsAndContext(
            fakeContext,
            mapOf(parentKey to parent, rootKey to root),
            flagEvaluationErrors = mapOf(errorKey to error)
        ).complete {}

        // Then
        assertThat(checkNotNull(testedRepository.getAssignmentsSnapshot()).flags)
            .containsOnlyKeys(parentKey, rootKey)
        assertThat(testedRepository.getEvaluationSnapshot(rootKey)?.flag).isEqualTo(root)
        assertThat(testedRepository.getEvaluationSnapshot(rootKey)?.dependencyEvaluations).hasSize(1)
        assertThat(testedRepository.getEvaluationSnapshot(errorKey)?.evaluationError).isEqualTo(error)
    }

    @Test
    fun `M retain plaintext dependent roots W installFlagsAndContext() {bridge sources}`() {
        for (source in listOf("react-native", "flutter")) {
            // Given
            val parentKey = "parent-$source"
            val independentKey = "independent-$source"
            val rootKey = "root-$source"
            val errorKey = "failed-root-$source"
            val parent = fakeFlag.copy(dependencyEvaluations = emptyList())
            val independent = fakeFlag.copy(dependencyEvaluations = emptyList())
            val root = fakeFlag.copy(
                dependencyEvaluations = listOf(PrecomputedDependencyEvaluation.Success(parentKey, true))
            )
            val error = PrecomputedFlagEvaluationError(
                errorCode = "GENERAL",
                dependencyEvaluations = listOf(PrecomputedDependencyEvaluation.Success(parentKey, false))
            )
            whenever(mockSdkCore.source) doReturn source
            testedRepository = DefaultFlagsRepository(mockSdkCore, "$fakeInstanceName-$source", mockDataStore, null)

            // When
            testedRepository.installFlagsAndContext(
                fakeContext,
                mapOf(parentKey to parent, independentKey to independent, rootKey to root),
                flagEvaluationErrors = mapOf(errorKey to error)
            ).complete {}

            // Then
            assertThat(checkNotNull(testedRepository.getAssignmentsSnapshot()).flags)
                .containsOnlyKeys(parentKey, independentKey, rootKey)
            assertThat(testedRepository.getEvaluationSnapshot(rootKey)?.flag).isEqualTo(root)
            assertThat(testedRepository.getEvaluationSnapshot(rootKey)?.dependencyEvaluations).hasSize(1)
            assertThat(testedRepository.getEvaluationSnapshot(errorKey)?.evaluationError).isEqualTo(error)
        }
    }

    @Test
    fun `M publish all cached flags W DefaultFlagsRepository() {react-native bridge}`() {
        // Given
        val parentKey = "cached-parent"
        val independentKey = "cached-independent"
        val rootKey = "cached-root"
        val errorKey = "cached-error"
        val root = fakeFlag.copy(
            dependencyEvaluations = listOf(PrecomputedDependencyEvaluation.Success(parentKey, true))
        )
        val entry = FlagsStateEntry(
            evaluationContext = fakeContext,
            flags = mapOf(parentKey to fakeFlag, independentKey to fakeFlag, rootKey to root),
            lastUpdateTimestamp = fakeTimestamp,
            flagEvaluationErrors = mapOf(
                errorKey to PrecomputedFlagEvaluationError(
                    errorCode = "GENERAL",
                    dependencyEvaluations = listOf(PrecomputedDependencyEvaluation.Success(parentKey, false))
                )
            )
        )
        whenever(mockSdkCore.source) doReturn "react-native"
        doAnswer {
            val key = it.getArgument<String>(0)
            val callback = it.getArgument<DataStoreReadCallback<FlagsStateEntry>>(2)
            if (key.contains("dependent-v1")) {
                callback.onSuccess(DataStoreContent(0, entry))
            } else {
                callback.onFailure()
            }
        }.whenever(mockDataStore).value<FlagsStateEntry>(any(), anyOrNull(), any(), any())

        // When
        val repository = DefaultFlagsRepository(mockSdkCore, fakeInstanceName, mockDataStore, null)
        val firstFlags = mutableListOf<List<String>>()
        repository.firstFlags.whenComplete { firstFlags.add(it) }

        // Then
        assertThat(firstFlags).containsExactly(listOf(parentKey, independentKey, rootKey))
        assertThat(checkNotNull(repository.getAssignmentsSnapshot()).flags)
            .containsOnlyKeys(parentKey, independentKey, rootKey)
        assertThat(repository.getEvaluationSnapshot(rootKey)?.flag).isEqualTo(root.copy(reason = ResolutionReason.CACHED.name))
        assertThat(repository.getEvaluationSnapshot(errorKey)?.evaluationError)
            .isEqualTo(entry.flagEvaluationErrors[errorKey])
    }

    @Test
    fun `M notify first readable flags W installFlagsAndContext() + complete() { wrapper replaces encoded cache }`() {
        // Given
        val entry = cachedEntry(encoded = true)
        whenever(mockSdkCore.source) doReturn "react-native"
        doAnswer { it.getArgument<DataStoreReadCallback<FlagsStateEntry>>(2).onSuccess(DataStoreContent(0, entry)) }
            .whenever(mockDataStore).value<FlagsStateEntry>(any(), anyOrNull(), any(), any())
        testedRepository = DefaultFlagsRepository(mockSdkCore, fakeInstanceName, mockDataStore, null)
        val firstFlags = mutableListOf<List<String>>()
        testedRepository.firstFlags.whenComplete { firstFlags.add(it) }
        assertThat(firstFlags).isEmpty()

        // When
        install(encoded = false)

        // Then
        assertThat(firstFlags).containsExactly(listOf(fakeFlagKey))
        assertThat(checkNotNull(testedRepository.getAssignmentsSnapshot()).flags).containsOnlyKeys(fakeFlagKey)
    }

    private fun cachedEntry(encoded: Boolean): FlagsStateEntry = FlagsStateEntry(
        fakeContext,
        mapOf((if (encoded) encodedKey else fakeFlagKey) to fakeFlag),
        fakeTimestamp,
        obfuscation = encoding.takeIf { encoded }
    )

    private fun install(encoded: Boolean) {
        val entry = cachedEntry(encoded)
        testedRepository.installFlagsAndContext(entry.evaluationContext, entry.flags, entry.obfuscation).complete {}
    }

    companion object {
        @JvmStatic
        fun cacheInitializations(): List<Arguments> =
            listOf("android", "react-native", "future-bridge").flatMap { source ->
                listOf(true, false).map { encoded -> Arguments.of(source, encoded) }
            }
    }
}
