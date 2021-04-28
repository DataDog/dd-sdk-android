/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.net.FirstPartyHostDetector
import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.utils.loggableStackTrace
import com.datadog.android.core.model.NetworkInfo
import com.datadog.android.core.model.UserInfo
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.assertj.RumEventAssert.Companion.assertThat
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.domain.event.ResourceTiming
import com.datadog.android.rum.internal.domain.event.RumEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.utils.asTimingsPayload
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.forge.aFilteredMap
import com.datadog.android.utils.forge.exhaustiveAttributes
import com.datadog.android.utils.mockCoreFeature
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.atMost
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.net.URL
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
internal class RumResourceScopeTest {

    private lateinit var testedScope: RumResourceScope

    @Mock
    lateinit var mockParentScope: RumScope

    @Mock
    lateinit var mockEvent: RumRawEvent

    @Mock
    lateinit var mockWriter: DataWriter<RumEvent>

    @Mock
    lateinit var mockDetector: FirstPartyHostDetector

    @StringForgery(regex = "http(s?)://[a-z]+.com/[a-z]+")
    lateinit var fakeUrl: String
    lateinit var fakeKey: String
    lateinit var fakeMethod: String
    lateinit var fakeAttributes: Map<String, Any?>

    @Forgery
    lateinit var fakeParentContext: RumContext

    @Forgery
    lateinit var fakeUserInfo: UserInfo

    @Forgery
    lateinit var fakeNetworkInfo: NetworkInfo

    private lateinit var fakeEventTime: Time

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeEventTime = Time()
        fakeAttributes = forge.exhaustiveAttributes()
        fakeKey = forge.anAsciiString()
        fakeMethod = forge.anElementFrom("PUT", "POST", "GET", "DELETE")
        mockEvent = mockEvent()

        mockCoreFeature()

        whenever(CoreFeature.userInfoProvider.getUserInfo()) doReturn fakeUserInfo
        whenever(CoreFeature.networkInfoProvider.getLatestNetworkInfo()) doReturn fakeNetworkInfo
        whenever(mockParentScope.getRumContext()) doReturn fakeParentContext
        doAnswer { false }.whenever(mockDetector).isFirstPartyUrl(any<String>())

