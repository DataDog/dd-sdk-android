/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.okhttp

import android.content.Context
import com.datadog.android.Datadog
import com.datadog.android.api.SdkCore
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.SdkFeatureMock
import com.datadog.android.core.sampling.DeterministicSampler.Companion.MAX_ID
import com.datadog.android.core.sampling.DeterministicSampler.Companion.SAMPLER_HASHER
import com.datadog.android.core.stub.StubSDKCore
import com.datadog.android.okhttp.RumContextPropagationTest.Companion.SAMPLING_THRESHOLD
import com.datadog.android.okhttp.tests.elmyr.OkHttpConfigurator
import com.datadog.android.okhttp.trace.TracingInterceptor
import com.datadog.android.trace.DatadogTracing
import com.datadog.android.trace.GlobalDatadogTracer
import com.datadog.android.trace.Trace
import com.datadog.android.trace.TraceConfiguration
import com.datadog.android.trace.TracingHeaderType
import com.datadog.android.trace.api.TestIdGenerationStrategy
import com.datadog.android.trace.api.setTestIdGenerationStrategy
import com.datadog.android.trace.api.tracer.DatadogTracerBuilder
import com.datadog.tools.unit.completedFutureMock
import com.datadog.tools.unit.extensions.TestConfigurationExtension
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@ForgeConfiguration(OkHttpConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RumContextPropagationTest {
    private lateinit var mockServer: MockWebServer
    private lateinit var testedClient: OkHttpClient
    private lateinit var stubSdkCore: SdkCore

    @BeforeEach
    fun `set up`() {
        mockServer = MockWebServer().apply {
            enqueue(MockResponse())
            start()
        }
    }

    @AfterEach
    fun `tear down`() {
        Datadog.stopInstance(stubSdkCore.name)
        mockServer.shutdown()
    }

    @Test
    fun `M send rum sessionId in baggage header W call is made`(forge: Forge) {
        // Given
        val fakeRumContext = forge.createRumContext(SAMPLED_IDS.random())
        stubSdkCore = forge.prepareStubSdkCore(fakeRumContext)
        Trace.enable(TraceConfiguration.Builder().build(), stubSdkCore)
        testedClient = prepareClient(stubSdkCore)
        registerTracer(createTracer(stubSdkCore))

        // When
        testedClient.makeNetworkCall()

        // Then
        assertSentRequest {
            assertThat(getHeader(HEADER_BAGGAGE)).isEqualTo(
                "session.id=${fakeRumContext[RUM_CONTEXT_SESSION_ID]}"
            )
        }
    }

    // region sampleId/tracingId sampling

    @Test
    fun `M set header x-datadog-sampling-priority=1 W call is made { sessionId sampled }`(
        forge: Forge
    ) {
        // Given
        val rumContext = forge.createRumContext(sessionId = SAMPLED_IDS.random())
        stubSdkCore = forge.prepareStubSdkCore(rumContext)
        Trace.enable(TraceConfiguration.Builder().build(), stubSdkCore)
        testedClient = prepareClient(stubSdkCore)
        registerTracer(createTracer(stubSdkCore))

        // When
        testedClient.makeNetworkCall()

        // Then
        assertSentRequest {
            assertThat(getHeader(HEADER_SAMPLE_PRIORITY)).isEqualTo("1")
        }
    }

    @Test
    fun `M set header x-datadog-sampling-priority=0 W call is made { sessionId not sampled }`(
        forge: Forge
    ) {
        // Given
        val rumContext = forge.createRumContext(sessionId = DROPPED_IDS.random())
        stubSdkCore = forge.prepareStubSdkCore(rumContext)
        Trace.enable(TraceConfiguration.Builder().build(), stubSdkCore)
        testedClient = prepareClient(stubSdkCore)
        registerTracer(createTracer(stubSdkCore))

        // When
        testedClient.makeNetworkCall()

        // Then
        assertSentRequest {
            assertThat(getHeader(HEADER_SAMPLE_PRIORITY)).isEqualTo("0")
        }
    }

    @Test
    @Suppress("MISSING_DEPENDENCY_SUPERCLASS_WARNING") // it's okay for tests
    fun `M set header x-datadog-sampling-priority=0 W call is made { sessionId = null, traceId not sampled }`(
        forge: Forge
    ) {
        // Given
        val rumContext = forge.createRumContext(sessionId = null)
        stubSdkCore = forge.prepareStubSdkCore(rumContext)
        Trace.enable(TraceConfiguration.Builder().build(), stubSdkCore)
        testedClient = prepareClient(stubSdkCore)
        registerTracer(createTracer(stubSdkCore).withTraceIdsFrom(DROPPED_IDS))

        // When
        testedClient.makeNetworkCall()

        // Then
        assertSentRequest {
            assertThat(getHeader(HEADER_SAMPLE_PRIORITY)).isEqualTo("0")
        }
    }

    @Test
    @Suppress("MISSING_DEPENDENCY_SUPERCLASS_WARNING") // it's okay for tests
    fun `M set header x-datadog-sampling-priority=1 W call is made { sessionId = null, traceId sampled }`(
        forge: Forge
    ) {
        // Given
        val rumContext = forge.createRumContext(sessionId = null)
        stubSdkCore = forge.prepareStubSdkCore(rumContext)
        Trace.enable(TraceConfiguration.Builder().build(), stubSdkCore)
        testedClient = prepareClient(stubSdkCore)
        registerTracer(createTracer(stubSdkCore).withTraceIdsFrom(SAMPLED_IDS))

        // When
        testedClient.makeNetworkCall()

        // Then
        assertSentRequest {
            assertThat(getHeader(HEADER_SAMPLE_PRIORITY)).isEqualTo("1")
        }
    }

    // endregion

    // region utilities
    private fun OkHttpClient.makeNetworkCall() {
        newCall(
            Request.Builder()
                .url(mockServer.url("/"))
                .build()
        ).execute()
    }

    private fun prepareClient(sdkCore: SdkCore) = OkHttpClient.Builder()
        .addInterceptor(
            TracingInterceptor.Builder(mapOf(mockServer.hostName to setOf(TracingHeaderType.DATADOG)))
                .setTraceContextInjection(TraceContextInjection.ALL)
                .setSdkInstanceName(sdkCore.name)
                .setTraceSampleRate(SAMPLE_RATE)
                .build()
        )
        .build()

    // endregion

    companion object {
        /**
         * The sample decision is made in the DeterministicSampler by the following logic:
         * val hash = idConverter(item).SAMPLER_HASHER
         * val threshold = (MAX_ID.toDouble() * sampleRate / SAMPLE_ALL_RATE).toULong()
         * isSampled = hash < threshold
         *
         * Setting the sample rate to a constant value (50% in this test case) allows us to derive the IDs.
         * that will either be sampled or dropped. The [SAMPLING_THRESHOLD] is the derived value.
         * All values below the threshold will be sampled, and all values above the threshold will
         * be dropped.
         *
         * isSampled = SAMPLING_THRESHOLD < MAX_ID / (2 * SAMPLE_HASHER).
         */
        private val SAMPLING_THRESHOLD: Long = (MAX_ID.toDouble() / (2.0 * SAMPLER_HASHER.toDouble())).toLong()
        private const val SAMPLE_RATE = 50f
        private val SAMPLED_IDS = listOf(SAMPLING_THRESHOLD - 1, SAMPLING_THRESHOLD - 2)
        private val DROPPED_IDS = listOf(SAMPLING_THRESHOLD + 1, SAMPLING_THRESHOLD + 2)

        private const val HEADER_BAGGAGE = "baggage"
        private const val HEADER_SAMPLE_PRIORITY = "x-datadog-sampling-priority"

        private const val RUM_CONTEXT_SESSION_ID = "session_id"

        private fun RumContextPropagationTest.assertSentRequest(block: RecordedRequest.() -> Unit) {
            block(mockServer.takeRequest())
        }

        private fun Forge.createRumContext(sessionId: Long?): Map<String, String> =
            buildMap {
                put("view_id", anAlphabeticalString())
                put("action_id", anAlphabeticalString())
                put("application_id", anAlphabeticalString())
                sessionId?.let {
                    put(
                        RUM_CONTEXT_SESSION_ID,
                        "aaaaaaaa-bbbb-Mccc-Nddd-${(sessionId).toULong().toString(16)}"
                    )
                }
            }

        private fun Forge.prepareStubSdkCore(rumContext: Map<String, Any?>): StubSDKCore {
            val datadogContext = getForgery<DatadogContext>().copy(
                source = "android",
                featuresContext = mapOf(Feature.RUM_FEATURE_NAME to rumContext)
            )

            val sdkCoreStub = StubSDKCore(this, datadogContext = datadogContext)

            Datadog::class.java
                .getStaticValue<Datadog, Any>("registry")
                .getFieldValue<MutableMap<String, SdkCore>, Any>("instances")
                .also { instances -> instances += sdkCoreStub.name to sdkCoreStub }

            sdkCoreStub.stubFeatureScope(
                StubRumFeature,
                SdkFeatureMock.create(completedFutureMock(datadogContext))
            )

            return sdkCoreStub
        }

        private fun registerTracer(
            builder: DatadogTracerBuilder
        ) {
            GlobalDatadogTracer.clear()
            GlobalDatadogTracer.registerIfAbsent(builder.build())
        }

        private fun createTracer(sdkCore: SdkCore) = DatadogTracing.newTracerBuilder(sdkCore)
            .withTracingHeadersTypes(setOf(TracingHeaderType.DATADOG))
            // this is on purpose, we want to make sure that it is not taken into account
            .withSampleRate(100.0)

        @Suppress("MISSING_DEPENDENCY_SUPERCLASS_WARNING") // it's okay for testing
        private fun DatadogTracerBuilder.withTraceIdsFrom(traceIds: List<Long>): DatadogTracerBuilder =
            setTestIdGenerationStrategy(TestIdGenerationStrategy(traceIds = traceIds))
    }
}

private object StubRumFeature : Feature {
    override val name: String = Feature.RUM_FEATURE_NAME
    override fun onStop() = Unit
    override fun onInitialize(appContext: Context) = Unit
}
