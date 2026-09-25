/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.openfeature

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.datastore.DataStoreHandler
import com.datadog.android.api.storage.datastore.DataStoreReadCallback
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.persistence.datastore.DataStoreContent
import com.datadog.android.flags.ClientReadyPolicy
import com.datadog.android.flags.Flags
import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags.FlagsConfiguration
import com.datadog.android.flags.model.FlagsClientState
import com.datadog.android.tests.elmyr.useCoreFactories
import dev.openfeature.kotlin.sdk.ImmutableContext
import dev.openfeature.kotlin.sdk.OpenFeatureAPI
import dev.openfeature.kotlin.sdk.OpenFeatureStatus
import dev.openfeature.kotlin.sdk.events.OpenFeatureProviderEvents
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.ExecutorService

@ExtendWith(ForgeExtension::class)
internal class ClientReadyPolicyIntegrationTest {
    @Test
    fun `M expose cached evaluation W disk load {explicit ready and network not dispatched}`(forge: Forge) = runTest {
        val fixture = Fixture(forge, ClientReadyPolicy.CACHE_OR_NETWORK)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val events = mutableListOf<OpenFeatureProviderEvents>()
        val collector = launch(dispatcher) { fixture.provider.observe().collect { events.add(it) } }
        val initialization = async(dispatcher) {
            OpenFeatureAPI.setProviderAndWait(fixture.provider, ImmutableContext(fixture.targetingKey), dispatcher)
        }
        try {
            testScheduler.runCurrent()
            assertThat(OpenFeatureAPI.getStatus()).isEqualTo(OpenFeatureStatus.NotReady)
            assertThat(initialization.isCompleted).isFalse()

            fixture.loadDisk()
            testScheduler.runCurrent()

            assertThat(events).contains(OpenFeatureProviderEvents.ProviderReady)
            assertThat(fixture.client.state.getCurrentState()).isEqualTo(FlagsClientState.Ready)
            assertThat(OpenFeatureAPI.getStatus()).isEqualTo(OpenFeatureStatus.Ready)
            assertThat(initialization.isCompleted).isTrue()
            initialization.await()
            val details = OpenFeatureAPI.getClient().getBooleanDetails(fixture.flagKey, !fixture.flagValue)
            assertThat(details.value).isEqualTo(fixture.flagValue)
            assertThat(details.reason).isEqualTo("CACHED")
            verify(fixture.callFactory, never()).newCall(any())

            fixture.networkOperation.run()
            testScheduler.runCurrent()
            assertThat(events.count { it == OpenFeatureProviderEvents.ProviderReady }).isEqualTo(1)
            assertThat(events).contains(OpenFeatureProviderEvents.ProviderConfigurationChanged)
            assertThat(OpenFeatureAPI.getClient().getBooleanDetails(fixture.flagKey, fixture.flagValue).value)
                .isEqualTo(!fixture.flagValue)
        } finally {
            collector.cancelAndJoin()
            OpenFeatureAPI.shutdown()
        }
    }

    @Test
    fun `M remain not ready W disk notification {network policy}`(forge: Forge) = runTest {
        val fixture = Fixture(forge, ClientReadyPolicy.NETWORK)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val events = mutableListOf<OpenFeatureProviderEvents>()
        val collector = launch(dispatcher) { fixture.provider.observe().collect { events.add(it) } }
        val initialization = async(dispatcher) {
            OpenFeatureAPI.setProviderAndWait(fixture.provider, ImmutableContext(fixture.targetingKey), dispatcher)
        }
        try {
            testScheduler.runCurrent()
            fixture.loadDisk()
            testScheduler.runCurrent()

            assertThat(events).contains(OpenFeatureProviderEvents.ProviderConfigurationChanged)
            assertThat(events).doesNotContain(OpenFeatureProviderEvents.ProviderReady)
            assertThat(OpenFeatureAPI.getStatus()).isEqualTo(OpenFeatureStatus.NotReady)
            assertThat(initialization.isCompleted).isFalse()
            val details = OpenFeatureAPI.getClient().getBooleanDetails(fixture.flagKey, !fixture.flagValue)
            assertThat(details.value).isEqualTo(!fixture.flagValue)
            assertThat(details.errorCode.toString()).isEqualTo("PROVIDER_NOT_READY")

            fixture.networkOperation.run()
            testScheduler.runCurrent()
            initialization.await()
            assertThat(OpenFeatureAPI.getStatus()).isEqualTo(OpenFeatureStatus.Ready)
        } finally {
            if (!initialization.isCompleted) {
                fixture.networkOperation.run()
                testScheduler.runCurrent()
            }
            collector.cancelAndJoin()
            OpenFeatureAPI.shutdown()
        }
    }

