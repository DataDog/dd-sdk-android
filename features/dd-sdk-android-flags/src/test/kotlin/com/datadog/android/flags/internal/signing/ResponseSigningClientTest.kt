/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.signing

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.storage.datastore.DataStoreHandler
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.flags.FlagsConfiguration
import com.datadog.android.flags.FlagsStateListener
import com.datadog.android.flags.ResponseSigningMode
import com.datadog.android.flags.internal.DatadogFlagsClient
import com.datadog.android.flags.internal.FlagsStateManager
import com.datadog.android.flags.internal.evaluation.EvaluationsManager
import com.datadog.android.flags.internal.model.FlagsStateEntry
import com.datadog.android.flags.internal.net.PrecomputedAssignmentsDownloader
import com.datadog.android.flags.internal.net.PrecomputedAssignmentsRequestFactory
import com.datadog.android.flags.internal.repository.DefaultFlagsRepository
import com.datadog.android.flags.internal.repository.FlagsRepository
import com.datadog.android.flags.internal.repository.SigningFlagsRepository
import com.datadog.android.flags.internal.repository.net.PrecomputeMapper
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.FlagsClientState
import com.datadog.android.flags.utils.forge.ForgeConfigurator
import com.datadog.android.internal.FeatureContextKeys
import com.datadog.android.internal.rum.RumContext
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.internal.utils.DDCoreStateHolder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.Call
import okhttp3.Headers
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.toByteString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.concurrent.ExecutorService

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(ForgeConfigurator::class)
internal class ResponseSigningClientTest {
    @Forgery lateinit var fakeDatadogContext: DatadogContext
    private val mockLogger: InternalLogger = mock()
    private val mockCore: InternalSdkCore = mock()
    private val mockStore: DataStoreHandler = mock()
    private val mockExecutor: ExecutorService = mock()
    private val queue = ArrayDeque<Runnable>()
    private val corpus = resource()
    private val vector = corpus.getAsJsonArray("cases")[0].asJsonObject
    private val originalNow = vector["now"].asLong
    private var now = originalNow
    private val coreSigningContext = ResponseSigningContext()
    private var cancelledChecks = 0
    private val requests = mutableListOf<Request>()
    private var responseKind = "valid"
    private var endpoint = "https://flags.example.test/assignments?dd_env=test"
    private var beforeResponse: () -> Unit = {}
    private lateinit var testedClient: DatadogFlagsClient
    private lateinit var repository: FlagsRepository

