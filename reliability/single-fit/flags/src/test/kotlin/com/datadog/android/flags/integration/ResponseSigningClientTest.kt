/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.integration

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureContextUpdateReceiver
import com.datadog.android.api.storage.datastore.DataStoreHandler
import com.datadog.android.api.storage.datastore.DataStoreReadCallback
import com.datadog.android.api.storage.datastore.DataStoreWriteCallback
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.persistence.Serializer
import com.datadog.android.core.stub.StubDataStoreHandler
import com.datadog.android.core.stub.StubExecutorService
import com.datadog.android.core.stub.StubSDKCore
import com.datadog.android.flags.Flags
import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags.FlagsConfiguration
import com.datadog.android.flags.ResponseSigningMode
import com.datadog.android.flags.integration.FlagsClientTest.Companion.createStubSdkCore
import com.datadog.android.flags.integration.tests.elmyr.FlagsIntegrationForgeConfigurator
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.internal.FeatureContextKeys
import com.datadog.android.internal.rum.RumContext
import com.datadog.android.internal.time.TimeProvider
import com.google.gson.JsonParser
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.Call
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.concurrent.ExecutorService

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(FlagsIntegrationForgeConfigurator::class)
internal class ResponseSigningClientTest {
    @Forgery lateinit var fakeDatadogContext: DatadogContext
    private lateinit var stubCore: StubSDKCore
    private val store = RecordingStore()
    private val queue = ArrayDeque<Runnable>()
    private var now = CanonicalResponseSigner.now
    private var cancelledChecks = 0
    private val requests = mutableListOf<Request>()
    private var responseKind = "valid"
    private var observeFullEvaluationData = false
    private var endpoint = "https://flags.example.test/assignments?dd_env=test"
    private var beforeResponse: () -> Unit = {}
    private lateinit var testedClient: FlagsClient

    @BeforeEach
    fun `set up`(forge: Forge) {
        stubCore = createStubSdkCore(forge)
        fakeDatadogContext = fakeDatadogContext.copy(source = "android", featuresContext = emptyMap())
        stubCore.stubDataStore(Feature.FLAGS_FEATURE_NAME, store)
        stubCore.stubNetworkResponses { request ->
            requests.add(request)
            beforeResponse()
            response(request)
        }
    }

    @AfterEach
    fun `tear down`() { stubCore.stopFeatures() }

    @ParameterizedTest
    @EnumSource(ResponseSigningMode::class)
    fun `M evaluate real signed responses W setEvaluationContext() {all modes}`(mode: ResponseSigningMode) {
        // Given
        create(mode)
        // When
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        // Then
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isTrue()
        assertThat(requests).hasSize(1)
        assertThat(requests.single().header(PREFIX + "signature-version"))
            .isEqualTo(if (mode == ResponseSigningMode.OFF) null else "1")
        assertThat(requests.single().header("X-stream"))
            .isEqualTo(if (mode == ResponseSigningMode.OFF) null else "false")
        if (mode == ResponseSigningMode.REQUIRED) assertThat(store.operations).isZero()
    }

    @ParameterizedTest
    @EnumSource(ResponseSigningMode::class, names = ["OBSERVE", "REQUIRED"])
    fun `M apply optional proof policy W setEvaluationContext() {tampered body}`(mode: ResponseSigningMode) {
        // Given
        create(mode)
        responseKind = "tampered"
        // When
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        // Then
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isEqualTo(
            mode == ResponseSigningMode.OBSERVE
        )
        assertThat(requests).hasSize(1)
    }

    @ParameterizedTest
    @EnumSource(ResponseSigningMode::class, names = ["OBSERVE", "REQUIRED"])
    fun `M apply optional proof policy W setEvaluationContext() {unsigned}`(mode: ResponseSigningMode) {
        // Given
        create(mode)
        responseKind = "unsigned"
        // When
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        // Then
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isEqualTo(
            mode == ResponseSigningMode.OBSERVE
        )
        assertThat(requests).hasSize(1)
    }

    @ParameterizedTest
    @EnumSource(ResponseSigningMode::class, names = ["OBSERVE", "REQUIRED"])
    fun `M reject redirects W setEvaluationContext() {custom transport follows}`(mode: ResponseSigningMode) {
        // Given
        create(mode)
        responseKind = "redirect"
        // When
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        // Then
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isFalse()
        assertThat(requests).hasSize(1)
    }

