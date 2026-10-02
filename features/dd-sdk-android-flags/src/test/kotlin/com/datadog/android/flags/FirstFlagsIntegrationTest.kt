/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.datastore.DataStoreHandler
import com.datadog.android.api.storage.datastore.DataStoreReadCallback
import com.datadog.android.api.storage.datastore.DataStoreWriteCallback
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.persistence.Serializer
import com.datadog.android.core.persistence.datastore.DataStoreContent
import com.datadog.android.flags.internal.FlagsFeature
import com.datadog.android.flags.internal.model.FlagsStateEntry
import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.datadog.android.flags.internal.persistence.FlagsStateSerializer
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.FlagsClientEvent
import com.datadog.android.flags.model.FlagsClientState
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Real native client graph and HTTP transport; only SDK host services and storage I/O are substituted. */
internal class FirstFlagsIntegrationTest {
    private val core = mock<FeatureSdkCore>()
    private val logger = mock<InternalLogger>()
    private val scope = mock<FeatureScope>()
    private val store = MemoryStore()
    private val server = MockWebServer()
    private val http = OkHttpClient()
    private val events = mutableListOf<FlagsClientEvent>()
    private lateinit var feature: FlagsFeature

    @BeforeEach
    fun `set up`() {
        server.start()
        whenever(core.internalLogger).thenReturn(logger)
        whenever(core.timeProvider).thenReturn(mock())
        whenever(core.createOkHttpCallFactory()).thenReturn(http)
        val executor = mock<java.util.concurrent.ExecutorService>()
        doAnswer { it.getArgument<Runnable>(0).run(); null }.whenever(executor).execute(any())
        whenever(core.createSingleThreadExecutorService(any())).thenReturn(executor)
        Flags.enable(
            FlagsConfiguration.Builder().trackEvaluations(false).initializationTimeout(0)
                .useCustomFlagEndpoint(server.url("/flags").toString()).build(),
            core
        )
        val captor = argumentCaptor<Feature>()
        verify(core).registerFeature(captor.capture())
        feature = captor.firstValue as FlagsFeature
        whenever(scope.unwrap<FlagsFeature>()).thenReturn(feature)
        whenever(scope.dataStore).thenReturn(store)
        whenever(core.getFeature(Feature.FLAGS_FEATURE_NAME)).thenReturn(scope)
        val context = mock<DatadogContext>()
        whenever(context.clientToken).thenReturn("test-token")
        whenever(context.env).thenReturn("test")
        whenever(context.sdkVersion).thenReturn("test")
        whenever(context.featuresContext).thenReturn(emptyMap())
        doAnswer { it.getArgument<(DatadogContext) -> Unit>(1)(context); null }
            .whenever(scope).withContext(any(), any())
    }

    @AfterEach
    fun `tear down`() {
        feature.clearClients()
        server.shutdown()
        http.connectionPool.evictAll()
        http.dispatcher.executorService.shutdownNow()
    }

    @Test
    fun `M evaluate cached first flags before readiness W build then network and recreation`() {
        store.json = cached()
        var cachedValue: Boolean? = null
        var initialState: FlagsClientState? = null
        val client = FlagsClient.Builder(sdkCore = core).onFirstFlags {
            events.add(it)
            // Another thread must be able to access the registry and evaluate before this callback returns.
            val reader = Executors.newSingleThreadExecutor()
            try {
                cachedValue = reader.submit<Boolean> {
                    val registered = FlagsClient.get(sdkCore = core)
                    initialState = registered.state.getCurrentState()
                    registered.resolveBooleanValue("enabled", false)
                }.get(5, TimeUnit.SECONDS)
            } finally {
                reader.shutdownNow()
            }
        }.build()
        assertThat(cachedValue).isTrue()
        assertThat(initialState).isEqualTo(FlagsClientState.NotReady)
        assertThat(events.single().flagsChanged).containsExactly("enabled")
        server.enqueue(MockResponse().setBody(response(false)))
        client.setEvaluationContext(EvaluationContext("next-user"))
        assertThat(client.resolveBooleanValue("enabled", true)).isFalse()
        assertThat(client.state.getCurrentState()).isEqualTo(FlagsClientState.Ready)
        assertThat(events).hasSize(1)
        // No public reset exists: a newly created client gets its own one-shot callback.
        feature.clearClients()
        FlagsClient.Builder(sdkCore = core).onFirstFlags { events.add(it) }.build()
        assertThat(events).hasSize(2)
    }

    @Test
    fun `M evaluate network first flags W late disk and later context update`() {
        store.json = cached()
        store.deferRead = true
        val client = FlagsClient.Builder(sdkCore = core).onFirstFlags {
            events.add(it)
            assertThat(FlagsClient.get(sdkCore = core).resolveBooleanValue("enabled", true)).isFalse()
        }.build()
        server.enqueue(MockResponse().setBody(response(false)))
        client.setEvaluationContext(EvaluationContext("network-user"))
        store.releaseRead()
        assertThat(events.single().flagsChanged).containsExactly("enabled")
        assertThat(client.resolveBooleanValue("enabled", true)).isFalse()
        server.enqueue(MockResponse().setBody(response(true)))
        client.setEvaluationContext(EvaluationContext("later-user"))
        assertThat(events).hasSize(1)
        assertThat(client.resolveBooleanValue("enabled", false)).isTrue()
    }

