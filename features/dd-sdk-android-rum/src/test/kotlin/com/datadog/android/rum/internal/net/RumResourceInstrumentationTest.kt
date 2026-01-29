/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.net

import com.datadog.android.Datadog
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.instrumentation.network.ExtendedRequestInfo
import com.datadog.android.api.instrumentation.network.HttpRequestInfo
import com.datadog.android.api.instrumentation.network.HttpRequestInfoModifier
import com.datadog.android.api.instrumentation.network.HttpResponseInfo
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.internal.net.HttpSpec
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.RumResourceAttributesProvider
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.RumResourceMethod
import com.datadog.android.rum.internal.domain.event.ResourceTiming
import com.datadog.android.rum.internal.monitor.AdvancedNetworkRumMonitor
import com.datadog.android.rum.resource.ResourceId
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.rum.utils.verifyLog
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Locale
import java.util.UUID

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumResourceInstrumentationTest {

    private lateinit var testedInstrumentation: RumResourceInstrumentation

    @Mock
    lateinit var mockSdkCore: InternalSdkCore

    @Mock
    lateinit var mockRumMonitor: FakeNetworkRumMonitor

    @Mock
    lateinit var mockRumResourceAttributesProvider: RumResourceAttributesProvider

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockRumFeature: FeatureScope

    @Mock
    lateinit var mockResponseInfo: HttpResponseInfo

    @StringForgery
    lateinit var fakeNetworkInstrumentationName: String

    @StringForgery
    lateinit var fakeUrl: String

    @StringForgery
    lateinit var fakeMethod: String

    private lateinit var fakeRequestInfo: StubHttpRequestInfo

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeature

        datadogRegistryRegisterMethod.invoke(datadogRegistryField.get(null), null, mockSdkCore)
        GlobalRumMonitor.registerIfAbsent(mockRumMonitor, mockSdkCore)

        fakeRequestInfo = StubHttpRequestInfo(
            url = fakeUrl,
            method = fakeMethod,
            headers = emptyMap(),
            contentType = null,
            contentLength = null,
            tags = mutableMapOf()
        )

        testedInstrumentation = RumResourceInstrumentation(
            sdkInstanceName = null,
            networkInstrumentationName = fakeNetworkInstrumentationName,
            rumResourceAttributesProvider = mockRumResourceAttributesProvider
        )
    }

    @AfterEach
    fun `tear down`() {
        GlobalRumMonitor.clear()
        datadogRegistryClearMethod.invoke(datadogRegistryField.get(null))
    }

    @Test
    fun `M call waitForResourceTiming W sendWaitForResourceTimingEvent()`() {
        // When
        testedInstrumentation.sendWaitForResourceTimingEvent(fakeRequestInfo)

        // Then
        argumentCaptor<ResourceId> {
            verify(mockRumMonitor).waitForResourceTiming(capture())
            assertThat(firstValue.key).isEqualTo("$fakeMethod•$fakeUrl")
            assertThat(firstValue.uuid).isNotNull()
        }
    }

    @Test
    fun `M generate new UUID W sendWaitForResourceTimingEvent()`() {
        // When
        testedInstrumentation.sendWaitForResourceTimingEvent(fakeRequestInfo)

        // Then
        argumentCaptor<ResourceId> {
            verify(mockRumMonitor).waitForResourceTiming(capture())
            assertThat(firstValue.uuid).isNotNull()
        }
    }

    @Test
    fun `M call addResourceTiming W sendTiming()`(
        @Forgery fakeResourceTiming: ResourceTiming
    ) {
        // When
        testedInstrumentation.sendTiming(fakeRequestInfo, fakeResourceTiming)

        // Then
        argumentCaptor<ResourceId> {
            verify(mockRumMonitor).addResourceTiming(capture(), eq(fakeResourceTiming))
            assertThat(firstValue.key).isEqualTo("$fakeMethod•$fakeUrl")
        }
    }

    @Test
    fun `M not generate new UUID W sendTiming()`(
        @Forgery fakeResourceTiming: ResourceTiming
    ) {
        // When
        testedInstrumentation.sendTiming(fakeRequestInfo, fakeResourceTiming)

        // Then
        argumentCaptor<ResourceId> {
            verify(mockRumMonitor).addResourceTiming(capture(), eq(fakeResourceTiming))
            assertThat(firstValue.uuid).isNull()
        }
    }

    @Test
    fun `M use existing UUID from request tag W sendTiming()`(
        @Forgery fakeResourceTiming: ResourceTiming,
        @Forgery fakeUuid: UUID
    ) {
        // Given
        fakeRequestInfo = fakeRequestInfo.copy(tags = mutableMapOf(UUID::class.java to fakeUuid))

        // When
        testedInstrumentation.sendTiming(fakeRequestInfo, fakeResourceTiming)

        // Then
        argumentCaptor<ResourceId> {
            verify(mockRumMonitor).addResourceTiming(capture(), eq(fakeResourceTiming))
            assertThat(firstValue.uuid).isEqualTo(fakeUuid.toString())
        }
    }

    @ParameterizedTest
    @MethodSource("httpMethodsToRumMethods")
    fun `M call startResource with correct method W startResource()`(
        httpMethod: String,
        expectedRumMethod: RumResourceMethod
    ) {
        // Given
        fakeRequestInfo = fakeRequestInfo.copy(method = httpMethod)

        // When
        testedInstrumentation.startResource(fakeRequestInfo)

        // Then
        argumentCaptor<ResourceId> {
            verify(mockRumMonitor).startResource(
                capture(),
                eq(expectedRumMethod),
                eq(fakeUrl),
                eq(emptyMap())
            )
            assertThat(firstValue.key).isEqualTo("$httpMethod•$fakeUrl")
            assertThat(firstValue.uuid).isNotNull()
        }
    }

    @Test
    fun `M log warning and use GET W startResource() {unknown HTTP method}`(
        @StringForgery fakeUnknownMethod: String
    ) {
        // Given
        fakeRequestInfo = fakeRequestInfo.copy(method = fakeUnknownMethod)

        // When
        testedInstrumentation.startResource(fakeRequestInfo)

        // Then
        verify(mockRumMonitor).startResource(
            any<ResourceId>(),
            eq(RumResourceMethod.GET),
            eq(fakeUrl),
            eq(emptyMap())
        )

        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            RumResourceInstrumentation.UNSUPPORTED_HTTP_METHOD.format(
                Locale.US,
                fakeUnknownMethod,
                fakeNetworkInstrumentationName
            )
        )
    }

    @Test
    fun `M call stopResource W stopResource()`(
        @IntForgery(min = 200, max = 600) fakeStatusCode: Int,
        @LongForgery(min = 0) fakeContentLength: Long,
        @StringForgery fakeMimeType: String
    ) {
        // Given
        whenever(mockResponseInfo.statusCode) doReturn fakeStatusCode
        whenever(mockResponseInfo.contentLength) doReturn fakeContentLength
        whenever(mockResponseInfo.contentType) doReturn fakeMimeType
        whenever(mockResponseInfo.headers) doReturn emptyMap()
        whenever(
            mockRumResourceAttributesProvider.onProvideAttributes(
                any<HttpRequestInfo>(),
                anyOrNull<HttpResponseInfo>(),
                anyOrNull()
            )
        ) doReturn emptyMap()

        // When
        testedInstrumentation.stopResource(fakeRequestInfo, mockResponseInfo)

        // Then
        argumentCaptor<ResourceId> {
            verify(mockRumMonitor).stopResource(
                capture(),
                eq(fakeStatusCode),
                eq(fakeContentLength),
                eq(RumResourceKind.fromMimeType(fakeMimeType)),
                any<Map<String, Any?>>()
            )
            assertThat(firstValue.key).isEqualTo("$fakeMethod•$fakeUrl")
        }
    }

    @Test
    fun `M merge attributes W stopResource()`(
        @IntForgery(min = 200, max = 600) fakeStatusCode: Int,
        @LongForgery(min = 0) fakeContentLength: Long,
        forge: Forge
    ) {
        // Given
        val fakePassedAttributes = forge.aMap { forge.aString() to forge.aString() }
        val fakeProviderAttributes = forge.aMap { forge.aString() to forge.aString() }

        whenever(mockResponseInfo.statusCode) doReturn fakeStatusCode
        whenever(mockResponseInfo.contentLength) doReturn fakeContentLength
        whenever(mockResponseInfo.contentType) doReturn null
        whenever(mockResponseInfo.headers) doReturn emptyMap()
        whenever(
            mockRumResourceAttributesProvider.onProvideAttributes(
                any<HttpRequestInfo>(),
                anyOrNull<HttpResponseInfo>(),
                anyOrNull()
            )
        ) doReturn fakeProviderAttributes

        // When
        testedInstrumentation.stopResource(fakeRequestInfo, mockResponseInfo, fakePassedAttributes)

        // Then
        argumentCaptor<Map<String, Any?>> {
            verify(mockRumMonitor).stopResource(
                any<ResourceId>(),
                anyOrNull(),
                anyOrNull(),
                any<RumResourceKind>(),
                capture()
            )
            assertThat(firstValue).containsAllEntriesOf(fakePassedAttributes)
            assertThat(firstValue).containsAllEntriesOf(fakeProviderAttributes)
        }
    }

    @Test
    fun `M return null body length for stream W stopResource() {streaming content type}`(
        @IntForgery(min = 200, max = 600) fakeStatusCode: Int
    ) {
        // Given
        whenever(mockResponseInfo.statusCode) doReturn fakeStatusCode
        whenever(mockResponseInfo.contentLength) doReturn 1000L
        whenever(mockResponseInfo.contentType) doReturn HttpSpec.ContentType.TEXT_EVENT_STREAM
        whenever(mockResponseInfo.headers) doReturn emptyMap()
        whenever(
            mockRumResourceAttributesProvider.onProvideAttributes(
                any<HttpRequestInfo>(),
                anyOrNull<HttpResponseInfo>(),
                anyOrNull()
            )
        ) doReturn emptyMap()

        // When
        testedInstrumentation.stopResource(fakeRequestInfo, mockResponseInfo)

        // Then
        verify(mockRumMonitor).stopResource(
            any<ResourceId>(),
            eq(fakeStatusCode),
            eq(null),
            any<RumResourceKind>(),
            any<Map<String, Any?>>()
        )
    }

    @Test
    fun `M return null body length W stopResource() {WebSocket}`(
        @IntForgery(min = 200, max = 600) fakeStatusCode: Int,
        @StringForgery fakeWebSocketAccept: String
    ) {
        // Given
        whenever(mockResponseInfo.statusCode) doReturn fakeStatusCode
        whenever(mockResponseInfo.contentLength) doReturn 1000L
        whenever(mockResponseInfo.contentType) doReturn null
        whenever(mockResponseInfo.headers) doReturn mapOf(
            HttpSpec.Headers.WEBSOCKET_ACCEPT_HEADER to listOf(fakeWebSocketAccept)
        )
        whenever(
            mockRumResourceAttributesProvider.onProvideAttributes(
                any<HttpRequestInfo>(),
                anyOrNull<HttpResponseInfo>(),
                anyOrNull()
            )
        ) doReturn emptyMap()

        // When
        testedInstrumentation.stopResource(fakeRequestInfo, mockResponseInfo)

        // Then
        verify(mockRumMonitor).stopResource(
            any<ResourceId>(),
            eq(fakeStatusCode),
            eq(null),
            any<RumResourceKind>(),
            any<Map<String, Any?>>()
        )
    }

    @Test
    fun `M return RumResourceKind NATIVE W stopResource() {null content type}`(
        @IntForgery(min = 200, max = 600) fakeStatusCode: Int
    ) {
        // Given
        whenever(mockResponseInfo.statusCode) doReturn fakeStatusCode
        whenever(mockResponseInfo.contentLength) doReturn null
        whenever(mockResponseInfo.contentType) doReturn null
        whenever(mockResponseInfo.headers) doReturn emptyMap()
        whenever(
            mockRumResourceAttributesProvider.onProvideAttributes(
                any<HttpRequestInfo>(),
                anyOrNull<HttpResponseInfo>(),
                anyOrNull()
            )
        ) doReturn emptyMap()

        // When
        testedInstrumentation.stopResource(fakeRequestInfo, mockResponseInfo)

        // Then
        verify(mockRumMonitor).stopResource(
            any<ResourceId>(),
            anyOrNull(),
            anyOrNull(),
            eq(RumResourceKind.NATIVE),
            any<Map<String, Any?>>()
        )
    }

    @Test
    fun `M call stopResourceWithError W stopResourceWithError()`(
        @Forgery fakeThrowable: Throwable
    ) {
        // Given
        whenever(
            mockRumResourceAttributesProvider.onProvideAttributes(
                any<HttpRequestInfo>(),
                anyOrNull<HttpResponseInfo>(),
                anyOrNull()
            )
        ) doReturn emptyMap()

        // When
        testedInstrumentation.stopResourceWithError(fakeRequestInfo, fakeThrowable)

        // Then
        val expectedMessage = RumResourceInstrumentation.ERROR_MSG_FORMAT.format(
            Locale.US,
            fakeNetworkInstrumentationName,
            fakeMethod,
            fakeUrl
        )

        argumentCaptor<ResourceId> {
            verify(mockRumMonitor).stopResourceWithError(
                capture(),
                eq(null),
                eq(expectedMessage),
                eq(RumErrorSource.NETWORK),
                eq(fakeThrowable),
                any<Map<String, Any?>>()
            )
            assertThat(firstValue.key).isEqualTo("$fakeMethod•$fakeUrl")
        }
    }

    @Test
    fun `M pass provider attributes W stopResourceWithError()`(
        @Forgery fakeThrowable: Throwable,
        forge: Forge
    ) {
        // Given
        val fakeProviderAttributes = forge.aMap { forge.aString() to forge.aString() }
        whenever(
            mockRumResourceAttributesProvider.onProvideAttributes(
                any<HttpRequestInfo>(),
                anyOrNull<HttpResponseInfo>(),
                anyOrNull()
            )
        ) doReturn fakeProviderAttributes

        // When
        testedInstrumentation.stopResourceWithError(fakeRequestInfo, fakeThrowable)

        // Then
        argumentCaptor<Map<String, Any?>> {
            verify(mockRumMonitor).stopResourceWithError(
                any<ResourceId>(),
                anyOrNull(),
                any<String>(),
                any<RumErrorSource>(),
                any<Throwable>(),
                capture()
            )
            assertThat(firstValue).containsAllEntriesOf(fakeProviderAttributes)
        }
    }

    @Test
    fun `M log warning W reportInstrumentationError()`(
        @StringForgery fakeErrorMessage: String
    ) {
        // When
        testedInstrumentation.reportInstrumentationError(fakeErrorMessage)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            "Unable to instrument RUM resource: $fakeErrorMessage"
        )
    }

    @Test
    fun `M generate UUID W buildResourceId() {generateUuid = true}`() {
        // When
        val resourceId = RumResourceInstrumentation.buildResourceId(fakeRequestInfo, generateUuid = true)

        // Then
        assertThat(resourceId.key).isEqualTo("$fakeMethod•$fakeUrl")
        assertThat(resourceId.uuid).isNotNull()
    }

    @Test
    fun `M not generate UUID W buildResourceId() {generateUuid = false}`() {
        // When
        val resourceId = RumResourceInstrumentation.buildResourceId(fakeRequestInfo, generateUuid = false)

        // Then
        assertThat(resourceId.key).isEqualTo("$fakeMethod•$fakeUrl")
        assertThat(resourceId.uuid).isNull()
    }

    @Test
    fun `M use existing UUID from request tag W buildResourceId()`(
        @Forgery fakeUuid: UUID
    ) {
        // Given
        fakeRequestInfo = fakeRequestInfo.copy(tags = mutableMapOf(UUID::class.java to fakeUuid))

        // When
        val resourceId = RumResourceInstrumentation.buildResourceId(fakeRequestInfo, generateUuid = false)

        // Then
        assertThat(resourceId.uuid).isEqualTo(fakeUuid.toString())
    }

    @Test
    fun `M include content length and type in key W buildResourceId() {non-empty body}`(
        @LongForgery(min = 1) fakeContentLength: Long,
        @StringForgery fakeContentType: String
    ) {
        // Given
        fakeRequestInfo = fakeRequestInfo.copy(contentLength = fakeContentLength, contentType = fakeContentType)

        // When
        val resourceId = RumResourceInstrumentation.buildResourceId(fakeRequestInfo, generateUuid = false)

        // Then
        assertThat(resourceId.key).isEqualTo("$fakeMethod•$fakeUrl•$fakeContentLength•$fakeContentType")
    }

    @Test
    fun `M include content type in key W buildResourceId() {with content type only}`(
        @StringForgery fakeContentType: String
    ) {
        // Given
        fakeRequestInfo = fakeRequestInfo.copy(contentLength = 0L, contentType = fakeContentType)

        // When
        val resourceId = RumResourceInstrumentation.buildResourceId(fakeRequestInfo, generateUuid = false)

        // Then
        assertThat(resourceId.key).isEqualTo("$fakeMethod•$fakeUrl•0•$fakeContentType")
    }

    @Test
    fun `M log info W startResource() {RUM disabled}`() {
        // Given
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn null

        val testedInstrumentationNoRum = RumResourceInstrumentation(
            sdkInstanceName = null,
            networkInstrumentationName = fakeNetworkInstrumentationName,
            rumResourceAttributesProvider = mockRumResourceAttributesProvider
        )

        // When
        testedInstrumentationNoRum.startResource(fakeRequestInfo)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.INFO,
            InternalLogger.Target.USER,
            RumResourceInstrumentation.WARN_RUM_DISABLED.format(
                Locale.US,
                fakeNetworkInstrumentationName,
                "Default SDK instance"
            )
        )
    }

    companion object {
        private val datadogRegistryField = Datadog::class.java.getDeclaredField("registry").apply {
            isAccessible = true
        }
        private val datadogRegistryRegisterMethod = datadogRegistryField.type.getMethod(
            "register",
            String::class.java,
            SdkCore::class.java
        )
        private val datadogRegistryClearMethod = datadogRegistryField.type.getMethod("clear")

        @JvmStatic
        fun httpMethodsToRumMethods() = listOf(
            Arguments.of(HttpSpec.Method.GET, RumResourceMethod.GET),
            Arguments.of(HttpSpec.Method.POST, RumResourceMethod.POST),
            Arguments.of(HttpSpec.Method.PUT, RumResourceMethod.PUT),
            Arguments.of(HttpSpec.Method.DELETE, RumResourceMethod.DELETE),
            Arguments.of(HttpSpec.Method.PATCH, RumResourceMethod.PATCH),
            Arguments.of(HttpSpec.Method.HEAD, RumResourceMethod.HEAD),
            Arguments.of(HttpSpec.Method.OPTIONS, RumResourceMethod.OPTIONS),
            Arguments.of(HttpSpec.Method.TRACE, RumResourceMethod.TRACE),
            Arguments.of(HttpSpec.Method.CONNECT, RumResourceMethod.CONNECT)
        )
    }

    private data class StubHttpRequestInfo(
        override val url: String,
        override val method: String,
        override val headers: Map<String, List<String>>,
        override val contentType: String?,
        val contentLength: Long?,
        val tags: MutableMap<Any, Any?>
    ) : HttpRequestInfo, ExtendedRequestInfo {
        @Suppress("UNCHECKED_CAST")
        override fun <T> tag(type: Class<out T>): T? = tags[type] as? T

        override fun contentLength(): Long? = contentLength

        override fun modify(): HttpRequestInfoModifier = StubHttpRequestInfoModifier(this.copy())
    }

    private data class StubHttpRequestInfoModifier(
        private var request: StubHttpRequestInfo
    ) : HttpRequestInfoModifier {
        override fun setUrl(url: String) = apply { request = request.copy(url = url) }

        override fun addHeader(key: String, vararg values: String) = apply {
            request = request.copy(
                headers = request.headers.toMutableMap().also { it[key] = values.asList() }
            )
        }

        override fun removeHeader(key: String) = apply {
            request = request.copy(headers = request.headers.toMutableMap().also { it.remove(key) })
        }

        override fun <T> addTag(type: Class<in T>, tag: T?) = apply {
            request = request.copy(tags = request.tags.toMutableMap().also { it[type] = tag })
        }

        override fun result(): HttpRequestInfo = request.copy()
    }

    internal interface FakeNetworkRumMonitor : RumMonitor, AdvancedNetworkRumMonitor
}
