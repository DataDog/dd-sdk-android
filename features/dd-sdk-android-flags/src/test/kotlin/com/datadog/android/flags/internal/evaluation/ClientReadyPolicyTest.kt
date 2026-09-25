/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.evaluation

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.datastore.DataStoreHandler
import com.datadog.android.api.storage.datastore.DataStoreReadCallback
import com.datadog.android.core.persistence.datastore.DataStoreContent
import com.datadog.android.flags.ClientReadyPolicy
import com.datadog.android.flags.EvaluationContextCallback
import com.datadog.android.flags.FlagsStateListener
import com.datadog.android.flags.internal.FlagsStateManager
import com.datadog.android.flags.internal.model.FlagsStateEntry
import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.datadog.android.flags.internal.net.PrecomputedAssignmentsReader
import com.datadog.android.flags.internal.repository.DefaultFlagsRepository
import com.datadog.android.flags.internal.repository.net.PrecomputeMapper
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.FlagsClientState
import com.datadog.android.flags.utils.forge.ForgeConfigurator
import com.datadog.android.internal.utils.DDCoreStateHolder
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.ExecutorService

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(ForgeConfigurator::class)
internal class ClientReadyPolicyTest {
    private val mockSdkCore = mock<FeatureSdkCore>()
    private val mockDataStore = mock<DataStoreHandler>()
    private val mockExecutor = mock<ExecutorService>()
    private val mockReader = mock<PrecomputedAssignmentsReader>()
    private val mockMapper = mock<PrecomputeMapper>()
    private val mockCallback = mock<EvaluationContextCallback>()
    private lateinit var fakeContext: EvaluationContext
    private lateinit var fakeEntry: FlagsStateEntry
    private lateinit var fakeResponse: String
    private lateinit var diskRead: DataStoreReadCallback<FlagsStateEntry>
    private lateinit var networkOperation: Runnable
    private lateinit var testedRepository: DefaultFlagsRepository
    private lateinit var stateManager: FlagsStateManager
    private lateinit var testedManager: EvaluationsManager
    private lateinit var forge: Forge

    @BeforeEach
    fun setUp(forge: Forge) {
        this.forge = forge
        fakeContext = forge.getForgery()
        fakeEntry =
            FlagsStateEntry(
                fakeContext,
                forge.aMap {
                    anAlphabeticalString() to getForgery<PrecomputedFlag>()
                },
                forge.aLong()
            )
        fakeResponse = forge.anAlphabeticalString()
        whenever(mockSdkCore.internalLogger).thenReturn(mock())
        whenever(mockSdkCore.timeProvider).thenReturn(mock())
        whenever(mockDataStore.value<FlagsStateEntry>(any(), anyOrNull(), any(), any())).doAnswer {
            diskRead = it.getArgument(2)
            null
        }
        val mockScope = mock<FeatureScope>()
        whenever(mockSdkCore.getFeature(Feature.FLAGS_FEATURE_NAME)).thenReturn(mockScope)
        whenever(mockScope.withContext(any(), any())).doAnswer {
            it.getArgument<(DatadogContext) -> Unit>(1)(forge.getForgery())
            null
        }
        whenever(mockExecutor.execute(any())).doAnswer {
            networkOperation = it.getArgument(0)
            null
        }
        testedRepository =
            DefaultFlagsRepository(
                mockSdkCore,
                forge.anAlphabeticalString(),
                mockDataStore,
                persistenceLoadTimeoutMs = 0
            )
        stateManager =
            FlagsStateManager(DDCoreStateHolder.create(FlagsClientState.NotReady, FlagsStateListener::onStateChanged))
    }

    @Test
    fun `M become ready W disk install {cache policy and network not dispatched}`() {
        createManager(ClientReadyPolicy.CACHE_OR_NETWORK)
        testedManager.updateEvaluationsForContext(fakeContext, mockCallback)

        loadDisk()

        assertThat(stateManager.getCurrentState()).isEqualTo(FlagsClientState.Ready)
        verify(mockCallback).onSuccess()
        verify(mockReader, never()).readPrecomputedFlags(any(), any())
    }

    @Test
    fun `M wait for network W disk install {network policy}`() {
        createManager(ClientReadyPolicy.NETWORK)
        testedManager.updateEvaluationsForContext(fakeContext, mockCallback)

        loadDisk()

        assertThat(stateManager.getCurrentState()).isNotEqualTo(FlagsClientState.Ready)
        verify(mockCallback, never()).onSuccess()
    }

