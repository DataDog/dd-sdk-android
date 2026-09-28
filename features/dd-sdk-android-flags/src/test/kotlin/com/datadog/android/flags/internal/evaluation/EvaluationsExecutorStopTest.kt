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
import com.datadog.android.flags.EvaluationContextCallback
import com.datadog.android.flags.FlagsStateListener
import com.datadog.android.flags.internal.FlagsStateManager
import com.datadog.android.flags.internal.net.PrecomputedAssignmentsReader
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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(ForgeConfigurator::class)
internal class EvaluationsExecutorStopTest {

    @Test
    fun `M terminate executor without interrupting admitted operations W stop`(forge: Forge) {
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, forge.anAlphabeticalString()).apply { isDaemon = true }
        }
        val mockSdk = mock<FeatureSdkCore>()
        val mockScope = mock<FeatureScope>()
        val mockRepository = mock<FlagsRepository>()
        val mockReader = mock<PrecomputedAssignmentsReader>()
        val mockMapper = mock<PrecomputeMapper>()
        val mockInitialCallback = mock<EvaluationContextCallback>()
        val mockQueuedCallback = mock<EvaluationContextCallback>()
        val fakeContext = forge.getForgery<EvaluationContext>()
        val fakeResponse = forge.anAlphabeticalString()
        val enteredNetwork = CountDownLatch(1)
        val releaseNetwork = CountDownLatch(1)
        val interrupted = AtomicBoolean(false)
        whenever(mockSdk.internalLogger).thenReturn(mock())
        whenever(mockSdk.getFeature(Feature.FLAGS_FEATURE_NAME)).thenReturn(mockScope)
        whenever(mockScope.withContext(any(), any())).doAnswer {
            it.getArgument<(DatadogContext) -> Unit>(1)(forge.getForgery())
            null
        }
        whenever(mockReader.readPrecomputedFlags(any(), any())).doAnswer {
            enteredNetwork.countDown()
            try {
                check(releaseNetwork.await(5, TimeUnit.SECONDS))
            } catch (error: InterruptedException) {
                interrupted.set(true)
                throw error
            }
            fakeResponse
        }
        whenever(mockMapper.map(fakeResponse)).thenReturn(emptyMap())
        val stateManager = FlagsStateManager(
            DDCoreStateHolder.create(FlagsClientState.NotReady, FlagsStateListener::onStateChanged)
        )
        val testedManager = EvaluationsManager(
            mockSdk, executor, mockSdk.internalLogger, mockRepository, mockReader,
            mockMapper, stateManager, null, { _, _ -> {} }
        )
        try {
            testedManager.updateEvaluationsForContext(fakeContext, mockInitialCallback)
            assertThat(enteredNetwork.await(5, TimeUnit.SECONDS)).isTrue()
            testedManager.updateEvaluationsForContext(fakeContext, mockQueuedCallback)
            val stop = FutureTask { testedManager.stop() }

            thread(isDaemon = true) { stop.run() }
            stop.get(1, TimeUnit.SECONDS)

            assertThat(executor.isShutdown).isTrue()
            assertThat(executor.isTerminated).isFalse()
            assertThat(stateManager.getCurrentState()).isEqualTo(FlagsClientState.NotReady)
            releaseNetwork.countDown()
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue()
            assertThat(interrupted).isFalse()
            assertThat(stateManager.getCurrentState()).isEqualTo(FlagsClientState.NotReady)
            verify(mockInitialCallback).onFailure(any())
            verify(mockQueuedCallback).onSuccess()
            verifyNoMoreInteractions(mockInitialCallback, mockQueuedCallback)
            verify(mockRepository, never()).setFlagsAndContext(any(), any())
        } finally {
            releaseNetwork.countDown()
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `M fail each admitted callback once W feature context delivered after stop`(forge: Forge) {
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, forge.anAlphabeticalString()).apply { isDaemon = true }
        }
        val mockSdk = mock<FeatureSdkCore>()
        val mockScope = mock<FeatureScope>()
        val mockRepository = mock<FlagsRepository>()
        val mockReader = mock<PrecomputedAssignmentsReader>()
        val mockInitialCallback = mock<EvaluationContextCallback>()
        val mockSubsequentCallback = mock<EvaluationContextCallback>()
        val pendingContexts = mutableListOf<(DatadogContext) -> Unit>()
        val fakeContext = forge.getForgery<EvaluationContext>()
        whenever(mockSdk.internalLogger).thenReturn(mock())
        whenever(mockSdk.getFeature(Feature.FLAGS_FEATURE_NAME)).thenReturn(mockScope)
        whenever(mockScope.withContext(any(), any())).doAnswer {
            pendingContexts.add(it.getArgument(1))
            null
        }
        val stateManager = FlagsStateManager(
            DDCoreStateHolder.create(FlagsClientState.NotReady, FlagsStateListener::onStateChanged)
        )
        val testedManager = EvaluationsManager(
            mockSdk, executor, mockSdk.internalLogger, mockRepository, mockReader,
            mock(), stateManager, null, { _, _ -> {} }
        )
        try {
            testedManager.updateEvaluationsForContext(fakeContext, mockInitialCallback)
            testedManager.updateEvaluationsForContext(fakeContext, mockSubsequentCallback)
            assertThat(pendingContexts).hasSize(2)

            testedManager.stop()
            pendingContexts.forEach { callback -> callback(forge.getForgery()) }

            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue()
            verify(mockInitialCallback).onFailure(any())
            verify(mockSubsequentCallback).onFailure(any())
            verifyNoMoreInteractions(mockInitialCallback, mockSubsequentCallback)
            verify(mockReader, never()).readPrecomputedFlags(any(), any())
            verify(mockRepository, never()).setFlagsAndContext(any(), any())
            assertThat(stateManager.getCurrentState()).isEqualTo(FlagsClientState.NotReady)
        } finally {
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `M release lifecycle monitor before failure callback W context requested after stop`(forge: Forge) {
        val mockSdk = mock<FeatureSdkCore>()
        val mockExecutor = mock<ExecutorService>()
        val mockRepository = mock<FlagsRepository>()
        val mockReader = mock<PrecomputedAssignmentsReader>()
        val mockCallback = mock<EvaluationContextCallback>()
        val acquiredMonitor = CountDownLatch(1)
        val callbackSawUnlockedMonitor = AtomicBoolean(false)
        val stateManager = FlagsStateManager(
            DDCoreStateHolder.create(FlagsClientState.NotReady, FlagsStateListener::onStateChanged)
        )
        whenever(mockSdk.internalLogger).thenReturn(mock())
        val testedManager = EvaluationsManager(
            mockSdk, mockExecutor, mockSdk.internalLogger, mockRepository, mockReader,
            mock(), stateManager, null, { _, _ -> {} }
        )
        whenever(mockCallback.onFailure(any())).doAnswer {
            thread(isDaemon = true) {
                stateManager.getCurrentState()
                acquiredMonitor.countDown()
            }
            callbackSawUnlockedMonitor.set(acquiredMonitor.await(1, TimeUnit.SECONDS))
            null
        }
        testedManager.stop()

        testedManager.updateEvaluationsForContext(forge.getForgery(), mockCallback)

        assertThat(acquiredMonitor.await(5, TimeUnit.SECONDS)).isTrue()
        assertThat(callbackSawUnlockedMonitor).isTrue()
        verify(mockCallback).onFailure(any())
        verifyNoMoreInteractions(mockCallback)
        verify(mockReader, never()).readPrecomputedFlags(any(), any())
        verify(mockExecutor, never()).execute(any())
    }
}