    @Test
    fun `M leave first notification available W missing invalid cache and failed HTTP`() {
        listOf(null, "not valid JSON").forEachIndexed { index, persisted ->
            store.json = persisted
            val client = FlagsClient.Builder("client-$index", core).onFirstFlags { events.add(it) }.build()
            assertThat(events).hasSize(index)
            server.enqueue(MockResponse().setResponseCode(500))
            client.setEvaluationContext(EvaluationContext("user"))
            assertThat(events).hasSize(index)
            assertThat(client.state.getCurrentState()).isInstanceOf(FlagsClientState.Error::class.java)
            server.enqueue(MockResponse().setBody("""{"data":{"attributes":{"flags":{}}}}"""))
            client.setEvaluationContext(EvaluationContext("user"))
            assertThat(events).hasSize(index + 1)
            assertThat(events.last().flagsChanged).isEmpty()
            assertThat(client.state.getCurrentState()).isEqualTo(FlagsClientState.Ready)
        }
    }

    @Test
    fun `M allow context update from callback W network installation`() {
        var callbackValue: Boolean? = null
        val client = FlagsClient.Builder(sdkCore = core).onFirstFlags {
            events.add(it)
            val reader = Executors.newSingleThreadExecutor()
            try {
                callbackValue = reader.submit<Boolean> {
                    val registered = FlagsClient.get(sdkCore = core)
                    registered.setEvaluationContext(EvaluationContext("reentrant-user"))
                    registered.resolveBooleanValue("enabled", true)
                }.get(5, TimeUnit.SECONDS)
            } finally {
                reader.shutdownNow()
            }
        }.build()
        server.enqueue(MockResponse().setBody(response(true)))
        server.enqueue(MockResponse().setBody(response(false)))
        client.setEvaluationContext(EvaluationContext("initial-user"))
        assertThat(callbackValue).isFalse()
        assertThat(store.json).contains("false")
        assertThat(events).hasSize(1)
        assertThat(client.state.getCurrentState()).isEqualTo(FlagsClientState.Ready)
        assertThat(server.takeRequest(5, TimeUnit.SECONDS)!!.body.readUtf8()).contains("initial-user")
        assertThat(server.takeRequest(5, TimeUnit.SECONDS)!!.body.readUtf8()).contains("reentrant-user")
    }

    @Test
    fun `M clear callback W nullable builder registration`() {
        store.json = cached()
        val client = FlagsClient.Builder(sdkCore = core).onFirstFlags { events.add(it) }.onFirstFlags(null).build()
        assertThat(events).isEmpty()
        assertThat(client.resolveBooleanValue("enabled", false)).isTrue()
    }

    @Test
    fun `M isolate callback failure W first network installation`() {
        val client = FlagsClient.Builder(sdkCore = core).onFirstFlags {
            throw IllegalStateException("application failure")
        }.build()
        server.enqueue(MockResponse().setBody(response(true)))
        client.setEvaluationContext(EvaluationContext("user"))
        assertThat(client.resolveBooleanValue("enabled", false)).isTrue()
        assertThat(store.json).contains("enabled")
        assertThat(client.state.getCurrentState()).isEqualTo(FlagsClientState.Ready)
    }

    @Test
    fun `M preserve original registration W duplicate builder`() {
        val first = FlagsClient.Builder(sdkCore = core).onFirstFlags { events.add(it) }.build()
        val duplicate = FlagsClient.Builder(sdkCore = core).onFirstFlags { error("replacement callback") }.build()
        assertThat(duplicate).isSameAs(first)
        server.enqueue(MockResponse().setBody(response(true)))
        first.setEvaluationContext(EvaluationContext("user"))
        assertThat(events).hasSize(1)
    }

    private fun cached(): String = checkNotNull(
        FlagsStateSerializer(logger).serialize(
            FlagsStateEntry(
                EvaluationContext("cached-user"),
                mapOf(
                    "enabled" to PrecomputedFlag("boolean", "true", false, "allocation", "on", JSONObject(), "STATIC")
                ),
                0
            )
        )
    )

    private fun response(value: Boolean): String = """
        {"data":{"attributes":{"flags":{"enabled":{
        "variationType":"boolean","variationValue":$value,"doLog":false,
        "allocationKey":"allocation","variationKey":"on","extraLogging":{},"reason":"STATIC"}}}}}
    """.trimIndent()

    private class MemoryStore : DataStoreHandler {
        var json: String? = null
        var deferRead = false
        var releaseRead: () -> Unit = {}

        override fun <T : Any> value(
            key: String,
            version: Int?,
            callback: DataStoreReadCallback<T>,
            deserializer: Deserializer<String, T>
        ) {
            val saved = json
            val read = {
                callback.onSuccess(saved?.let { deserializer.deserialize(it) }?.let { DataStoreContent(0, it) })
            }
            if (deferRead) releaseRead = read else read()
        }

        override fun <T : Any> setValue(
            key: String,
            data: T,
            version: Int,
            callback: DataStoreWriteCallback?,
            serializer: Serializer<T>
        ) {
            json = serializer.serialize(data)
            callback?.onSuccess()
        }

        override fun removeValue(key: String, callback: DataStoreWriteCallback?) {
            json = null
            callback?.onSuccess()
        }

        override fun clearAllData() {
            json = null
        }
    }
}
