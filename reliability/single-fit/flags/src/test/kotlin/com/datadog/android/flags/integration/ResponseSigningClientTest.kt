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
import com.datadog.android.flags.FlagsContextSupersededException
import com.datadog.android.flags.FlagsInitializationTimeoutException
import com.datadog.android.flags.FlagsResponseSigningException
import com.datadog.android.flags.FlagsStateListener
import com.datadog.android.flags.ResponseSigningFailureReason
import com.datadog.android.flags.ResponseSigningMode
import com.datadog.android.flags.integration.FlagsClientTest.Companion.createStubSdkCore
import com.datadog.android.flags.integration.tests.elmyr.FlagsIntegrationForgeConfigurator
import com.datadog.android.flags.integration.tests.utils.responseSigningWithTestRoots
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.FlagsClientState
import com.datadog.android.flags.openfeature.asOpenFeatureProvider
import com.datadog.android.internal.FeatureContextKeys
import com.datadog.android.internal.rum.RumContext
import com.datadog.android.internal.time.TimeProvider
import com.google.gson.JsonParser
import dev.openfeature.kotlin.sdk.ImmutableContext
import dev.openfeature.kotlin.sdk.OpenFeatureAPI
import dev.openfeature.kotlin.sdk.OpenFeatureStatus
import dev.openfeature.kotlin.sdk.events.OpenFeatureProviderEvents
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
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
import org.junit.jupiter.params.provider.ValueSource
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

    // Scheduled timers and the elapsed realtime clock run on elapsed time; a wall-clock step changes only now.
    private var elapsedShift = 0L
    private var coreContextReads = 0
    private var buildContextReads = 0
    private var cancelledChecks = 0
    private val requests = mutableListOf<Request>()
    private val scheduledExecutors = mutableListOf<StubScheduledExecutorService>()
    private val userMessages = mutableListOf<String>()

    // Every log of every target: message, properties and throwable messages.
    private val loggedText = mutableListOf<String>()

    // When set, the wall clock moves by this step before elapsed-time read number stepAtElapsedRead; read 1 is at
    // verification.
    private var stepAfterVerification: Long? = null
    private var stepAtElapsedRead = 2
    private var elapsedReads = 0
    private var signedAt: Long? = null
    private var minimumNotBefore: Long? = null
    private var flagSelectionTags: List<String>? = null
    private var flagSelectionIgnored: String? = null
    private var listSelectionIgnored = true
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
    fun `M list each bound header W setEvaluationContext() {all modes}`(mode: ResponseSigningMode) {
        // Given
        updateApp("app-1")
        create(mode)
        // When
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        // Then
        val request = requests.single()
        if (mode == ResponseSigningMode.OFF) {
            assertThat(request.header(PREFIX + "signed-headers")).isNull()
        } else {
            assertThat(request.header(PREFIX + "signed-headers"))
                .isEqualTo("content-type,dd-application-id,x-dd-feature-flags-capabilities")
            assertThat(testedClient.state.getCurrentState()).isEqualTo(FlagsClientState.Ready)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["OBSERVE", "REQUIRED"])
    fun `M report the signed ignored reason W setEvaluationContext() {verified response with selection}`(
        mode: ResponseSigningMode
    ) {
        // Given
        flagSelectionTags = listOf("team:payments")
        flagSelectionIgnored = "tag_load_failed"
        create(mode)
        // When
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        // Then
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isTrue()
        assertThat(userMessages).contains(
            "flagSelection was ignored by the server (tag_load_failed). Loaded the full flag configuration."
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["OBSERVE", "REQUIRED"])
    fun `M keep the raw signed ignored value out of every log W setEvaluationContext() {unknown reason}`(
        mode: ResponseSigningMode
    ) {
        // Given a verified response whose signed DD-Flag-Selection-Ignored value is not a known reason
        flagSelectionTags = listOf("team:payments")
        flagSelectionIgnored = "raw-edge-value-1234"
        create(mode)
        // When
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        // Then the logs and telemetry carry only the sanitized reason
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isTrue()
        assertThat(userMessages).contains(
            "flagSelection was ignored by the server (unknown). Loaded the full flag configuration."
        )
        assertThat(loggedText).isNotEmpty.noneMatch { it.contains("raw-edge-value-1234") }
    }

    @Test
    fun `M reject the proof W setEvaluationContext() {selection sent, response list omits the ignored reason}`() {
        // Given
        flagSelectionTags = listOf("team:payments")
        listSelectionIgnored = false
        create(ResponseSigningMode.REQUIRED)
        val failures = mutableListOf<Throwable>()
        // When
        testedClient.setEvaluationContext(EvaluationContext("subject"), failureCallback(failures))
        queue.removeFirst().run()
        // Then
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isFalse()
        assertThat((failures.single() as FlagsResponseSigningException).reason)
            .isEqualTo(ResponseSigningFailureReason.INVALID_PROOF)
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
        assertThat(signingEvents()).anyMatch { it["result"] == "unverified" && it["reason"] == "missing-proof" }
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

    @Test
    fun `M keep defaults W clock moves back after expiry {required, expireVerifiedFlags}`() {
        // Given
        create(ResponseSigningMode.REQUIRED, expireVerifiedFlags = true)
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        advanceClock(PROOF_WINDOW_SECONDS)
        assertThat(testedClient.state.getCurrentState()).isInstanceOf(FlagsClientState.Error::class.java)
        // When the wall clock moves back into the proof window, without a new fetch
        now -= PROOF_WINDOW_SECONDS / 2
        // Then the expired proof stays expired
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isFalse()
        assertThat(testedClient.state.getCurrentState()).isInstanceOf(FlagsClientState.Error::class.java)
    }

    @Test
    fun `M keep defaults W proof expires in Error, then clock moves back {required, expireVerifiedFlags}`() {
        // Given: flags for the subject, then a failed switch to another context
        create(ResponseSigningMode.REQUIRED, expireVerifiedFlags = true)
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        responseKind = "tampered"
        testedClient.setEvaluationContext(EvaluationContext("other"), null)
        queue.removeFirst().run()
        assertThat(testedClient.state.getCurrentState()).isInstanceOf(FlagsClientState.Error::class.java)
        // When the proof expires while the client is in Error, and the clock moves back without a read
        advanceClock(PROOF_WINDOW_SECONDS)
        now -= PROOF_WINDOW_SECONDS / 2
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        // Then the expired subject flags stay retired
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isFalse()
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
        assertThat(signingEvents()).noneMatch { it["result"] == "unverified" }
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

    @Test
    fun `M keep the newer unsigned flags W verified proof expired, then error {observe}`() {
        // Given: verified flags, then a newer unsigned observation for the same context
        create(ResponseSigningMode.OBSERVE)
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        responseKind = "unsigned-false"
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        assertThat(testedClient.resolveBooleanValue("critical-demo", true)).isFalse()
        // When the verified proof expires, and a refresh fails
        advanceClock(PROOF_WINDOW_SECONDS)
        responseKind = "403"
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        // Then the latest fetched flags stay; the expired verified flags do not come back
        assertThat(testedClient.resolveBooleanValue("critical-demo", true)).isFalse()
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

    private fun signingEvents(): List<Map<String, Any?>> = stubCore.telemetryEventsWritten()
        .filter { it.message.startsWith("flags.response_signing ") }
        .map { it.additionalProperties }

    @Test
    fun `M send no signing telemetry W setEvaluationContext() {OFF}`() {
        // Given
        create(ResponseSigningMode.OFF)
        responseKind = "unsigned"
        // When
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        // Then
        assertThat(signingEvents()).isEmpty()
    }

    @Test
    fun `M send the bounded event W setEvaluationContext() {OBSERVE verified and unverified}`() {
        // Given
        create(ResponseSigningMode.OBSERVE)
        // When
        testedClient.setEvaluationContext(EvaluationContext("subject-1"), null)
        queue.removeFirst().run()
        responseKind = "unsigned"
        repeat(2) {
            testedClient.setEvaluationContext(EvaluationContext("subject-$it-unsigned"), null)
            queue.removeFirst().run()
        }
        // Then: each outcome has its own message identity; the RUM handler deduplicates identical ones per session.
        assertThat(stubCore.telemetryEventsWritten().map { it.message }.filter { it.startsWith("flags.") }.distinct())
            .containsExactlyInAnyOrder(
                "flags.response_signing observe verified",
                "flags.response_signing observe unverified missing-proof"
            )
        assertThat(signingEvents()).allSatisfy {
            assertThat(it.keys).isSubsetOf("mode", "result", "reason", "sdk_version", "site")
            assertThat(it["mode"]).isEqualTo("observe")
        }
        assertThat(signingEvents().map { it["result"] to it["reason"] }.distinct())
            .containsExactlyInAnyOrder("verified" to null, "unverified" to "missing-proof")
    }

    @ParameterizedTest(name = "expireVerifiedFlags={0}")
    @ValueSource(booleans = [false, true])
    fun `M apply proof expiry W refresh is reconciling {required}`(expireVerifiedFlags: Boolean) {
        // Given
        create(ResponseSigningMode.REQUIRED, expireVerifiedFlags = expireVerifiedFlags)
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        assertThat(testedClient.state.getCurrentState()).isEqualTo(FlagsClientState.Reconciling)
        // When
        advanceClock(PROOF_WINDOW_SECONDS)
        // Then
        assertExpiredState(expireVerifiedFlags)
        // When the pending refresh returns a verified response
        signedAt = now
        queue.removeFirst().run()
        // Then
        assertThat(testedClient.state.getCurrentState()).isEqualTo(FlagsClientState.Ready)
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isTrue()
    }

    @Test
    fun `M keep the expiry error W refresh fails after expiry {required, expireVerifiedFlags}`() {
        // Given
        create(ResponseSigningMode.REQUIRED, expireVerifiedFlags = true)
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        advanceClock(PROOF_WINDOW_SECONDS)
        // When
        responseKind = "unsigned"
        queue.removeFirst().run()
        // Then: the failed refresh rechecks eligibility, so it does not publish Stale.
        assertThat(testedClient.state.getCurrentState()).isInstanceOf(FlagsClientState.Error::class.java)
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isFalse()
    }

    @ParameterizedTest(name = "expireVerifiedFlags={0}")
    @ValueSource(booleans = [false, true])
    fun `M handle a backward clock step W resolveBooleanValue() {required}`(expireVerifiedFlags: Boolean) {
        // Given
        create(ResponseSigningMode.REQUIRED, expireVerifiedFlags = expireVerifiedFlags)
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        val verifiedAt = now
        // When the clock steps back 30 s
        now = verifiedAt - CLOCK_STEP_ALLOWANCE_SECONDS
        // Then
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isTrue()
        assertThat(testedClient.state.getCurrentState()).isEqualTo(FlagsClientState.Ready)
        // When the clock steps back 31 s
        now = verifiedAt - CLOCK_STEP_ALLOWANCE_SECONDS - 1
        val value = testedClient.resolveBooleanValue("critical-demo", false)
        // Then
        assertThat(value).isEqualTo(!expireVerifiedFlags)
        assertExpiredState(expireVerifiedFlags)
        // When the clock returns into the proof window
        now = verifiedAt + 1
        // Then
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isTrue()
        assertThat(testedClient.state.getCurrentState()).isEqualTo(FlagsClientState.Ready)
    }

    private fun assertExpiredState(expireVerifiedFlags: Boolean) {
        val state = testedClient.state.getCurrentState()
        if (expireVerifiedFlags) {
            assertThat(state).isInstanceOf(FlagsClientState.Error::class.java)
            val error = (state as FlagsClientState.Error).error
            assertThat(error).isInstanceOf(FlagsResponseSigningException::class.java)
            assertThat((error as FlagsResponseSigningException).reason.code).isEqualTo("expired-proof")
        } else {
            assertThat(state).isEqualTo(FlagsClientState.Stale)
        }
    }

    @Test
    fun `M complete as superseded W setEvaluationContext() {newer context before admission}`() {
        // Given
        create(ResponseSigningMode.REQUIRED)
        val failuresA = mutableListOf<Throwable>()
        val failuresB = mutableListOf<Throwable>()
        testedClient.setEvaluationContext(EvaluationContext("subject-a"), failureCallback(failuresA))
        testedClient.setEvaluationContext(EvaluationContext("subject-b"), failureCallback(failuresB))
        // When
        queue.removeFirst().run()
        queue.removeFirst().run()
        // Then
        assertThat(failuresA.single()).isInstanceOf(FlagsContextSupersededException::class.java)
        assertThat(failuresB).isEmpty()
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isTrue()
    }

    @Test
    fun `M apply the initialization timeout W setEvaluationContext() {newer context while pending}`() {
        // Given
        create(ResponseSigningMode.REQUIRED)
        testedClient.setEvaluationContext(EvaluationContext("subject-a"), null)
        testedClient.setEvaluationContext(EvaluationContext("subject-b"), null)
        // When
        advanceClock(INITIALIZATION_TIMEOUT_SECONDS + 1)
        // Then
        val state = testedClient.state.getCurrentState()
        assertThat(state).isInstanceOf(FlagsClientState.Error::class.java)
        assertThat(
            (state as FlagsClientState.Error).error
        ).isInstanceOf(FlagsInitializationTimeoutException::class.java)
    }

    @Test
    fun `M keep observe assignments readable W setEvaluationContext() {refresh in flight}`() {
        // Given
        create(ResponseSigningMode.OBSERVE)
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        // When
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        // Then
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isTrue()
    }

    @Test
    fun `M persist under the emitted scope W setEvaluationContext() {OBSERVE, transport changed the token}`() {
        // Given
        create(ResponseSigningMode.OBSERVE)
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        val preparedScope = store.writtenScopes.last()
        stubCore.stopFeatures()
        create(ResponseSigningMode.OBSERVE)
        responseKind = "emitted-token"
        // When
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        // Then
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isTrue()
        assertThat(store.writtenScopes.last()).isNotEqualTo(preparedScope)
    }

    @ParameterizedTest
    @EnumSource(ResponseSigningMode::class, names = ["OFF", "REQUIRED"])
    fun `M remove the other mode's persisted entries W build() {mode change}`(mode: ResponseSigningMode) {
        // Given: an OFF entry, then an OBSERVE entry for the same client name.
        create(null)
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        stubCore.stopFeatures()
        create(ResponseSigningMode.OBSERVE)
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        stubCore.stopFeatures()
        assertThat(store.values.keys).anyMatch { it.contains("signing-v1-observe-state-") }
        assertThat(store.values.keys).anyMatch { !it.contains("signing-v1-observe-state-") }
        // When
        create(mode)
        // Then
        assertThat(store.values.keys).noneMatch { it.contains("signing-v1-observe-state-") }
        if (mode == ResponseSigningMode.REQUIRED) {
            assertThat(store.values).isEmpty()
        } else {
            assertThat(store.values).isNotEmpty()
        }
    }

    @Test
    fun `M apply the initialization timeout W superseded request completes first {required}`() {
        // Given
        create(ResponseSigningMode.REQUIRED)
        testedClient.setEvaluationContext(EvaluationContext("subject-a"), null)
        testedClient.setEvaluationContext(EvaluationContext("subject-b"), null)
        queue.removeFirst().run()
        // When
        advanceClock(INITIALIZATION_TIMEOUT_SECONDS + 1)
        // Then
        val state = testedClient.state.getCurrentState()
        assertThat(state).isInstanceOf(FlagsClientState.Error::class.java)
        assertThat(
            (state as FlagsClientState.Error).error
        ).isInstanceOf(FlagsInitializationTimeoutException::class.java)
    }

    @Test
    fun `M ignore the kept deadline W later context {required, replacement succeeded}`() {
        // Given
        create(ResponseSigningMode.REQUIRED)
        testedClient.setEvaluationContext(EvaluationContext("subject-a"), null)
        testedClient.setEvaluationContext(EvaluationContext("subject-b"), null)
        queue.removeFirst().run()
        queue.removeFirst().run()
        assertThat(testedClient.state.getCurrentState()).isEqualTo(FlagsClientState.Ready)
        testedClient.setEvaluationContext(EvaluationContext("subject-c"), null)
        // When the first request's deadline passes
        advanceClock(INITIALIZATION_TIMEOUT_SECONDS + 1)
        // Then
        assertThat(testedClient.state.getCurrentState()).isEqualTo(FlagsClientState.Reconciling)
    }

    @Test
    fun `M ignore the superseded deadline W replacement installs first {required}`() {
        // Given
        create(ResponseSigningMode.REQUIRED)
        testedClient.setEvaluationContext(EvaluationContext("subject-a"), null)
        testedClient.setEvaluationContext(EvaluationContext("subject-b"), null)
        queue.removeLast().run()
        assertThat(testedClient.state.getCurrentState()).isEqualTo(FlagsClientState.Ready)
        queue.removeFirst().run()
        testedClient.setEvaluationContext(EvaluationContext("subject-c"), null)
        // When the first request's deadline passes
        advanceClock(INITIALIZATION_TIMEOUT_SECONDS + 1)
        // Then
        assertThat(testedClient.state.getCurrentState()).isEqualTo(FlagsClientState.Reconciling)
    }

    @Test
    fun `M ignore the superseded deadline W replacement installs before the first fetch runs {required}`() {
        // Given
        create(ResponseSigningMode.REQUIRED)
        val failures = mutableListOf<Throwable>()
        testedClient.setEvaluationContext(EvaluationContext("subject-a"), failureCallback(failures))
        testedClient.setEvaluationContext(EvaluationContext("subject-b"), null)
        queue.removeLast().run()
        assertThat(testedClient.state.getCurrentState()).isEqualTo(FlagsClientState.Ready)
        testedClient.setEvaluationContext(EvaluationContext("subject-c"), null)
        // When the first request's deadline passes before its fetch runs
        advanceClock(INITIALIZATION_TIMEOUT_SECONDS + 1)
        // Then
        assertThat(testedClient.state.getCurrentState()).isEqualTo(FlagsClientState.Reconciling)
        assertThat(failures).isEmpty()
        queue.removeFirst().run()
        assertThat(failures.single()).isInstanceOf(FlagsContextSupersededException::class.java)
    }

    @Test
    fun `M use the current scope for fallback W refresh fails {OBSERVE, transport changed the token}`() {
        // Given
        create(ResponseSigningMode.OBSERVE)
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        assertThat(testedClient.state.getCurrentState()).isEqualTo(FlagsClientState.Ready)
        responseKind = "emitted-token-invalid"
        // When
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        // Then: the new scope has no assignments, so the client does not report Stale.
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isFalse()
        assertThat(testedClient.state.getCurrentState()).isInstanceOf(FlagsClientState.Error::class.java)
    }

    @ParameterizedTest
    @EnumSource(ResponseSigningMode::class)
    fun `M read the core context only with signing W build()`(mode: ResponseSigningMode) {
        // When
        create(mode.takeUnless { it == ResponseSigningMode.OFF })
        // Then
        assertThat(buildContextReads).isEqualTo(if (mode == ResponseSigningMode.OFF) 0 else 1)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `M report proof expiry W OpenFeatureAPI {required, expireVerifiedFlags, context set pending}`() = runTest {
        // Given
        create(ResponseSigningMode.REQUIRED, expireVerifiedFlags = true)
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val errorEvents = mutableListOf<OpenFeatureProviderEvents.ProviderError>()
        backgroundScope.launch(dispatcher) {
            OpenFeatureAPI.observe<OpenFeatureProviderEvents.ProviderError>().collect { errorEvents += it }
        }
        try {
            val initialize = launch(dispatcher) {
                OpenFeatureAPI.setProviderAndWait(
                    testedClient.asOpenFeatureProvider(),
                    ImmutableContext("subject"),
                    dispatcher
                )
            }
            queue.removeFirst().run()
            initialize.join()
            val contextSet = launch(dispatcher) {
                OpenFeatureAPI.setEvaluationContextAndWait(ImmutableContext("other-subject"))
            }
            // When the proof expires while the context update is pending
            advanceClock(PROOF_WINDOW_SECONDS)
            // Then
            assertThat(errorEvents).hasSize(1)
            assertThat(errorEvents.single().error.message).startsWith("Flag response signing failed (expired-proof)")
            assertThat(OpenFeatureAPI.getStatus()).isInstanceOf(OpenFeatureStatus.Error::class.java)
            signedAt = now
            queue.removeFirst().run()
            contextSet.join()
        } finally {
            OpenFeatureAPI.shutdown()
        }
    }

    @Test
    fun `M go Stale at the elapsed deadline W wall clock stepped back {required}`() {
        // Given
        create(ResponseSigningMode.REQUIRED)
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        // When the wall clock steps back 20 s, inside the step allowance
        now -= 20
        elapsedShift += 20
        advanceClock(PROOF_WINDOW_SECONDS - 1)
        // Then
        assertThat(testedClient.state.getCurrentState()).isEqualTo(FlagsClientState.Ready)
        // When the elapsed time reaches the proof window, before the wall clock reaches expires-at
        advanceClock(1)
        // Then the step back does not extend the proof
        assertThat(testedClient.state.getCurrentState()).isEqualTo(FlagsClientState.Stale)
    }

    @Test
    fun `M keep defaults at the elapsed deadline W wall clock stepped back {required, expireVerifiedFlags, read}`() {
        // Given
        create(ResponseSigningMode.REQUIRED, expireVerifiedFlags = true)
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        // When the wall clock steps back 20 s and the elapsed time reaches the window, before any timer runs
        now -= 20
        elapsedShift += 20 + PROOF_WINDOW_SECONDS
        // Then a read does not serve the verified flags
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isFalse()
        assertThat(testedClient.state.getCurrentState()).isInstanceOf(FlagsClientState.Error::class.java)
    }

    @Test
    fun `M go Stale at the original deadline W late response, then wall clock stepped back {required}`() {
        // Given a response verified 290 s after its issued-at, with 10 s left
        signedAt = now - PROOF_WINDOW_SECONDS + 10
        create(ResponseSigningMode.REQUIRED)
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        assertThat(testedClient.state.getCurrentState()).isEqualTo(FlagsClientState.Ready)
        // When the wall clock steps back 20 s, inside the step allowance, and 10 s of elapsed time pass
        now -= 20
        elapsedShift += 20
        advanceClock(10)
        // Then the step back does not extend the proof past its original deadline
        assertThat(testedClient.state.getCurrentState()).isEqualTo(FlagsClientState.Stale)
    }

    @Test
    fun `M install as Stale and serve the flags W proof expires before installation {required, default}`() {
        // Given a response with 1 s left, and a clock that passes expires-at after verification
        signedAt = now - PROOF_WINDOW_SECONDS + 1
        stepAfterVerification = 2
        create(ResponseSigningMode.REQUIRED)
        val states = mutableListOf<FlagsClientState>()
        testedClient.state.addListener(object : FlagsStateListener {
            override fun onStateChanged(newState: FlagsClientState) {
                states += newState
            }
        })
        // When
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        // Then the client never reports Ready for the expired proof, and keeps the verified flags
        assertThat(stepAfterVerification).isNull()
        assertThat(states).doesNotContain(FlagsClientState.Ready)
        assertThat(testedClient.state.getCurrentState()).isEqualTo(FlagsClientState.Stale)
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isTrue()
    }

    @ParameterizedTest(name = "expires at elapsed read {0}")
    @ValueSource(ints = [2, 3])
    fun `M fail with expired-proof W proof expires around installation {required, expireVerifiedFlags}`(read: Int) {
        // Given a response with 1 s left, and a clock that passes expires-at before (read 2) or after (read 3) the
        // repository admits the proof
        signedAt = now - PROOF_WINDOW_SECONDS + 1
        stepAfterVerification = 2
        stepAtElapsedRead = read
        create(ResponseSigningMode.REQUIRED, expireVerifiedFlags = true)
        val states = mutableListOf<FlagsClientState>()
        testedClient.state.addListener(object : FlagsStateListener {
            override fun onStateChanged(newState: FlagsClientState) {
                states += newState
            }
        })
        val failures = mutableListOf<Throwable>()
        // When
        testedClient.setEvaluationContext(EvaluationContext("subject"), failureCallback(failures))
        queue.removeFirst().run()
        // Then the strict policy never reports the expired proof as Ready or Stale
        assertThat(stepAfterVerification).isNull()
        assertThat(states).noneMatch { it == FlagsClientState.Ready || it == FlagsClientState.Stale }
        val state = testedClient.state.getCurrentState()
        assertThat(((state as FlagsClientState.Error).error as FlagsResponseSigningException).reason)
            .isEqualTo(ResponseSigningFailureReason.EXPIRED_PROOF)
        assertThat((failures.single() as FlagsResponseSigningException).reason)
            .isEqualTo(ResponseSigningFailureReason.EXPIRED_PROOF)
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isFalse()
    }

    @Test
    fun `M fail with expired-proof W leaf expired before the build floor {required, device clock in leaf validity}`() {
        // Given the device clock is inside the leaf validity, but the leaf expired before the release build floor
        create(ResponseSigningMode.REQUIRED, buildFloor = RELEASE_BUILD_FLOOR)
        // When
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        // Then
        val state = testedClient.state.getCurrentState()
        assertThat(state).isInstanceOf(FlagsClientState.Error::class.java)
        assertThat(((state as FlagsClientState.Error).error as FlagsResponseSigningException).reason)
            .isEqualTo(ResponseSigningFailureReason.EXPIRED_PROOF)
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isFalse()
    }

    @Test
    fun `M reject an older proof W clock set back below the last issued-at {required}`() {
        // Given
        create(ResponseSigningMode.REQUIRED)
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        assertThat(testedClient.state.getCurrentState()).isEqualTo(FlagsClientState.Ready)
        // When the clock moves back 1 h and a refresh returns a proof that is valid at that clock
        now -= HOUR_SECONDS
        signedAt = now
        testedClient.setEvaluationContext(EvaluationContext("other"), null)
        queue.removeFirst().run()
        // Then
        val state = testedClient.state.getCurrentState()
        assertThat(state).isInstanceOf(FlagsClientState.Error::class.java)
        assertThat(((state as FlagsClientState.Error).error as FlagsResponseSigningException).reason)
            .isEqualTo(ResponseSigningFailureReason.EXPIRED_PROOF)
    }

    @Test
    fun `M keep Ready W previous proof timer fires during installation {required}`() {
        // Given
        create(ResponseSigningMode.REQUIRED)
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        advanceClock(PROOF_WINDOW_SECONDS - 10)
        signedAt = now
        var armed = false
        var stepped = false
        testedClient.state.addListener(object : FlagsStateListener {
            override fun onStateChanged(newState: FlagsClientState) {
                if (armed && newState == FlagsClientState.Ready && !stepped) {
                    stepped = true
                    advanceClock(20)
                }
            }
        })
        armed = true
        // When the refresh installs, and the previous proof's timer fires before Ready is delivered
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        // Then
        assertThat(stepped).isTrue()
        assertThat(testedClient.state.getCurrentState()).isEqualTo(FlagsClientState.Ready)
    }

    @Test
    fun `M keep the refresh result W clock returns {required, refresh failed during clock step}`() {
        // Given
        create(ResponseSigningMode.REQUIRED)
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        val verifiedAt = now
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        now = verifiedAt - CLOCK_STEP_ALLOWANCE_SECONDS - 1
        testedClient.resolveBooleanValue("critical-demo", false)
        assertThat(testedClient.state.getCurrentState()).isEqualTo(FlagsClientState.Stale)
        responseKind = "403"
        queue.removeFirst().run()
        val afterFailure = testedClient.state.getCurrentState()
        // When the clock returns into the proof window
        now = verifiedAt + 1
        testedClient.resolveBooleanValue("critical-demo", false)
        // Then: no request is pending, so the client does not return to Reconciling.
        assertThat(testedClient.state.getCurrentState()).isNotEqualTo(FlagsClientState.Reconciling)
        assertThat(afterFailure).isNotEqualTo(FlagsClientState.Reconciling)
    }

    @Test
    fun `M stay reconciling W proof expires {required, new context has no fallback}`() {
        // Given
        create(ResponseSigningMode.REQUIRED)
        testedClient.setEvaluationContext(EvaluationContext("subject-a"), null)
        queue.removeFirst().run()
        testedClient.setEvaluationContext(EvaluationContext("subject-b"), null)
        // When
        advanceClock(PROOF_WINDOW_SECONDS)
        // Then
        assertThat(testedClient.state.getCurrentState()).isEqualTo(FlagsClientState.Reconciling)
        // When the pending fetch fails
        responseKind = "403"
        queue.removeFirst().run()
        // Then
        assertThat(testedClient.state.getCurrentState()).isInstanceOf(FlagsClientState.Error::class.java)
    }

    @Test
    fun `M keep another client's cache W build() {name ends with the observe suffix}`() {
        // Given
        create(null, name = "a-signing-v1-observe")
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        val persisted = store.values.keys.toSet()
        assertThat(persisted).isNotEmpty()
        stubCore.stopFeatures()
        // When
        create(null, name = "a")
        // Then
        assertThat(store.values.keys).containsAll(persisted)
    }

    @ParameterizedTest
    @ValueSource(strings = ["unsigned", "tampered"])
    fun `M persist no flag data W setEvaluationContext() {required rejects the response}`(kind: String) {
        // Given
        create(ResponseSigningMode.REQUIRED)
        responseKind = kind
        // When
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        // Then
        assertThat(testedClient.state.getCurrentState()).isInstanceOf(FlagsClientState.Error::class.java)
        assertThat(store.values).isEmpty()
        assertThat(store.operations).isZero()
        // When the app restarts and the next fetch fails
        stubCore.stopFeatures()
        responseKind = "403"
        create(ResponseSigningMode.REQUIRED)
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        // Then
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isFalse()
        assertThat(store.values).isEmpty()
        assertThat(store.operations).isZero()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @ParameterizedTest
    @ValueSource(strings = ["unsigned", "403"])
    fun `M report each failure once W OpenFeatureAPI {required, initialize and context set fail}`(kind: String) =
        runTest {
            // Given
            create(ResponseSigningMode.REQUIRED)
            responseKind = kind
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val errorStatuses = mutableListOf<OpenFeatureStatus.Error>()
            val errorEvents = mutableListOf<OpenFeatureProviderEvents.ProviderError>()
            backgroundScope.launch(dispatcher) {
                OpenFeatureAPI.statusFlow.filterIsInstance<OpenFeatureStatus.Error>().collect { errorStatuses += it }
            }
            backgroundScope.launch(dispatcher) {
                OpenFeatureAPI.observe<OpenFeatureProviderEvents.ProviderError>().collect { errorEvents += it }
            }
            try {
                // When
                val initialize = launch(dispatcher) {
                    OpenFeatureAPI.setProviderAndWait(
                        testedClient.asOpenFeatureProvider(),
                        ImmutableContext("subject"),
                        dispatcher
                    )
                }
                queue.removeFirst().run()
                initialize.join()
                val initializeCounts = errorStatuses.size to errorEvents.size
                val contextSet = launch(dispatcher) {
                    OpenFeatureAPI.setEvaluationContextAndWait(ImmutableContext("other-subject"))
                }
                queue.removeFirst().run()
                contextSet.join()
                // Then
                val signingFailure = kind == "unsigned"
                val expectedEvents = if (signingFailure) 0 else 1
                // A 403 keeps its provider event, so OpenFeature reports it as an event and as the thrown error.
                assertThat(initializeCounts).isEqualTo(1 + expectedEvents to expectedEvents)
                assertThat(errorStatuses.size to errorEvents.size)
                    .isEqualTo(2 * (1 + expectedEvents) to 2 * expectedEvents)
                assertThat(OpenFeatureAPI.getStatus()).isInstanceOf(OpenFeatureStatus.Error::class.java)
                if (signingFailure) {
                    assertThat(errorStatuses).allSatisfy {
                        assertThat(it.error.message).startsWith("Flag response signing failed (missing-proof)")
                    }
                }
            } finally {
                OpenFeatureAPI.shutdown()
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
    private fun logged(messageBuilder: () -> String, throwable: Throwable?, properties: Map<String, Any?>?): String =
        listOfNotNull(messageBuilder(), properties?.toString(), throwable?.toString(), throwable?.cause?.toString())
            .joinToString(" ")

    private fun advanceClock(seconds: Long) {
        now += seconds
        scheduledExecutors.toList().forEach { it.runDueTasks() }
    }

    private fun create(
        mode: ResponseSigningMode?,
        roots: List<String>? = listOf(CanonicalResponseSigner.root),
        expireVerifiedFlags: Boolean = false,
        name: String? = null,
        buildFloor: Long = 0L
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
                    loggedText += logged(messageBuilder, throwable, additionalProperties)
                    stubCore.internalLogger.log(
                        level,
                        target,
                        messageBuilder,
                        throwable,
                        onlyOnce,
                        additionalProperties
                    )
                }

                override fun log(
                    level: InternalLogger.Level,
                    targets: List<InternalLogger.Target>,
                    messageBuilder: () -> String,
                    throwable: Throwable?,
                    onlyOnce: Boolean,
                    additionalProperties: Map<String, Any?>?
                ) {
                    if (InternalLogger.Target.USER in targets) userMessages += messageBuilder()
                    loggedText += logged(messageBuilder, throwable, additionalProperties)
                    stubCore.internalLogger.log(
                        level,
                        targets,
                        messageBuilder,
                        throwable,
                        onlyOnce,
                        additionalProperties
                    )
                }
            }
            override fun createScheduledExecutorService(executorContext: String): ScheduledExecutorService =
                StubScheduledExecutorService(executorContext) {
                    (now + elapsedShift) * 1000
                }.also {
                    scheduledExecutors +=
                        it
                }
            override fun getDatadogContext(withFeatureContexts: Set<String>) = fakeDatadogContext.also {
                if (withFeatureContexts.isEmpty()) coreContextReads++
            }
            override fun setContextUpdateReceiver(listener: FeatureContextUpdateReceiver) {
                stubCore.setContextUpdateReceiver(listener)
                fakeDatadogContext.featuresContext[Feature.RUM_FEATURE_NAME]?.let {
                    listener.onContextUpdate(Feature.RUM_FEATURE_NAME, it)
                }
            }
            override val timeProvider = object : TimeProvider by stubCore.timeProvider {
                override fun getDeviceTimestampMillis() = now * 1000
                override fun getDeviceElapsedRealtimeMillis(): Long {
                    elapsedReads++
                    stepAfterVerification?.takeIf { elapsedReads == stepAtElapsedRead }?.let { step ->
                        stepAfterVerification = null
                        now += step
                        elapsedShift -= step
                    }
                    return (now + elapsedShift) * 1000
                }
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
        flagSelectionTags?.let { builder.flagSelection(it, null) }
        when {
            mode == null -> Unit
            roots == null -> builder.experimentalResponseSigning(mode).expireVerifiedFlags(expireVerifiedFlags)
            else -> builder.responseSigningWithTestRoots(mode, roots, expireVerifiedFlags, buildFloor)
        }
        Flags.enable(builder.build(), core)
        val readsBeforeBuild = coreContextReads
        testedClient =
            (if (name == null) FlagsClient.Builder(sdkCore = core) else FlagsClient.Builder(name, core)).build()
        buildContextReads = coreContextReads - readsBeforeBuild
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
            minimumNotBefore = minimumNotBefore,
            flagSelectionIgnored = flagSelectionIgnored,
            listSelectionIgnored = listSelectionIgnored
        ).headers
        val response = Response.Builder().request(request).protocol(Protocol.HTTP_1_1).message("test")
            .code(if (responseKind == "403") 403 else 200)
            .headers(if (responseKind.startsWith("unsigned")) Headers.Builder().build() else headers)
            .body(
                when (responseKind) {
                    "tampered" -> (json + " ").toResponseBody()
                    "emitted-token-invalid" -> "{".toResponseBody()
                    "unsigned-false" -> json.replace("\"true\"", "\"false\"").toResponseBody()
                    else -> json.toResponseBody()
                }
            )
        if (responseKind.startsWith("emitted-token")) {
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
        var removals = 0
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

        // A removal deletes stale targeting data; it is not a read or write of flag data.
        override fun removeValue(key: String, callback: DataStoreWriteCallback?) {
            removals++
            values.remove(key)
            delegate.removeValue(key, callback)
        }
    }

    companion object {
        const val PREFIX = "x-datadog-feature-flags-"
        const val PROOF_WINDOW_SECONDS = 300L
        const val HOUR_SECONDS = 3600L

        // The SDK release floor (ResponseSigningVerifier.BUILD_FLOOR, internal); the shared fixture leaves expire before it.
        const val RELEASE_BUILD_FLOOR = 1795564800L
        const val INITIALIZATION_TIMEOUT_SECONDS = 5L
        const val CLOCK_STEP_ALLOWANCE_SECONDS = 30L

        @JvmStatic
        fun expiryCases(): List<Arguments> = listOf(true, false).flatMap { expire ->
            listOf(true, false).flatMap { fromStale ->
                listOf(true, false).map { byRead -> Arguments.of(expire, fromStale, byRead) }
            }
        }
    }
}
