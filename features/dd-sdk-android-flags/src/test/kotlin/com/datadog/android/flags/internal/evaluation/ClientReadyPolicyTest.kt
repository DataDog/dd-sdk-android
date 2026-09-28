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
import com.datadog.android.flags.internal.repository.FlagsRepository
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

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
    fun `M publish stale availability W disk install {cache policy and network not dispatched}`() {
        createManager(ClientReadyPolicy.CACHE_OR_NETWORK)
        testedManager.updateEvaluationsForContext(fakeContext, mockCallback)

        loadDisk()

        assertThat(stateManager.getCurrentState()).isEqualTo(FlagsClientState.Stale)
        verify(mockCallback).onSuccess()
        verify(mockReader, never()).readPrecomputedFlags(any(), any())
    }

    @Test
    fun `M wait for network W disk install {network policy}`() {
        createManager(ClientReadyPolicy.NETWORK)
        testedManager.updateEvaluationsForContext(fakeContext, mockCallback)

        loadDisk()

        assertThat(stateManager.getCurrentState()).isNotEqualTo(FlagsClientState.Stale)
        verify(mockCallback, never()).onSuccess()
    }

    @Test
    fun `M use already restored cache W first context {cache policy}`() {
        loadDisk()
        createManager(ClientReadyPolicy.CACHE_OR_NETWORK)

        testedManager.updateEvaluationsForContext(fakeContext, mockCallback)

        assertThat(stateManager.getCurrentState()).isEqualTo(FlagsClientState.Stale)
        verify(mockCallback).onSuccess()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M release lifecycle lock W restored cache completes initialization`(immediateTimeout: Boolean) {
        loadDisk()
        testedManager = EvaluationsManager(
            mockSdkCore, mockExecutor, mockSdkCore.internalLogger, testedRepository, mockReader,
            mockMapper, stateManager, if (immediateTimeout) 0 else null,
            { _, action -> action(); {} }, ClientReadyPolicy.CACHE_OR_NETWORK
        )
        val stateReadCompleted = CountDownLatch(1)
        val observedState = AtomicReference<FlagsClientState>()
        var stateReader: Thread? = null
        whenever(mockCallback.onSuccess()).doAnswer {
            stateReader = thread(isDaemon = true) {
                observedState.set(stateManager.getCurrentState())
                stateReadCompleted.countDown()
            }
            assertThat(stateReadCompleted.await(5, TimeUnit.SECONDS)).isTrue()
            null
        }

        try {
            testedManager.updateEvaluationsForContext(fakeContext, mockCallback)
        } finally {
            stateReader?.join(5000)
        }

        assertThat(observedState.get()).isEqualTo(FlagsClientState.Stale)
        verify(mockCallback).onSuccess()
        verify(mockCallback, never()).onFailure(any())
    }

    @Test
    fun `M settle successfully W network failure {network policy and cached assignments}`() {
        createManager(ClientReadyPolicy.NETWORK)
        testedManager.updateEvaluationsForContext(fakeContext, mockCallback)
        loadDisk()

        networkOperation.run()

        assertThat(stateManager.getCurrentState()).isEqualTo(FlagsClientState.Stale)
        verify(mockCallback).onSuccess()
        verify(mockCallback, never()).onFailure(any())
    }

    @Test
    fun `M retain stale cache W background network failure`() {
        createManager(ClientReadyPolicy.CACHE_OR_NETWORK)
        testedManager.updateEvaluationsForContext(fakeContext, mockCallback)
        loadDisk()

        networkOperation.run()

        assertThat(stateManager.getCurrentState()).isEqualTo(FlagsClientState.Stale)
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
    fun `M publish stale availability W valid empty cache`() {
        createManager(ClientReadyPolicy.CACHE_OR_NETWORK)
        testedManager.updateEvaluationsForContext(fakeContext, mockCallback)
        fakeEntry = fakeEntry.copy(flags = emptyMap())

        loadDisk()

        assertThat(stateManager.getCurrentState()).isEqualTo(FlagsClientState.Stale)
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
        assertThat(stateManager.getCurrentState()).isEqualTo(FlagsClientState.Ready)
        verify(mockCallback).onSuccess()
    }

    @Test
    fun `M not become ready W failed disk read`() {
        createManager(ClientReadyPolicy.CACHE_OR_NETWORK)
        testedManager.updateEvaluationsForContext(fakeContext, mockCallback)

        diskRead.onFailure()

        assertThat(stateManager.getCurrentState()).isNotEqualTo(FlagsClientState.Stale)
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
    fun `M wait for disk W initial network failure {cache policy}`() {
        createManager(ClientReadyPolicy.CACHE_OR_NETWORK)
        testedManager.updateEvaluationsForContext(fakeContext, mockCallback)
        networkOperation.run()

        loadDisk()

        assertThat(stateManager.getCurrentState()).isEqualTo(FlagsClientState.Stale)
        verify(mockCallback, never()).onFailure(any())
        verify(mockCallback).onSuccess()
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

        // Stop suppresses lifecycle publication; it does not cancel the pending disk read.
        assertThat(testedRepository.getEvaluationContext()).isEqualTo(fakeContext)
        assertThat(testedRepository.getFlagsSnapshot()).isEqualTo(
            fakeEntry.flags.mapValues { (_, flag) -> flag.copy(reason = "CACHED") }
        )
        assertThat(stateManager.getCurrentState()).isEqualTo(FlagsClientState.NotReady)
        verify(mockCallback).onFailure(any())
        verify(mockCallback, never()).onSuccess()
    }

    @Test
    fun `M notify exhausted cache load W disk loses to network`() {
        val snapshots = mutableListOf<Map<String, PrecomputedFlag>>()
        testedRepository.setOnCacheLoadCompletedListener { snapshots.add(testedRepository.getFlagsSnapshot()) }
        testedRepository.setFlagsAndContext(fakeContext, fakeEntry.flags)

        loadDisk()

        assertThat(snapshots).containsExactly(fakeEntry.flags)
    }

    @Test
    fun `M notify installed cached snapshot W disk finishes`() {
        val snapshots = mutableListOf<Map<String, PrecomputedFlag>>()
        val listener: () -> Unit = { snapshots.add(testedRepository.getFlagsSnapshot()); Unit }
        testedRepository.setOnCacheLoadCompletedListener(listener)

        loadDisk()
        testedRepository.setFlagsAndContext(fakeContext, emptyMap())

        assertThat(snapshots).hasSize(1)
        assertThat(snapshots.single()).isEqualTo(
            fakeEntry.flags.mapValues { (_, flag) ->
                flag.copy(reason = "CACHED")
            }
        )
    }

    @Test
    fun `M notify exhaustion W disk read fails`() {
        val mockListener = mock<() -> Unit>()
        testedRepository.setOnCacheLoadCompletedListener(mockListener)

        diskRead.onFailure()

        verify(mockListener).invoke()
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

        assertThat(stateManager.getCurrentState()).isEqualTo(FlagsClientState.Stale)
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
            assertThat(stateManager.getCurrentState()).isEqualTo(FlagsClientState.Stale)
            verify(mockCallback).onSuccess()
            null
        }

        networkOperation.run()

        assertThat(stateManager.getCurrentState()).isEqualTo(FlagsClientState.Stale)
        verify(mockCallback, never()).onFailure(any())
    }

    @Test
    fun `M publish stale availability W cache installed before any context request`() {
        createManager(ClientReadyPolicy.CACHE_OR_NETWORK)

        loadDisk()

        assertThat(stateManager.getCurrentState()).isEqualTo(FlagsClientState.Stale)
        verify(mockExecutor, never()).execute(any())
    }

    @ParameterizedTest
    @EnumSource(ClientReadyPolicy::class)
    fun `M use mismatched cache W initial fetch fails {current eligibility policy}`(policy: ClientReadyPolicy) {
        val requestedContext = forge.getForgery<EvaluationContext>().copy(
            targetingKey = fakeContext.targetingKey + forge.anAlphabeticalString()
        )
        createManager(policy)
        testedManager.updateEvaluationsForContext(requestedContext, mockCallback)
        loadDisk()

        networkOperation.run()

        assertThat(stateManager.getCurrentState()).isEqualTo(FlagsClientState.Stale)
        assertThat(testedRepository.getEvaluationContext()).isEqualTo(fakeContext)
        verify(mockCallback).onSuccess()
        verify(mockCallback, never()).onFailure(any())
    }

    @Test
    fun `M accept empty installed configuration W initial network failure {network policy}`() {
        createManager(ClientReadyPolicy.NETWORK)
        testedManager.updateEvaluationsForContext(fakeContext, mockCallback)
        fakeEntry = fakeEntry.copy(flags = emptyMap())
        loadDisk()

        networkOperation.run()

        assertThat(stateManager.getCurrentState()).isEqualTo(FlagsClientState.Stale)
        assertThat(testedRepository.hasLoadedConfiguration()).isTrue()
        assertThat(testedRepository.hasFlags()).isFalse()
        verify(mockCallback).onSuccess()
        verify(mockCallback, never()).onFailure(any())
    }

    @Test
    fun `M publish stale before completion W cache satisfies initial request`() {
        createManager(ClientReadyPolicy.CACHE_OR_NETWORK)
        val observedStates = mutableListOf<FlagsClientState>()
        whenever(mockCallback.onSuccess()).doAnswer {
            observedStates.add(stateManager.getCurrentState())
            null
        }
        testedManager.updateEvaluationsForContext(fakeContext, mockCallback)

        loadDisk()
        networkOperation.run()

        assertThat(observedStates).containsExactly(FlagsClientState.Stale)
        verify(mockCallback).onSuccess()
        verify(mockCallback, never()).onFailure(any())
    }

    @Test
    fun `M avoid lock inversion W listener registration reenters during concurrent context update`() {
        createManager(ClientReadyPolicy.NETWORK)
        testedManager.updateEvaluationsForContext(fakeContext)
        val listenerEntered = CountDownLatch(1)
        val releaseListener = CountDownLatch(1)
        val updateStarted = CountDownLatch(1)
        val completed = CountDownLatch(2)
        val didReenter = AtomicBoolean(false)
        val failure = AtomicReference<Throwable?>(null)
        val fakeSecondContext = forge.getForgery<EvaluationContext>()
        val fakeThirdContext = forge.getForgery<EvaluationContext>()
        val listenerThread = thread(isDaemon = true) {
            try {
                stateManager.addListener(object : FlagsStateListener {
                    override fun onStateChanged(newState: FlagsClientState) {
                        if (didReenter.compareAndSet(false, true)) {
                            listenerEntered.countDown()
                            check(releaseListener.await(5, TimeUnit.SECONDS))
                            testedManager.updateEvaluationsForContext(fakeThirdContext)
                        }
                    }
                })
            } catch (error: Throwable) {
                failure.set(error)
            } finally {
                completed.countDown()
            }
        }
        try {
            assertThat(listenerEntered.await(5, TimeUnit.SECONDS)).isTrue()
            val updateThread = thread(isDaemon = true) {
                try {
                    updateStarted.countDown()
                    testedManager.updateEvaluationsForContext(fakeSecondContext)
                } catch (error: Throwable) {
                    failure.set(error)
                } finally {
                    completed.countDown()
                }
            }
            assertThat(updateStarted.await(5, TimeUnit.SECONDS)).isTrue()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (updateThread.state == Thread.State.RUNNABLE && System.nanoTime() < deadline) {
                Thread.yield()
            }
            assertThat(updateThread.state).isIn(Thread.State.BLOCKED, Thread.State.WAITING)
            releaseListener.countDown()
            assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue()
            assertThat(failure.get()).isNull()
            whenever(mockReader.readPrecomputedFlags(any(), any())).thenReturn(fakeResponse)
            whenever(mockMapper.map(fakeResponse)).thenReturn(fakeEntry.flags)
            networkOperation.run()
            assertThat(testedRepository.getEvaluationContext()).isEqualTo(fakeSecondContext)
        } finally {
            releaseListener.countDown()
            listenerThread.join(100)
        }
    }

    @ParameterizedTest
    @EnumSource(ClientReadyPolicy::class)
    fun `M wait for disk after failed network W initialization {both policies}`(policy: ClientReadyPolicy) {
        createManager(policy)
        testedManager.updateEvaluationsForContext(fakeContext, mockCallback)
        networkOperation.run()
        verify(mockCallback, never()).onFailure(any())
        verify(mockCallback, never()).onSuccess()
        assertThat(testedRepository.isCacheLoadPending()).isTrue()

        loadDisk()

        assertThat(stateManager.getCurrentState()).isEqualTo(FlagsClientState.Stale)
        assertThat(testedRepository.isCacheLoadPending()).isFalse()
        verify(mockCallback).onSuccess()
        verify(mockCallback, never()).onFailure(any())
    }

    @ParameterizedTest
    @EnumSource(ClientReadyPolicy::class)
    fun `M fail once W disk exhausted after failed network`(policy: ClientReadyPolicy) {
        createManager(policy)
        testedManager.updateEvaluationsForContext(fakeContext, mockCallback)
        networkOperation.run()
        verify(mockCallback, never()).onFailure(any())

        diskRead.onFailure()

        assertThat(stateManager.getCurrentState()).isInstanceOf(FlagsClientState.Error::class.java)
        verify(mockCallback).onFailure(any())
        verify(mockCallback, never()).onSuccess()
    }

    @Test
    fun `M retain empty matching configuration W refresh failure`() {
        createManager(ClientReadyPolicy.CACHE_OR_NETWORK)
        fakeEntry = fakeEntry.copy(flags = emptyMap())
        loadDisk()
        testedManager.updateEvaluationsForContext(fakeContext, mockCallback)
        networkOperation.run()
        val refreshCallback = mock<EvaluationContextCallback>()

        testedManager.updateEvaluationsForContext(fakeContext, refreshCallback)
        networkOperation.run()

        assertThat(stateManager.getCurrentState()).isEqualTo(FlagsClientState.Stale)
        verify(mockCallback).onSuccess()
        verify(refreshCallback).onFailure(any())
    }

    @Test
    fun `M retain matching configuration W disk arrives during refresh failure`() {
        createManager(ClientReadyPolicy.NETWORK)
        testedManager.updateEvaluationsForContext(fakeContext, mockCallback)
        val initialOperation = networkOperation
        val refreshCallback = mock<EvaluationContextCallback>()
        testedManager.updateEvaluationsForContext(fakeContext, refreshCallback)
        whenever(mockReader.readPrecomputedFlags(any(), any())).doAnswer {
            loadDisk()
            null
        }

        networkOperation.run()
        initialOperation.run()

        assertThat(stateManager.getCurrentState()).isEqualTo(FlagsClientState.Stale)
        verify(refreshCallback).onFailure(any())
    }

    @Test
    fun `M not confuse network publication with disk exhaustion`() {
        testedRepository.setFlagsAndContext(fakeContext, fakeEntry.flags)
        assertThat(testedRepository.isCacheLoadPending()).isTrue()

        diskRead.onFailure()

        assertThat(testedRepository.isCacheLoadPending()).isFalse()
    }

    @Test
    fun `M see installed snapshot W disk exhausts while choosing network failure outcome`() {
        val repository = mock<FlagsRepository>()
        var completeDuringRead = false
        var installed = false
        whenever(repository.isCacheLoadPending()).doAnswer {
            if (completeDuringRead) installed = true
            !installed
        }
        whenever(repository.hasLoadedConfiguration()).doAnswer { installed }
        whenever(repository.hasLoadedConfigurationForContext(fakeContext)).doAnswer { installed }
        testedManager = EvaluationsManager(
            mockSdkCore, mockExecutor, mockSdkCore.internalLogger, repository, mockReader,
            mockMapper, stateManager, null, { _, _ -> {} }, ClientReadyPolicy.NETWORK
        )
        testedManager.updateEvaluationsForContext(fakeContext, mockCallback)
        completeDuringRead = true

        networkOperation.run()

        assertThat(stateManager.getCurrentState()).isEqualTo(FlagsClientState.Stale)
        verify(mockCallback).onSuccess()
        verify(mockCallback, never()).onFailure(any())
    }

    @Test
    fun `M fail pending initialization W disk miss after network failure`() {
        createManager(ClientReadyPolicy.NETWORK)
        testedManager.updateEvaluationsForContext(fakeContext, mockCallback)
        networkOperation.run()

        diskRead.onSuccess(null)

        verify(mockCallback).onFailure(any())
        verify(mockCallback, never()).onSuccess()
    }

    @Test
    fun `M suppress late disk publication W stopped while awaiting disk fallback`() {
        createManager(ClientReadyPolicy.CACHE_OR_NETWORK)
        testedManager.updateEvaluationsForContext(fakeContext, mockCallback)
        networkOperation.run()

        testedManager.stop()
        loadDisk()

        assertThat(stateManager.getCurrentState()).isEqualTo(FlagsClientState.NotReady)
        verify(mockCallback).onFailure(any())
        verify(mockCallback, never()).onSuccess()
    }

    @Test
    fun `M retain callback deadline W disk arrives after network failure and timeout`() {
        var timeoutAction: (() -> Unit)? = null
        testedManager = EvaluationsManager(
            mockSdkCore, mockExecutor, mockSdkCore.internalLogger, testedRepository, mockReader,
            mockMapper, stateManager, forge.aLong(min = 1, max = 5000),
            { _, action -> timeoutAction = action; {} }, ClientReadyPolicy.NETWORK
        )
        testedManager.updateEvaluationsForContext(fakeContext, mockCallback)
        networkOperation.run()
        checkNotNull(timeoutAction).invoke()

        loadDisk()

        assertThat(stateManager.getCurrentState()).isEqualTo(FlagsClientState.Stale)
        verify(mockCallback).onFailure(any())
        verify(mockCallback, never()).onSuccess()
    }

    @Test
    fun `M preserve newer state W old disk fallback settles after supersession`() {
        createManager(ClientReadyPolicy.NETWORK)
        testedManager.updateEvaluationsForContext(fakeContext, mockCallback)
        networkOperation.run()
        val newerCallback = mock<EvaluationContextCallback>()
        testedManager.updateEvaluationsForContext(forge.getForgery(), newerCallback)

        loadDisk()

        assertThat(stateManager.getCurrentState()).isEqualTo(FlagsClientState.Reconciling)
        verify(mockCallback).onSuccess()
        verify(newerCallback, never()).onSuccess()
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
