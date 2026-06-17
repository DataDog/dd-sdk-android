/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.datadog.android.okhttp

import com.datadog.android.Datadog
import com.datadog.android.api.SdkCore
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.SdkFeatureMock
import com.datadog.android.core.stub.StubSDKCore
import com.datadog.android.internal.sampling.DeterministicSampling
import com.datadog.android.okhttp.tests.elmyr.OkHttpConfigurator
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.trace.ApmNetworkInstrumentationConfiguration
import com.datadog.android.trace.ExperimentalTraceApi
import com.datadog.android.trace.Trace
import com.datadog.android.trace.TraceConfiguration
import com.datadog.android.trace.TraceContextInjection
import com.datadog.android.trace.TracingHeaderType
import com.datadog.android.trace.api.DatadogTracingConstants.PrioritySampling
import com.datadog.tools.unit.completedFutureMock
import com.datadog.tools.unit.getFieldValue
import com.datadog.tools.unit.getStaticValue
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.quality.Strictness

/**
 * Integration test for cross-product trace sampling rebasing.
 *
 * Verifies that the SDK's network instrumentation applies the rebased effective rate
 * `traceSampleRate * sessionSampleRate / 100` when both RUM session sampling and APM trace
 * sampling are active. Sampling is deterministic on `sessionId` via `SpanSamplingIdProvider`,
 * so curated session UUIDs yield exact sampled/dropped outcomes.
 *
 * Both rebased entry points are covered: legacy `DatadogInterceptor` and new
 * `ApmNetworkInstrumentationConfiguration` with `setHeaderPropagationOnly()`. Because both
 * paths drop the client-side span when RUM is registered / `headerPropagationOnly = true`,
 * assertions target the `x-datadog-sampling-priority` request header.
 */
