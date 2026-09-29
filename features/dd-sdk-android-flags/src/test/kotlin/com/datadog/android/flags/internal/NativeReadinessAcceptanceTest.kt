/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.datastore.DataStoreHandler
import com.datadog.android.api.storage.datastore.DataStoreReadCallback
import com.datadog.android.core.persistence.datastore.DataStoreContent
import com.datadog.android.flags.ClientReadyPolicy
import com.datadog.android.flags.EvaluationContextCallback
import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags.FlagsConfiguration
import com.datadog.android.flags.FlagsStateListener
import com.datadog.android.flags.internal.evaluation.EvaluationsManager
import com.datadog.android.flags.internal.model.FlagsStateEntry
import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.datadog.android.flags.internal.model.VariationType
import com.datadog.android.flags.internal.net.PrecomputedAssignmentsReader
import com.datadog.android.flags.internal.repository.DefaultFlagsRepository
import com.datadog.android.flags.internal.repository.net.PrecomputeMapper
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.FlagsClientState
import com.datadog.android.flags.model.ResolutionReason
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
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.util.concurrent.ExecutorService

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(ForgeConfigurator::class)
internal class NativeReadinessAcceptanceTest {
    private val mockSdk = mock<FeatureSdkCore>()
    private val mockStore = mock<DataStoreHandler>()
    private val mockExecutor = mock<ExecutorService>()
    private val mockReader = mock<PrecomputedAssignmentsReader>()
    private val mockMapper = mock<PrecomputeMapper>()
    private val mockCallback = mock<EvaluationContextCallback>()
    private lateinit var diskRead: DataStoreReadCallback<FlagsStateEntry>
    private lateinit var network: Runnable
    private lateinit var testedClient: FlagsClient
    private lateinit var fakeContext: EvaluationContext
    private lateinit var fakeFlag: PrecomputedFlag
    private lateinit var fakeKey: String
    private lateinit var forge: Forge
    private var fakeValue = false