    @ParameterizedTest
    @EnumSource(ResponseSigningMode::class, names = ["OBSERVE", "REQUIRED"])
    fun `M preserve HTTP errors W setEvaluationContext() {403 with signed headers}`(mode: ResponseSigningMode) {
        // Given
        create(mode)
        responseKind = "403"
        // When
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        // Then
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isFalse()
        assertThat(requests).hasSize(1)
    }

    @Test
    fun `M freeze request context W setEvaluationContext() {caller mutates attributes}`() {
        // Given
        create(ResponseSigningMode.REQUIRED)
        val attributes = mutableMapOf("plan" to "before")
        // When
        testedClient.setEvaluationContext(EvaluationContext("subject", attributes), null)
        attributes["plan"] = "after"
        queue.removeFirst().run()
        // Then
        val body = Buffer().also { requests.single().body?.writeTo(it) }.readUtf8()
        assertThat(body).contains("before").doesNotContain("after")
    }

    @Test
    fun `M discard superseded responses W setEvaluationContext() {new context during fetch}`() {
        // Given
        create(ResponseSigningMode.REQUIRED)
        beforeResponse = {
            beforeResponse = {}
            testedClient.setEvaluationContext(EvaluationContext("new-subject"), null)
        }
        // When
        testedClient.setEvaluationContext(EvaluationContext("old-subject"), null)
        queue.removeFirst().run()
        // Then
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isFalse()
        queue.removeFirst().run()
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isTrue()
    }

    @Test
    fun `M enforce original expiry on every read W resolveBooleanValue() {required cache}`() {
        // Given
        create(ResponseSigningMode.REQUIRED)
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        // When
        now += 300
        val actual = testedClient.resolveBooleanValue("critical-demo", false)
        // Then
        assertThat(actual).isFalse()
        assertThat(store.operations).isZero()
    }

    @Test
    fun `M reject changed token fallback W setEvaluationContext() {required failed fetch}`() {
        // Given
        create(ResponseSigningMode.REQUIRED)
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        fakeDatadogContext = fakeDatadogContext.copy(clientToken = "different-token")
        responseKind = "unsigned"
        // When
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        // Then
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isFalse()
    }

    @Test
    fun `M fail locally W setEvaluationContext() {required without anchor}`() {
        // Given
        create(ResponseSigningMode.REQUIRED, emptyList())
        // When
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        // Then
        assertThat(requests).isEmpty()
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isFalse()
        assertThat(store.operations).isZero()
    }

    @Test
    fun `M retain verified fallback W setEvaluationContext() {unsigned observation then error}`() {
        // Given
        create(ResponseSigningMode.OBSERVE)
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        responseKind = "unsigned-false"
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        assertThat(testedClient.resolveBooleanValue("critical-demo", true)).isFalse()
        responseKind = "403"
        // When
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        // Then
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isTrue()
    }

    @ParameterizedTest
    @EnumSource(ResponseSigningMode::class, names = ["OBSERVE", "REQUIRED"])
    fun `M preserve ordinary observe behavior W setEvaluationContext() {unsupported HTTP endpoint}`(
        mode: ResponseSigningMode
    ) {
        // Given
        endpoint = "http://flags.example.test/assignments"
        create(mode)
        responseKind = "unsigned"
        // When
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        // Then
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isEqualTo(
            mode == ResponseSigningMode.OBSERVE
        )
        assertThat(requests).hasSize(if (mode == ResponseSigningMode.OBSERVE) 1 else 0)
    }

    @Test
    fun `M start empty W resolveBooleanValue() {new required client cannot load persistence}`() {
        // Given
        create(ResponseSigningMode.REQUIRED)
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        create(ResponseSigningMode.REQUIRED)
        // When
        val actual = testedClient.resolveBooleanValue("critical-demo", false)
        // Then
        assertThat(actual).isFalse()
        assertThat(store.operations).isZero()
    }

    @Test
    fun `M defer persistence W resolveBooleanValue() {observe scope not established}`() {
        // Given
        create(ResponseSigningMode.OBSERVE)
        // When
        val actual = testedClient.resolveBooleanValue("critical-demo", false)
        // Then
        assertThat(actual).isFalse()
        assertThat(store.operations).isZero()
    }

    @Test
    fun `M bound persistence W setEvaluationContext() {attributes and environment change}`() {
        // Given
        create(ResponseSigningMode.OBSERVE)
        // When
        repeat(3) { index ->
            testedClient.setEvaluationContext(EvaluationContext("subject", mapOf("counter" to "$index")))
            queue.removeFirst().run()
        }
        fakeDatadogContext = fakeDatadogContext.copy(env = "other-environment")
        testedClient.setEvaluationContext(EvaluationContext("subject"))
        queue.removeFirst().run()
        // Then
        assertThat(store.values).hasSize(1)
        assertThat(store.writtenScopes.toSet()).hasSize(4)
    }

