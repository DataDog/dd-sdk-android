/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:OptIn(ExperimentalFlagsApi::class)

package com.datadog.android.flags.integration

import com.datadog.android.DatadogSite
import com.datadog.android.api.InternalLogger
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
import com.datadog.android.core.stub.StubScheduledExecutorService
import com.datadog.android.flags.EvaluationContextCallback
import com.datadog.android.flags.ExperimentalFlagsApi
import com.datadog.android.flags.Flags
import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags.FlagsConfiguration
import com.datadog.android.flags.FlagsResponseSigningException
import com.datadog.android.flags.FlagsStateListener
import com.datadog.android.flags.ResponseSigningMode
import com.datadog.android.flags.integration.FlagsClientTest.Companion.createStubSdkCore
import com.datadog.android.flags.integration.tests.elmyr.FlagsIntegrationForgeConfigurator
import com.datadog.android.flags.integration.tests.utils.responseSigningWithTestRoots
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.FlagsClientState
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
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService

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
    private val scheduledExecutors = mutableListOf<StubScheduledExecutorService>()
    private val userMessages = mutableListOf<String>()
    private var signedAt: Long? = null
    private var minimumNotBefore: Long? = null
    private var responseKind = "valid"
    private var credentialName = CanonicalResponseSigner.DEFAULT_CREDENTIAL
    private var observeFullEvaluationData = false
    private var endpoint = "https://flags.example.test/assignments?dd_env=test"
    private var beforeResponse: () -> Unit = {}
    private lateinit var testedClient: FlagsClient

    @BeforeEach
    fun `set up`(forge: Forge) {
        stubCore = createStubSdkCore(forge)
        fakeDatadogContext = fakeDatadogContext.copy(
            source = "android",
            featuresContext = emptyMap(),
            site = DatadogSite.STAGING
        )
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
        if (mode == ResponseSigningMode.REQUIRED) assertThat(store.operations).isZero()
    }

    @ParameterizedTest
    @EnumSource(ResponseSigningMode::class)
    fun `M never send X-stream W setEvaluationContext() {all modes}`(mode: ResponseSigningMode) {
        // Given
        create(mode)
        // When
        repeat(2) {
            testedClient.setEvaluationContext(EvaluationContext("subject-$it"), null)
            queue.removeFirst().run()
        }
        // Then
        assertThat(requests).hasSize(2).allSatisfy {
            assertThat(it.headers.names().map { name -> name.lowercase() }).doesNotContain("x-stream")
        }
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
        // An unsigned 200 (edge before credential publication) fails closed in REQUIRED and is reported in OBSERVE.
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isEqualTo(
            mode == ResponseSigningMode.OBSERVE
        )
        assertThat(requests).hasSize(1)
        assertThat(stubCore.telemetryEventsWritten().map { it.message })
            .contains("Flag response signing v1: unverified (missing-proof)")
    }

    @ParameterizedTest
    @EnumSource(ResponseSigningMode::class, names = ["OBSERVE", "REQUIRED"])
    fun `M report the reason code W setEvaluationContext() {signing rejects the response}`(mode: ResponseSigningMode) {
        listOf("unsigned" to "missing-proof", "tampered" to "invalid-signature").forEach { (kind, code) ->
            // Given
            create(mode)
            responseKind = kind
            val failures = mutableListOf<Throwable>()
            // When
            testedClient.setEvaluationContext(EvaluationContext("subject-$kind"), failureCallback(failures))
            queue.removeFirst().run()
            // Then
            assertThat(userMessages).anyMatch { it.startsWith("Flag response signing failed ($code): ") }
            assertThat(userMessages).noneMatch { it.contains("check your network connection") }
            if (mode == ResponseSigningMode.REQUIRED) {
                val failure = failures.single()
                assertThat(failure).isInstanceOf(FlagsResponseSigningException::class.java)
                assertThat((failure as FlagsResponseSigningException).reason.code).isEqualTo(code)
                val state = testedClient.state.getCurrentState() as FlagsClientState.Error
                assertThat(state.error).isInstanceOf(FlagsResponseSigningException::class.java)
            } else {
                assertThat(failures).isEmpty()
            }
            stubCore.stopFeatures()
        }
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
    fun `M enforce original expiry on every read W resolveBooleanValue() {required, expireVerifiedFlags}`() {
        // Given
        create(ResponseSigningMode.REQUIRED, expireVerifiedFlags = true)
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
    fun `M go stale and keep verified flags W proof expires {required, default}`() {
        // Given
        create(ResponseSigningMode.REQUIRED)
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        assertThat(testedClient.state.getCurrentState()).isEqualTo(FlagsClientState.Ready)
        // When
        advanceClock(PROOF_WINDOW_SECONDS)
        // Then
        assertThat(testedClient.state.getCurrentState()).isEqualTo(FlagsClientState.Stale)
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isTrue()
        // When a new verified response arrives
        signedAt = now
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        // Then
        assertThat(testedClient.state.getCurrentState()).isEqualTo(FlagsClientState.Ready)
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isTrue()
        // When the app restarts
        stubCore.stopFeatures()
        create(ResponseSigningMode.REQUIRED)
        // Then
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isFalse()
        assertThat(store.operations).isZero()
    }

    @Test
    fun `M go to error and return defaults W proof expires {required, expireVerifiedFlags}`() {
        // Given
        create(ResponseSigningMode.REQUIRED, expireVerifiedFlags = true)
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        // When
        advanceClock(PROOF_WINDOW_SECONDS)
        // Then
        val state = testedClient.state.getCurrentState()
        assertThat(state).isInstanceOf(FlagsClientState.Error::class.java)
        assertThat((state as FlagsClientState.Error).error).hasMessageContaining("expired")
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isFalse()
        // When a new verified response arrives
        signedAt = now
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        // Then
        assertThat(testedClient.state.getCurrentState()).isEqualTo(FlagsClientState.Ready)
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isTrue()
        // When the app restarts
        stubCore.stopFeatures()
        create(ResponseSigningMode.REQUIRED, expireVerifiedFlags = true)
        // Then
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isFalse()
        assertThat(store.operations).isZero()
    }

    @ParameterizedTest(name = "expireVerifiedFlags={0}, from Stale={1}, by read={2}")
    @MethodSource("expiryCases")
    fun `M apply expiry W proof expires {required}`(expireVerifiedFlags: Boolean, fromStale: Boolean, byRead: Boolean) {
        // Given
        create(ResponseSigningMode.REQUIRED, expireVerifiedFlags = expireVerifiedFlags)
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        if (fromStale) {
            // A rejected refresh keeps the still-valid verified flags and reports Stale.
            responseKind = "unsigned"
            testedClient.setEvaluationContext(EvaluationContext("subject"), null)
            queue.removeFirst().run()
            assertThat(testedClient.state.getCurrentState()).isEqualTo(FlagsClientState.Stale)
        }
        val states = mutableListOf<FlagsClientState>()
        testedClient.state.addListener(object : FlagsStateListener {
            override fun onStateChanged(newState: FlagsClientState) {
                states += newState
            }
        })
        states.clear()
        // When
        if (byRead) now += PROOF_WINDOW_SECONDS else advanceClock(PROOF_WINDOW_SECONDS)
        val value = testedClient.resolveBooleanValue("critical-demo", false)
        // Then
        val state = testedClient.state.getCurrentState()
        if (expireVerifiedFlags) {
            assertThat(state).isInstanceOf(FlagsClientState.Error::class.java)
            assertThat(states.filterIsInstance<FlagsClientState.Error>()).hasSize(1)
            assertThat(value).isFalse()
        } else {
            assertThat(state).isEqualTo(FlagsClientState.Stale)
            assertThat(states.filterIsInstance<FlagsClientState.Error>()).isEmpty()
            assertThat(value).isTrue()
        }
        // The timer that fires after a read-time change does not send a second event.
        val events = states.size
        scheduledExecutors.toList().forEach { it.runDueTasks() }
        assertThat(states).hasSize(events)
    }

    @ParameterizedTest
    @EnumSource(ResponseSigningMode::class, names = ["OFF", "OBSERVE"])
    fun `M keep state W proof expires {expireVerifiedFlags outside required}`(mode: ResponseSigningMode) {
        // Given
        create(mode, expireVerifiedFlags = true)
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        // When
        advanceClock(PROOF_WINDOW_SECONDS)
        // Then
        assertThat(testedClient.state.getCurrentState()).isEqualTo(FlagsClientState.Ready)
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isTrue()
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
    fun `M reject test chains W setEvaluationContext() {required with the pinned staging anchor}`() {
        // Given
        create(ResponseSigningMode.REQUIRED, roots = null)
        // When
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        // Then
        assertThat(requests).hasSize(1)
        assertThat(requests.single().header(PREFIX + "signature-version")).isEqualTo("1")
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isFalse()
        assertThat(store.operations).isZero()
    }

    @ParameterizedTest
    @EnumSource(ResponseSigningMode::class, names = ["OBSERVE", "REQUIRED"])
    fun `M accept every leaf under the root W setEvaluationContext() {rotation between fetches}`(
        mode: ResponseSigningMode
    ) {
        // Given
        create(mode)
        val generations = listOf(
            CanonicalResponseSigner.DEFAULT_CREDENTIAL,
            CanonicalResponseSigner.ROTATED_CREDENTIAL,
            CanonicalResponseSigner.DEFAULT_CREDENTIAL
        )
        assertThat(generations.map { CanonicalResponseSigner.leafCertificate(it) }.distinct()).hasSize(2)
        // When
        val results = generations.mapIndexed { index, generation ->
            credentialName = generation
            observeFullEvaluationData = index % 2 == 1
            testedClient.setEvaluationContext(EvaluationContext("subject-$index"), null)
            queue.removeFirst().run()
            testedClient.resolve("critical-demo", false)
        }
        // Then
        assertThat(results).allSatisfy { assertThat(it.value).isTrue() }
        assertThat(results.map { it.flagMetadata["__dd_observe_full_evaluation_data"] })
            .containsExactly(false, true, false)
        assertThat(stubCore.telemetryEventsWritten().map { it.message })
            .noneMatch { it.startsWith("Flag response signing v1: unverified") }
    }

    @ParameterizedTest
    @EnumSource(DatadogSite::class)
    fun `M send today's requests W setEvaluationContext() {OBSERVE or OFF on every non-staging site}`(
        site: DatadogSite
    ) {
        if (site == DatadogSite.STAGING) return
        // Given
        fakeDatadogContext = fakeDatadogContext.copy(site = site)
        responseKind = "unsigned"
        create(null)
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        val baseline = requests.removeAt(requests.lastIndex)
        for (mode in listOf(ResponseSigningMode.OFF, ResponseSigningMode.OBSERVE)) {
            create(mode)
            // When
            testedClient.setEvaluationContext(EvaluationContext("subject"), null)
            queue.removeFirst().run()
            // Then
            val request = requests.removeAt(requests.lastIndex)
            assertThat(request.url).isEqualTo(baseline.url)
            assertThat(request.method).isEqualTo(baseline.method)
            assertThat(request.headers).isEqualTo(baseline.headers)
            assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isTrue()
        }
    }

    @ParameterizedTest
    @EnumSource(DatadogSite::class)
    fun `M fail closed without a request W setEvaluationContext() {REQUIRED on every non-staging site}`(
        site: DatadogSite
    ) {
        if (site == DatadogSite.STAGING) return
        // Given
        fakeDatadogContext = fakeDatadogContext.copy(site = site)
        responseKind = "unsigned"
        create(ResponseSigningMode.REQUIRED)
        val failures = mutableListOf<Throwable>()
        val callback = object : EvaluationContextCallback {
            override fun onSuccess() = Unit
            override fun onFailure(error: Throwable) {
                failures += error
            }
        }
        // When
        repeat(2) {
            testedClient.setEvaluationContext(EvaluationContext("subject-$it"), callback)
            while (queue.isNotEmpty()) queue.removeFirst().run()
        }
        // Then
        assertThat(requests).isEmpty()
        assertThat(failures).hasSize(2).allSatisfy {
            assertThat(it).isInstanceOf(FlagsResponseSigningException::class.java)
                .hasMessageStartingWith("Flag response signing failed (unsupported-site): ")
        }
        assertThat(testedClient.state.getCurrentState()).isInstanceOf(FlagsClientState.Error::class.java)
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isFalse()
    }

    @Test
    fun `M send today's requests W setEvaluationContext() {staging without opt in or with OFF}`() {
        // Given
        create(null)
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        val baseline = requests.removeAt(requests.lastIndex)
        create(ResponseSigningMode.OFF)
        // When
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        // Then
        val request = requests.removeAt(requests.lastIndex)
        assertThat(request.headers).isEqualTo(baseline.headers)
        assertThat(request.url).isEqualTo(baseline.url)
        assertThat(baseline.headers.names().map { it.lowercase() })
            .doesNotContain("x-stream", PREFIX + "signature-version", PREFIX + "request-nonce")
        assertThat(baseline.header("X-DD-FEATURE-FLAGS-CAPABILITIES").orEmpty())
            .doesNotContain("response-signing-v1")
        // A signed proof on the response is ignored without opt in.
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isTrue()
    }

    @Test
    fun `M send the v1 opt-in request W setEvaluationContext() {staging opt in}`() {
        // Given
        create(ResponseSigningMode.REQUIRED)
        // When
        repeat(2) {
            testedClient.setEvaluationContext(EvaluationContext("subject-$it"), null)
            queue.removeFirst().run()
        }
        // Then
        val nonces = requests.map { checkNotNull(it.header(PREFIX + "request-nonce")) }
        assertThat(nonces).allSatisfy { assertThat(it).matches("[0-9a-f]{32}") }
        assertThat(nonces.distinct()).hasSize(2)
        requests.forEach {
            assertThat(it.headers.values(PREFIX + "signature-version")).containsExactly("1")
            assertThat(it.header("X-stream")).isNull()
            assertThat(it.header("X-DD-FEATURE-FLAGS-CAPABILITIES")?.split(',')).contains("response-signing-v1")
        }
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
    fun `M keep verified flags W resolveBooleanValue() {RUM app changes}`() {
        // Given
        updateApp("application-a")
        create(ResponseSigningMode.REQUIRED)
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        // When
        updateApp("application-b")
        val actual = testedClient.resolveBooleanValue("critical-demo", false)
        // Then
        assertThat(actual).isTrue()
        assertThat(testedClient.state.getCurrentState()).isEqualTo(FlagsClientState.Ready)
        assertThat(requests).hasSize(1)
    }

    @Test
    fun `M keep the response W setEvaluationContext() {RUM app changes during fetch}`() {
        // Given
        updateApp("application-a")
        create(ResponseSigningMode.REQUIRED)
        beforeResponse = { beforeResponse = {}; updateApp("application-b") }
        // When
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        // Then the transcript still binds the application ID that the request sent.
        assertThat(requests.single().header("dd-application-id")).isEqualTo("application-a")
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isTrue()
        assertThat(testedClient.state.getCurrentState()).isEqualTo(FlagsClientState.Ready)
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isTrue()
        assertThat(requests.last().header("dd-application-id")).isEqualTo("application-b")
    }

    @Test
    fun `M keep verified flags W resolveBooleanValue() {RUM starts after init}`() {
        // Given
        create(ResponseSigningMode.REQUIRED)
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        assertThat(requests.single().header("dd-application-id")).isNull()
        // When
        updateApp("application-a")
        // Then
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isTrue()
        assertThat(testedClient.state.getCurrentState()).isEqualTo(FlagsClientState.Ready)
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        assertThat(requests.last().header("dd-application-id")).isEqualTo("application-a")
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isTrue()
    }

    @Test
    fun `M keep verified flags W resolveBooleanValue() {RUM stops}`() {
        // Given
        updateApp("application-a")
        create(ResponseSigningMode.REQUIRED)
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        // When
        fakeDatadogContext = fakeDatadogContext.copy(featuresContext = emptyMap())
        stubCore.updateFeatureContext(Feature.RUM_FEATURE_NAME, false) { it.clear() }
        // Then
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isTrue()
        assertThat(testedClient.state.getCurrentState()).isEqualTo(FlagsClientState.Ready)
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        assertThat(requests.last().header("dd-application-id")).isNull()
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isTrue()
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

    @ParameterizedTest
    @EnumSource(ResponseSigningMode::class)
    fun `M preserve response consent W resolve() {assigned and missing flags}`(mode: ResponseSigningMode) {
        // Given
        create(mode)
        for (consent in listOf(false, true)) {
            observeFullEvaluationData = consent
            testedClient.setEvaluationContext(EvaluationContext("subject"))
            queue.removeFirst().run()
            // When
            val assigned = testedClient.resolve("critical-demo", false)
            val missing = testedClient.resolve("missing", false)
            // Then
            assertThat(assigned.value).isTrue()
            assertThat(assigned.flagMetadata).containsEntry("__dd_observe_full_evaluation_data", consent)
            assertThat(missing.flagMetadata).containsEntry("__dd_observe_full_evaluation_data", consent)
        }
    }

    private fun failureCallback(failures: MutableList<Throwable>) = object : EvaluationContextCallback {
        override fun onSuccess() = Unit
        override fun onFailure(error: Throwable) {
            failures += error
        }
    }

    private fun updateApp(applicationId: String, sessionId: String = "session") {
        val context = mapOf(FeatureContextKeys.RUM_CONTEXT to RumContext(applicationId, sessionId))
        fakeDatadogContext = fakeDatadogContext.copy(featuresContext = mapOf(Feature.RUM_FEATURE_NAME to context))
        stubCore.updateFeatureContext(Feature.RUM_FEATURE_NAME, false) { it.putAll(context) }
    }

    /** [mode] null builds a client that never calls `experimentalResponseSigning()`; [roots] null uses the pinned anchor. */
    private fun advanceClock(seconds: Long) {
        now += seconds
        scheduledExecutors.toList().forEach { it.runDueTasks() }
    }

    private fun create(
        mode: ResponseSigningMode?,
        roots: List<String>? = listOf(CanonicalResponseSigner.root),
        expireVerifiedFlags: Boolean = false
    ) {
        val core = object : InternalSdkCore by stubCore {
            override val internalLogger: InternalLogger = object : InternalLogger by stubCore.internalLogger {
                override fun log(
                    level: InternalLogger.Level,
                    target: InternalLogger.Target,
                    messageBuilder: () -> String,
                    throwable: Throwable?,
                    onlyOnce: Boolean,
                    additionalProperties: Map<String, Any?>?
                ) {
                    if (target == InternalLogger.Target.USER) userMessages += messageBuilder()
                    stubCore.internalLogger.log(
                        level,
                        target,
                        messageBuilder,
                        throwable,
                        onlyOnce,
                        additionalProperties
                    )
                }
            }
            override fun createScheduledExecutorService(executorContext: String): ScheduledExecutorService =
                StubScheduledExecutorService(executorContext) { now * 1000 }.also { scheduledExecutors += it }
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
        val builder = FlagsConfiguration.Builder().trackExposures(false).trackEvaluations(false)
            .rumIntegrationEnabled(false).useCustomFlagEndpoint(endpoint)
        when {
            mode == null -> Unit
            roots == null -> builder.experimentalResponseSigning(mode).expireVerifiedFlags(expireVerifiedFlags)
            else -> builder.responseSigningWithTestRoots(mode, roots, expireVerifiedFlags)
        }
        Flags.enable(builder.build(), core)
        testedClient = FlagsClient.Builder(sdkCore = core).build()
    }

    private fun response(request: Request): Response {
        val json = """
            {"data":{"attributes":{"observeFullEvaluationData":$observeFullEvaluationData,"flags":{"critical-demo":{
              "variationType":"boolean","variationValue":"true","doLog":false,
              "allocationKey":"test","variationKey":"on","reason":"TARGETING_MATCH","extraLogging":{}
            }}}}}
        """.trimIndent()
        val headers = CanonicalResponseSigner.response(
            request,
            json,
            credentialName,
            issuedAt = signedAt ?: CanonicalResponseSigner.now,
            minimumNotBefore = minimumNotBefore
        ).headers
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

    companion object {
        const val PREFIX = "x-datadog-feature-flags-"
        const val PROOF_WINDOW_SECONDS = 300L

        @JvmStatic
        fun expiryCases(): List<Arguments> = listOf(true, false).flatMap { expire ->
            listOf(true, false).flatMap { fromStale ->
                listOf(true, false).map { byRead -> Arguments.of(expire, fromStale, byRead) }
            }
        }
    }
}
