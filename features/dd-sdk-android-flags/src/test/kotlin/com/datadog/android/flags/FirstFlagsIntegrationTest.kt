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
import com.datadog.android.flags.internal.model.JsonKeys
import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.datadog.android.flags.internal.persistence.FlagsStateSerializer
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.FlagsClientEvent
import com.datadog.android.flags.model.FlagsClientState
import com.datadog.android.flags.model.ResolutionReason
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
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
    fun `M shutdown callback worker W submission accepted or rejected`() {
        listOf(false, true).forEach { rejected ->
            val worker = mock<ExecutorService>()
            if (rejected) {
                doThrow(java.util.concurrent.RejectedExecutionException("stopped"))
                    .whenever(worker).execute(any())
            } else {
                doAnswer { it.getArgument<Runnable>(0).run(); null }.whenever(worker).execute(any())
            }
            whenever(core.createSingleThreadExecutorService(eq(FlagsClient.FLAGS_FIRST_FLAGS_EXECUTOR_NAME)))
                .thenReturn(worker)
            store.json = cached()
            store.deferRead = true
            val client = FlagsClient.Builder("worker-$rejected", sdkCore = core).build()
            var delivered = false
            client.onFirstFlags { delivered = true }
            store.releaseRead()
            verify(worker).shutdown()
            assertThat(delivered).isEqualTo(!rejected)
        }
    }

    @Test
    fun `M avoid callback worker W no pending listeners`() {
        store.json = cached()
        val client = FlagsClient.Builder(sdkCore = core).build()
        client.onFirstFlags { events.add(it) }
        verify(core, never()).createSingleThreadExecutorService(FlagsClient.FLAGS_FIRST_FLAGS_EXECUTOR_NAME)
        assertThat(events).hasSize(1)
    }

    @Test
    fun `M notify empty keys W genuine empty cached configuration`() {
        store.json = JSONObject(cached()).put(JsonKeys.FLAGS.value, JSONObject()).toString()
        val client = FlagsClient.Builder(sdkCore = core).build()
        client.onFirstFlags { events.add(it) }
        assertThat(events.single().flagsChanged).isEmpty()
    }

    @Test
    fun `M notify empty keys W genuine empty network configuration`() {
        val client = FlagsClient.Builder(sdkCore = core).build()
        client.onFirstFlags { events.add(it) }
        val body = JSONObject(response(true)).apply {
            getJSONObject("data").getJSONObject("attributes").put("flags", JSONObject())
        }
        server.enqueue(MockResponse().setBody(body.toString()))
        client.setEvaluationContext(EvaluationContext("user"))
        assertThat(events.single().flagsChanged).isEmpty()
    }

    @Test
    fun `M not notify first flags W successful network body is malformed`() {
        val client = FlagsClient.Builder(sdkCore = core).build()
        val callback = mock<EvaluationContextCallback>()
        client.onFirstFlags { events.add(it) }
        server.enqueue(MockResponse().setBody("not valid JSON"))

        client.setEvaluationContext(EvaluationContext("user"), callback)

        assertThat(events).isEmpty()
        assertThat(client.state.getCurrentState()).isInstanceOf(FlagsClientState.Error::class.java)
        verify(callback).onFailure(any())
        server.enqueue(MockResponse().setBody(response(true)))
        client.setEvaluationContext(EvaluationContext("user"))
        assertThat(events.single().flagsChanged).containsExactly("enabled")
    }

    @Test
    fun `M not notify first flags W cached envelope contains only invalid flags`() {
        store.json = JSONObject(cached()).apply {
            getJSONObject(JsonKeys.FLAGS.value).getJSONObject("enabled").remove(JsonKeys.VARIATION_TYPE.value)
        }.toString()
        val client = FlagsClient.Builder(sdkCore = core).build()
        client.onFirstFlags { events.add(it) }
        assertThat(events).isEmpty()
        server.enqueue(MockResponse().setBody(response(true)))
        client.setEvaluationContext(EvaluationContext("user"))
        assertThat(events.single().flagsChanged).containsExactly("enabled")
    }

    @Test
    fun `M let callback wait for a newer context W first network installation`() {
        val executors = CopyOnWriteArrayList<ExecutorService>()
        whenever(core.createSingleThreadExecutorService(any())).thenAnswer {
            Executors.newSingleThreadExecutor().also(executors::add)
        }
        try {
            val client = FlagsClient.Builder(sdkCore = core).build()
            val newerApplied = CountDownLatch(1)
            val finished = CountDownLatch(1)
            var waited = false
            client.onFirstFlags {
                client.setEvaluationContext(
                    EvaluationContext("newer"),
                    object : EvaluationContextCallback {
                        override fun onSuccess() = newerApplied.countDown()
                        override fun onFailure(error: Throwable) = newerApplied.countDown()
                    }
                )
                waited = newerApplied.await(2, TimeUnit.SECONDS)
                finished.countDown()
            }
            server.enqueue(MockResponse().setBody(response(true)))
            server.enqueue(MockResponse().setBody(response(false)))

            client.setEvaluationContext(EvaluationContext("initial"))

            assertThat(finished.await(10, TimeUnit.SECONDS)).isTrue()
            assertThat(waited).isTrue()
        } finally {
            executors.forEach { it.shutdownNow() }
        }
    }

    @Test
    fun `M deliver pending cached flags off the disk worker W cache wins before network`() {
        val deliveryExecutor = Executors.newSingleThreadExecutor()
        whenever(core.createSingleThreadExecutorService(eq(FlagsClient.FLAGS_FIRST_FLAGS_EXECUTOR_NAME)))
            .thenReturn(deliveryExecutor)
        store.json = cached()
        store.deferRead = true
        val client = FlagsClient.Builder(sdkCore = core).build()
        val delivered = CountDownLatch(1)
        var deliveryThread: Thread? = null
        var cachedValue: Boolean? = null
        var cachedReason: ResolutionReason? = null
        client.onFirstFlags {
            events.add(it)
            deliveryThread = Thread.currentThread()
            val details = client.resolve("enabled", false)
            cachedValue = details.value
            cachedReason = details.reason
            delivered.countDown()
        }
        assertThat(events).isEmpty()
        val diskExecutor = Executors.newSingleThreadExecutor()
        try {
            val diskThread = diskExecutor.submit<Thread> {
                store.releaseRead()
                Thread.currentThread()
            }.get(5, TimeUnit.SECONDS)
            assertThat(delivered.await(5, TimeUnit.SECONDS)).isTrue()
            assertThat(deliveryThread).isNotNull.isNotSameAs(diskThread)
        } finally {
            diskExecutor.shutdownNow()
            deliveryExecutor.shutdownNow()
        }
        assertThat(cachedValue).isTrue()
        assertThat(cachedReason).isEqualTo(ResolutionReason.CACHED)
        assertThat(events.single().flagsChanged).containsExactly("enabled")
        server.enqueue(MockResponse().setBody(response(false)))
        client.setEvaluationContext(EvaluationContext("network-user"))
        assertThat(events).hasSize(1)
        assertThat(client.resolveBooleanValue("enabled", true)).isFalse()
    }

    @Test
    fun `M release cancelled registration W first flags arrive later`() {
        store.json = cached()
        store.deferRead = true
        val client = FlagsClient.Builder(sdkCore = core).build()
        val registration = client.onFirstFlags { events.add(it) }
        registration.unsubscribe()
        registration.unsubscribe()
        store.releaseRead()
        assertThat(events).isEmpty()
        client.onFirstFlags { events.add(it) }.unsubscribe()
        assertThat(events.single().flagsChanged).containsExactly("enabled")
    }

    @Test
    fun `M unsubscribe only one registration W same named listener registered twice`() {
        store.json = cached()
        store.deferRead = true
        val client = FlagsClient.Builder(sdkCore = core).build()
        val listener = FlagsClientEventListener { events.add(it) }
        val first = client.onFirstFlags(listener)
        client.onFirstFlags(listener)
        first.unsubscribe()
        first.unsubscribe()
        store.releaseRead()
        assertThat(events).hasSize(1)
    }

    @Test
    fun `M evaluate cached first flags before readiness W build then network and recreation`() {
        store.json = cached()
        var cachedValue: Boolean? = null
        var initialState: FlagsClientState? = null
        val client = FlagsClient.Builder(sdkCore = core).build().also { client ->
            client.onFirstFlags {
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
            }
        }
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
        FlagsClient.Builder(sdkCore = core).build().onFirstFlags { events.add(it) }
        assertThat(events).hasSize(2)
    }

    @Test
    fun `M evaluate network first flags W late disk and later context update`() {
        store.json = cached()
        store.deferRead = true
        val client = FlagsClient.Builder(sdkCore = core).build().also { client ->
            client.onFirstFlags {
                events.add(it)
                assertThat(FlagsClient.get(sdkCore = core).resolveBooleanValue("enabled", true)).isFalse()
            }
        }
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
            val client = FlagsClient.Builder("client-$index", core).build().also { client ->
                client.onFirstFlags { events.add(it) }
            }
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
        val client = FlagsClient.Builder(sdkCore = core).build().also { client ->
            client.onFirstFlags {
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
            }
        }
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
    fun `M replay first keys W registration after later installation`() {
        store.json = cached()
        val client = FlagsClient.Builder(sdkCore = core).build()
        server.enqueue(MockResponse().setBody("""{"data":{"attributes":{"flags":{}}}}"""))
        client.setEvaluationContext(EvaluationContext("user"))
        client.onFirstFlags { events.add(it) }
        assertThat(events.single().flagsChanged).containsExactly("enabled")
    }

    @Test
    fun `M isolate callback failure W first network installation`() {
        val client = FlagsClient.Builder(sdkCore = core).build().also { client ->
            client.onFirstFlags {
                throw IllegalStateException("application failure")
            }
        }
        server.enqueue(MockResponse().setBody(response(true)))
        client.setEvaluationContext(EvaluationContext("user"))
        assertThat(client.resolveBooleanValue("enabled", false)).isTrue()
        assertThat(store.json).contains("enabled")
        assertThat(client.state.getCurrentState()).isEqualTo(FlagsClientState.Ready)
    }

    @Test
    fun `M notify each registration W duplicate client`() {
        val first = FlagsClient.Builder(sdkCore = core).build().also { client ->
            client.onFirstFlags { events.add(it) }
        }
        val duplicate = FlagsClient.Builder(sdkCore = core).build().also { client ->
            client.onFirstFlags { events.add(it) }
        }
        assertThat(duplicate).isSameAs(first)
        server.enqueue(MockResponse().setBody(response(true)))
        first.setEvaluationContext(EvaluationContext("user"))
        assertThat(events).hasSize(2)
        assertThat(events[0]).isSameAs(events[1])
    }

    @Test
    fun `M release locks W callback registers another handler on another thread`() {
        val client = FlagsClient.Builder(sdkCore = core).build()
        val executor = Executors.newSingleThreadExecutor()
        try {
            client.onFirstFlags { event ->
                events.add(event)
                executor.submit {
                    client.onFirstFlags { replay -> events.add(replay) }
                }.get(5, TimeUnit.SECONDS)
            }
            server.enqueue(MockResponse().setBody(response(true)))
            client.setEvaluationContext(EvaluationContext("user"))
            assertThat(events).hasSize(2)
            assertThat(events[1]).isSameAs(events[0])
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `M isolate failures and deliver per registration W same handler registered twice`() {
        val client = FlagsClient.Builder(sdkCore = core).build()
        client.onFirstFlags { error("application failure") }
        val callback = FlagsClientEventListener { events.add(it) }
        client.onFirstFlags(callback)
        client.onFirstFlags(callback)
        server.enqueue(MockResponse().setBody(response(true)))
        client.setEvaluationContext(EvaluationContext("user"))
        client.onFirstFlags(callback)
        assertThat(events).hasSize(3)
        assertThat(events[1]).isSameAs(events[0])
        assertThat(events[2]).isSameAs(events[0])
    }

    @Test
    fun `M preserve newer failure W first callback changes context`() {
        val client = FlagsClient.Builder(sdkCore = core).build()
        client.onFirstFlags { client.setEvaluationContext(EvaluationContext("newer")) }
        server.enqueue(MockResponse().setBody(response(true)))
        server.enqueue(MockResponse().setResponseCode(500))
        client.setEvaluationContext(EvaluationContext("initial"))
        assertThat(client.state.getCurrentState()).isInstanceOf(FlagsClientState.Error::class.java)
    }

    @Test
    fun `M preserve reconciling W first callback queues newer context`() {
        val executor = mock<java.util.concurrent.ExecutorService>()
        val work = java.util.ArrayDeque<Runnable>()
        doAnswer { work.add(it.getArgument(0)); null }.whenever(executor).execute(any())
        whenever(
            core.createSingleThreadExecutorService(eq(FlagsClient.FLAGS_NETWORK_EXECUTOR_NAME))
        ).thenReturn(executor)
        val client = FlagsClient.Builder(sdkCore = core).build()
        client.onFirstFlags { client.setEvaluationContext(EvaluationContext("newer")) }
        server.enqueue(MockResponse().setBody(response(true)))
        client.setEvaluationContext(EvaluationContext("initial"))
        work.removeFirst().run()
        assertThat(work).hasSize(1)
        assertThat(client.state.getCurrentState()).isEqualTo(FlagsClientState.Reconciling)
        server.enqueue(MockResponse().setBody(response(false)))
        work.removeFirst().run()
        assertThat(client.state.getCurrentState()).isEqualTo(FlagsClientState.Ready)
    }

    @Test
    fun `M complete context before first flags W handler waits for completion`() {
        val client = FlagsClient.Builder(sdkCore = core).build()
        val completed = CountDownLatch(1)
        val callback = mock<EvaluationContextCallback>()
        doAnswer { completed.countDown(); null }.whenever(callback).onSuccess()
        var completionDelivered = false
        client.onFirstFlags { completionDelivered = completed.await(5, TimeUnit.SECONDS) }
        server.enqueue(MockResponse().setBody(response(true)))
        client.setEvaluationContext(EvaluationContext("initial"), callback)
        assertThat(completionDelivered).isTrue()
        verify(callback).onSuccess()
    }

    @Test
    fun `M preserve newer failure W context completion changes context`() {
        val client = FlagsClient.Builder(sdkCore = core).build()
        val callback = mock<EvaluationContextCallback>()
        doAnswer {
            client.setEvaluationContext(EvaluationContext("newer"))
            null
        }.whenever(callback).onSuccess()
        client.onFirstFlags { events.add(it) }
        server.enqueue(MockResponse().setBody(response(true)))
        server.enqueue(MockResponse().setResponseCode(500))
        client.setEvaluationContext(EvaluationContext("initial"), callback)
        assertThat(client.state.getCurrentState()).isInstanceOf(FlagsClientState.Error::class.java)
        assertThat(events).hasSize(1)
        verify(callback).onSuccess()
    }

    @Test
    fun `M preserve newer reconciliation W context completion queues context`() {
        val executor = mock<java.util.concurrent.ExecutorService>()
        val work = java.util.ArrayDeque<Runnable>()
        doAnswer { work.add(it.getArgument(0)); null }.whenever(executor).execute(any())
        whenever(
            core.createSingleThreadExecutorService(eq(FlagsClient.FLAGS_NETWORK_EXECUTOR_NAME))
        ).thenReturn(executor)
        val client = FlagsClient.Builder(sdkCore = core).build()
        val callback = mock<EvaluationContextCallback>()
        doAnswer {
            client.setEvaluationContext(EvaluationContext("newer"))
            null
        }.whenever(callback).onSuccess()
        client.onFirstFlags { events.add(it) }
        server.enqueue(MockResponse().setBody(response(true)))
        client.setEvaluationContext(EvaluationContext("initial"), callback)
        work.removeFirst().run()
        assertThat(work).hasSize(1)
        assertThat(client.state.getCurrentState()).isEqualTo(FlagsClientState.Reconciling)
        assertThat(events).hasSize(1)
        verify(callback).onSuccess()
    }

    @Test
    fun `M deliver accepted first flags W context completion throws`() {
        val client = FlagsClient.Builder(sdkCore = core).build()
        val callback = mock<EvaluationContextCallback>()
        doAnswer { error("application completion failure") }.whenever(callback).onSuccess()
        client.onFirstFlags { events.add(it) }
        server.enqueue(MockResponse().setBody(response(true)))
        assertThrows<IllegalStateException> {
            client.setEvaluationContext(EvaluationContext("initial"), callback)
        }
        assertThat(events).hasSize(1)
        assertThat(client.state.getCurrentState()).isEqualTo(FlagsClientState.Ready)
        verify(callback).onSuccess()
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
