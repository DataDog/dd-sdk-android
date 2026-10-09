/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.diagnostics

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.datastore.DataStoreHandler
import com.datadog.android.api.storage.datastore.DataStoreReadCallback
import com.datadog.android.core.persistence.datastore.DataStoreContent
import com.datadog.android.flags.internal.model.FlagsStateEntry
import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.datadog.android.flags.internal.net.PrecomputedAssignmentsDownloader
import com.datadog.android.flags.internal.net.PrecomputedAssignmentsRequestFactory
import com.datadog.android.flags.internal.persistence.FlagsStateDeserializer
import com.datadog.android.flags.internal.repository.DefaultFlagsRepository
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.internal.profiler.BenchmarkProfiler
import com.datadog.android.internal.profiler.BenchmarkSpan
import com.datadog.android.internal.profiler.BenchmarkSpanBuilder
import com.datadog.android.internal.profiler.BenchmarkTracer
import com.datadog.android.internal.profiler.GlobalBenchmark
import okhttp3.Call
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.IOException

internal class StartupTraceTest {
    private val saved = GlobalBenchmark.getProfiler()
    private val capture = FlagsStartupCapture()
    private val logger: InternalLogger = mock()
    private val core: FeatureSdkCore = mock()
    private val store: DataStoreHandler = mock()
    private lateinit var disk: DataStoreReadCallback<FlagsStateEntry>
    private val context = EvaluationContext("fixture", emptyMap())
    private val flags = mapOf(
        "fixture" to PrecomputedFlag(
            "string",
            "fixture-value",
            false,
            "allocation",
            "variant",
            JSONObject(),
            "TARGETING_MATCH"
        )
    )

    @BeforeEach
    fun setUp() {
        GlobalBenchmark.register(capture)
        whenever(core.internalLogger).thenReturn(logger)
        whenever(core.timeProvider).thenReturn(mock())
        whenever(store.value<FlagsStateEntry>(any(), anyOrNull(), any(), any())).doAnswer {
            disk = it.getArgument(2)
            null
        }
    }

    @AfterEach
    fun tearDown() {
        Thread.interrupted()
        GlobalBenchmark.register(saved)
    }

    private fun repository() = DefaultFlagsRepository(core, "fixture", store, persistenceLoadTimeoutMs = 0)

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `M retain installation semantics and provenance W competing installs`(diskFirst: Boolean) {
        val repository = repository()
        val entry = DataStoreContent(0, FlagsStateEntry(context, flags, 0))
        var notified = 0
        repository.firstFlags.whenComplete { notified++ }
        if (diskFirst) disk.onSuccess(entry)
        repository.setFlagsAndContext(context, flags) { }
        if (!diskFirst) disk.onSuccess(entry)
        assertThat(repository.getPrecomputedFlagWithContext("fixture")?.first).isSameAs(flags["fixture"])
        assertThat(notified).isEqualTo(1)
        val installs = capture.events.filter { it.first == "state.install" }.map { it.second }
        assertThat(installs.last { it["source"] == "disk" }["accepted"]).isEqualTo(diskFirst.toString())
        val installedNetwork = installs.first { it["source"] == "network" }
        val read = capture.events.last { it.first == "state.read" }.second
        assertThat(read["state"]).isEqualTo(installedNetwork["state"])
        assertThat(read["source"]).isEqualTo("network")
        assertThat(capture.events.map { it.second["source"] }).contains("disk_callback", "network_installation")
    }

    @Test
    fun `M correlate exact old state W replacement during diagnostic observation`() {
        val repository = repository()
        disk.onSuccess(DataStoreContent(0, FlagsStateEntry(context, flags, 0)))
        capture.onEvent = { name ->
            if (name == "state.read") {
                capture.onEvent = {}
                repository.setFlagsAndContext(context, emptyMap()) { }
            }
        }
        assertThat(repository.getPrecomputedFlagWithContext("fixture")).isNotNull()
        assertThat(capture.events.last { it.first == "state.read" }.second["source"]).isEqualTo("disk")
        assertThat(repository.getFlagsSnapshot()).isEmpty()
    }

