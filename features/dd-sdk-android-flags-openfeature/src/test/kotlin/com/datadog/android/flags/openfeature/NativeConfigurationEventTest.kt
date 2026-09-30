/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.openfeature

import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.datastore.DataStoreHandler
import com.datadog.android.api.storage.datastore.DataStoreReadCallback
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.persistence.datastore.DataStoreContent
import com.datadog.android.flags.EvaluationContextCallback
import com.datadog.android.flags.Flags
import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags.FlagsConfiguration
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.FlagsClientEventType.CONFIGURATION_CHANGED
import com.datadog.android.flags.model.FlagsClientState
import com.datadog.android.flags.model.ResolutionReason
import dev.openfeature.kotlin.sdk.OpenFeatureAPI
import dev.openfeature.kotlin.sdk.OpenFeatureStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class NativeConfigurationEventTest {
    @Test
    fun `M resolve native cached data while OF stays not ready W disk installation notification`() = runTest {
        val core = mock<FeatureSdkCore>()
        whenever(core.internalLogger).thenReturn(mock())
        whenever(core.createSingleThreadExecutorService(any())).thenReturn(mock())
        whenever(core.createOkHttpCallFactory()).thenReturn(mock())
        Flags.enable(FlagsConfiguration.Builder().trackEvaluations(false).build(), core)
        val feature = argumentCaptor<Feature>()
        verify(core).registerFeature(feature.capture())
        val scope = mock<FeatureScope>()
        whenever(scope.unwrap<Feature>()).thenReturn(feature.firstValue)
        whenever(core.getFeature(Feature.FLAGS_FEATURE_NAME)).thenReturn(scope)
        val store = mock<DataStoreHandler>()
        whenever(scope.dataStore).thenReturn(store)
        lateinit var deliverDisk: () -> Unit
        doAnswer { invocation ->
            val callback = invocation.getArgument<DataStoreReadCallback<Any>>(2)
            val deserializer = invocation.getArgument<Deserializer<String, Any>>(3)
            deliverDisk = {
                val restored = checkNotNull(deserializer.deserialize(DISK_STATE))
                callback.onSuccess(DataStoreContent(1, restored))
            }
            null
        }.whenever(store).value<Any>(any(), anyOrNull(), any(), any())
        var events = 0
        val native = FlagsClient.Builder(sdkCore = core).addHandler(CONFIGURATION_CHANGED) {
            val client = FlagsClient.get(sdkCore = core)
            assertThat(client.state.getCurrentState()).isEqualTo(FlagsClientState.NotReady)
            assertThat(client.resolve("flag", false).value).isTrue()
            assertThat(client.resolve("flag", false).reason).isEqualTo(ResolutionReason.CACHED)
            assertThat(OpenFeatureAPI.getStatus()).isEqualTo(OpenFeatureStatus.NotReady)
            assertThat(OpenFeatureAPI.getClient().getBooleanValue("flag", false)).isFalse()
            events++
        }.build()
        // Hold only the initialization completion bridge. Disk loading, native resolution, the
        // Datadog provider and the pinned OpenFeature SDK are real; transport is not under test.
        lateinit var completion: EvaluationContextCallback
        val heldInitialization = object : FlagsClient by native {
            override fun setEvaluationContext(context: EvaluationContext, callback: EvaluationContextCallback?) {
                completion = checkNotNull(callback)
            }
        }
        val provider = DatadogFlagsProvider.wrap(heldInitialization, core)
        val initialization = launch(UnconfinedTestDispatcher(testScheduler)) {
            OpenFeatureAPI.setProviderAndWait(provider, dispatcher = UnconfinedTestDispatcher(testScheduler))
        }
        try {
            deliverDisk()
            assertThat(events).isEqualTo(1)
            assertThat(initialization.isCompleted).isFalse()
            assertThat(OpenFeatureAPI.getStatus()).isEqualTo(OpenFeatureStatus.NotReady)
        } finally {
            completion.onSuccess()
            initialization.join()
            OpenFeatureAPI.shutdown()
        }
    }

    private companion object {
        val DISK_STATE = """
            {"evaluationContext":{"targetingKey":"disk","attributes":{}},"lastUpdateTimestamp":0,
            "flags":{"flag":{"variationType":"boolean","variationValue":"true","doLog":false,
            "allocationKey":"allocation","variationKey":"variant","extraLogging":{},"reason":"STATIC"}}}
        """.trimIndent()
    }
}