    @ParameterizedTest
    @EnumSource(ResponseSigningMode::class, names = ["OBSERVE", "REQUIRED"])
    fun `M reject changed emitted token proof W setEvaluationContext()`(mode: ResponseSigningMode) {
        // Given
        create(mode)
        responseKind = "emitted-token"
        // When
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        // Then
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isEqualTo(
            mode == ResponseSigningMode.OBSERVE
        )
        assertThat(requests).hasSize(1)
    }

    @Test
    fun `M invalidate cached app scope W resolveBooleanValue() {RUM app changes}`() {
        // Given
        updateApp("application-a")
        create(ResponseSigningMode.REQUIRED)
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        // When
        updateApp("application-b")
        val actual = testedClient.resolveBooleanValue("critical-demo", false)
        // Then
        assertThat(actual).isFalse()
        assertThat(requests).hasSize(1)
    }

    @Test
    fun `M discard old app response W setEvaluationContext() {RUM changes during fetch}`() {
        // Given
        updateApp("application-a")
        create(ResponseSigningMode.REQUIRED)
        beforeResponse = { beforeResponse = {}; updateApp("application-b") }
        // When
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        // Then
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isFalse()
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isTrue()
        assertThat(requests.last().header("dd-application-id")).isEqualTo("application-b")
    }

    @Test
    fun `M retain current app proof W resolveBooleanValue() {RUM session update}`() {
        // Given
        updateApp("application-a")
        create(ResponseSigningMode.REQUIRED)
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        // When
        updateApp("application-a", "another-session")
        val actual = testedClient.resolveBooleanValue("critical-demo", false)
        // Then
        assertThat(actual).isTrue()
    }

    @Test
    fun `M discard stopped feature response W setEvaluationContext()`() {
        // Given
        create(ResponseSigningMode.REQUIRED)
        beforeResponse = { stubCore.stopFeatures() }
        // When
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        // Then
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isFalse()
    }

    @ParameterizedTest
    @EnumSource(ResponseSigningMode::class, names = ["OBSERVE", "REQUIRED"])
    fun `M discard cancellation after verification W setEvaluationContext()`(mode: ResponseSigningMode) {
        // Given
        create(mode)
        responseKind = "cancelled"
        // When
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        // Then
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isFalse()
        assertThat(cancelledChecks).isEqualTo(2)
    }

    @Test
    fun `M retain configured anchor W setEvaluationContext() {caller clears original list}`() {
        // Given
        val roots = mutableListOf(CanonicalResponseSigner.root)
        create(ResponseSigningMode.REQUIRED, roots)
        roots.clear()
        // When
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        // Then
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isTrue()
    }

    @Test
    fun `M retain response consent W setEvaluationContext() {observe persistence}`() {
        // Given
        create(ResponseSigningMode.OBSERVE)
        // When
        testedClient.setEvaluationContext(EvaluationContext("subject"))
        queue.removeFirst().run()
        // Then
        assertThat(store.values.values.single()).contains("\"observeFullEvaluationData\":false")
        // When
        observeFullEvaluationData = true
        testedClient.setEvaluationContext(EvaluationContext("other-subject"))
        queue.removeFirst().run()
        // Then
        assertThat(store.values.values.single()).contains("\"observeFullEvaluationData\":true")
    }

    @Test
    fun `M restore only matching trust scope W setEvaluationContext() {observe restart}`() {
        // Given
        create(ResponseSigningMode.OBSERVE)
        testedClient.setEvaluationContext(EvaluationContext("subject"))
        queue.removeFirst().run()
        assertThat(store.values).hasSize(1)
        responseKind = "403"
        // When
        create(ResponseSigningMode.OBSERVE)
        testedClient.setEvaluationContext(EvaluationContext("subject"))
        queue.removeFirst().run()
        // Then
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isTrue()
        // When
        fakeDatadogContext = fakeDatadogContext.copy(clientToken = "different-token")
        create(ResponseSigningMode.OBSERVE)
        testedClient.setEvaluationContext(EvaluationContext("subject"))
        queue.removeFirst().run()
        // Then
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isFalse()
        assertThat(store.values).hasSize(1)
    }