    @Test
    fun `M use already restored cache W first context {cache policy}`() {
        loadDisk()
        createManager(ClientReadyPolicy.CACHE_OR_NETWORK)

        testedManager.updateEvaluationsForContext(fakeContext, mockCallback)

        assertThat(stateManager.getCurrentState()).isEqualTo(FlagsClientState.Ready)
        verify(mockCallback).onSuccess()
    }

    @Test
    fun `M settle successfully W network failure {network policy and cached assignments}`() {
        createManager(ClientReadyPolicy.NETWORK)
        testedManager.updateEvaluationsForContext(fakeContext, mockCallback)
        loadDisk()

        networkOperation.run()

        assertThat(stateManager.getCurrentState()).isEqualTo(FlagsClientState.Ready)
        verify(mockCallback).onSuccess()
        verify(mockCallback, never()).onFailure(any())
    }

    @Test
    fun `M retain ready cache W background network failure`() {
        createManager(ClientReadyPolicy.CACHE_OR_NETWORK)
        testedManager.updateEvaluationsForContext(fakeContext, mockCallback)
        loadDisk()

        networkOperation.run()

        assertThat(stateManager.getCurrentState()).isEqualTo(FlagsClientState.Ready)
        verify(mockCallback).onSuccess()
        verify(mockCallback, never()).onFailure(any())
    }

    @Test
    fun `M fail initialization W network failure {no cache}`() {
        createManager(ClientReadyPolicy.NETWORK)
        testedManager.updateEvaluationsForContext(fakeContext, mockCallback)
        diskRead.onFailure()

        networkOperation.run()

        assertThat(stateManager.getCurrentState()).isInstanceOf(FlagsClientState.Error::class.java)
        verify(mockCallback).onFailure(any())
        verify(mockCallback, never()).onSuccess()
    }

    @Test
    fun `M become ready W valid empty cache`() {
        createManager(ClientReadyPolicy.CACHE_OR_NETWORK)
        testedManager.updateEvaluationsForContext(fakeContext, mockCallback)
        fakeEntry = fakeEntry.copy(flags = emptyMap())

        loadDisk()

        assertThat(stateManager.getCurrentState()).isEqualTo(FlagsClientState.Ready)
        verify(mockCallback).onSuccess()
    }

    @Test
    fun `M retain network assignments W late disk read`() {
        createManager(ClientReadyPolicy.CACHE_OR_NETWORK)
        testedManager.updateEvaluationsForContext(fakeContext, mockCallback)
        val fakeFlags = forge.aMap { anAlphabeticalString() to getForgery<PrecomputedFlag>() }
        whenever(mockReader.readPrecomputedFlags(any(), any())).thenReturn(fakeResponse)
        whenever(mockMapper.map(fakeResponse)).thenReturn(fakeFlags)
        networkOperation.run()

        loadDisk()

        assertThat(testedRepository.getFlagsSnapshot()).isEqualTo(fakeFlags)
        verify(mockCallback).onSuccess()
    }

    @Test
    fun `M not become ready W failed disk read`() {
        createManager(ClientReadyPolicy.CACHE_OR_NETWORK)
        testedManager.updateEvaluationsForContext(fakeContext, mockCallback)

        diskRead.onFailure()

        assertThat(stateManager.getCurrentState()).isNotEqualTo(FlagsClientState.Ready)
        verify(mockCallback, never()).onSuccess()
    }

    @Test
    fun `M wait for reconciliation W disk completes after second context request`() {
        createManager(ClientReadyPolicy.CACHE_OR_NETWORK)
        testedManager.updateEvaluationsForContext(fakeContext, mockCallback)
        val mockSecondCallback = mock<EvaluationContextCallback>()
        testedManager.updateEvaluationsForContext(forge.getForgery(), mockSecondCallback)

        loadDisk()

        assertThat(stateManager.getCurrentState()).isEqualTo(FlagsClientState.Reconciling)
        verify(mockSecondCallback, never()).onSuccess()
    }

    @Test
    fun `M not publish obsolete readiness W older network finishes after newer context request`() {
        createManager(ClientReadyPolicy.NETWORK)
        testedManager.updateEvaluationsForContext(fakeContext, mockCallback)
        val oldOperation = networkOperation
        testedManager.updateEvaluationsForContext(forge.getForgery(), mock<EvaluationContextCallback>())
        whenever(mockReader.readPrecomputedFlags(any(), any())).thenReturn(fakeResponse)
        whenever(mockMapper.map(fakeResponse)).thenReturn(fakeEntry.flags)

        oldOperation.run()

        assertThat(stateManager.getCurrentState()).isEqualTo(FlagsClientState.Reconciling)
        assertThat(testedRepository.hasLoadedConfiguration()).isFalse()
    }

