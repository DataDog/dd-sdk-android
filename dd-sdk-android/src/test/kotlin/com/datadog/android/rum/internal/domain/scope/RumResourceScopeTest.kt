/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import android.util.Log
import com.datadog.android.core.internal.net.FirstPartyHostDetector
import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.utils.loggableStackTrace
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.assertj.ErrorEventAssert.Companion.assertThat
import com.datadog.android.rum.assertj.ResourceEventAssert.Companion.assertThat
import com.datadog.android.rum.internal.FeaturesContextResolver
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.domain.event.ResourceTiming
import com.datadog.android.rum.internal.domain.event.RumEventSourceProvider
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.utils.asTimingsPayload
import com.datadog.android.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.utils.config.LoggerTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.forge.aFilteredMap
import com.datadog.android.utils.forge.exhaustiveAttributes
import com.datadog.android.v2.api.context.DatadogContext
import com.datadog.android.v2.core.internal.ContextProvider
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
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
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.net.URL
import java.util.Locale
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
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
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
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
    lateinit var mockWriter: DataWriter<Any>

    @Mock
    lateinit var mockDetector: FirstPartyHostDetector

    @Mock
    lateinit var mockContextProvider: ContextProvider

    @StringForgery(regex = "http(s?)://[a-z]+\\.com/[a-z]+")
    lateinit var fakeUrl: String
    lateinit var fakeKey: String
    lateinit var fakeMethod: String
    lateinit var fakeAttributes: Map<String, Any?>

    @Forgery
    lateinit var fakeParentContext: RumContext

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    var fakeServerOffset: Long = 0L

    private lateinit var fakeEventTime: Time
    var fakeSourceResourceEvent: ResourceEvent.Source? = null
    var fakeSourceErrorEvent: ErrorEvent.ErrorEventSource? = null

    @Mock
    lateinit var mockRumEventSourceProvider: RumEventSourceProvider

    @BoolForgery
    var fakeHasReplay: Boolean = false

    @Mock
    lateinit var mockFeaturesContextResolver: FeaturesContextResolver

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeSourceResourceEvent = forge.aNullable { aValueFrom(ResourceEvent.Source::class.java) }
        fakeSourceErrorEvent = forge.aNullable {
            aValueFrom(ErrorEvent.ErrorEventSource::class.java)
        }
        whenever(mockRumEventSourceProvider.resourceEventSource)
            .thenReturn(fakeSourceResourceEvent)
        whenever(mockRumEventSourceProvider.errorEventSource)
            .thenReturn(fakeSourceErrorEvent)
        fakeEventTime = Time()
        val maxLimit = Long.MAX_VALUE - fakeEventTime.timestamp
        val minLimit = -fakeEventTime.timestamp
        fakeServerOffset =
            forge.aLong(min = minLimit, max = maxLimit)
        fakeAttributes = forge.exhaustiveAttributes()
        fakeKey = forge.anAsciiString()
        fakeMethod = forge.anElementFrom("PUT", "POST", "GET", "DELETE")
        mockEvent = mockEvent()

        whenever(mockContextProvider.context) doReturn fakeDatadogContext
        whenever(mockParentScope.getRumContext()) doReturn fakeParentContext
        doAnswer { false }.whenever(mockDetector).isFirstPartyUrl(any<String>())
        whenever(mockFeaturesContextResolver.resolveHasReplay(fakeDatadogContext))
            .thenReturn(fakeHasReplay)

        testedScope = RumResourceScope(
            mockParentScope,
            fakeUrl,
            fakeMethod,
            fakeKey,
            fakeEventTime,
            fakeAttributes,
            fakeServerOffset,
            mockDetector,
            mockRumEventSourceProvider,
            mockContextProvider,
            mockFeaturesContextResolver
        )
    }

    @Test
    fun `M return true W isActive() {not stopped}`() {
        // Given
        testedScope.stopped = false

        // When
        val isActive = testedScope.isActive()

        // Then
        assertThat(isActive).isTrue()
    }

    @Test
    fun `M return false W isActive() {stopped}`() {
        // Given
        testedScope.stopped = true

        // When
        val isActive = testedScope.isActive()

        // Then
        assertThat(isActive).isFalse()
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
        argumentCaptor<ResourceEvent> {
            verify(mockWriter).write(capture())
            assertThat(firstValue)
                .apply {
                    hasId(testedScope.resourceId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasUrl(fakeUrl)
                    hasMethod(fakeMethod)
                    hasKind(kind)
                    hasStatusCode(statusCode)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(RESOURCE_DURATION_MS))
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasTraceId(null)
                    hasSpanId(null)
                    hasReplay(fakeHasReplay)
                    doesNotHaveAResourceProvider()
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(expectedAttributes)
                    hasSource(fakeSourceResourceEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toResourceSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
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
        argumentCaptor<ResourceEvent> {
            verify(mockWriter).write(capture())
            assertThat(firstValue)
                .apply {
                    hasId(testedScope.resourceId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasUrl(fakeUrl)
                    hasMethod(fakeMethod)
                    hasKind(kind)
                    hasStatusCode(statusCode)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(RESOURCE_DURATION_MS))
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasTraceId(null)
                    hasSpanId(null)
                    hasReplay(fakeHasReplay)
                    hasProviderType(ResourceEvent.ProviderType.FIRST_PARTY)
                    hasProviderDomain(URL(fakeUrl).host)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(expectedAttributes)
                    hasSource(fakeSourceResourceEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toResourceSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
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
            fakeServerOffset,
            mockDetector,
            mockRumEventSourceProvider,
            mockContextProvider,
            mockFeaturesContextResolver
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
        argumentCaptor<ResourceEvent> {
            verify(mockWriter).write(capture())
            assertThat(firstValue)
                .apply {
                    hasId(testedScope.resourceId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasUrl(brokenUrl)
                    hasMethod(fakeMethod)
                    hasKind(kind)
                    hasStatusCode(statusCode)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(RESOURCE_DURATION_MS))
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasTraceId(null)
                    hasSpanId(null)
                    hasReplay(fakeHasReplay)
                    hasProviderType(ResourceEvent.ProviderType.FIRST_PARTY)
                    hasProviderDomain(brokenUrl)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(expectedAttributes)
                    hasSource(fakeSourceResourceEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toResourceSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
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
        argumentCaptor<ResourceEvent> {
            verify(mockWriter).write(capture())
            assertThat(firstValue)
                .apply {
                    hasId(testedScope.resourceId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasUrl(fakeUrl)
                    hasMethod(fakeMethod)
                    hasKind(kind)
                    hasStatusCode(statusCode)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(RESOURCE_DURATION_MS))
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasTraceId(fakeTraceId)
                    hasSpanId(fakeSpanId)
                    hasReplay(fakeHasReplay)
                    doesNotHaveAResourceProvider()
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(expectedAttributes)
                    hasSource(fakeSourceResourceEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toResourceSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
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
        argumentCaptor<ResourceEvent> {
            verify(mockWriter).write(capture())
            assertThat(firstValue)
                .apply {
                    hasId(testedScope.resourceId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasUrl(fakeUrl)
                    hasMethod(fakeMethod)
                    hasKind(kind)
                    hasStatusCode(statusCode)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(RESOURCE_DURATION_MS))
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasTraceId(null)
                    hasSpanId(null)
                    hasReplay(fakeHasReplay)
                    doesNotHaveAResourceProvider()
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(expectedAttributes)
                    hasSource(fakeSourceResourceEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toResourceSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
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
        argumentCaptor<ResourceEvent> {
            verify(mockWriter).write(capture())
            assertThat(firstValue)
                .apply {
                    hasId(testedScope.resourceId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasUrl(fakeUrl)
                    hasMethod(fakeMethod)
                    hasKind(kind)
                    hasStatusCode(statusCode)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(RESOURCE_DURATION_MS))
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasTraceId(null)
                    hasSpanId(null)
                    hasReplay(fakeHasReplay)
                    doesNotHaveAResourceProvider()
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceResourceEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toResourceSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
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
        argumentCaptor<ResourceEvent> {
            verify(mockWriter).write(capture())
            assertThat(firstValue)
                .apply {
                    hasKind(kind)
                    hasStatusCode(statusCode)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasLiteSessionPlan()
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceResourceEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toResourceSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
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
        argumentCaptor<Any> {
            verify(mockWriter).write(capture())
            assertThat(lastValue).isNotInstanceOf(ErrorEvent::class.java)
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
        argumentCaptor<Any> {
            verify(mockWriter).write(capture())
            assertThat(lastValue).isNotInstanceOf(ErrorEvent::class.java)
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `𝕄 send Resource with initial global attributes 𝕎 handleEvent(StopResource)`(
        @Forgery kind: RumResourceKind,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        forge: Forge
    ) {
        // Given
        val fakeGlobalAttributes = forge.aFilteredMap(excludedKeys = fakeAttributes.keys) {
            anHexadecimalString() to anAsciiString()
        }
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(fakeGlobalAttributes)
        GlobalRum.globalAttributes.putAll(fakeGlobalAttributes)
        testedScope = RumResourceScope(
            mockParentScope,
            fakeUrl,
            fakeMethod,
            fakeKey,
            fakeEventTime,
            fakeAttributes,
            fakeServerOffset,
            mockDetector,
            mockRumEventSourceProvider,
            mockContextProvider,
            mockFeaturesContextResolver
        )
        fakeGlobalAttributes.keys.forEach { GlobalRum.removeAttribute(it) }

        // When
        Thread.sleep(RESOURCE_DURATION_MS)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, emptyMap())
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        // Then
        argumentCaptor<ResourceEvent> {
            verify(mockWriter).write(capture())
            assertThat(firstValue)
                .apply {
                    hasId(testedScope.resourceId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasUrl(fakeUrl)
                    hasMethod(fakeMethod)
                    hasKind(kind)
                    hasStatusCode(statusCode)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(RESOURCE_DURATION_MS))
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasTraceId(null)
                    hasSpanId(null)
                    hasReplay(fakeHasReplay)
                    doesNotHaveAResourceProvider()
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(expectedAttributes)
                    hasSource(fakeSourceResourceEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toResourceSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                }
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
        val fakeGlobalAttributes = forge.aFilteredMap(excludedKeys = fakeAttributes.keys) {
            anHexadecimalString() to anAsciiString()
        }
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(fakeGlobalAttributes)
        GlobalRum.globalAttributes.putAll(fakeGlobalAttributes)

        // When
        Thread.sleep(RESOURCE_DURATION_MS)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, emptyMap())
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        // Then
        argumentCaptor<ResourceEvent> {
            verify(mockWriter).write(capture())
            assertThat(firstValue)
                .apply {
                    hasId(testedScope.resourceId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasUrl(fakeUrl)
                    hasMethod(fakeMethod)
                    hasKind(kind)
                    hasStatusCode(statusCode)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(RESOURCE_DURATION_MS))
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasTraceId(null)
                    hasSpanId(null)
                    hasReplay(fakeHasReplay)
                    doesNotHaveAResourceProvider()
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(expectedAttributes)
                    hasSource(fakeSourceResourceEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toResourceSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
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
        argumentCaptor<ResourceEvent> {
            verify(mockWriter).write(capture())
            assertThat(firstValue)
                .apply {
                    hasId(testedScope.resourceId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasUrl(fakeUrl)
                    hasMethod(fakeMethod)
                    hasKind(kind)
                    hasStatusCode(statusCode)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(RESOURCE_DURATION_MS))
                    hasTiming(timing)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasTraceId(null)
                    hasSpanId(null)
                    hasReplay(fakeHasReplay)
                    doesNotHaveAResourceProvider()
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(expectedAttributes)
                    hasSource(fakeSourceResourceEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toResourceSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
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
        argumentCaptor<ResourceEvent> {
            verify(mockWriter).write(capture())
            assertThat(firstValue)
                .apply {
                    hasId(testedScope.resourceId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasUrl(fakeUrl)
                    hasMethod(fakeMethod)
                    hasKind(kind)
                    hasStatusCode(statusCode)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(RESOURCE_DURATION_MS))
                    hasNoTiming()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasTraceId(null)
                    hasSpanId(null)
                    hasReplay(fakeHasReplay)
                    doesNotHaveAResourceProvider()
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(expectedAttributes)
                    hasSource(fakeSourceResourceEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toResourceSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
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
        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .apply {
                    hasMessage(message)
                    hasErrorSource(source)
                    hasStackTrace(throwable.loggableStackTrace())
                    isCrash(false)
                    hasResource(fakeUrl, fakeMethod, 0L)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasErrorType(throwable.javaClass.canonicalName)
                    hasErrorSourceType(ErrorEvent.SourceType.ANDROID)
                    hasLiteSessionPlan()
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(expectedAttributes)
                    hasSource(fakeSourceErrorEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `𝕄 send Error 𝕎 handleEvent(StopResourceWithStackTrace)`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @StringForgery stackTrace: String,
        forge: Forge
    ) {
        // Given
        val errorType = forge.aNullable { anAlphabeticalString() }
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        mockEvent = RumRawEvent.StopResourceWithStackTrace(
            fakeKey,
            null,
            message,
            source,
            stackTrace,
            errorType,
            attributes
        )

        // When
        Thread.sleep(RESOURCE_DURATION_MS)
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        // Then
        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .apply {
                    hasMessage(message)
                    hasErrorSource(source)
                    hasStackTrace(stackTrace)
                    isCrash(false)
                    hasResource(fakeUrl, fakeMethod, 0L)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasErrorType(errorType)
                    hasErrorSourceType(ErrorEvent.SourceType.ANDROID)
                    hasLiteSessionPlan()
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(expectedAttributes)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `𝕄 use the url for domain 𝕎 handleEvent(StopResourceWithError) { broken url }`(
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
            fakeServerOffset,
            mockDetector,
            mockRumEventSourceProvider,
            mockContextProvider,
            mockFeaturesContextResolver
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
        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .apply {
                    hasMessage(message)
                    hasErrorSource(source)
                    hasStackTrace(throwable.loggableStackTrace())
                    isCrash(false)
                    hasResource(brokenUrl, fakeMethod, 0L)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasProviderDomain(brokenUrl)
                    hasProviderType(ErrorEvent.ProviderType.FIRST_PARTY)
                    hasErrorType(throwable.javaClass.canonicalName)
                    hasErrorSourceType(ErrorEvent.SourceType.ANDROID)
                    hasLiteSessionPlan()
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(expectedAttributes)
                    hasSource(fakeSourceErrorEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `𝕄 use the url for domain 𝕎 handleEvent(StopResourceWithStacktrace){ broken url }`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @StringForgery stackTrace: String,
        forge: Forge
    ) {
        // Given
        val errorType = forge.aNullable { anAlphabeticalString() }
        val brokenUrl = forge.aStringMatching("[a-z]+.com/[a-z]+")
        testedScope = RumResourceScope(
            mockParentScope,
            brokenUrl,
            fakeMethod,
            fakeKey,
            fakeEventTime,
            fakeAttributes,
            fakeServerOffset,
            mockDetector,
            mockRumEventSourceProvider,
            mockContextProvider,
            mockFeaturesContextResolver
        )
        doAnswer { true }.whenever(mockDetector).isFirstPartyUrl(brokenUrl)
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        mockEvent = RumRawEvent.StopResourceWithStackTrace(
            fakeKey,
            null,
            message,
            source,
            stackTrace,
            errorType,
            attributes
        )

        // When
        Thread.sleep(RESOURCE_DURATION_MS)
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        // Then
        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .apply {
                    hasMessage(message)
                    hasErrorSource(source)
                    hasStackTrace(stackTrace)
                    isCrash(false)
                    hasResource(brokenUrl, fakeMethod, 0L)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasProviderDomain(brokenUrl)
                    hasProviderType(ErrorEvent.ProviderType.FIRST_PARTY)
                    hasErrorType(errorType)
                    hasErrorSourceType(ErrorEvent.SourceType.ANDROID)
                    hasLiteSessionPlan()
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(expectedAttributes)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
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
        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .apply {
                    hasMessage(message)
                    hasErrorSource(source)
                    hasStackTrace(throwable.loggableStackTrace())
                    isCrash(false)
                    hasResource(fakeUrl, fakeMethod, 0L)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasProviderDomain(URL(fakeUrl).host)
                    hasProviderType(ErrorEvent.ProviderType.FIRST_PARTY)
                    hasErrorType(throwable.javaClass.canonicalName)
                    hasErrorSourceType(ErrorEvent.SourceType.ANDROID)
                    hasLiteSessionPlan()
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(expectedAttributes)
                    hasSource(fakeSourceErrorEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `𝕄 add first party type provider to Error 𝕎 handleEvent(StopResourceWithStackTrace)`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @StringForgery stackTrace: String,
        forge: Forge
    ) {
        // Given
        doAnswer { true }.whenever(mockDetector).isFirstPartyUrl(fakeUrl)
        val errorType = forge.aNullable { anAlphabeticalString() }
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        mockEvent = RumRawEvent.StopResourceWithStackTrace(
            fakeKey,
            null,
            message,
            source,
            stackTrace,
            errorType,
            attributes
        )

        // When
        Thread.sleep(RESOURCE_DURATION_MS)
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        // Then
        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .apply {
                    hasMessage(message)
                    hasErrorSource(source)
                    hasStackTrace(stackTrace)
                    isCrash(false)
                    hasResource(fakeUrl, fakeMethod, 0L)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasProviderDomain(URL(fakeUrl).host)
                    hasProviderType(ErrorEvent.ProviderType.FIRST_PARTY)
                    hasErrorType(errorType)
                    hasErrorSourceType(ErrorEvent.SourceType.ANDROID)
                    hasLiteSessionPlan()
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(expectedAttributes)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
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
        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .apply {
                    hasMessage(message)
                    hasErrorSource(source)
                    hasStackTrace(throwable.loggableStackTrace())
                    isCrash(false)
                    hasResource(fakeUrl, fakeMethod, 0L)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasErrorType(throwable.javaClass.canonicalName)
                    hasErrorSourceType(ErrorEvent.SourceType.ANDROID)
                    doesNotHaveAResourceProvider()
                    hasLiteSessionPlan()
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(expectedAttributes)
                    hasSource(fakeSourceErrorEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `𝕄 send Error with initial context 𝕎 handleEvent(StopResourceWithStackTrace)`(
        @Forgery context: RumContext,
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @StringForgery stackTrace: String,
        forge: Forge
    ) {
        // Given
        val errorType = forge.aNullable { anAlphabeticalString() }
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        mockEvent = RumRawEvent.StopResourceWithStackTrace(
            fakeKey,
            null,
            message,
            source,
            stackTrace,
            errorType,
            attributes
        )
        whenever(mockParentScope.getRumContext()) doReturn context

        // When
        Thread.sleep(RESOURCE_DURATION_MS)
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        // Then
        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .apply {
                    hasMessage(message)
                    hasErrorSource(source)
                    hasStackTrace(stackTrace)
                    isCrash(false)
                    hasResource(fakeUrl, fakeMethod, 0L)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasErrorType(errorType)
                    hasErrorSourceType(ErrorEvent.SourceType.ANDROID)
                    doesNotHaveAResourceProvider()
                    hasLiteSessionPlan()
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(expectedAttributes)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
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
        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .apply {
                    hasMessage(message)
                    hasErrorSource(source)
                    hasStackTrace(throwable.loggableStackTrace())
                    isCrash(false)
                    hasResource(fakeUrl, fakeMethod, statusCode)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasErrorType(throwable.javaClass.canonicalName)
                    hasErrorSourceType(ErrorEvent.SourceType.ANDROID)
                    doesNotHaveAResourceProvider()
                    hasLiteSessionPlan()
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(expectedAttributes)
                    hasSource(fakeSourceErrorEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `𝕄 send Error with global attributes 𝕎 handleEvent(StopResourceWithStackTrace)`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @LongForgery(200, 600) statusCode: Long,
        @StringForgery stackTrace: String,
        forge: Forge
    ) {
        // Given
        val errorType = forge.aNullable { anAlphabeticalString() }
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
        mockEvent = RumRawEvent.StopResourceWithStackTrace(
            fakeKey,
            statusCode,
            message,
            source,
            stackTrace,
            errorType,
            errorAttributes
        )

        // When
        Thread.sleep(RESOURCE_DURATION_MS)
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        // Then
        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .apply {
                    hasMessage(message)
                    hasErrorSource(source)
                    hasStackTrace(stackTrace)
                    isCrash(false)
                    hasResource(fakeUrl, fakeMethod, statusCode)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasErrorType(errorType)
                    hasErrorSourceType(ErrorEvent.SourceType.ANDROID)
                    doesNotHaveAResourceProvider()
                    hasLiteSessionPlan()
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(expectedAttributes)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
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
    fun `𝕄 do nothing 𝕎 handleEvent(StopResourceWithStackTrace) with different key`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @LongForgery(200, 600) statusCode: Long,
        @StringForgery stackTrace: String,
        forge: Forge
    ) {
        val errorType = forge.aNullable { anAlphabeticalString() }
        mockEvent = RumRawEvent.StopResourceWithStackTrace(
            "not_the_$fakeKey",
            statusCode,
            message,
            source,
            stackTrace,
            errorType,
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

        argumentCaptor<ResourceEvent> {
            verify(mockWriter).write(capture())
            assertThat(firstValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp())
                    hasUrl(fakeUrl)
                    hasMethod(fakeMethod)
                    hasKind(kind)
                    hasStatusCode(statusCode)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(RESOURCE_DURATION_MS))
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    doesNotHaveAResourceProvider()
                    hasLiteSessionPlan()
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(expectedAttributes)
                    hasSource(fakeSourceResourceEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toResourceSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
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

        argumentCaptor<ResourceEvent> {
            verify(mockWriter).write(capture())
            assertThat(firstValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp())
                    hasUrl(fakeUrl)
                    hasMethod(fakeMethod)
                    hasKind(kind)
                    hasStatusCode(statusCode)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(RESOURCE_DURATION_MS))
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    doesNotHaveAResourceProvider()
                    hasLiteSessionPlan()
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(expectedAttributes)
                    hasSource(fakeSourceResourceEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toResourceSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
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

        argumentCaptor<ResourceEvent> {
            verify(mockWriter).write(capture())
            assertThat(firstValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp())
                    hasUrl(fakeUrl)
                    hasMethod(fakeMethod)
                    hasKind(kind)
                    hasStatusCode(statusCode)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(RESOURCE_DURATION_MS))
                    hasTiming(timing)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    doesNotHaveAResourceProvider()
                    hasLiteSessionPlan()
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(expectedAttributes)
                    hasSource(fakeSourceResourceEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toResourceSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
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
        argumentCaptor<ResourceEvent> {
            verify(mockWriter).write(capture())
            assertThat(firstValue).hasTiming(timing)
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
        argumentCaptor<ResourceEvent> {
            verify(mockWriter).write(capture())
            assertThat(firstValue).hasTiming(timing)
        }
    }

    @Test
    fun `M update the duration to 1ns W handleEvent { computed duration less than 0 }`(
        forge: Forge
    ) {
        val fakeStatusCode = forge.aNullable { forge.aLong() }
        val fakeSize = forge.aNullable { forge.aLong() }
        val fakeKind = forge.aValueFrom(RumResourceKind::class.java)
        val fakeStopEvent = RumRawEvent.StopResource(
            fakeKey,
            fakeStatusCode,
            fakeSize,
            fakeKind,
            emptyMap(),
            Time(0, 0)
        )
        // Given
        val result = testedScope.handleEvent(fakeStopEvent, mockWriter)

        // Then
        argumentCaptor<ResourceEvent> {
            verify(mockWriter).write(capture())
            assertThat(firstValue)
                .apply {
                    hasId(testedScope.resourceId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasDuration(1)
                    hasUrl(fakeUrl)
                    hasMethod(fakeMethod)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    doesNotHaveAResourceProvider()
                    hasLiteSessionPlan()
                    hasReplay(fakeHasReplay)
                    hasSource(fakeSourceResourceEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toResourceSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verify(loggerTestConfiguration.mockDevLogHandler).handleLog(
            Log.WARN,
            RumResourceScope.NEGATIVE_DURATION_WARNING_MESSAGE.format(Locale.US, fakeUrl)
        )
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `M update the duration to 1ns W handleEvent { computed duration equal to 0 }`(
        forge: Forge
    ) {
        val fakeStatusCode = forge.aNullable { forge.aLong() }
        val fakeSize = forge.aNullable { forge.aLong() }
        val fakeKind = forge.aValueFrom(RumResourceKind::class.java)
        val fakeStopEvent = RumRawEvent.StopResource(
            fakeKey,
            fakeStatusCode,
            fakeSize,
            fakeKind,
            emptyMap(),
            fakeEventTime
        )
        // Given
        val result = testedScope.handleEvent(fakeStopEvent, mockWriter)

        // Then
        argumentCaptor<ResourceEvent> {
            verify(mockWriter).write(capture())
            assertThat(firstValue)
                .apply {
                    hasId(testedScope.resourceId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasDuration(1)
                    hasUrl(fakeUrl)
                    hasMethod(fakeMethod)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    doesNotHaveAResourceProvider()
                    hasLiteSessionPlan()
                    hasReplay(fakeHasReplay)
                    hasSource(fakeSourceResourceEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toResourceSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verify(loggerTestConfiguration.mockDevLogHandler).handleLog(
            Log.WARN,
            RumResourceScope.NEGATIVE_DURATION_WARNING_MESSAGE.format(Locale.US, fakeUrl)
        )
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    // region Internal

    private fun mockEvent(): RumRawEvent {
        val event: RumRawEvent = mock()
        whenever(event.eventTime) doReturn Time()
        return event
    }

    private fun resolveExpectedTimestamp(): Long {
        return fakeEventTime.timestamp + fakeServerOffset
    }

    // endregion

    companion object {
        private const val RESOURCE_DURATION_MS = 50L

        val rumMonitor = GlobalRumMonitorTestConfiguration()
        val loggerTestConfiguration = LoggerTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(rumMonitor, loggerTestConfiguration)
        }
    }
}