    @BeforeEach
    fun setUp(forge: Forge) {
        this.forge = forge
        fakeContext = forge.getForgery()
        fakeKey = forge.anAlphabeticalString()
        fakeValue = forge.aBool()
        fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.BOOLEAN.value,
            variationValue = fakeValue.toString(),
            reason = forge.aValueFrom(
                ResolutionReason::class.java,
                exclude = listOf(ResolutionReason.CACHED, ResolutionReason.STALE)
            ).name
        )
        whenever(mockSdk.internalLogger).thenReturn(mock())
        whenever(mockSdk.timeProvider).thenReturn(mock())
        whenever(mockStore.value<FlagsStateEntry>(any(), anyOrNull(), any(), any())).doAnswer {
            diskRead = it.getArgument(2)
            null
        }
        val scope = mock<FeatureScope>()
        whenever(mockSdk.getFeature(Feature.FLAGS_FEATURE_NAME)).thenReturn(scope)
        whenever(scope.withContext(any(), any())).doAnswer {
            it.getArgument<(DatadogContext) -> Unit>(1)(forge.getForgery())
            null
        }
        whenever(mockExecutor.execute(any())).doAnswer {
            network = it.getArgument(0)
            null
        }
        val repository =
            DefaultFlagsRepository(
                mockSdk,
                forge.anAlphabeticalString(),
                mockStore,
                persistenceLoadTimeoutMs = 0
            )
        val state = FlagsStateManager(
            DDCoreStateHolder.create(FlagsClientState.NotReady, FlagsStateListener::onStateChanged)
        )
        val manager = EvaluationsManager(
            mockSdk, mockExecutor, mockSdk.internalLogger, repository, mockReader, mockMapper, state,
            null, { _, _ -> {} }, ClientReadyPolicy.CACHE_OR_NETWORK
        )
        testedClient = DatadogFlagsClient(
            mockSdk,
            manager,
            repository,
            forge.getForgery<FlagsConfiguration>().copy(trackExposures = false, rumIntegrationEnabled = false),
            mock(),
            mock(),
            null,
            state
        )
    }

    @Test
    fun `M expose installed cached details inside state and success callbacks W matching disk load`() {
        testedClient.state.addListener(object : FlagsStateListener {
            override fun onStateChanged(newState: FlagsClientState) {
                if (newState == FlagsClientState.Ready) assertDetails(ResolutionReason.CACHED)
            }
        })
        whenever(mockCallback.onSuccess()).doAnswer {
            assertThat(testedClient.state.getCurrentState()).isEqualTo(FlagsClientState.Ready)
            assertDetails(ResolutionReason.CACHED)
            null
        }
        testedClient.setEvaluationContext(fakeContext, mockCallback)

        loadDisk()

        verify(mockCallback).onSuccess()
        verifyNoMoreInteractions(mockCallback)
        verify(mockReader, never()).readPrecomputedFlags(any(), any())
    }

    @Test
    fun `M expose cached details W disk loads before first context`() {
        loadDisk()

        assertThat(testedClient.state.getCurrentState()).isEqualTo(FlagsClientState.Ready)
        assertDetails(ResolutionReason.CACHED)
        testedClient.setEvaluationContext(fakeContext, mockCallback)
        verify(mockCallback).onSuccess()
        verifyNoMoreInteractions(mockCallback)
    }

    @Test
    fun `M complete availability with stale reason W mismatched disk {provisional eligibility}`() {
        val otherContext = fakeContext.copy(targetingKey = fakeContext.targetingKey + forge.anAlphabeticalString())
        testedClient.setEvaluationContext(otherContext, mockCallback)

        loadDisk()

        assertThat(testedClient.state.getCurrentState()).isEqualTo(FlagsClientState.Ready)
        assertDetails(ResolutionReason.STALE)
        verify(mockCallback).onSuccess()
        verifyNoMoreInteractions(mockCallback)
    }

    @Test
    fun `M notify ready twice W network replaces cache with new value and original reason`() {
        val observedValues = mutableListOf<Boolean>()
        val observedReasons = mutableListOf<ResolutionReason?>()
        testedClient.state.addListener(object : FlagsStateListener {
            override fun onStateChanged(newState: FlagsClientState) {
                if (newState == FlagsClientState.Ready) {
                    val details = testedClient.resolve(fakeKey, fakeValue)
                    observedValues.add(details.value)
                    observedReasons.add(details.reason)
                    assertThat(details.errorCode).isNull()
                }
            }
        })
        testedClient.setEvaluationContext(fakeContext, mockCallback)
        loadDisk()
        val fakeResponse = forge.anAlphabeticalString()
        val fakeNetworkFlag = fakeFlag.copy(variationValue = (!fakeValue).toString())
        whenever(mockReader.readPrecomputedFlags(any(), any())).thenReturn(fakeResponse)
        whenever(mockMapper.map(fakeResponse)).thenReturn(mapOf(fakeKey to fakeNetworkFlag))

        network.run()

        assertThat(testedClient.state.getCurrentState()).isEqualTo(FlagsClientState.Ready)
        assertThat(observedValues).containsExactly(fakeValue, !fakeValue)
        assertThat(observedReasons).containsExactly(ResolutionReason.CACHED, ResolutionReason.valueOf(fakeFlag.reason))
        verify(mockCallback).onSuccess()
        verifyNoMoreInteractions(mockCallback)
    }

    @Test
    fun `M complete availability W installed configuration is empty`() {
        testedClient.setEvaluationContext(fakeContext, mockCallback)

        loadDisk(emptyMap())

        assertThat(testedClient.state.getCurrentState()).isEqualTo(FlagsClientState.Ready)
        verify(mockCallback).onSuccess()
        verifyNoMoreInteractions(mockCallback)
    }

    @Test
    fun `M wait for pending disk W network fails before disk success`() {
        testedClient.setEvaluationContext(fakeContext, mockCallback)
        network.run()
        verifyNoMoreInteractions(mockCallback)

        loadDisk()

        assertThat(testedClient.state.getCurrentState()).isEqualTo(FlagsClientState.Stale)
        assertDetails(ResolutionReason.CACHED)
        verify(mockCallback).onSuccess()
        verifyNoMoreInteractions(mockCallback)
    }

    @Test
    fun `M fail only after disk exhausted W network fails before disk miss`() {
        testedClient.setEvaluationContext(fakeContext, mockCallback)
        network.run()
        verifyNoMoreInteractions(mockCallback)

        diskRead.onSuccess(null)

        assertThat(testedClient.state.getCurrentState()).isInstanceOf(FlagsClientState.Error::class.java)
        verify(mockCallback).onFailure(any())
        verifyNoMoreInteractions(mockCallback)
    }

    private fun loadDisk(flags: Map<String, PrecomputedFlag> = mapOf(fakeKey to fakeFlag)) {
        diskRead.onSuccess(DataStoreContent(forge.aPositiveInt(), FlagsStateEntry(fakeContext, flags, forge.aLong())))
    }

    private fun assertDetails(reason: ResolutionReason) {
        val details = testedClient.resolve(fakeKey, !fakeValue)
        assertThat(details.value).isEqualTo(fakeValue)
        assertThat(details.reason).isEqualTo(reason)
        assertThat(details.errorCode).isNull()
        assertThat(details.variant).isEqualTo(fakeFlag.variationKey)
    }
}
