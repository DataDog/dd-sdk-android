/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.context.NetworkInfo
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.core.internal.net.FirstPartyHostHeaderTypeResolver
import com.datadog.android.internal.utils.loggableStackTrace
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.RumResourceMethod
import com.datadog.android.rum.assertj.ErrorEventAssert.Companion.assertThat
import com.datadog.android.rum.assertj.ResourceEventAssert.Companion.assertThat
import com.datadog.android.rum.internal.FeaturesContextResolver
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.domain.event.ResourceTiming
import com.datadog.android.rum.internal.metric.networksettled.InternalResourceContext
import com.datadog.android.rum.internal.metric.networksettled.NetworkSettledMetricResolver
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.internal.monitor.StorageEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.resource.ResourceId
import com.datadog.android.rum.utils.asTimingsPayload
import com.datadog.android.rum.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.rum.utils.verifyLog
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.forge.aFilteredMap
import com.datadog.tools.unit.forge.anException
import com.datadog.tools.unit.forge.exhaustiveAttributes
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atMost
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.net.URL
import java.util.Locale
import java.util.concurrent.TimeUnit

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
    lateinit var mockResolver: FirstPartyHostHeaderTypeResolver

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockRumFeatureScope: FeatureScope

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    @StringForgery(regex = "http(s?)://[a-z]+\\.com/[a-z]+")
    lateinit var fakeUrl: String

    @Forgery
    lateinit var fakeKey: ResourceId

    @Forgery
    lateinit var fakeMethod: RumResourceMethod
    lateinit var fakeAttributes: Map<String, Any?>

    @Forgery
    lateinit var fakeParentContext: RumContext

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @Forgery
    lateinit var fakeNetworkInfoAtScopeStart: NetworkInfo

    var fakeServerOffset: Long = 0L
    var fakeSampleRate: Float = 0.0f

    private lateinit var fakeEventTime: Time
    var fakeSourceResourceEvent: ResourceEvent.ResourceEventSource? = null
    var fakeSourceErrorEvent: ErrorEvent.ErrorEventSource? = null

    @BoolForgery
    var fakeHasReplay: Boolean = false

    @Mock
    lateinit var mockFeaturesContextResolver: FeaturesContextResolver

    @Mock
    lateinit var mockNetworkSettledMetricResolver: NetworkSettledMetricResolver

    @BeforeEach
    fun `set up`(forge: Forge) {
        val isValidSource = forge.aBool()

        val fakeSource = if (isValidSource) {
            forge.anElementFrom(
                ErrorEvent.ErrorEventSource.values().map { it.toJson().asString }
            )
        } else {
            forge.anAlphabeticalString()
        }

        fakeDatadogContext = fakeDatadogContext.copy(
            source = fakeSource
        )

        fakeParentContext = fakeParentContext.copy(syntheticsTestId = null, syntheticsResultId = null)

        fakeSourceResourceEvent = if (isValidSource) {
            ResourceEvent.ResourceEventSource.fromJson(fakeSource)
        } else {
            null
        }

        fakeSourceErrorEvent = if (isValidSource) {
            ErrorEvent.ErrorEventSource.fromJson(fakeSource)
        } else {
            null
        }

        fakeEventTime = Time()
        val maxLimit = Long.MAX_VALUE - fakeEventTime.timestamp
        val minLimit = -fakeEventTime.timestamp
        fakeServerOffset =
            forge.aLong(min = minLimit, max = maxLimit)
        fakeAttributes = forge.exhaustiveAttributes()
        mockEvent = mockEvent()
        fakeSampleRate = forge.aFloat(min = 0.0f, max = 100.0f)

        whenever(rumMonitor.mockSdkCore.networkInfo) doReturn fakeNetworkInfoAtScopeStart
        whenever(rumMonitor.mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockParentScope.getRumContext()) doReturn fakeParentContext
        doAnswer { false }.whenever(mockResolver).isFirstPartyUrl(any<String>())
        whenever(
            mockFeaturesContextResolver.resolveViewHasReplay(
                fakeDatadogContext,
                fakeParentContext.viewId.orEmpty()
            )
        ).thenReturn(fakeHasReplay)
        whenever(rumMonitor.mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope
        whenever(mockRumFeatureScope.withWriteContext(any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventBatchWriter) -> Unit>(0)
            callback.invoke(fakeDatadogContext, mockEventBatchWriter)
        }
        whenever(mockWriter.write(eq(mockEventBatchWriter), any(), eq(EventType.DEFAULT))) doReturn true

        testedScope = RumResourceScope(
            mockParentScope,
            rumMonitor.mockSdkCore,
            fakeUrl,
            fakeMethod,
            fakeKey,
            fakeEventTime,
            fakeAttributes,
            fakeServerOffset,
            mockResolver,
            mockFeaturesContextResolver,
            fakeSampleRate,
            mockNetworkSettledMetricResolver
        )
    }

    @Test
    fun `M notify the NetworkSettledMetricsResolver W initialized()`() {
        // Then
        verify(mockNetworkSettledMetricResolver).resourceWasStarted(
            InternalResourceContext(testedScope.resourceId, fakeEventTime.nanoTime)
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
    fun `M send Resource event W handleEvent(StopResource)`(
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
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
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
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasTraceId(null)
                    hasSpanId(null)
                    hasReplay(fakeHasReplay)
                    hasRulePsr(null)
                    doesNotHaveAResourceProvider()
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
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
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `M add first party type provider to Resource W handleEvent(StopResource)`(
        @Forgery kind: RumResourceKind,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        forge: Forge
    ) {
        // Given
        doAnswer { true }.whenever(mockResolver).isFirstPartyUrl(fakeUrl)
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
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
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
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasTraceId(null)
                    hasSpanId(null)
                    hasReplay(fakeHasReplay)
                    hasRulePsr(null)
                    hasProviderType(ResourceEvent.ProviderType.FIRST_PARTY)
                    hasProviderDomain(URL(fakeUrl).host)
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasUserSession()
                    hasNoSyntheticsTest()
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
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `M use the url for provider domain W handleEvent(StopResource) { url is broken }`(
        @Forgery kind: RumResourceKind,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        forge: Forge
    ) {
        // Given
        val brokenUrl = forge.aStringMatching("[a-z]+.com/[a-z]+")
        testedScope = RumResourceScope(
            mockParentScope,
            rumMonitor.mockSdkCore,
            brokenUrl,
            fakeMethod,
            fakeKey,
            fakeEventTime,
            fakeAttributes,
            fakeServerOffset,
            mockResolver,
            mockFeaturesContextResolver,
            fakeSampleRate,
            mockNetworkSettledMetricResolver
        )
        doAnswer { true }.whenever(mockResolver).isFirstPartyUrl(brokenUrl)
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
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
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
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasTraceId(null)
                    hasSpanId(null)
                    hasReplay(fakeHasReplay)
                    hasRulePsr(null)
                    hasProviderType(ResourceEvent.ProviderType.FIRST_PARTY)
                    hasProviderDomain(brokenUrl)
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasUserSession()
                    hasNoSyntheticsTest()
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
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `M send Resource with trace info W handleEvent(StopResource)`(
        @Forgery kind: RumResourceKind,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        @StringForgery(type = StringForgeryType.HEXADECIMAL) fakeSpanId: String,
        @StringForgery(type = StringForgeryType.HEXADECIMAL) fakeTraceId: String,
        @FloatForgery(0f, 1f) fakeRulePsr: Float,
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
        attributes[RumAttributes.RULE_PSR] = fakeRulePsr

        // When
        Thread.sleep(RESOURCE_DURATION_MS)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        // Then
        argumentCaptor<ResourceEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
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
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasTraceId(fakeTraceId)
                    hasSpanId(fakeSpanId)
                    hasReplay(fakeHasReplay)
                    hasRulePsr(fakeRulePsr)
                    doesNotHaveAResourceProvider()
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
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
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `M send Resource with initial context W handleEvent(StopResource)`(
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
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
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
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasTraceId(null)
                    hasSpanId(null)
                    hasReplay(fakeHasReplay)
                    hasRulePsr(null)
                    doesNotHaveAResourceProvider()
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
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
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `M send Resource with synthetics info context W handleEvent(StopResource)`(
        @StringForgery fakeTestId: String,
        @StringForgery fakeResultId: String,
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
        fakeParentContext = fakeParentContext.copy(
            syntheticsTestId = fakeTestId,
            syntheticsResultId = fakeResultId
        )
        whenever(mockParentScope.getRumContext()) doReturn fakeParentContext
        testedScope = RumResourceScope(
            mockParentScope,
            rumMonitor.mockSdkCore,
            fakeUrl,
            fakeMethod,
            fakeKey,
            fakeEventTime,
            fakeAttributes,
            fakeServerOffset,
            mockResolver,
            mockFeaturesContextResolver,
            fakeSampleRate,
            mockNetworkSettledMetricResolver
        )

        // When
        Thread.sleep(RESOURCE_DURATION_MS)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        // Then
        argumentCaptor<ResourceEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
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
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasTraceId(null)
                    hasSpanId(null)
                    hasReplay(fakeHasReplay)
                    hasRulePsr(null)
                    doesNotHaveAResourceProvider()
                    hasSyntheticsSession()
                    hasSyntheticsTest(fakeTestId, fakeResultId)
                    hasStartReason(fakeParentContext.sessionStartReason)
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
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `M send event with user extra attributes W handleEvent(StopResource)`(
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
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
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
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasTraceId(null)
                    hasSpanId(null)
                    hasReplay(fakeHasReplay)
                    hasRulePsr(null)
                    doesNotHaveAResourceProvider()
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
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
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `M do not send error event W handleEvent(StopResource with error statusCode)`(
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
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(firstValue)
                .apply {
                    hasKind(kind)
                    hasStatusCode(statusCode)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasUserSession()
                    hasNoSyntheticsTest()
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
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `M not send related error event W handleEvent(StopResource with success statusCode)`(
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
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue).isNotInstanceOf(ErrorEvent::class.java)
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `M not send related error event W handleEvent(StopResource with missing statusCode)`(
        @Forgery kind: RumResourceKind,
        @LongForgery(0, 1024) size: Long
    ) {
        // When
        Thread.sleep(RESOURCE_DURATION_MS)
        mockEvent = RumRawEvent.StopResource(fakeKey, null, size, kind, emptyMap())
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        // Then
        argumentCaptor<Any> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue).isNotInstanceOf(ErrorEvent::class.java)
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `M send Resource with initial global attributes W handleEvent(StopResource)`(
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
        whenever(rumMonitor.mockInstance.getAttributes()) doReturn fakeGlobalAttributes
        testedScope = RumResourceScope(
            mockParentScope,
            rumMonitor.mockSdkCore,
            fakeUrl,
            fakeMethod,
            fakeKey,
            fakeEventTime,
            fakeAttributes,
            fakeServerOffset,
            mockResolver,
            mockFeaturesContextResolver,
            fakeSampleRate,
            mockNetworkSettledMetricResolver
        )
        whenever(rumMonitor.mockInstance.getAttributes()) doReturn emptyMap()

        // When
        Thread.sleep(RESOURCE_DURATION_MS)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, emptyMap())
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        // Then
        argumentCaptor<ResourceEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
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
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasTraceId(null)
                    hasSpanId(null)
                    hasReplay(fakeHasReplay)
                    hasRulePsr(null)
                    doesNotHaveAResourceProvider()
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
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
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `M send Resource with global attributes W handleEvent(StopResource)`(
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
        whenever(rumMonitor.mockInstance.getAttributes()) doReturn fakeGlobalAttributes

        // When
        Thread.sleep(RESOURCE_DURATION_MS)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, emptyMap())
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        // Then
        argumentCaptor<ResourceEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
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
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasTraceId(null)
                    hasSpanId(null)
                    hasReplay(fakeHasReplay)
                    hasRulePsr(null)
                    doesNotHaveAResourceProvider()
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
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
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `M send Resource with timing W handleEvent(AddResourceTiming+StopResource)`(
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
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
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
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasTraceId(null)
                    hasSpanId(null)
                    hasReplay(fakeHasReplay)
                    hasRulePsr(null)
                    doesNotHaveAResourceProvider()
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
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
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(resultTiming).isEqualTo(testedScope)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `M send Resource W handleEvent(AddResourceTiming+StopResource) {unrelated timing}`(
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
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
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
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasTraceId(null)
                    hasSpanId(null)
                    hasReplay(fakeHasReplay)
                    hasRulePsr(null)
                    doesNotHaveAResourceProvider()
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
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
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(resultTiming).isEqualTo(testedScope)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `M send Error W handleEvent(StopResourceWithError)`(
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
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasMessage(message)
                    hasErrorSource(source)
                    hasStackTrace(throwable.loggableStackTrace())
                    isCrash(false)
                    hasResource(fakeUrl, fakeMethod, 0L)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasErrorType(throwable.javaClass.canonicalName)
                    hasErrorSourceType(ErrorEvent.SourceType.ANDROID)
                    hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                    hasTimeSinceAppStart(null)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
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
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                    hasBuildId(fakeDatadogContext.appBuildId)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `M send Error with fingerprint W handleEvent(StopResourceWithError) { contains fingerprint }`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @StringForgery fakeFingerprint: String,
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)

        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        // Expected attributes should not have the ERROR_FINGERPRINT attribute so add it after
        attributes[RumAttributes.ERROR_FINGERPRINT] = fakeFingerprint

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
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasMessage(message)
                    hasErrorSource(source)
                    hasStackTrace(throwable.loggableStackTrace())
                    isCrash(false)
                    hasResource(fakeUrl, fakeMethod, 0L)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasErrorType(throwable.javaClass.canonicalName)
                    hasErrorSourceType(ErrorEvent.SourceType.ANDROID)
                    hasUserSession()
                    hasErrorFingerprint(fakeFingerprint)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
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
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `M send Error W handleEvent(StopResourceWithStackTrace)`(
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
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasMessage(message)
                    hasErrorSource(source)
                    hasStackTrace(stackTrace)
                    isCrash(false)
                    hasResource(fakeUrl, fakeMethod, 0L)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasErrorType(errorType)
                    hasErrorSourceType(ErrorEvent.SourceType.ANDROID)
                    hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                    hasTimeSinceAppStart(null)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
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
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                    hasBuildId(fakeDatadogContext.appBuildId)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `M send Error with synthetics info W handleEvent(StopResourceWithError)`(
        @StringForgery fakeTestId: String,
        @StringForgery fakeResultId: String,
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
        fakeParentContext = fakeParentContext.copy(
            syntheticsTestId = fakeTestId,
            syntheticsResultId = fakeResultId
        )
        whenever(mockParentScope.getRumContext()) doReturn fakeParentContext
        testedScope = RumResourceScope(
            mockParentScope,
            rumMonitor.mockSdkCore,
            fakeUrl,
            fakeMethod,
            fakeKey,
            fakeEventTime,
            fakeAttributes,
            fakeServerOffset,
            mockResolver,
            mockFeaturesContextResolver,
            fakeSampleRate,
            mockNetworkSettledMetricResolver
        )

        // When
        Thread.sleep(RESOURCE_DURATION_MS)
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        // Then
        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasMessage(message)
                    hasErrorSource(source)
                    hasStackTrace(throwable.loggableStackTrace())
                    isCrash(false)
                    hasResource(fakeUrl, fakeMethod, 0L)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasErrorType(throwable.javaClass.canonicalName)
                    hasErrorSourceType(ErrorEvent.SourceType.ANDROID)
                    hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                    hasTimeSinceAppStart(null)
                    hasSyntheticsSession()
                    hasSyntheticsTest(fakeTestId, fakeResultId)
                    hasStartReason(fakeParentContext.sessionStartReason)
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
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                    hasBuildId(fakeDatadogContext.appBuildId)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `M send Error with synthetics info W handleEvent(StopResourceWithStackTrace)`(
        @StringForgery fakeTestId: String,
        @StringForgery fakeResultId: String,
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
        fakeParentContext = fakeParentContext.copy(
            syntheticsTestId = fakeTestId,
            syntheticsResultId = fakeResultId
        )
        whenever(mockParentScope.getRumContext()) doReturn fakeParentContext
        mockEvent = RumRawEvent.StopResourceWithStackTrace(
            fakeKey,
            null,
            message,
            source,
            stackTrace,
            errorType,
            attributes
        )
        testedScope = RumResourceScope(
            mockParentScope,
            rumMonitor.mockSdkCore,
            fakeUrl,
            fakeMethod,
            fakeKey,
            fakeEventTime,
            fakeAttributes,
            fakeServerOffset,
            mockResolver,
            mockFeaturesContextResolver,
            fakeSampleRate,
            mockNetworkSettledMetricResolver
        )

        // When
        Thread.sleep(RESOURCE_DURATION_MS)
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        // Then
        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasMessage(message)
                    hasErrorSource(source)
                    hasStackTrace(stackTrace)
                    isCrash(false)
                    hasResource(fakeUrl, fakeMethod, 0L)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasErrorType(errorType)
                    hasErrorSourceType(ErrorEvent.SourceType.ANDROID)
                    hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                    hasTimeSinceAppStart(null)
                    hasSyntheticsSession()
                    hasSyntheticsTest(fakeTestId, fakeResultId)
                    hasStartReason(fakeParentContext.sessionStartReason)
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
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                    hasBuildId(fakeDatadogContext.appBuildId)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `M use the url for domain W handleEvent(StopResourceWithError) { broken url }`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        forge: Forge
    ) {
        // Given
        val brokenUrl = forge.aStringMatching("[a-z]+.com/[a-z]+")
        testedScope = RumResourceScope(
            mockParentScope,
            rumMonitor.mockSdkCore,
            brokenUrl,
            fakeMethod,
            fakeKey,
            fakeEventTime,
            fakeAttributes,
            fakeServerOffset,
            mockResolver,
            mockFeaturesContextResolver,
            fakeSampleRate,
            mockNetworkSettledMetricResolver
        )
        doAnswer { true }.whenever(mockResolver).isFirstPartyUrl(brokenUrl)
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
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasMessage(message)
                    hasErrorSource(source)
                    hasStackTrace(throwable.loggableStackTrace())
                    isCrash(false)
                    hasResource(brokenUrl, fakeMethod, 0L)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasProviderDomain(brokenUrl)
                    hasProviderType(ErrorEvent.ProviderType.FIRST_PARTY)
                    hasErrorType(throwable.javaClass.canonicalName)
                    hasErrorSourceType(ErrorEvent.SourceType.ANDROID)
                    hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                    hasTimeSinceAppStart(null)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
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
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                    hasBuildId(fakeDatadogContext.appBuildId)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `M use the url for domain W handleEvent(StopResourceWithStacktrace){ broken url }`(
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
            rumMonitor.mockSdkCore,
            brokenUrl,
            fakeMethod,
            fakeKey,
            fakeEventTime,
            fakeAttributes,
            fakeServerOffset,
            mockResolver,
            mockFeaturesContextResolver,
            fakeSampleRate,
            mockNetworkSettledMetricResolver
        )
        doAnswer { true }.whenever(mockResolver).isFirstPartyUrl(brokenUrl)
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
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasMessage(message)
                    hasErrorSource(source)
                    hasStackTrace(stackTrace)
                    isCrash(false)
                    hasResource(brokenUrl, fakeMethod, 0L)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasProviderDomain(brokenUrl)
                    hasProviderType(ErrorEvent.ProviderType.FIRST_PARTY)
                    hasErrorType(errorType)
                    hasErrorSourceType(ErrorEvent.SourceType.ANDROID)
                    hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                    hasTimeSinceAppStart(null)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
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
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                    hasBuildId(fakeDatadogContext.appBuildId)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `M add first party type provider to Error W handleEvent(StopResourceWithError)`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        forge: Forge
    ) {
        // Given
        doAnswer { true }.whenever(mockResolver).isFirstPartyUrl(fakeUrl)

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
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasMessage(message)
                    hasErrorSource(source)
                    hasStackTrace(throwable.loggableStackTrace())
                    isCrash(false)
                    hasResource(fakeUrl, fakeMethod, 0L)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasProviderDomain(URL(fakeUrl).host)
                    hasProviderType(ErrorEvent.ProviderType.FIRST_PARTY)
                    hasErrorType(throwable.javaClass.canonicalName)
                    hasErrorSourceType(ErrorEvent.SourceType.ANDROID)
                    hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                    hasTimeSinceAppStart(null)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
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
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                    hasBuildId(fakeDatadogContext.appBuildId)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `M add first party type provider to Error W handleEvent(StopResourceWithStackTrace)`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @StringForgery stackTrace: String,
        forge: Forge
    ) {
        // Given
        doAnswer { true }.whenever(mockResolver).isFirstPartyUrl(fakeUrl)
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
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasMessage(message)
                    hasErrorSource(source)
                    hasStackTrace(stackTrace)
                    isCrash(false)
                    hasResource(fakeUrl, fakeMethod, 0L)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasProviderDomain(URL(fakeUrl).host)
                    hasProviderType(ErrorEvent.ProviderType.FIRST_PARTY)
                    hasErrorType(errorType)
                    hasErrorSourceType(ErrorEvent.SourceType.ANDROID)
                    hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                    hasTimeSinceAppStart(null)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
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
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                    hasBuildId(fakeDatadogContext.appBuildId)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `M send Error with initial context W handleEvent(StopResourceWithError)`(
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
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasMessage(message)
                    hasErrorSource(source)
                    hasStackTrace(throwable.loggableStackTrace())
                    isCrash(false)
                    hasResource(fakeUrl, fakeMethod, 0L)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasErrorType(throwable.javaClass.canonicalName)
                    hasErrorSourceType(ErrorEvent.SourceType.ANDROID)
                    hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                    hasTimeSinceAppStart(null)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    doesNotHaveAResourceProvider()
                    hasStartReason(fakeParentContext.sessionStartReason)
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
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                    hasBuildId(fakeDatadogContext.appBuildId)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `M send Error with initial context W handleEvent(StopResourceWithStackTrace)`(
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
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasMessage(message)
                    hasErrorSource(source)
                    hasStackTrace(stackTrace)
                    isCrash(false)
                    hasResource(fakeUrl, fakeMethod, 0L)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasErrorType(errorType)
                    hasErrorSourceType(ErrorEvent.SourceType.ANDROID)
                    hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                    hasTimeSinceAppStart(null)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    doesNotHaveAResourceProvider()
                    hasStartReason(fakeParentContext.sessionStartReason)
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
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                    hasBuildId(fakeDatadogContext.appBuildId)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `M send Error with global attributes W handleEvent(StopResourceWithError)`(
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

        whenever(rumMonitor.mockInstance.getAttributes()) doReturn attributes
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
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasMessage(message)
                    hasErrorSource(source)
                    hasStackTrace(throwable.loggableStackTrace())
                    isCrash(false)
                    hasResource(fakeUrl, fakeMethod, statusCode)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasErrorType(throwable.javaClass.canonicalName)
                    hasErrorSourceType(ErrorEvent.SourceType.ANDROID)
                    hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                    hasTimeSinceAppStart(null)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    doesNotHaveAResourceProvider()
                    hasStartReason(fakeParentContext.sessionStartReason)
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
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                    hasBuildId(fakeDatadogContext.appBuildId)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `M send Error with global attributes W handleEvent(StopResourceWithStackTrace)`(
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
        whenever(rumMonitor.mockInstance.getAttributes()) doReturn attributes
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
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasMessage(message)
                    hasErrorSource(source)
                    hasStackTrace(stackTrace)
                    isCrash(false)
                    hasResource(fakeUrl, fakeMethod, statusCode)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasErrorType(errorType)
                    hasErrorSourceType(ErrorEvent.SourceType.ANDROID)
                    hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                    hasTimeSinceAppStart(null)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    doesNotHaveAResourceProvider()
                    hasStartReason(fakeParentContext.sessionStartReason)
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
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                    hasBuildId(fakeDatadogContext.appBuildId)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `M do nothing W handleEvent(StopResource) with different key`(
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
        verify(mockNetworkSettledMetricResolver, never()).resourceWasStopped(
            InternalResourceContext(testedScope.resourceId, mockEvent.eventTime.nanoTime)
        )
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M do nothing W handleEvent(StopResourceWithError) with different key`(
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
        verify(mockNetworkSettledMetricResolver, never()).resourceWasStopped(
            InternalResourceContext(testedScope.resourceId, mockEvent.eventTime.nanoTime)
        )
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M do nothing W handleEvent(StopResourceWithStackTrace) with different key`(
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
        verify(mockNetworkSettledMetricResolver, never()).resourceWasStopped(
            InternalResourceContext(testedScope.resourceId, mockEvent.eventTime.nanoTime)
        )
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M do nothing W handleEvent(WaitForResourceTiming+StopResource)`(
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
        verify(mockNetworkSettledMetricResolver, never()).resourceWasStopped(
            InternalResourceContext(testedScope.resourceId, mockEvent.eventTime.nanoTime)
        )
        assertThat(resultStop).isSameAs(testedScope)
    }

    @Test
    fun `M send Resource W handleEvent(WaitForResourceTiming+StopResource) {unrelated wait}`(
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
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(firstValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp())
                    hasUrl(fakeUrl)
                    hasMethod(fakeMethod)
                    hasKind(kind)
                    hasStatusCode(statusCode)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(RESOURCE_DURATION_MS))
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    doesNotHaveAResourceProvider()
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
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
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        verify(mockParentScope, never()).handleEvent(any(), any())
        assertThat(resultWaitForTiming).isSameAs(testedScope)
        assertThat(resultStop).isEqualTo(null)
    }

    @Test
    fun `M send Resource W handleEvent(WaitForResourceTiming+AddResourceTiming+StopResource)`(
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
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(firstValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp())
                    hasUrl(fakeUrl)
                    hasMethod(fakeMethod)
                    hasKind(kind)
                    hasStatusCode(statusCode)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(RESOURCE_DURATION_MS))
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    doesNotHaveAResourceProvider()
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
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
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        verify(mockParentScope, never()).handleEvent(any(), any())
        assertThat(resultWaitForTiming).isEqualTo(testedScope)
        assertThat(resultTiming).isEqualTo(testedScope)
        assertThat(resultStop).isEqualTo(null)
    }

    @Test
    fun `M send Resource W handleEvent(WaitForResourceTiming+StopResource+AddResourceTiming)`(
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
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
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
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    doesNotHaveAResourceProvider()
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
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
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        verify(mockParentScope, never()).handleEvent(any(), any())
        assertThat(resultWaitForTiming).isEqualTo(testedScope)
        assertThat(resultStop).isEqualTo(testedScope)
        assertThat(resultTiming).isEqualTo(null)
    }

    @Test
    fun `M use explicit timings W handleEvent { AddResourceTiming + StopResource }`(
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
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(firstValue).hasTiming(timing)
        }
    }

    @Test
    fun `M use attributes timings W handleEvent { StopResource without AddResourceTiming  }`(
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
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
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
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(firstValue)
                .apply {
                    hasId(testedScope.resourceId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasDuration(1)
                    hasUrl(fakeUrl)
                    hasMethod(fakeMethod)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    doesNotHaveAResourceProvider()
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
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
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
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
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(firstValue)
                .apply {
                    hasId(testedScope.resourceId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasDuration(1)
                    hasUrl(fakeUrl)
                    hasMethod(fakeMethod)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    doesNotHaveAResourceProvider()
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
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
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            RumResourceScope.NEGATIVE_DURATION_WARNING_MESSAGE.format(Locale.US, fakeUrl)
        )
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `M use graphql attributes W handleEvent`(
        @Forgery kind: RumResourceKind,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        forge: Forge
    ) {
        // Given
        val operationType = forge.aValueFrom(ResourceEvent.OperationType::class.java)
        val operationName = forge.aNullable { aString() }
        val payload = forge.aNullable { aString() }
        val variables = forge.aNullable { aString() }
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys) +
            mapOf(
                "_dd.graphql.operation_type" to operationType.toString(),
                "_dd.graphql.operation_name" to operationName,
                "_dd.graphql.payload" to payload,
                "_dd.graphql.variables" to variables
            )

        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)

        // When
        testedScope.handleEvent(mockEvent, mockWriter)

        // Then
        argumentCaptor<ResourceEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(firstValue).hasGraphql(operationType, operationName, payload, variables)
        }
    }

    // region write notification

    @Test
    fun `M notify about success W handleEvent() { resource write succeeded }`(
        @Forgery kind: RumResourceKind,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)

        // When
        Thread.sleep(RESOURCE_DURATION_MS)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)
        testedScope.handleEvent(mockEvent, mockWriter)

        // Then
        verify(rumMonitor.mockInstance as AdvancedRumMonitor)
            .eventSent(
                fakeParentContext.viewId.orEmpty(),
                StorageEvent.Resource(testedScope.resourceId, mockEvent.eventTime.nanoTime)
            )
    }

    @Test
    fun `M notify about error W handleEvent() { resource write failed }`(
        @Forgery kind: RumResourceKind,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        whenever(mockWriter.write(eq(mockEventBatchWriter), isA<ResourceEvent>(), eq(EventType.DEFAULT))) doReturn false

        // When
        Thread.sleep(RESOURCE_DURATION_MS)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)
        testedScope.handleEvent(mockEvent, mockWriter)

        // Then
        verify(rumMonitor.mockInstance as AdvancedRumMonitor)
            .eventDropped(
                fakeParentContext.viewId.orEmpty(),
                StorageEvent.Resource(testedScope.resourceId, mockEvent.eventTime.nanoTime)
            )
    }

    @Test
    fun `M notify about error W handleEvent() { resource write throws }`(
        @Forgery kind: RumResourceKind,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        whenever(
            mockWriter.write(eq(mockEventBatchWriter), isA<ResourceEvent>(), eq(EventType.DEFAULT))
        ) doThrow forge.anException()

        // When
        Thread.sleep(RESOURCE_DURATION_MS)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)
        testedScope.handleEvent(mockEvent, mockWriter)

        // Then
        verify(rumMonitor.mockInstance as AdvancedRumMonitor)
            .eventDropped(
                fakeParentContext.viewId.orEmpty(),
                StorageEvent.Resource(testedScope.resourceId, mockEvent.eventTime.nanoTime)
            )
    }

    @Test
    fun `M notify about success W handleEvent() { error write succeeded }`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)

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
        testedScope.handleEvent(mockEvent, mockWriter)

        // Then
        verify(rumMonitor.mockInstance as AdvancedRumMonitor)
            .eventSent(
                fakeParentContext.viewId.orEmpty(),
                StorageEvent.Error(testedScope.resourceId, mockEvent.eventTime.nanoTime)
            )
    }

    @Test
    fun `M notify about error W handleEvent() { error write failed }`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        whenever(mockWriter.write(eq(mockEventBatchWriter), isA<ErrorEvent>(), eq(EventType.DEFAULT))) doReturn false

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
        testedScope.handleEvent(mockEvent, mockWriter)

        // Then
        verify(rumMonitor.mockInstance as AdvancedRumMonitor)
            .eventDropped(
                fakeParentContext.viewId.orEmpty(),
                StorageEvent.Error(testedScope.resourceId, mockEvent.eventTime.nanoTime)
            )
    }

    @Test
    fun `M notify about error W handleEvent() { error write throws }`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        whenever(
            mockWriter.write(eq(mockEventBatchWriter), isA<ErrorEvent>(), eq(EventType.DEFAULT))
        ) doThrow forge.anException()

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
        testedScope.handleEvent(mockEvent, mockWriter)

        // Then
        verify(rumMonitor.mockInstance as AdvancedRumMonitor)
            .eventDropped(
                fakeParentContext.viewId.orEmpty(),
                StorageEvent.Error(testedScope.resourceId, mockEvent.eventTime.nanoTime)
            )
    }

    // endregion

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

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(rumMonitor)
        }
    }
}