        testedScope = RumResourceScope(
            mockParentScope,
            fakeUrl,
            fakeMethod,
            fakeKey,
            fakeEventTime,
            fakeAttributes,
            mockDetector
        )
    }

    @AfterEach
    fun `tear down`() {
        CoreFeature.stop()
        GlobalRum.globalAttributes.clear()
    }

    @Test
    fun `𝕄 send Resource event 𝕎 handleEvent(StopResource)`(
        @Forgery kind: RumResourceKind,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        // When
        Thread.sleep(RESOURCE_DURATION_MS)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(firstValue)
                .hasAttributes(expectedAttributes)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasResourceData {
                    hasId(testedScope.resourceId)
                    hasTimestamp(fakeEventTime.timestamp)
                    hasUrl(fakeUrl)
                    hasMethod(fakeMethod)
                    hasKind(kind)
                    hasStatusCode(statusCode)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(RESOURCE_DURATION_MS))
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasTraceId(null)
                    hasSpanId(null)
                    doesNotHaveAResourceProvider()
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `𝕄 add first party type provider to Resource 𝕎 handleEvent(StopResource)`(
        @Forgery kind: RumResourceKind,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        forge: Forge
    ) {
        // Given
        doAnswer { true }.whenever(mockDetector).isFirstPartyUrl(fakeUrl)
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        // When
        Thread.sleep(RESOURCE_DURATION_MS)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(firstValue)
                .hasAttributes(expectedAttributes)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasResourceData {
                    hasId(testedScope.resourceId)
                    hasTimestamp(fakeEventTime.timestamp)
                    hasUrl(fakeUrl)
                    hasMethod(fakeMethod)
                    hasKind(kind)
                    hasStatusCode(statusCode)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(RESOURCE_DURATION_MS))
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasTraceId(null)
                    hasSpanId(null)
                    hasProviderType(ResourceEvent.ProviderType.FIRST_PARTY)
                    hasProviderDomain(URL(fakeUrl).host)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `𝕄 use the url for provider domain 𝕎 handleEvent(StopResource) { url is broken }`(
        @Forgery kind: RumResourceKind,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        forge: Forge
    ) {
        // Given
        val brokenUrl = forge.aStringMatching("[a-z]+.com/[a-z]+")
        testedScope = RumResourceScope(
            mockParentScope,
            brokenUrl,
            fakeMethod,
            fakeKey,
            fakeEventTime,
            fakeAttributes,
            mockDetector
        )
        doAnswer { true }.whenever(mockDetector).isFirstPartyUrl(brokenUrl)
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        // When
        Thread.sleep(RESOURCE_DURATION_MS)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(firstValue)
                .hasAttributes(expectedAttributes)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasResourceData {
                    hasId(testedScope.resourceId)
                    hasTimestamp(fakeEventTime.timestamp)
                    hasUrl(brokenUrl)
                    hasMethod(fakeMethod)
                    hasKind(kind)
                    hasStatusCode(statusCode)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(RESOURCE_DURATION_MS))
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasTraceId(null)
                    hasSpanId(null)
                    hasProviderType(ResourceEvent.ProviderType.FIRST_PARTY)
                    hasProviderDomain(brokenUrl)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `𝕄 send Resource with trace info 𝕎 handleEvent(StopResource)`(
        @Forgery kind: RumResourceKind,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        @StringForgery(type = StringForgeryType.HEXADECIMAL) fakeSpanId: String,
        @StringForgery(type = StringForgeryType.HEXADECIMAL) fakeTraceId: String,
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
            .toMutableMap()
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)
        attributes[RumAttributes.TRACE_ID] = fakeTraceId
        attributes[RumAttributes.SPAN_ID] = fakeSpanId

        // When
        Thread.sleep(RESOURCE_DURATION_MS)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(firstValue)
                .hasAttributes(expectedAttributes)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasResourceData {
                    hasId(testedScope.resourceId)
                    hasTimestamp(fakeEventTime.timestamp)
                    hasUrl(fakeUrl)
                    hasMethod(fakeMethod)
                    hasKind(kind)
                    hasStatusCode(statusCode)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(RESOURCE_DURATION_MS))
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasTraceId(fakeTraceId)
                    hasSpanId(fakeSpanId)
                    doesNotHaveAResourceProvider()
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `𝕄 send Resource with initial context 𝕎 handleEvent(StopResource)`(
        @Forgery context: RumContext,
        @Forgery kind: RumResourceKind,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)
        whenever(mockParentScope.getRumContext()) doReturn context

        // When
        Thread.sleep(RESOURCE_DURATION_MS)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(firstValue)
                .hasAttributes(expectedAttributes)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasResourceData {
                    hasId(testedScope.resourceId)
                    hasTimestamp(fakeEventTime.timestamp)
                    hasUrl(fakeUrl)
                    hasMethod(fakeMethod)
                    hasKind(kind)
                    hasStatusCode(statusCode)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(RESOURCE_DURATION_MS))
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasTraceId(null)
                    hasSpanId(null)
                    doesNotHaveAResourceProvider()
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `𝕄 send event with user extra attributes 𝕎 handleEvent(StopResource)`(
        @Forgery kind: RumResourceKind,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long
    ) {
        // When
        Thread.sleep(RESOURCE_DURATION_MS)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, emptyMap())
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(firstValue)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasResourceData {
                    hasId(testedScope.resourceId)
                    hasTimestamp(fakeEventTime.timestamp)
                    hasUrl(fakeUrl)
                    hasMethod(fakeMethod)
                    hasKind(kind)
                    hasStatusCode(statusCode)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(RESOURCE_DURATION_MS))
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasTraceId(null)
                    hasSpanId(null)
                    doesNotHaveAResourceProvider()
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `𝕄 do not send error event 𝕎 handleEvent(StopResource with error statusCode)`(
        @Forgery kind: RumResourceKind,
        @LongForgery(400, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long
    ) {
        // When
        Thread.sleep(RESOURCE_DURATION_MS)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, emptyMap())
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(firstValue)
                .hasAttributes(fakeAttributes)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasResourceData {
                    hasKind(kind)
                    hasStatusCode(statusCode)
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `𝕄 not send related error event 𝕎 handleEvent(StopResource with success statusCode)`(
        @Forgery kind: RumResourceKind,
        @LongForgery(200, 399) statusCode: Long,
        @LongForgery(0, 1024) size: Long
    ) {
        // When
        Thread.sleep(RESOURCE_DURATION_MS)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, emptyMap())
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue.event).isNotInstanceOf(ErrorEvent::class.java)
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `𝕄 not send related error event 𝕎 handleEvent(StopResource with missing statusCode)`(
        @Forgery kind: RumResourceKind,
        @LongForgery(0, 1024) size: Long
    ) {
        // When
        Thread.sleep(RESOURCE_DURATION_MS)
        mockEvent = RumRawEvent.StopResource(fakeKey, null, size, kind, emptyMap())
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue.event).isNotInstanceOf(ErrorEvent::class.java)
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `𝕄 send Resource with global attributes 𝕎 handleEvent(StopResource)`(
        @Forgery kind: RumResourceKind,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        forge: Forge
    ) {
        // Given
        val attributes = forge.aFilteredMap(excludedKeys = fakeAttributes.keys) {
            anHexadecimalString() to anAsciiString()
        }
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)
        GlobalRum.globalAttributes.putAll(attributes)

        // When
        Thread.sleep(RESOURCE_DURATION_MS)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, emptyMap())
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(firstValue)
                .hasAttributes(expectedAttributes)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasResourceData {
                    hasId(testedScope.resourceId)
                    hasTimestamp(fakeEventTime.timestamp)
                    hasUrl(fakeUrl)
                    hasMethod(fakeMethod)
                    hasKind(kind)
                    hasStatusCode(statusCode)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(RESOURCE_DURATION_MS))
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasTraceId(null)
                    hasSpanId(null)
                    doesNotHaveAResourceProvider()
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `𝕄 send Resource with timing 𝕎 handleEvent(AddResourceTiming+StopResource)`(
        @Forgery kind: RumResourceKind,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        @Forgery timing: ResourceTiming,
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        // When
        mockEvent = RumRawEvent.AddResourceTiming(fakeKey, timing)
        val resultTiming = testedScope.handleEvent(mockEvent, mockWriter)
        Thread.sleep(RESOURCE_DURATION_MS)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(firstValue)
                .hasAttributes(expectedAttributes)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasResourceData {
                    hasId(testedScope.resourceId)
                    hasTimestamp(fakeEventTime.timestamp)
                    hasUrl(fakeUrl)
                    hasMethod(fakeMethod)
                    hasKind(kind)
                    hasStatusCode(statusCode)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(RESOURCE_DURATION_MS))
                    hasTiming(timing)
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasTraceId(null)
                    hasSpanId(null)
                    doesNotHaveAResourceProvider()
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(resultTiming).isEqualTo(testedScope)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `𝕄 send Resource 𝕎 handleEvent(AddResourceTiming+StopResource) {unrelated timing}`(
        @Forgery kind: RumResourceKind,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        @Forgery timing: ResourceTiming,
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        // When
        mockEvent = RumRawEvent.AddResourceTiming("not_the_$fakeKey", timing)
        val resultTiming = testedScope.handleEvent(mockEvent, mockWriter)
        Thread.sleep(RESOURCE_DURATION_MS)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(firstValue)
                .hasAttributes(expectedAttributes)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasResourceData {
                    hasId(testedScope.resourceId)
                    hasTimestamp(fakeEventTime.timestamp)
                    hasUrl(fakeUrl)
                    hasMethod(fakeMethod)
                    hasKind(kind)
                    hasStatusCode(statusCode)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(RESOURCE_DURATION_MS))
                    hasNoTiming()
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasTraceId(null)
                    hasSpanId(null)
                    doesNotHaveAResourceProvider()
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(resultTiming).isEqualTo(testedScope)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `𝕄 send Error 𝕎 handleEvent(StopResourceWithError)`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        mockEvent = RumRawEvent.StopResourceWithError(
            fakeKey,
            null,
            message,
            source,
            throwable,
            attributes
        )

        // When
        Thread.sleep(RESOURCE_DURATION_MS)
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(expectedAttributes)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasErrorData {
                    hasMessage(message)
                    hasSource(source)
                    hasStackTrace(throwable.loggableStackTrace())
                    isCrash(false)
                    hasResource(fakeUrl, fakeMethod, 0L)
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasErrorType(throwable.javaClass.canonicalName)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `𝕄 use the url for provider domain 𝕎 handleEvent(StopResourceWithError) { broken url }`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        forge: Forge
    ) {
        // Given
        val brokenUrl = forge.aStringMatching("[a-z]+.com/[a-z]+")
        testedScope = RumResourceScope(
            mockParentScope,
            brokenUrl,
            fakeMethod,
            fakeKey,
            fakeEventTime,
            fakeAttributes,
            mockDetector
        )
        doAnswer { true }.whenever(mockDetector).isFirstPartyUrl(brokenUrl)
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        mockEvent = RumRawEvent.StopResourceWithError(
            fakeKey,
            null,
            message,
            source,
            throwable,
            attributes
        )

        // When
        Thread.sleep(RESOURCE_DURATION_MS)
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(expectedAttributes)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasErrorData {
                    hasMessage(message)
                    hasSource(source)
                    hasStackTrace(throwable.loggableStackTrace())
                    isCrash(false)
                    hasResource(brokenUrl, fakeMethod, 0L)
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasProviderDomain(brokenUrl)
                    hasProviderType(ErrorEvent.ProviderType.FIRST_PARTY)
                    hasErrorType(throwable.javaClass.canonicalName)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `𝕄 add first party type provider to Error 𝕎 handleEvent(StopResourceWithError)`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        forge: Forge
    ) {
        // Given
        doAnswer { true }.whenever(mockDetector).isFirstPartyUrl(fakeUrl)

        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        mockEvent = RumRawEvent.StopResourceWithError(
            fakeKey,
            null,
            message,
            source,
            throwable,
            attributes
        )

        // When
        Thread.sleep(RESOURCE_DURATION_MS)
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(expectedAttributes)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasErrorData {
                    hasMessage(message)
                    hasSource(source)
                    hasStackTrace(throwable.loggableStackTrace())
                    isCrash(false)
                    hasResource(fakeUrl, fakeMethod, 0L)
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasProviderDomain(URL(fakeUrl).host)
                    hasProviderType(ErrorEvent.ProviderType.FIRST_PARTY)
                    hasErrorType(throwable.javaClass.canonicalName)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `𝕄 send Error with initial context 𝕎 handleEvent(StopResourceWithError)`(
        @Forgery context: RumContext,
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        mockEvent = RumRawEvent.StopResourceWithError(
            fakeKey,
            null,
            message,
            source,
            throwable,
            attributes
        )
        whenever(mockParentScope.getRumContext()) doReturn context

        // When
        Thread.sleep(RESOURCE_DURATION_MS)
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(expectedAttributes)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasErrorData {
                    hasMessage(message)
                    hasSource(source)
                    hasStackTrace(throwable.loggableStackTrace())
                    isCrash(false)
                    hasResource(fakeUrl, fakeMethod, 0L)
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasErrorType(throwable.javaClass.canonicalName)
                    doesNotHaveAResourceProvider()
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `𝕄 send Error with global attributes 𝕎 handleEvent(StopResourceWithError)`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @LongForgery(200, 600) statusCode: Long,
        @Forgery throwable: Throwable,
        forge: Forge
    ) {
        // Given
        val attributes = forge.aFilteredMap(excludedKeys = fakeAttributes.keys) {
            anHexadecimalString() to anAsciiString()
        }
        val errorAttributes = forge.exhaustiveAttributes(
            excludedKeys = fakeAttributes.keys + attributes.keys
        )
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)
        expectedAttributes.putAll(errorAttributes)

        GlobalRum.globalAttributes.putAll(attributes)
        mockEvent = RumRawEvent.StopResourceWithError(
            fakeKey,
            statusCode,
            message,
            source,
            throwable,
            errorAttributes
        )

        // When
        Thread.sleep(RESOURCE_DURATION_MS)
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(expectedAttributes)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasErrorData {
                    hasMessage(message)
                    hasSource(source)
                    hasStackTrace(throwable.loggableStackTrace())
                    isCrash(false)
                    hasResource(fakeUrl, fakeMethod, statusCode)
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasErrorType(throwable.javaClass.canonicalName)
                    doesNotHaveAResourceProvider()
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(StopResource) with different key`(
        @Forgery kind: RumResourceKind,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        forge: Forge
    ) {
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)
        mockEvent = RumRawEvent.StopResource("not_the_$fakeKey", statusCode, size, kind, attributes)

        Thread.sleep(RESOURCE_DURATION_MS)
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        verify(mockParentScope, atMost(1)).getRumContext()
        verifyNoMoreInteractions(mockWriter, mockParentScope)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(StopResourceWithError) with different key`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @LongForgery(200, 600) statusCode: Long,
        @Forgery throwable: Throwable
    ) {
        mockEvent = RumRawEvent.StopResourceWithError(
            "not_the_$fakeKey",
            statusCode,
            message,
            source,
            throwable,
            emptyMap()
        )

        Thread.sleep(RESOURCE_DURATION_MS)
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        verify(mockParentScope, atMost(1)).getRumContext()
        verifyNoMoreInteractions(mockWriter, mockParentScope)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(WaitForResourceTiming+StopResource)`(
        @Forgery kind: RumResourceKind,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        forge: Forge
    ) {
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        mockEvent = RumRawEvent.WaitForResourceTiming(fakeKey)
        val resultWaitForTiming = testedScope.handleEvent(mockEvent, mockWriter)
        Thread.sleep(RESOURCE_DURATION_MS)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)
        val resultStop = testedScope.handleEvent(mockEvent, mockWriter)

        verify(mockParentScope, atMost(1)).getRumContext()
        verifyNoMoreInteractions(mockWriter, mockParentScope)
        assertThat(resultWaitForTiming).isEqualTo(testedScope)
        assertThat(resultStop).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send Resource 𝕎 handleEvent(WaitForResourceTiming+StopResource) {unrelated wait}`(
        @Forgery kind: RumResourceKind,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        forge: Forge
    ) {
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        mockEvent = RumRawEvent.WaitForResourceTiming("not_the_$fakeKey")
        val resultWaitForTiming = testedScope.handleEvent(mockEvent, mockWriter)
        Thread.sleep(RESOURCE_DURATION_MS)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)
        val resultStop = testedScope.handleEvent(mockEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(firstValue)
                .hasAttributes(expectedAttributes)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasResourceData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasUrl(fakeUrl)
                    hasMethod(fakeMethod)
                    hasKind(kind)
                    hasStatusCode(statusCode)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(RESOURCE_DURATION_MS))
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    doesNotHaveAResourceProvider()
                }
        }
        verifyNoMoreInteractions(mockWriter)
        verify(mockParentScope, never()).handleEvent(any(), any())
        assertThat(resultWaitForTiming).isSameAs(testedScope)
        assertThat(resultStop).isEqualTo(null)
    }

    @Test
    fun `𝕄 send Resource 𝕎 handleEvent(WaitForResourceTiming+AddResourceTiming+StopResource)`(
        @Forgery kind: RumResourceKind,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        @Forgery timing: ResourceTiming,
        forge: Forge
    ) {
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        mockEvent = RumRawEvent.WaitForResourceTiming(fakeKey)
        val resultWaitForTiming = testedScope.handleEvent(mockEvent, mockWriter)
        mockEvent = RumRawEvent.AddResourceTiming(fakeKey, timing)
        val resultTiming = testedScope.handleEvent(mockEvent, mockWriter)
        Thread.sleep(RESOURCE_DURATION_MS)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)
        val resultStop = testedScope.handleEvent(mockEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(firstValue)
                .hasAttributes(expectedAttributes)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasResourceData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasUrl(fakeUrl)
                    hasMethod(fakeMethod)
                    hasKind(kind)
                    hasStatusCode(statusCode)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(RESOURCE_DURATION_MS))
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    doesNotHaveAResourceProvider()
                }
        }
        verifyNoMoreInteractions(mockWriter)
        verify(mockParentScope, never()).handleEvent(any(), any())
        assertThat(resultWaitForTiming).isEqualTo(testedScope)
        assertThat(resultTiming).isEqualTo(testedScope)
        assertThat(resultStop).isEqualTo(null)
    }

    @Test
    fun `𝕄 send Resource 𝕎 handleEvent(WaitForResourceTiming+StopResource+AddResourceTiming)`(
        @Forgery kind: RumResourceKind,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        @Forgery timing: ResourceTiming,
        forge: Forge
    ) {
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        mockEvent = RumRawEvent.WaitForResourceTiming(fakeKey)
        val resultWaitForTiming = testedScope.handleEvent(mockEvent, mockWriter)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)
        val resultStop = testedScope.handleEvent(mockEvent, mockWriter)
        Thread.sleep(RESOURCE_DURATION_MS)
        mockEvent = RumRawEvent.AddResourceTiming(fakeKey, timing)
        val resultTiming = testedScope.handleEvent(mockEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(firstValue)
                .hasAttributes(expectedAttributes)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasResourceData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasUrl(fakeUrl)
                    hasMethod(fakeMethod)
                    hasKind(kind)
                    hasStatusCode(statusCode)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(RESOURCE_DURATION_MS))
                    hasTiming(timing)
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    doesNotHaveAResourceProvider()
                }
        }
        verifyNoMoreInteractions(mockWriter)
        verify(mockParentScope, never()).handleEvent(any(), any())
        assertThat(resultWaitForTiming).isEqualTo(testedScope)
        assertThat(resultStop).isEqualTo(testedScope)
        assertThat(resultTiming).isEqualTo(null)
    }

    @Test
    fun `𝕄 use explicit timings 𝕎 handleEvent { AddResourceTiming + StopResource }`(
        @Forgery kind: RumResourceKind,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        @Forgery timing: ResourceTiming,
        forge: Forge
    ) {

        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys) +
            mapOf(
                "_dd.resource_timings"
                    to forge.getForgery(ResourceTiming::class.java).asTimingsPayload()
            )

        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)

        // When
        testedScope
            .handleEvent(RumRawEvent.AddResourceTiming(fakeKey, timing = timing), mockWriter)
            ?.handleEvent(mockEvent, mockWriter)

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(firstValue)
                .hasResourceData {
                    hasTiming(timing)
                }
        }
    }

    @Test
    fun `𝕄 use attributes timings 𝕎 handleEvent { StopResource without AddResourceTiming  }`(
        @Forgery kind: RumResourceKind,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        @Forgery timing: ResourceTiming,
        forge: Forge
    ) {

        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys) +
            mapOf("_dd.resource_timings" to timing.asTimingsPayload())

        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)

        // When
        testedScope.handleEvent(mockEvent, mockWriter)

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(firstValue)
                .hasResourceData {
                    hasTiming(timing)
                }
        }
    }

    // region Internal

    private fun mockEvent(): RumRawEvent {
        val event: RumRawEvent = mock()
        whenever(event.eventTime) doReturn Time()
        return event
    }

    // endregion

    companion object {
        private const val RESOURCE_DURATION_MS = 50L
    }
}