@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(OkHttpConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TraceCrossProductSamplingTest {

    private lateinit var stubSdkCore: StubSDKCore
    private lateinit var mockServer: MockWebServer

    @BeforeEach
    fun `set up`(forge: Forge) {
        stubSdkCore = StubSDKCore(forge)
        val registry: Any = Datadog::class.java.getStaticValue("registry")
        val instances: MutableMap<String, SdkCore> = registry.getFieldValue("instances")
        instances += stubSdkCore.name to stubSdkCore
        mockServer = MockWebServer()

        val fakeTraceConfiguration = TraceConfiguration.Builder().build()
        Trace.enable(fakeTraceConfiguration, stubSdkCore)
    }

    @AfterEach
    fun `tear down`() {
        Datadog.stopInstance(stubSdkCore.name)
        mockServer.shutdown()
    }

    // region legacy DatadogInterceptor

    @Test
    fun `M propagate sampled decision W DatadogInterceptor { rebased rate keeps low-hash session }`() {
        // rebased rate = 50% × 50% = 25%; LOW_HASH lands at ~0.36% → sampled.
        stubRumSessionContext(sessionIdToken = LOW_HASH_TOKEN, sessionSampleRate = 50f)
        val okHttpClient = buildClientWithDatadogInterceptor(traceSampleRate = 50f)

        val request = executeRequest(okHttpClient)

        assertSamplingPriority(request, expectedPriority = PrioritySampling.SAMPLER_KEEP)
    }

    @Test
    fun `M propagate dropped decision W DatadogInterceptor { rebased rate drops high-hash session }`() {
        // rebased rate = 50% × 50% = 25%; HIGH_HASH lands at ~99.94% → dropped.
        stubRumSessionContext(sessionIdToken = HIGH_HASH_TOKEN, sessionSampleRate = 50f)
        val okHttpClient = buildClientWithDatadogInterceptor(traceSampleRate = 50f)

        val request = executeRequest(okHttpClient)

        assertSamplingPriority(request, expectedPriority = PrioritySampling.SAMPLER_DROP)
    }

    @Test
    fun `M propagate sampled W DatadogInterceptor { rebasing changes outcome vs raw rate }`() {
        // MID_HASH sits at ~26.49%: raw 50% would keep it; rebased 25% drops it.
        // Proves rebasing is applied, not just the raw trace rate.
        stubRumSessionContext(sessionIdToken = MID_HASH_TOKEN, sessionSampleRate = 50f)
        val okHttpClient = buildClientWithDatadogInterceptor(traceSampleRate = 50f)

        val request = executeRequest(okHttpClient)

        assertSamplingPriority(request, expectedPriority = PrioritySampling.SAMPLER_DROP)
    }

    @Test
    fun `M propagate sampled W DatadogInterceptor { sessionRate=100 leaves trace rate untouched }`() {
        // sessionRate=100 → rebasing is a no-op; raw 50% > MID_HASH 26.49% → sampled.
        stubRumSessionContext(sessionIdToken = MID_HASH_TOKEN, sessionSampleRate = 100f)
        val okHttpClient = buildClientWithDatadogInterceptor(traceSampleRate = 50f)

        val request = executeRequest(okHttpClient)

        assertSamplingPriority(request, expectedPriority = PrioritySampling.SAMPLER_KEEP)
    }

    @Test
    fun `M propagate dropped W DatadogInterceptor { sessionRate=0 forces effective rate to 0 }`() {
        stubRumSessionContext(sessionIdToken = LOW_HASH_TOKEN, sessionSampleRate = 0f)
        val okHttpClient = buildClientWithDatadogInterceptor(traceSampleRate = 100f)

        val request = executeRequest(okHttpClient)

        assertSamplingPriority(request, expectedPriority = PrioritySampling.SAMPLER_DROP)
    }

    @Test
    fun `M propagate dropped W DatadogInterceptor { traceRate=0 forces effective rate to 0 }`() {
        stubRumSessionContext(sessionIdToken = LOW_HASH_TOKEN, sessionSampleRate = 100f)
        val okHttpClient = buildClientWithDatadogInterceptor(traceSampleRate = 0f)

        val request = executeRequest(okHttpClient)

        assertSamplingPriority(request, expectedPriority = PrioritySampling.SAMPLER_DROP)
    }

    // endregion

    // region new ApmNetworkInstrumentationConfiguration with headerPropagationOnly=true

    @Test
    fun `M propagate sampled W ApmNetworkInstrumentation headerPropagationOnly { low-hash session }`() {
        stubRumSessionContext(sessionIdToken = LOW_HASH_TOKEN, sessionSampleRate = 50f)
        val okHttpClient = buildClientWithHeaderPropagationOnly(traceSampleRate = 50f)

        val request = executeRequest(okHttpClient)

        assertSamplingPriority(request, expectedPriority = PrioritySampling.SAMPLER_KEEP)
    }

    @Test
    fun `M propagate dropped W ApmNetworkInstrumentation headerPropagationOnly { high-hash session }`() {
        stubRumSessionContext(sessionIdToken = HIGH_HASH_TOKEN, sessionSampleRate = 50f)
        val okHttpClient = buildClientWithHeaderPropagationOnly(traceSampleRate = 50f)

        val request = executeRequest(okHttpClient)

        assertSamplingPriority(request, expectedPriority = PrioritySampling.SAMPLER_DROP)
    }

    @Test
    fun `M propagate dropped W ApmNetworkInstrumentation headerPropagationOnly { rebasing changes outcome }`() {
        // MID_HASH (~26.49%) sampled at raw 50%, dropped at rebased 25%.
        stubRumSessionContext(sessionIdToken = MID_HASH_TOKEN, sessionSampleRate = 50f)
        val okHttpClient = buildClientWithHeaderPropagationOnly(traceSampleRate = 50f)

        val request = executeRequest(okHttpClient)

        assertSamplingPriority(request, expectedPriority = PrioritySampling.SAMPLER_DROP)
    }

    // endregion

    // region negative control: no RUM session

    @Test
    fun `M use raw trace rate W DatadogInterceptor { no RUM session present, falls back to traceId }`() {
        // No RUM session → SessionRebasedSampler falls through to raw trace rate.
        // traceRate=100% gives a deterministic 'sampled' regardless of traceId.
        val okHttpClient = buildClientWithDatadogInterceptor(traceSampleRate = 100f)

        val request = executeRequest(okHttpClient)

        assertSamplingPriority(request, expectedPriority = PrioritySampling.SAMPLER_KEEP)
    }

    @Test
    fun `M use raw trace rate W DatadogInterceptor { no RUM session present, traceRate=0 drops }`() {
        val okHttpClient = buildClientWithDatadogInterceptor(traceSampleRate = 0f)

        val request = executeRequest(okHttpClient)

        assertSamplingPriority(request, expectedPriority = PrioritySampling.SAMPLER_DROP)
    }

    // endregion

    // region effective sample rate (aggregate across many sessions)

    @Test
    fun `M apply rebased effective rate W DatadogInterceptor { aggregate across sessions }`(
        forge: Forge
    ) {
        // Sweep (traceRate, sessionRate) pairs: no-op rebase (100,50), symmetric (50,50),
        // asymmetric (40,80). Across SAMPLES_PER_CASE random session tokens the keep ratio
        // must converge to traceRate × sessionRate / 100.
        val cases = listOf(
            100f to 50f,
            50f to 50f,
            40f to 80f
        )

        cases.forEach { (traceRate, sessionRate) ->
            val expectedRatio = DeterministicSampling.combinedSampleRate(sessionRate, traceRate) / ONE_HUNDRED
            val actualRatio = runAggregateThroughDatadogInterceptor(
                forge = forge,
                traceRate = traceRate,
                sessionRate = sessionRate
            )
            assertThat(actualRatio)
                .withFailMessage(
                    "Effective rate mismatch for (traceRate=$traceRate, sessionRate=$sessionRate): " +
                        "expected ~$expectedRatio, got $actualRatio"
                )
                .isCloseTo(expectedRatio, Offset.offset(TOLERANCE))
        }
    }

    @Test
    fun `M apply rebased effective rate W ApmNetworkInstrumentation { aggregate, headerPropagationOnly }`(
        forge: Forge
    ) {
        val cases = listOf(
            100f to 50f,
            50f to 50f,
            40f to 80f
        )

        cases.forEach { (traceRate, sessionRate) ->
            val expectedRatio = DeterministicSampling.combinedSampleRate(sessionRate, traceRate) / ONE_HUNDRED
            val actualRatio = runAggregateThroughHeaderPropagationOnly(
                forge = forge,
                traceRate = traceRate,
                sessionRate = sessionRate
            )
            assertThat(actualRatio)
                .withFailMessage(
                    "Effective rate mismatch for (traceRate=$traceRate, sessionRate=$sessionRate): " +
                        "expected ~$expectedRatio, got $actualRatio"
                )
                .isCloseTo(expectedRatio, Offset.offset(TOLERANCE))
        }
    }

    // endregion

    // region helpers

    /**
     * Registers a stubbed RUM feature on [stubSdkCore] with the given session identifier and
     * sample rate. Uses [SdkFeatureMock] so that `RumContextPropagator` can pull a non-null
     * `Future<DatadogContext?>` from it.
     */
    private fun stubRumSessionContext(sessionIdToken: Long, sessionSampleRate: Float) {
        val sessionId = sessionIdToFakeUuid(sessionIdToken)
        val rumContext = mapOf<String, Any?>(
            "application_id" to FAKE_APPLICATION_ID,
            "session_id" to sessionId,
            "view_id" to FAKE_VIEW_ID,
            "action_id" to null,
            "session_sample_rate" to sessionSampleRate
        )
        val datadogContext: DatadogContext = stubSdkCore.getDatadogContext().let { context ->
            context.copy(
                featuresContext = context.featuresContext.toMutableMap().apply {
                    put(Feature.RUM_FEATURE_NAME, rumContext)
                }
            )
        }
        val rumFeature = mock<Feature> {
            on { name } doReturn Feature.RUM_FEATURE_NAME
        }
        stubSdkCore.stubFeatureScope(
            rumFeature,
            SdkFeatureMock.create(completedFutureMock(datadogContext))
        )
    }

    private fun buildClientWithDatadogInterceptor(traceSampleRate: Float): OkHttpClient {
        mockServer.enqueue(MockResponse())
        mockServer.start()
        return OkHttpClient.Builder()
            .addInterceptor(
                DatadogInterceptor.Builder(
                    tracedHostsWithHeaderType = mapOf(
                        mockServer.hostName to setOf(TracingHeaderType.DATADOG)
                    )
                )
                    .setTraceContextInjection(TraceContextInjection.ALL)
                    .setSdkInstanceName(stubSdkCore.name)
                    .setTraceSampleRate(traceSampleRate)
                    .build()
            )
            .build()
    }

    @OptIn(ExperimentalTraceApi::class, ExperimentalRumApi::class)
    private fun buildClientWithHeaderPropagationOnly(
        @Suppress("SameParameterValue") traceSampleRate: Float
    ): OkHttpClient {
        mockServer.enqueue(MockResponse())
        mockServer.start()
        return OkHttpClient.Builder()
            .configureDatadogInstrumentation(
                rumInstrumentationConfiguration = null,
                apmInstrumentationConfiguration = ApmNetworkInstrumentationConfiguration(
                    mapOf(mockServer.hostName to setOf(TracingHeaderType.DATADOG))
                )
                    .setTraceContextInjection(TraceContextInjection.ALL)
                    .setSdkInstanceName(stubSdkCore.name)
                    .setTraceSampleRate(traceSampleRate)
                    .setHeaderPropagationOnly()
            )
            .build()
    }

    private fun executeRequest(okHttpClient: OkHttpClient): RecordedRequest {
        okHttpClient.newCall(
            Request.Builder().url(mockServer.url("/")).build()
        ).execute().close()
        return mockServer.takeRequest()
    }

    private fun assertSamplingPriority(
        request: RecordedRequest,
        expectedPriority: Int
    ) {
        assertThat(request.getHeader(DATADOG_SAMPLING_PRIORITY_HEADER))
            .isEqualTo(expectedPriority.toString())
        assertThat(request.getHeader(DATADOG_TRACE_ID_HEADER)).isNotEmpty()
        assertThat(request.getHeader(DATADOG_SPAN_ID_HEADER)).isNotEmpty()
    }

    /**
     * Builds a synthetic UUID whose last 12-hex-character segment encodes [token].
     * `SpanSamplingIdProvider.provideId` parses that segment as the sampling key.
     */
    private fun sessionIdToFakeUuid(token: Long): String =
        "00000000-0000-0000-0000-${"%012x".format(token)}"

    /**
     * Drives [SAMPLES_PER_CASE] requests through a [DatadogInterceptor] at [traceRate], each
     * with a fresh random-session-token RUM context at [sessionRate]. Returns the keep ratio.
     */
    private fun runAggregateThroughDatadogInterceptor(
        forge: Forge,
        traceRate: Float,
        sessionRate: Float
    ): Float {
        prepareMockServer()
        val client = OkHttpClient.Builder()
            .addInterceptor(
                DatadogInterceptor.Builder(
                    tracedHostsWithHeaderType = mapOf(
                        mockServer.hostName to setOf(TracingHeaderType.DATADOG)
                    )
                )
                    .setTraceContextInjection(TraceContextInjection.ALL)
                    .setSdkInstanceName(stubSdkCore.name)
                    .setTraceSampleRate(traceRate)
                    .build()
            )
            .build()
        return runAggregate(forge, client, sessionRate)
    }

    @OptIn(ExperimentalTraceApi::class, ExperimentalRumApi::class)
    private fun runAggregateThroughHeaderPropagationOnly(
        forge: Forge,
        traceRate: Float,
        sessionRate: Float
    ): Float {
        prepareMockServer()
        val client = OkHttpClient.Builder()
            .configureDatadogInstrumentation(
                rumInstrumentationConfiguration = null,
                apmInstrumentationConfiguration = ApmNetworkInstrumentationConfiguration(
                    mapOf(mockServer.hostName to setOf(TracingHeaderType.DATADOG))
                )
                    .setTraceContextInjection(TraceContextInjection.ALL)
                    .setSdkInstanceName(stubSdkCore.name)
                    .setTraceSampleRate(traceRate)
                    .setHeaderPropagationOnly()
            )
            .build()
        return runAggregate(forge, client, sessionRate)
    }

    private fun runAggregate(
        forge: Forge,
        client: OkHttpClient,
        sessionRate: Float
    ): Float {
        var kept = 0
        repeat(SAMPLES_PER_CASE) {
            // 48-bit token = last 12 hex chars of the synthetic UUID.
            val token = forge.aLong(min = 1L, max = MAX_SESSION_TOKEN)
            stubRumSessionContext(sessionIdToken = token, sessionSampleRate = sessionRate)
            client.newCall(Request.Builder().url(mockServer.url("/")).build()).execute().close()
            val recorded = mockServer.takeRequest()
            if (recorded.getHeader(DATADOG_SAMPLING_PRIORITY_HEADER) ==
                PrioritySampling.SAMPLER_KEEP.toString()
            ) {
                kept++
            }
        }
        return kept.toFloat() / SAMPLES_PER_CASE.toFloat()
    }

    /** Restarts [mockServer] pre-loaded with [SAMPLES_PER_CASE] empty responses. */
    private fun prepareMockServer() {
        mockServer.shutdown()
        mockServer = MockWebServer()
        repeat(SAMPLES_PER_CASE) { mockServer.enqueue(MockResponse()) }
        mockServer.start()
    }

    // endregion

    companion object {

        private const val DATADOG_SAMPLING_PRIORITY_HEADER = "x-datadog-sampling-priority"
        private const val DATADOG_TRACE_ID_HEADER = "x-datadog-trace-id"
        private const val DATADOG_SPAN_ID_HEADER = "x-datadog-parent-id"

        private const val FAKE_APPLICATION_ID = "00000000-0000-0000-0000-000000000001"
        private const val FAKE_VIEW_ID = "00000000-0000-0000-0000-000000000002"

        /** Token whose Knuth hash sits at ~0.36% of the 64-bit space (sampled at any rate > ~0.5%). */
        private const val LOW_HASH_TOKEN: Long = 1129L

        /** Token whose Knuth hash sits at ~26.49% (sampled at rate ≥ 27%, dropped at rate ≤ 26%). */
        private const val MID_HASH_TOKEN: Long = 21L

        /** Token whose Knuth hash sits at ~99.94% (dropped at any rate ≤ 99.9%). */
        private const val HIGH_HASH_TOKEN: Long = 83L

        /** Requests per (traceRate, sessionRate) pair in the aggregate tests. */
        private const val SAMPLES_PER_CASE: Int = 1000

        /** ~3σ headroom around the sample mean for [SAMPLES_PER_CASE] at the rates tested. */
        private const val TOLERANCE: Float = 0.05f

        /** Exclusive upper bound for session tokens: 2^48 fits the 12-hex UUID last segment. */
        private const val MAX_SESSION_TOKEN: Long = 1L shl 48

        /** Conversion factor from the SDK's [0..100] sample-rate scale to a [0..1] ratio. */
        private const val ONE_HUNDRED: Float = 100f
    }
}
