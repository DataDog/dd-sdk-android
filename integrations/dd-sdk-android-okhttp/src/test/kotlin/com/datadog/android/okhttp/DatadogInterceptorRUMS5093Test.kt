/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp

import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.Feature
import com.datadog.android.okhttp.trace.TracingInterceptor
import com.datadog.android.okhttp.trace.TracingInterceptorNotSendingSpanTest
import com.datadog.android.rum.RumResourceAttributesProvider
import com.datadog.android.rum.resource.ResourceId
import com.datadog.android.trace.TracingHeaderType
import com.datadog.android.trace.api.tracer.DatadogTracer
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

/**
 * Regression tests for RUMS-5093 / RUM-13514.
 *
 * When two concurrent OkHttp requests are made to the same URL, DatadogInterceptor
 * creates a start-event ResourceId with a fresh UUID (generateUuid=true) for each request, but
 * creates a stop-event ResourceId with uuid=null (generateUuid=false) in handleResponse().
 *
 * Because ResourceId.equals() falls back to key-only comparison when either uuid is null,
 * the null-uuid stop-event for request R1 is equal to the UUID-carrying start-event for request R2
 * (and vice-versa), causing the wrong scope to be terminated.
 *
 * The tests in this class FAIL on unfixed code and PASS once the fix is applied.
 */
@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(BaseConfigurator::class)
internal class DatadogInterceptorRUMS5093Test : TracingInterceptorNotSendingSpanTest() {

    @Mock
    lateinit var mockRumAttributesProvider: RumResourceAttributesProvider

    @Mock
    lateinit var mockChainR2: Interceptor.Chain

    override fun instantiateTestedInterceptor(
        tracedHosts: Map<String, Set<TracingHeaderType>>,
        globalTracerProvider: () -> DatadogTracer?,
        localTracerFactory: (SdkCore, Set<TracingHeaderType>) -> DatadogTracer
    ): TracingInterceptor {
        whenever(rumMonitor.mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mock()
        whenever(rumMonitor.mockSdkCore.firstPartyHostResolver) doReturn mockResolver
        return DatadogInterceptor(
            sdkInstanceName = null,
            tracedHosts = tracedHosts,
            tracedRequestListener = mockRequestListener,
            rumResourceAttributesProvider = mockRumAttributesProvider,
            traceSampler = mockTraceSampler,
            traceContextInjection = TraceContextInjection.ALL,
            redacted404ResourceName = fakeRedacted404Resources,
            localTracerFactory = localTracerFactory,
            globalTracerProvider = globalTracerProvider
        )
    }

    override fun getExpectedOrigin(): String {
        return DatadogInterceptor.ORIGIN_RUM
    }

    @BeforeEach
    override fun `set up`(forge: Forge) {
        super.`set up`(forge)
        whenever(
            mockRumAttributesProvider.onProvideAttributes(any(), anyOrNull(), anyOrNull())
        ) doReturn emptyMap()
        whenever(mockTraceSampler.getSampleRate()) doReturn 100f
    }

    // -------------------------------------------------------------------------
    // Test 3 — RUMS-5093: the ResourceId passed to stopResource for request R1
    // must NOT be equal to the ResourceId passed to startResource for the
    // concurrent request R2 (and vice-versa), even when both target the same URL.
    //
    // On unfixed code:
    //   stopResourceId(R1).uuid == null  →  equals() uses key-only comparison
    //   stopResourceId(R1) == startResourceId(R2)  →  TRUE  ← BUG
    //
    // The assertion assertThat(stopR1).isNotEqualTo(startR2) therefore FAILS on
    // unfixed code, proving the collision bug.
    // -------------------------------------------------------------------------
    @Test
    fun `M not collide with other request scope W intercept() { two sequential requests same URL } RUMS-5093`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given — both chains return the same fakeUrl (same URL = same key in ResourceId)
        stubChain(mockChain, statusCode)
        val fakeResponseR2 = Response.Builder()
            .request(fakeRequest)
            .protocol(Protocol.HTTP_2)
            .code(statusCode)
            .message("HTTP $statusCode")
            .body("responseR2".toResponseBody(null))
            .build()
        whenever(mockChainR2.request()) doReturn fakeRequest
        whenever(mockChainR2.proceed(any())) doReturn fakeResponseR2

        // When — intercept R1 (starts and stops its own scope)
        testedInterceptor.intercept(mockChain)

        // Capture the ResourceIds used for R1
        val startCaptor = argumentCaptor<ResourceId>()
        val stopCaptor = argumentCaptor<ResourceId>()

        verify(rumMonitor.mockInstance).startResource(
            startCaptor.capture(),
            any(),
            any(),
            any()
        )
        verify(rumMonitor.mockInstance).stopResource(
            stopCaptor.capture(),
            any(),
            anyOrNull(),
            any(),
            any()
        )

        val startResourceIdR1 = startCaptor.firstValue
        val stopResourceIdR1 = stopCaptor.firstValue

        // When — intercept R2 (same URL, would create a second scope)
        testedInterceptor.intercept(mockChainR2)

        // Capture the ResourceIds used for R2
        val startCaptorR2 = argumentCaptor<ResourceId>()
        verify(rumMonitor.mockInstance, org.mockito.kotlin.times(2)).startResource(
            startCaptorR2.capture(),
            any(),
            any(),
            any()
        )
        // The second captured value belongs to R2
        val startResourceIdR2 = startCaptorR2.lastValue

        // Then
        // 1. The stop-event ResourceId for R1 must be equal to the START-event ResourceId for R1
        //    (this is the expected happy-path — same request, should match).
        assertThat(stopResourceIdR1)
            .withFailMessage(
                "RUMS-5093: stopResource ResourceId for R1 should match startResource " +
                    "ResourceId for R1 (same request), but it does not."
            )
            .isEqualTo(startResourceIdR1)

        // 2. The stop-event ResourceId for R1 must NOT be equal to the start-event ResourceId
        //    for R2 (different requests — their scopes must stay isolated).
        //
        //    On UNFIXED code: stopResourceIdR1.uuid == null → key-only fallback
        //    → stopResourceIdR1 == startResourceIdR2 == true → isNotEqualTo() FAILS → BUG PROVEN
        //
        //    On FIXED code:   stopResourceIdR1.uuid == R1's uuid
        //    → stopResourceIdR1 != startResourceIdR2 → isNotEqualTo() passes
        assertThat(stopResourceIdR1)
            .withFailMessage(
                "RUMS-5093 regression: stopResource ResourceId for R1 (uuid=null on " +
                    "unfixed code) incorrectly matches startResource ResourceId for R2 " +
                    "because ResourceId.equals() falls back to key-only comparison when " +
                    "either uuid is null. This causes the wrong RUM resource scope to be " +
                    "terminated, producing incorrect timing and corrupted span context."
            )
            .isNotEqualTo(startResourceIdR2)
    }
}