    @Test
    fun `M replay readiness W provider initialized after disk load`(forge: Forge) = runTest {
        val fixture = Fixture(forge, ClientReadyPolicy.CACHE_OR_NETWORK)
        fixture.loadDisk()
        val dispatcher = StandardTestDispatcher(testScheduler)
        try {
            OpenFeatureAPI.setProviderAndWait(fixture.provider, ImmutableContext(fixture.targetingKey), dispatcher)
            testScheduler.runCurrent()

            assertThat(OpenFeatureAPI.getStatus()).isEqualTo(OpenFeatureStatus.Ready)
            val details = OpenFeatureAPI.getClient().getBooleanDetails(fixture.flagKey, !fixture.flagValue)
            assertThat(details.value).isEqualTo(fixture.flagValue)
            assertThat(details.reason).isEqualTo("CACHED")
            verify(fixture.callFactory, never()).newCall(any())
        } finally {
            OpenFeatureAPI.shutdown()
        }
    }

    private class Fixture(private val forge: Forge, policy: ClientReadyPolicy) {
        val targetingKey = forge.anAlphabeticalString()
        val flagKey = forge.anAlphabeticalString()
        val flagValue = forge.aBool()
        val callFactory = mock<Call.Factory>()
        val client: FlagsClient
        val provider: DatadogFlagsProvider
        lateinit var networkOperation: Runnable
        private lateinit var completeDisk: () -> Unit

        init {
            com.datadog.tools.unit.forge.BaseConfigurator().configure(forge)
            forge.useCoreFactories()
            val core = mock<FeatureSdkCore>()
            val dataStore = mock<DataStoreHandler>()
            val scope = mock<FeatureScope>()
            val executor = mock<ExecutorService>()
            whenever(core.internalLogger).thenReturn(mock())
            whenever(core.timeProvider).thenReturn(mock())
            whenever(core.createSingleThreadExecutorService(any())).thenReturn(executor)
            whenever(core.createOkHttpCallFactory()).thenReturn(callFactory)
            whenever(executor.execute(any())).doAnswer {
                networkOperation = it.getArgument(0)
                null
            }
            whenever(scope.dataStore).thenReturn(dataStore)
            whenever(scope.withContext(any(), any())).doAnswer {
                it.getArgument<(DatadogContext) -> Unit>(1)(forge.getForgery())
                null
            }
            whenever(dataStore.value<Any>(any(), anyOrNull(), any(), any())).doAnswer {
                val callback = it.getArgument<DataStoreReadCallback<Any>>(2)
                val deserializer = it.getArgument<Deserializer<String, Any>>(3)
                completeDisk = {
                    val entry = checkNotNull(deserializer.deserialize(persistedJson()))
                    callback.onSuccess(DataStoreContent(forge.aPositiveInt(), entry))
                }
                null
            }
            whenever(callFactory.newCall(any())).doAnswer { invocation ->
                val request = invocation.getArgument<Request>(0)
                mock<Call> {
                    whenever(it.execute()).thenReturn(
                        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200)
                            .message(forge.anAlphabeticalString()).body(networkJson().toResponseBody()).build()
                    )
                }
            }
            Flags.enable(
                FlagsConfiguration.Builder().trackEvaluations(false).trackExposures(false).rumIntegrationEnabled(false)
                    .initializationTimeout(0).useCustomFlagEndpoint("https://${forge.anAlphabeticalString()}.com")
                    .build(),
                core
            )
            val feature = argumentCaptor<Feature>().apply { verify(core).registerFeature(capture()) }.firstValue
            whenever(scope.unwrap<Feature>()).thenReturn(feature)
            whenever(core.getFeature(Feature.FLAGS_FEATURE_NAME)).thenReturn(scope)
            client = FlagsClient.Builder(forge.anAlphabeticalString(), core).clientReadyPolicy(policy).build()
            provider = DatadogFlagsProvider.wrap(client, core)
        }

        fun loadDisk() = completeDisk()

        private fun flagJson(value: Boolean) = JSONObject()
            .put("variationType", "boolean").put("variationValue", value.toString()).put("doLog", false)
            .put("allocationKey", forge.anAlphabeticalString()).put("variationKey", forge.anAlphabeticalString())
            .put("extraLogging", JSONObject()).put("reason", "TARGETING_MATCH")

        private fun persistedJson(): String = JSONObject()
            .put("evaluationContext", JSONObject().put("targetingKey", targetingKey).put("attributes", JSONObject()))
            .put("flags", JSONObject().put(flagKey, flagJson(flagValue)))
            .put("lastUpdateTimestamp", forge.aPositiveLong()).toString()

        private fun networkJson(): String = JSONObject().put(
            "data",
            JSONObject().put(
                "attributes",
                JSONObject()
                    .put("flags", JSONObject().put(flagKey, flagJson(!flagValue)))
            )
        ).toString()
    }
}
