/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android

import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.internal.net.identifyRequest
import com.datadog.android.rum.NoOpRumResourceAttributesProvider
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceAttributesProvider
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.tracing.NoOpTracedRequestListener
import com.datadog.android.tracing.TracingInterceptor
import com.datadog.android.tracing.TracingInterceptorNotSendingSpanTest
import com.datadog.android.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.forge.exhaustiveAttributes
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.opentracing.Tracer
import java.io.IOException
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okio.BufferedSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogInterceptorTest : TracingInterceptorNotSendingSpanTest() {

    @Mock
    lateinit var mockRumAttributesProvider: RumResourceAttributesProvider

    @Forgery
    lateinit var fakeRumConfig: Configuration.Feature.RUM

    private lateinit var fakeAttributes: Map<String, Any?>

    override fun instantiateTestedInterceptor(
        tracedHosts: List<String>,
        factory: () -> Tracer
    ): TracingInterceptor {
        RumFeature.initialize(
            appContext.mockInstance,
            fakeRumConfig
        )
        return DatadogInterceptor(
            tracedHosts = tracedHosts,
            tracedRequestListener = mockRequestListener,
            firstPartyHostDetector = mockDetector,
            rumResourceAttributesProvider = mockRumAttributesProvider,
            localTracerFactory = factory
        )
    }

    override fun getExpectedOrigin(): String {
        return DatadogInterceptor.ORIGIN_RUM
    }

    @BeforeEach
    override fun `set up`(forge: Forge) {
        super.`set up`(forge)
        fakeAttributes = forge.exhaustiveAttributes()
        whenever(
            mockRumAttributesProvider.onProvideAttributes(
                any(),
                anyOrNull(),
                anyOrNull()
            )
        ) doReturn fakeAttributes
    }

    @AfterEach
    override fun `tear down`() {
        super.`tear down`()
        RumFeature.stop()
    }

    @Test
    fun `M instantiate with default callbacks W init()`() {
        // When
        val interceptor = DatadogInterceptor()

        // Then
        assertThat(interceptor.tracedHosts).isEmpty()
        assertThat(interceptor.rumResourceAttributesProvider)
            .isInstanceOf(NoOpRumResourceAttributesProvider::class.java)
        assertThat(interceptor.tracedRequestListener)
            .isInstanceOf(NoOpTracedRequestListener::class.java)
    }

    @Test
    fun `M instantiate with default callbacks W init()`(
        @StringForgery(regex = "[a-z]+\\.[a-z]{3}") hosts: List<String>
    ) {
        // When
        val interceptor = DatadogInterceptor(hosts)

        // Then
        assertThat(interceptor.tracedHosts).containsAll(hosts)
        assertThat(interceptor.rumResourceAttributesProvider)
            .isInstanceOf(NoOpRumResourceAttributesProvider::class.java)
        assertThat(interceptor.tracedRequestListener)
            .isInstanceOf(NoOpTracedRequestListener::class.java)
    }

    @Test
    fun `𝕄 start and stop RUM Resource 𝕎 intercept() {successful request}`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        stubChain(mockChain, statusCode)
        val expectedStartAttrs = emptyMap<String, Any?>()
        val expectedStopAttrs = mapOf(
            RumAttributes.TRACE_ID to fakeTraceId,
            RumAttributes.SPAN_ID to fakeSpanId
        ) + fakeAttributes
        val requestId = identifyRequest(fakeRequest)
        val mimeType = fakeMediaType?.type()
        val kind = when {
            mimeType != null -> RumResourceKind.fromMimeType(mimeType)
            else -> RumResourceKind.NATIVE
        }

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        inOrder(rumMonitor.mockInstance) {
            verify(rumMonitor.mockInstance).startResource(
                requestId,
                fakeMethod,
                fakeUrl,
                expectedStartAttrs
            )
            verify(rumMonitor.mockInstance).stopResource(
                requestId,
                statusCode,
                fakeResponseBody.toByteArray().size.toLong(),
                kind,
                expectedStopAttrs
            )
        }
    }

    @Test
    fun `𝕄 start and stop RUM Resource 𝕎 intercept() {successful request empty response}`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        stubChain(mockChain) {
            Response.Builder()
                .request(fakeRequest)
                .protocol(Protocol.HTTP_2)
                .code(statusCode)
                .message("HTTP $statusCode")
                .body(ResponseBody.create(fakeMediaType, ""))
                .header(TracingInterceptor.HEADER_CT, fakeMediaType?.type().orEmpty())
                .build()
        }
        val expectedStopAttrs = mapOf(
            RumAttributes.TRACE_ID to fakeTraceId,
            RumAttributes.SPAN_ID to fakeSpanId
        ) + fakeAttributes
        val requestId = identifyRequest(fakeRequest)
        val mimeType = fakeMediaType?.type()
        val kind = when {
            mimeType != null -> RumResourceKind.fromMimeType(mimeType)
            else -> RumResourceKind.NATIVE
        }

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        inOrder(rumMonitor.mockInstance) {
            verify(rumMonitor.mockInstance).startResource(
                requestId,
                fakeMethod,
                fakeUrl,
                emptyMap()
            )
            verify(rumMonitor.mockInstance).stopResource(
                requestId,
                statusCode,
                null,
                kind,
                expectedStopAttrs
            )
        }
    }

    @Test
    fun `𝕄 start and stop RUM Resource 𝕎 intercept() {successful request throwing response}`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        stubChain(mockChain) {
            Response.Builder()
                .request(fakeRequest)
                .protocol(Protocol.HTTP_2)
                .code(statusCode)
                .message("HTTP $statusCode")
                .header(TracingInterceptor.HEADER_CT, fakeMediaType?.type().orEmpty())
                .body(object : ResponseBody() {
                    override fun contentType(): MediaType? = fakeMediaType

                    override fun contentLength(): Long = fakeResponseBody.length.toLong()

                    override fun source(): BufferedSource {
                        return mock<BufferedSource>().apply {
                            whenever(this.request(any())) doThrow IOException()
                        }
                    }
                })
                .build()
        }
        val expectedStartAttrs = emptyMap<String, Any?>()
        val expectedStopAttrs = mapOf(
            RumAttributes.TRACE_ID to fakeTraceId,
            RumAttributes.SPAN_ID to fakeSpanId
        ) + fakeAttributes
        val requestId = identifyRequest(fakeRequest)
        val mimeType = fakeMediaType?.type()
        val kind = when {
            mimeType != null -> RumResourceKind.fromMimeType(mimeType)
            else -> RumResourceKind.NATIVE
        }

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        inOrder(rumMonitor.mockInstance) {
            verify(rumMonitor.mockInstance).startResource(
                requestId,
                fakeMethod,
                fakeUrl,
                expectedStartAttrs
            )
            verify(rumMonitor.mockInstance).stopResource(
                requestId,
                statusCode,
                null,
                kind,
                expectedStopAttrs
            )
        }
    }

    @Test
    fun `𝕄 start and stop RUM Resource 𝕎 intercept() {failing request}`(
        @IntForgery(min = 400, max = 500) statusCode: Int
    ) {
        // Given
        stubChain(mockChain, statusCode)
        val expectedStartAttrs = emptyMap<String, Any?>()
        val expectedStopAttrs = mapOf(
            RumAttributes.TRACE_ID to fakeTraceId,
            RumAttributes.SPAN_ID to fakeSpanId
        ) + fakeAttributes
        val requestId = identifyRequest(fakeRequest)
        val mimeType = fakeMediaType?.type()
        val kind = when {
            mimeType != null -> RumResourceKind.fromMimeType(mimeType)
            else -> RumResourceKind.NATIVE
        }

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        inOrder(rumMonitor.mockInstance) {
            verify(rumMonitor.mockInstance).startResource(
                requestId,
                fakeMethod,
                fakeUrl,
                expectedStartAttrs
            )
            verify(rumMonitor.mockInstance).stopResource(
                requestId,
                statusCode,
                fakeResponseBody.toByteArray().size.toLong(),
                kind,
                expectedStopAttrs
            )
        }
    }

    @Test
    fun `𝕄 start and stop RUM Resource 𝕎 intercept() {throwing request}`(
        @Forgery throwable: Throwable
    ) {
        // Given
        val expectedStartAttrs = emptyMap<String, Any?>()
        val requestId = identifyRequest(fakeRequest)
        whenever(mockChain.request()) doReturn fakeRequest
        whenever(mockChain.proceed(any())) doThrow throwable

        // When
        assertThrows<Throwable>(throwable.message.orEmpty()) {
            testedInterceptor.intercept(mockChain)
        }

        // Then
        inOrder(rumMonitor.mockInstance) {
            verify(rumMonitor.mockInstance).startResource(
                requestId,
                fakeMethod,
                fakeUrl,
                expectedStartAttrs
            )
            verify(rumMonitor.mockInstance).stopResourceWithError(
                requestId,
                null,
                "OkHttp request error $fakeMethod $fakeUrl",
                RumErrorSource.NETWORK,
                throwable,
                fakeAttributes
            )
        }
    }

    companion object {
        val rumMonitor = GlobalRumMonitorTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(appContext, coreFeature, rumMonitor)
        }
    }
}