    @Test
    fun `M distinguish timeout interruption and missing cache W wait`() {
        val repository = repository()
        assertThat(repository.getEvaluationContext()).isNull()
        Thread.currentThread().interrupt()
        assertThat(repository.getEvaluationContext()).isNull()
        assertThat(Thread.interrupted()).isTrue()
        disk.onSuccess(null)
        assertThat(repository.getEvaluationContext()).isNull()
        assertThat(capture.events.filter { it.first == "latch.result" }.map { it.second["outcome"] })
            .containsExactly("timeout", "interrupted", "completed")
        assertThat(capture.events.last { it.first == "state.read" }.second["source"]).isEqualTo("none")
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `M close HTTP scopes and preserve downloader outcome W enabled capture`(fails: Boolean) {
        val requests: PrecomputedAssignmentsRequestFactory = mock()
        val calls: Call.Factory = mock()
        val call: Call = mock()
        val request = Request.Builder().url("https://fixture.invalid").build()
        whenever(requests.create(any(), any())).thenReturn(request)
        whenever(calls.newCall(any())).thenReturn(call)
        if (fails) {
            whenever(call.execute()).thenThrow(IOException("fixture"))
        } else {
            whenever(call.execute()).thenReturn(
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").body("fixture-response".toResponseBody()).build()
            )
        }
        val result = PrecomputedAssignmentsDownloader(calls, logger, requests).readPrecomputedFlags(context, mock())
        assertThat(result).isEqualTo(if (fails) null else "fixture-response")
        assertThat(capture.events.map { it.first }).contains("network.http")
        assertThat(capture.events.map { it.first }).contains(if (fails) "operation.threw" else "network.body")
        assertThat(capture.openSpans).isZero()
    }

    @Test
    fun `M close parsing spans W invalid cache`() {
        assertThat(FlagsStateDeserializer(logger).deserialize("invalid-json")).isNull()
        assertThat(capture.events.last { it.first == "cache.parse_result" }.second["outcome"]).isEqualTo("invalid")
        assertThat(capture.openSpans).isZero()
    }

    @Test
    fun `M skip diagnostic property work W disabled`() {
        GlobalBenchmark.register(saved)
        val value = StartupTrace.span("disabled", { error("must not run") }) { 42 }
        assertThat(value).isEqualTo(42)
        assertThat(StartupTrace.nextId()).isZero()
        assertThat(capture.events).isEmpty()
    }

    @Test
    fun `M preserve result W diagnostic sink fails`() {
        capture.onEvent = { error("sink failed") }
        assertThat(StartupTrace.span("failure") { 42 }).isEqualTo(42)
    }

    @Test
    fun `M close span and propagate original exception W operation throws`() {
        assertThatThrownBy { StartupTrace.span("failure") { throw IllegalStateException("original") } }
            .isInstanceOf(IllegalStateException::class.java).hasMessage("original")
        assertThat(capture.openSpans).isZero()
    }
}

// Matches the explicit opt-in adapter name; it captures only fixture metadata, without Android logging.
internal class FlagsStartupCapture : BenchmarkProfiler {
    val events = mutableListOf<Pair<String, Map<String, String>>>()
    var openSpans = 0
    var onEvent: (String) -> Unit = {}

    override fun getTracer(operation: String): BenchmarkTracer = object : BenchmarkTracer {
        override fun spanBuilder(spanName: String, additionalProperties: Map<String, String>): BenchmarkSpanBuilder =
            object : BenchmarkSpanBuilder {
                override fun startSpan(): BenchmarkSpan {
                    onEvent(spanName)
                    events.add(spanName to additionalProperties)
                    openSpans++
                    return object : BenchmarkSpan {
                        override fun stop() { openSpans-- }
                    }
                }
            }
    }
}