    private fun updateApp(applicationId: String, sessionId: String = "session") {
        val context = mapOf(FeatureContextKeys.RUM_CONTEXT to RumContext(applicationId, sessionId))
        fakeDatadogContext = fakeDatadogContext.copy(featuresContext = mapOf(Feature.RUM_FEATURE_NAME to context))
        stubCore.updateFeatureContext(Feature.RUM_FEATURE_NAME, false) { it.putAll(context) }
    }

    private fun create(mode: ResponseSigningMode, roots: List<String> = listOf(CanonicalResponseSigner.root)) {
        val core = object : InternalSdkCore by stubCore {
            override fun getDatadogContext(withFeatureContexts: Set<String>) = fakeDatadogContext
            override fun setContextUpdateReceiver(listener: FeatureContextUpdateReceiver) {
                stubCore.setContextUpdateReceiver(listener)
                fakeDatadogContext.featuresContext[Feature.RUM_FEATURE_NAME]?.let {
                    listener.onContextUpdate(Feature.RUM_FEATURE_NAME, it)
                }
            }
            override val timeProvider = object : TimeProvider by stubCore.timeProvider {
                override fun getDeviceTimestampMillis() = now * 1000
            }
            override fun createSingleThreadExecutorService(executorContext: String): ExecutorService {
                val executor = StubExecutorService(executorContext)
                return if (executorContext == "flags-network") {
                    object : ExecutorService by executor {
                        override fun execute(command: Runnable) { queue.add(command) }
                    }
                } else {
                    executor
                }
            }
            override fun createOkHttpCallFactory(block: OkHttpClient.Builder.() -> Unit): Call.Factory {
                val factory = stubCore.createOkHttpCallFactory(block)
                return Call.Factory { request ->
                    val call = factory.newCall(request)
                    object : Call by call {
                        override fun isCanceled() = responseKind == "cancelled" && ++cancelledChecks > 1
                    }
                }
            }
        }
        Flags.enable(
            FlagsConfiguration.Builder().trackExposures(false).trackEvaluations(false).rumIntegrationEnabled(false)
                .useCustomFlagEndpoint(endpoint).responseSigning(mode, roots).build(),
            core
        )
        testedClient = FlagsClient.Builder(sdkCore = core).build()
    }

    private fun response(request: Request): Response {
        val json = """
            {"data":{"attributes":{"observeFullEvaluationData":$observeFullEvaluationData,"flags":{"critical-demo":{
              "variationType":"boolean","variationValue":"true","doLog":false,
              "allocationKey":"test","variationKey":"on","reason":"TARGETING_MATCH","extraLogging":{}
            }}}}}
        """.trimIndent()
        val headers = CanonicalResponseSigner.response(request, json).headers
        val response = Response.Builder().request(request).protocol(Protocol.HTTP_1_1).message("test")
            .code(if (responseKind == "403") 403 else 200)
            .headers(if (responseKind.startsWith("unsigned")) Headers.Builder().build() else headers)
            .body(
                when (responseKind) {
                    "tampered" -> (json + " ").toResponseBody()
                    "unsigned-false" -> json.replace("\"true\"", "\"false\"").toResponseBody()
                    else -> json.toResponseBody()
                }
            )
        if (responseKind == "emitted-token") {
            response.request(request.newBuilder().header("dd-client-token", "different-emitted-token").build())
        }
        if (responseKind == "redirect") {
            response.priorResponse(
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(302).message("redirect").build()
            )
        }
        return response.build()
    }

    private class RecordingStore(
        private val delegate: DataStoreHandler = StubDataStoreHandler()
    ) : DataStoreHandler by delegate {
        val values = mutableMapOf<String, String>()
        val writtenScopes = mutableListOf<String>()
        var operations = 0
        override fun <T : Any> setValue(
            key: String,
            data: T,
            version: Int,
            callback: DataStoreWriteCallback?,
            serializer: Serializer<T>
        ) {
            operations++
            serializer.serialize(data)?.let { json ->
                values[key] = json
                JsonParser.parseString(json).asJsonObject["responseSigningCacheScope"]?.let {
                    writtenScopes.add(it.asString)
                }
            }
            delegate.setValue(key, data, version, callback, serializer)
        }
        override fun <T : Any> value(
            key: String,
            version: Int?,
            callback: DataStoreReadCallback<T>,
            deserializer: Deserializer<String, T>
        ) {
            operations++
            delegate.value(key, version, callback, deserializer)
        }
        override fun removeValue(key: String, callback: DataStoreWriteCallback?) {
            operations++
            values.remove(key)
            delegate.removeValue(key, callback)
        }
    }

    private companion object {
        const val PREFIX = "x-datadog-feature-flags-"
    }
}
