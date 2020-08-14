/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android

import android.content.Context
import android.util.Log
import com.datadog.android.core.internal.net.identifyRequest
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.tracing.TracingInterceptor
import com.datadog.android.tracing.TracingInterceptorTest
import com.datadog.android.tracing.internal.TracesFeature
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockContext
import com.datadog.android.utils.mockDevLogHandler
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.RegexForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.opentracing.Tracer
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody
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
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogInterceptorWithoutTracesTest {

    lateinit var testedInterceptor: TracingInterceptor

    // region Mocks

    @Mock
    lateinit var mockLocalTracer: Tracer

    @Mock
    lateinit var mockChain: Interceptor.Chain

    lateinit var mockDevLogHandler: LogHandler

    lateinit var mockAppContext: Context

    @Mock
    lateinit var mockRumMonitor: RumMonitor

    // endregion

    // region Fakes

    @RegexForgery(TracingInterceptorTest.HOSTNAME_PATTERN)
    lateinit var fakeHostName: String

    @RegexForgery(TracingInterceptorTest.IPV4_PATTERN)
    lateinit var fakeHostIp: String

    lateinit var fakeMethod: String
    var fakeBody: String? = null
    var fakeMediaType: MediaType? = null

    @StringForgery(StringForgeryType.ASCII)
    lateinit var fakeResponseBody: String

    lateinit var fakeUrl: String

    @Forgery
    lateinit var fakeConfig: DatadogConfig.TracesConfig

    @StringForgery(StringForgeryType.ALPHABETICAL)
    lateinit var fakePackageName: String

    @RegexForgery("\\d(\\.\\d){3}")
    lateinit var fakePackageVersion: String

    lateinit var fakeRequest: Request
    lateinit var fakeResponse: Response

    // endregion

    @BeforeEach
    fun `set up`(forge: Forge) {
        mockDevLogHandler = mockDevLogHandler()
        mockAppContext = mockContext(fakePackageName, fakePackageVersion)
        Datadog.setVerbosity(Log.VERBOSE)

        val mediaType = forge.anElementFrom("application", "image", "text", "model") +
            "/" + forge.anAlphabeticalString()
        fakeMediaType = MediaType.parse(mediaType)
        fakeRequest = buildRequest(forge)
        testedInterceptor = DatadogInterceptor(emptyList()) { mockLocalTracer }
        TracesFeature.initialize(
            mockAppContext,
            fakeConfig,
            mock(), mock(), mock(), mock(), mock(), mock(), mock()
        )

        GlobalRum.registerIfAbsent(mockRumMonitor)
    }

    @AfterEach
    fun `tear down`() {
        GlobalRum.isRegistered.set(false)
    }

    @Test
    fun `starts and stop RUM Resource around successful request`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        setupFakeResponse(statusCode)
        val expectedStartAttrs = emptyMap<String, Any?>()
        val expectedStopAttrs = emptyMap<String, Any?>()
        val requestId = identifyRequest(fakeRequest)
        val mimeType = fakeMediaType?.type()
        val kind = when {
            fakeMethod in DatadogInterceptor.xhrMethods -> RumResourceKind.XHR
            mimeType != null -> RumResourceKind.fromMimeType(mimeType)
            else -> RumResourceKind.UNKNOWN
        }

        testedInterceptor.intercept(mockChain)

        inOrder(mockRumMonitor) {
            verify(mockRumMonitor).startResource(
                requestId,
                fakeMethod,
                fakeUrl,
                expectedStartAttrs
            )
            verify(mockRumMonitor).stopResource(
                requestId,
                statusCode,
                fakeResponseBody.toByteArray().size.toLong(),
                kind,
                expectedStopAttrs
            )
        }
    }

    @Test
    fun `starts and stop RUM Resource around failing request`(
        @IntForgery(min = 400, max = 500) statusCode: Int
    ) {
        setupFakeResponse(statusCode)
        val expectedStartAttrs = emptyMap<String, Any?>()
        val expectedStopAttrs = emptyMap<String, Any?>()
        val requestId = identifyRequest(fakeRequest)
        val mimeType = fakeMediaType?.type()
        val kind = when {
            fakeMethod in DatadogInterceptor.xhrMethods -> RumResourceKind.XHR
            mimeType != null -> RumResourceKind.fromMimeType(mimeType)
            else -> RumResourceKind.UNKNOWN
        }

        testedInterceptor.intercept(mockChain)

        inOrder(mockRumMonitor) {
            verify(mockRumMonitor).startResource(
                requestId,
                fakeMethod,
                fakeUrl,
                expectedStartAttrs
            )
            verify(mockRumMonitor).stopResource(
                requestId,
                statusCode,
                fakeResponseBody.toByteArray().size.toLong(),
                kind,
                expectedStopAttrs
            )
        }
    }

    @Test
    fun `starts and stop RUM Resource around throwing request`(
        @Forgery throwable: Throwable
    ) {
        val expectedStartAttrs = emptyMap<String, Any?>()
        val requestId = identifyRequest(fakeRequest)
        whenever(mockChain.request()) doReturn fakeRequest
        whenever(mockChain.proceed(any())) doThrow throwable

        assertThrows<Throwable>(throwable.message.orEmpty()) {
            testedInterceptor.intercept(mockChain)
        }

        inOrder(mockRumMonitor) {
            verify(mockRumMonitor).startResource(
                requestId,
                fakeMethod,
                fakeUrl,
                expectedStartAttrs
            )
            verify(mockRumMonitor).stopResourceWithError(
                requestId,
                null,
                "OkHttp request error $fakeMethod $fakeUrl",
                RumErrorSource.NETWORK,
                throwable
            )
        }
    }

    // region Internal

    internal fun buildRequest(
        forge: Forge,
        validHost: Boolean = true,
        configure: (Request.Builder) -> Unit = {}
    ): Request {
        val protocol = forge.anElementFrom("http", "https")
        val host = if (validHost) {
            forge.anElementFrom(fakeHostIp, fakeHostName)
        } else {
            forge.aString(3) { anAlphabeticalChar() } + fakeHostName
        }

        val path = forge.anAlphaNumericalString()
        fakeUrl = "$protocol://$host/$path"
        val builder = Request.Builder().url(fakeUrl)
        if (forge.aBool()) {
            fakeMethod = "POST"
            fakeBody = forge.anAlphabeticalString()
            builder.post(RequestBody.create(null, fakeBody!!.toByteArray()))
        } else {
            fakeMethod = forge.anElementFrom("GET", "HEAD", "DELETE")
            fakeBody = null
            builder.method(fakeMethod, null)
        }

        configure(builder)

        return builder.build()
    }

    internal fun setupFakeResponse(statusCode: Int = 200) {

        val builder = Response.Builder()
            .request(fakeRequest)
            .protocol(Protocol.HTTP_2)
            .code(statusCode)
            .message("HTTP $statusCode")
            .header(TracingInterceptor.HEADER_CT, fakeMediaType?.type().orEmpty())
            .body(ResponseBody.create(fakeMediaType, fakeResponseBody))

        fakeResponse = builder.build()

        whenever(mockChain.request()) doReturn fakeRequest
        whenever(mockChain.proceed(any())) doReturn fakeResponse
    }

    // endregion
}