    @BeforeEach
    fun `set up`() {
        fakeDatadogContext = fakeDatadogContext.copy(source = "android")
        whenever(mockCore.internalLogger) doReturn mockLogger
        val mockTimeProvider = mock<TimeProvider> {
            on { getDeviceTimestampMillis() } doAnswer { now * 1000 }
        }
        whenever(mockCore.timeProvider) doReturn mockTimeProvider
        whenever(mockCore.getFeature(Feature.FLAGS_FEATURE_NAME)) doReturn mock<FeatureScope>()
        whenever(mockCore.getDatadogContext(setOf(Feature.RUM_FEATURE_NAME))) doAnswer { fakeDatadogContext }
        whenever(mockCore.source) doReturn "android"
        whenever(mockExecutor.execute(any())) doAnswer { queue.add(it.getArgument(0)); Unit }
    }

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
        assertThat(requests.single().header(ResponseSigningVerifier.PREFIX + "signature-version"))
            .isEqualTo(if (mode == ResponseSigningMode.OFF) null else "3")
        if (mode == ResponseSigningMode.REQUIRED) verifyNoInteractions(mockStore)
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
        assertThat(repository.getEvaluationContext()?.attributes).containsEntry("plan", "before")
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
        assertThat(repository.getEvaluationContext()).isNull()
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isFalse()
        queue.removeFirst().run()
        assertThat(repository.getEvaluationContext()?.targetingKey).isEqualTo("new-subject")
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
        assertThat(repository.hasFlags()).isFalse()
        assertThat(repository.getFlagsSnapshot()).isEmpty()
        assertThat(repository.getPrecomputedFlag("critical-demo")).isNull()
        assertThat(repository.getEvaluationContext()).isNull()
        assertThat(repository.hasLoadedFlagsForContext(EvaluationContext("subject"))).isFalse()
        verifyNoInteractions(mockStore)
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
        verifyNoInteractions(mockStore)
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
        verifyNoInteractions(mockStore)
    }

    @Test
    fun `M defer persistence W resolveBooleanValue() {observe scope not established}`() {
        // Given
        create(ResponseSigningMode.OBSERVE)
        // When
        val actual = testedClient.resolveBooleanValue("critical-demo", false)
        // Then
        assertThat(actual).isFalse()
        verifyNoInteractions(mockStore)
    }

    @Test
    fun `M scope persistence without nonce churn W setEvaluationContext() {observe environment changes}`() {
        // Given
        create(ResponseSigningMode.OBSERVE)
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        fakeDatadogContext = fakeDatadogContext.copy(env = "other-environment")
        // When
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        // Then
        val keys = argumentCaptor<String>()
        verify(mockStore, times(2)).value<FlagsStateEntry>(keys.capture(), anyOrNull(), any(), any())
        assertThat(keys.allValues).hasSize(2).doesNotHaveDuplicates()
        assertThat(keys.allValues).allMatch { it.startsWith("flags-state-signing-test-") && it.length > 64 }
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
        beforeResponse = { coreSigningContext.stop() }
        // When
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        // Then
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isFalse()
        assertThat(repository.getFlagsSnapshot()).isEmpty()
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
        val roots = mutableListOf(corpus["anchor_der_base64"].asString)
        create(ResponseSigningMode.REQUIRED, roots)
        roots.clear()
        // When
        testedClient.setEvaluationContext(EvaluationContext("subject"), null)
        queue.removeFirst().run()
        // Then
        assertThat(testedClient.resolveBooleanValue("critical-demo", false)).isTrue()
    }

    private fun updateApp(applicationId: String, sessionId: String = "session") {
        val context = mapOf(FeatureContextKeys.RUM_CONTEXT to RumContext(applicationId, sessionId))
        fakeDatadogContext = fakeDatadogContext.copy(featuresContext = mapOf(Feature.RUM_FEATURE_NAME to context))
        coreSigningContext.onContextUpdate(Feature.RUM_FEATURE_NAME, context)
    }

    private fun create(mode: ResponseSigningMode, roots: List<String> = listOf(corpus["anchor_der_base64"].asString)) {
        val configuration = FlagsConfiguration.Builder().trackExposures(false).trackEvaluations(false)
            .rumIntegrationEnabled(false).responseSigning(mode, roots).build()
        val signing = if (mode ==
            ResponseSigningMode.OFF
        ) {
            null
        } else {
            ResponseSigningState(configuration.responseSigning, mockLogger, coreSigningContext) { now }
        }
        fun ordinary(scope: String): FlagsRepository = DefaultFlagsRepository(
            mockCore,
            "signing-test-$scope",
            mockStore,
            persistenceLoadTimeoutMs = 0
        )
        repository = signing?.let { SigningFlagsRepository(::ordinary, it, Runnable::run) } ?: ordinary("off")

        val transport = Call.Factory { request ->
            requests.add(request)
            mock<Call> {
                on { execute() } doAnswer { beforeResponse(); response(request) }
                on { isCanceled() } doAnswer { responseKind == "cancelled" && ++cancelledChecks > 1 }
            }
        }
        val reader = PrecomputedAssignmentsDownloader(
            transport,
            mockLogger,
            PrecomputedAssignmentsRequestFactory(mockLogger, endpoint),
            signing
        )
        val stateManager =
            FlagsStateManager(DDCoreStateHolder.create(FlagsClientState.NotReady, FlagsStateListener::onStateChanged))
        val manager = EvaluationsManager(
            mockCore, mockExecutor, mockLogger, repository, reader, PrecomputeMapper(mockLogger), stateManager,
            initializationTimeoutMs = null, signing = signing, initializationTimeoutScheduler = { _, _ -> {} }
        )
        testedClient =
            DatadogFlagsClient(mockCore, manager, repository, configuration, mock(), mock(), null, stateManager, false)
    }

    private fun response(request: Request): Response {
        val json = """
            {"data":{"attributes":{"flags":{"critical-demo":{
              "variationType":"boolean","variationValue":"true","doLog":false,
              "allocationKey":"test","variationKey":"on","reason":"TARGETING_MATCH","extraLogging":{}
            }}}}}
        """.trimIndent()
        val headers = signedHeaders(request, json.toByteArray())
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

    private fun signedHeaders(request: Request, body: ByteArray): Headers {
        if (request.header(ResponseSigningVerifier.PREFIX + "request-nonce") == null) return Headers.Builder().build()
        val credential = vector.getAsJsonObject("credential")
        val leaf = bytes(credential["certificate_der_base64"].asString)
        val intermediate = bytes(credential["intermediate_der_base64"].asString)
        val output = ByteArrayOutputStream()
        val transcript = DataOutputStream(output)
        transcript.write("datadog.feature-flags.precomputed-assignments.v3\u0000".toByteArray())
        fun lp(bytes: ByteArray) { transcript.writeInt(bytes.size); transcript.write(bytes) }
        val url = request.url
        listOf(request.method, url.scheme, url.host, url.encodedPath, url.encodedQuery?.let { "?$it" } ?: "")
            .forEach { lp(it.toByteArray()) }
        lp(checkNotNull(request.header(ResponseSigningVerifier.PREFIX + "request-nonce")).decodeHex().toByteArray())
        transcript.write(sha(Buffer().also { request.body?.writeTo(it) }.readByteArray()))
        transcript.write(sha(checkNotNull(request.header("dd-client-token")).toByteArray()))
        listOf(
            "content-type",
            "dd-application-id",
            "x-rkyv",
            "x-use-cache",
            "x-dd-feature-flags-capabilities"
        ).forEach {
            val header = request.header(it)
            transcript.writeByte(if (header == null) 0 else 1)
            if (header != null) lp(header.toByteArray())
        }
        transcript.writeByte(0)
        transcript.writeShort(200)
        transcript.writeLong(originalNow)
        transcript.writeLong(originalNow + 300)
        transcript.writeLong(body.size.toLong())
        transcript.write(sha(body)); transcript.write(sha(leaf)); transcript.write(sha(intermediate))
        val key = KeyFactory.getInstance(
            "EC"
        ).generatePrivate(PKCS8EncodedKeySpec(bytes(credential["private_key_pkcs8_der_base64"].asString)))
        val signature = Signature.getInstance("SHA256withECDSA").apply {
            initSign(key); update(output.toByteArray())
        }.sign()
        return Headers.Builder()
            .add(ResponseSigningVerifier.PREFIX + "signature-version", "3")
            .add(ResponseSigningVerifier.PREFIX + "signing-certificate", credential["certificate_der_base64"].asString)
            .add(
                ResponseSigningVerifier.PREFIX + "signing-intermediate",
                credential["intermediate_der_base64"].asString
            )
            .add(ResponseSigningVerifier.PREFIX + "certificate-id", sha(leaf).toByteString().hex())
            .add(ResponseSigningVerifier.PREFIX + "issued-at", originalNow.toString())
            .add(ResponseSigningVerifier.PREFIX + "expires-at", (originalNow + 300).toString())
            .add(ResponseSigningVerifier.PREFIX + "signature", signature.toByteString().base64()).build()
    }

    private fun sha(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
    private fun bytes(value: String): ByteArray = checkNotNull(value.decodeBase64()).toByteArray()
    private fun resource(): JsonObject = checkNotNull(javaClass.classLoader)
        .getResourceAsStream("response-signing-v3/corpus.json").let { checkNotNull(it) }.bufferedReader()
        .use { JsonParser.parseReader(it).asJsonObject }
}
