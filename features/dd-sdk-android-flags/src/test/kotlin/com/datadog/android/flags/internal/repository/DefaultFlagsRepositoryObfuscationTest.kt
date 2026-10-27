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
import com.datadog.android.flags.internal.model.PrecomputedFlag
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
        testedRepository = DefaultFlagsRepository(mockSdkCore, fakeInstanceName, mockDataStore)
    }

    @Test
    fun `M restore encoded cache W DefaultFlagsRepository() { native consumer }`() {
        // Given
        val entry = cachedEntry(encoded = true)
        doAnswer { it.getArgument<DataStoreReadCallback<FlagsStateEntry>>(2).onSuccess(DataStoreContent(0, entry)) }
            .whenever(mockDataStore).value<FlagsStateEntry>(any(), anyOrNull(), any(), any())

        // When
        val testedRepository = DefaultFlagsRepository(mockSdkCore, fakeInstanceName, mockDataStore)

        // Then
        val (flag, context) = checkNotNull(testedRepository.getPrecomputedFlagWithContext(fakeFlagKey))
        assertThat(context).isEqualTo(fakeContext)
        checkNotNull(flag)
        assertThat(flag.variationValue).isEqualTo(fakeFlag.variationValue)
        assertThat(flag.reason).isEqualTo(ResolutionReason.CACHED.name)
        assertThat(testedRepository.getPrecomputedFlag(encodedKey)).isNull()
    }

    @ParameterizedTest
    @MethodSource("cacheInitializations")
    fun `M gate cached assignments W getPrecomputedFlag() {synchronous source and delayed disk}`(
        source: String,
        encoded: Boolean
    ) {
        // Given
        var diskCallback: DataStoreReadCallback<FlagsStateEntry>? = null
        doAnswer { diskCallback = it.getArgument(2) }
            .whenever(mockDataStore).value<FlagsStateEntry>(any(), anyOrNull(), any(), any())
        whenever(mockSdkCore.source) doReturn source
        val entry = cachedEntry(encoded)
        testedRepository = DefaultFlagsRepository(mockSdkCore, fakeInstanceName, mockDataStore)
        val firstFlags = mutableListOf<List<String>>()
        testedRepository.firstFlags.whenComplete { firstFlags.add(it) }

        // When
        checkNotNull(diskCallback).onSuccess(DataStoreContent(0, entry))
        val result = testedRepository.getPrecomputedFlag(fakeFlagKey)

        // Then
        val readable = !encoded || source == "android"
        assertThat(result?.variationValue).isEqualTo(if (readable) fakeFlag.variationValue else null)
        assertThat(testedRepository.hasFlags()).isEqualTo(readable)
        assertThat(testedRepository.hasLoadedFlagsForContext(fakeContext)).isEqualTo(readable)
        assertThat(testedRepository.getEvaluationContext()).isEqualTo(if (readable) fakeContext else null)
        assertThat(testedRepository.getPrecomputedFlagWithContext(fakeFlagKey) != null).isEqualTo(readable)
        assertThat(testedRepository.getFlagsSnapshot().isEmpty()).isEqualTo(encoded)
        assertThat(firstFlags).hasSize(if (readable) 1 else 0)
        if (readable) {
            assertThat(firstFlags.single()).isEqualTo(if (encoded) emptyList() else listOf(fakeFlagKey))
        }
        verify(mockSdkCore, never()).getFeature(Feature.FLAGS_FEATURE_NAME)
    }

    @Test
    fun `M notify first flags W setFlagsAndContext() { encoded network response }`() {
        // Given
        val firstFlags = mutableListOf<List<String>>()
        testedRepository.firstFlags.whenComplete { firstFlags.add(it) }

        // When
        install(encoded = true)
        install(encoded = true)

        // Then
        assertThat(firstFlags).containsExactly(emptyList())
        assertThat(testedRepository.getPrecomputedFlag(fakeFlagKey) != null).isTrue()
    }

    @Test
    fun `M notify first readable flags W setFlagsAndContext() { wrapper replaces encoded cache }`() {
        // Given
        val entry = cachedEntry(encoded = true)
        whenever(mockSdkCore.source) doReturn "react-native"
        doAnswer { it.getArgument<DataStoreReadCallback<FlagsStateEntry>>(2).onSuccess(DataStoreContent(0, entry)) }
            .whenever(mockDataStore).value<FlagsStateEntry>(any(), anyOrNull(), any(), any())
        testedRepository = DefaultFlagsRepository(mockSdkCore, fakeInstanceName, mockDataStore)
        val firstFlags = mutableListOf<List<String>>()
        testedRepository.firstFlags.whenComplete { firstFlags.add(it) }
        assertThat(firstFlags).isEmpty()

        // When
        install(encoded = false)

        // Then
        assertThat(firstFlags).containsExactly(listOf(fakeFlagKey))
        assertThat(testedRepository.getFlagsSnapshot()).containsOnlyKeys(fakeFlagKey)
    }

    private fun cachedEntry(encoded: Boolean): FlagsStateEntry = FlagsStateEntry(
        fakeContext,
        mapOf((if (encoded) encodedKey else fakeFlagKey) to fakeFlag),
        fakeTimestamp,
        encoding.takeIf { encoded }
    )

    private fun install(encoded: Boolean) {
        val entry = cachedEntry(encoded)
        testedRepository.setFlagsAndContext(entry.evaluationContext, entry.flags, entry.obfuscation)
    }

    companion object {
        @JvmStatic
        fun cacheInitializations(): List<Arguments> =
            listOf("android", "react-native", "future-bridge").flatMap { source ->
                listOf(true, false).map { encoded -> Arguments.of(source, encoded) }
            }
    }
}