    @Test
    fun `M recover W disk completes after initial failure {cache policy}`() {
        createManager(ClientReadyPolicy.CACHE_OR_NETWORK)
        testedManager.updateEvaluationsForContext(fakeContext, mockCallback)
        networkOperation.run()

        loadDisk()

        assertThat(stateManager.getCurrentState()).isEqualTo(FlagsClientState.Ready)
        verify(mockCallback).onFailure(any())
        verify(mockCallback, never()).onSuccess()
    }

    @Test
    fun `M not publish readiness W stop before disk and network completion`() {
        createManager(ClientReadyPolicy.CACHE_OR_NETWORK)
        testedManager.updateEvaluationsForContext(fakeContext, mockCallback)
        whenever(mockReader.readPrecomputedFlags(any(), any())).thenReturn(fakeResponse)
        whenever(mockMapper.map(fakeResponse)).thenReturn(fakeEntry.flags)

        testedManager.stop()
        loadDisk()
        networkOperation.run()

        assertThat(stateManager.getCurrentState()).isEqualTo(FlagsClientState.NotReady)
        verify(mockCallback).onFailure(any())
        verify(mockCallback, never()).onSuccess()
    }

    @Test
    fun `M notify only accepted installs W disk loses to network`() {
        val snapshots = mutableListOf<Map<String, PrecomputedFlag>>()
        testedRepository.addConfigurationChangeListener { snapshots.add(testedRepository.getFlagsSnapshot()) }
        testedRepository.setFlagsAndContext(fakeContext, fakeEntry.flags)

        loadDisk()

        assertThat(snapshots).containsExactly(fakeEntry.flags)
    }

    @Test
    fun `M notify installed cached snapshot W disk finishes`() {
        val snapshots = mutableListOf<Map<String, PrecomputedFlag>>()
        val listener: () -> Unit = { snapshots.add(testedRepository.getFlagsSnapshot()); Unit }
        testedRepository.addConfigurationChangeListener(listener)

        loadDisk()
        testedRepository.removeConfigurationChangeListener(listener)
        testedRepository.setFlagsAndContext(fakeContext, emptyMap())

        assertThat(snapshots).hasSize(1)
        assertThat(snapshots.single()).isEqualTo(
            fakeEntry.flags.mapValues { (_, flag) ->
                flag.copy(reason = "CACHED")
            }
        )
    }

    @Test
    fun `M not notify W disk read fails`() {
        val mockListener = mock<() -> Unit>()
        testedRepository.addConfigurationChangeListener(mockListener)

        diskRead.onFailure()

        verify(mockListener, never()).invoke()
    }

    @Test
    fun `M cancel initialization timeout W disk satisfies cache policy`() {
        var timeoutAction: (() -> Unit)? = null
        val mockCancelTimeout = mock<() -> Unit>()
        testedManager = EvaluationsManager(
            mockSdkCore, mockExecutor, mockSdkCore.internalLogger, testedRepository, mockReader,
            mockMapper, stateManager, forge.aLong(min = 1, max = 5000),
            { _, action -> timeoutAction = action; mockCancelTimeout }, ClientReadyPolicy.CACHE_OR_NETWORK
        )
        testedManager.updateEvaluationsForContext(fakeContext, mockCallback)

        loadDisk()
        checkNotNull(timeoutAction).invoke()

        assertThat(stateManager.getCurrentState()).isEqualTo(FlagsClientState.Ready)
        verify(mockCancelTimeout).invoke()
        verify(mockCallback).onSuccess()
        verify(mockCallback, never()).onFailure(any())
    }

    @Test
    fun `M satisfy cache policy W disk finishes during network request`() {
        createManager(ClientReadyPolicy.CACHE_OR_NETWORK)
        testedManager.updateEvaluationsForContext(fakeContext, mockCallback)
        whenever(mockReader.readPrecomputedFlags(any(), any())).doAnswer {
            loadDisk()
            assertThat(stateManager.getCurrentState()).isEqualTo(FlagsClientState.Ready)
            verify(mockCallback).onSuccess()
            null
        }

        networkOperation.run()

        assertThat(stateManager.getCurrentState()).isEqualTo(FlagsClientState.Ready)
        verify(mockCallback, never()).onFailure(any())
    }

    private fun createManager(policy: ClientReadyPolicy) {
        testedManager =
            EvaluationsManager(
                mockSdkCore, mockExecutor, mockSdkCore.internalLogger, testedRepository, mockReader,
                mockMapper, stateManager, null, { _, _ -> {} }, policy
            )
    }

    private fun loadDisk() {
        diskRead.onSuccess(DataStoreContent(versionCode = forge.aPositiveInt(), data = fakeEntry))
    }
}
