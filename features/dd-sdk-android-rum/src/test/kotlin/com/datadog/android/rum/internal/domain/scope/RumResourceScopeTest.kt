/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.context.NetworkInfo
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.core.internal.net.FirstPartyHostHeaderTypeResolver
import com.datadog.android.internal.utils.loggableStackTrace
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.RumResourceMethod
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.assertj.ErrorEventAssert.Companion.assertThat
import com.datadog.android.rum.assertj.ResourceEventAssert.Companion.assertThat
import com.datadog.android.rum.internal.FeaturesContextResolver
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.domain.event.ResourceTiming
import com.datadog.android.rum.internal.instrumentation.insights.InsightsCollector
import com.datadog.android.rum.internal.metric.networksettled.InternalResourceContext
import com.datadog.android.rum.internal.metric.networksettled.NetworkSettledMetricResolver
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.internal.monitor.StorageEvent
import com.datadog.android.rum.internal.toError
import com.datadog.android.rum.internal.toResource
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
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
    lateinit var mockEventBatchWriter: EventBatchWriter

    @Mock
    lateinit var mockEventWriteScope: EventWriteScope

    @Mock
    lateinit var mockFeaturesContextResolver: FeaturesContextResolver

    @Mock
    lateinit var mockNetworkSettledMetricResolver: NetworkSettledMetricResolver

    @StringForgery(regex = "http(s?)://[a-z]+\\.com/[a-z]+")
    lateinit var fakeUrl: String

    @Forgery
    lateinit var fakeKey: ResourceId

    @Forgery
    lateinit var fakeMethod: RumResourceMethod
    lateinit var fakeResourceAttributes: Map<String, Any?>

    @Forgery
    lateinit var fakeParentContext: RumContext

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @Forgery
    lateinit var fakeNetworkInfoAtScopeStart: NetworkInfo

    private var fakeServerOffset: Long = 0L
    private var fakeSampleRate: Float = 0.0f

    private lateinit var fakeEventTime: Time
    private var fakeSourceResourceEvent: ResourceEvent.ResourceEventSource? = null
    private var fakeSourceErrorEvent: ErrorEvent.ErrorEventSource? = null

    @BoolForgery
    var fakeHasReplay: Boolean = false

    private var fakeRumSessionType: RumSessionType? = null

    @Mock
    private lateinit var mockInsightsCollector: InsightsCollector

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
        fakeResourceAttributes = forge.exhaustiveAttributes()
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
        whenever(mockEventWriteScope.invoke(any())) doAnswer {
            val callback = it.getArgument<(EventBatchWriter) -> Unit>(0)
            callback.invoke(mockEventBatchWriter)
        }
        whenever(mockWriter.write(eq(mockEventBatchWriter), any(), eq(EventType.DEFAULT))) doReturn true

        fakeRumSessionType = forge.aNullable { aValueFrom(RumSessionType::class.java) }

        testedScope = RumResourceScope(
            parentScope = mockParentScope,
            sdkCore = rumMonitor.mockSdkCore,
            url = fakeUrl,
            method = fakeMethod,
            key = fakeKey,
            eventTime = fakeEventTime,
            initialAttributes = fakeResourceAttributes,
            serverTimeOffsetInMs = fakeServerOffset,
            firstPartyHostHeaderTypeResolver = mockResolver,
            featuresContextResolver = mockFeaturesContextResolver,
            sampleRate = fakeSampleRate,
            networkSettledMetricResolver = mockNetworkSettledMetricResolver,
            rumSessionTypeOverride = fakeRumSessionType,
            insightsCollector = mockInsightsCollector
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
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeResourceAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeResourceAttributes)
        expectedAttributes.putAll(attributes)

        // When
        Thread.sleep(RESOURCE_DURATION_MS)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)
        val result = testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

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
                    hasAccountInfo(fakeDatadogContext.accountInfo)
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
                    hasSessionType(fakeRumSessionType?.toResource() ?: ResourceEvent.ResourceEventSessionType.USER)
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
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
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
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeResourceAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeResourceAttributes)
        expectedAttributes.putAll(attributes)

        // When
        Thread.sleep(RESOURCE_DURATION_MS)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)
        val result = testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

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
                    hasAccountInfo(fakeDatadogContext.accountInfo)
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
                    hasSessionType(fakeRumSessionType?.toResource() ?: ResourceEvent.ResourceEventSessionType.USER)
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
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
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
            parentScope = mockParentScope,
            sdkCore = rumMonitor.mockSdkCore,
            url = brokenUrl,
            method = fakeMethod,
            key = fakeKey,
            eventTime = fakeEventTime,
            initialAttributes = fakeResourceAttributes,
            serverTimeOffsetInMs = fakeServerOffset,
            firstPartyHostHeaderTypeResolver = mockResolver,
            featuresContextResolver = mockFeaturesContextResolver,
            sampleRate = fakeSampleRate,
            networkSettledMetricResolver = mockNetworkSettledMetricResolver,
            rumSessionTypeOverride = fakeRumSessionType,
            insightsCollector = mockInsightsCollector
        )
        doAnswer { true }.whenever(mockResolver).isFirstPartyUrl(brokenUrl)
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeResourceAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeResourceAttributes)
        expectedAttributes.putAll(attributes)

        // When
        Thread.sleep(RESOURCE_DURATION_MS)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)
        val result = testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

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
                    hasAccountInfo(fakeDatadogContext.accountInfo)
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
                    hasSessionType(fakeRumSessionType?.toResource() ?: ResourceEvent.ResourceEventSessionType.USER)
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
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
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
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeResourceAttributes.keys)
            .toMutableMap()
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeResourceAttributes)
        expectedAttributes.putAll(attributes)
        attributes[RumAttributes.TRACE_ID] = fakeTraceId
        attributes[RumAttributes.SPAN_ID] = fakeSpanId
        attributes[RumAttributes.RULE_PSR] = fakeRulePsr

        // When
        Thread.sleep(RESOURCE_DURATION_MS)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)
        val result = testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

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
                    hasAccountInfo(fakeDatadogContext.accountInfo)
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
                    hasSessionType(fakeRumSessionType?.toResource() ?: ResourceEvent.ResourceEventSessionType.USER)
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
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
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
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeResourceAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeResourceAttributes)
        expectedAttributes.putAll(attributes)
        whenever(mockParentScope.getRumContext()) doReturn context

        // When
        Thread.sleep(RESOURCE_DURATION_MS)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)
        val result = testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

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
                    hasAccountInfo(fakeDatadogContext.accountInfo)
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
                    hasSessionType(fakeRumSessionType?.toResource() ?: ResourceEvent.ResourceEventSessionType.USER)
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
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
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
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeResourceAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeResourceAttributes)
        expectedAttributes.putAll(attributes)
        fakeParentContext = fakeParentContext.copy(
            syntheticsTestId = fakeTestId,
            syntheticsResultId = fakeResultId
        )
        whenever(mockParentScope.getRumContext()) doReturn fakeParentContext
        testedScope = RumResourceScope(
            parentScope = mockParentScope,
            sdkCore = rumMonitor.mockSdkCore,
            url = fakeUrl,
            method = fakeMethod,
            key = fakeKey,
            eventTime = fakeEventTime,
            initialAttributes = fakeResourceAttributes,
            serverTimeOffsetInMs = fakeServerOffset,
            firstPartyHostHeaderTypeResolver = mockResolver,
            featuresContextResolver = mockFeaturesContextResolver,
            sampleRate = fakeSampleRate,
            networkSettledMetricResolver = mockNetworkSettledMetricResolver,
            rumSessionTypeOverride = fakeRumSessionType,
            insightsCollector = mockInsightsCollector
        )

        // When
        Thread.sleep(RESOURCE_DURATION_MS)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)
        val result = testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

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
                    hasAccountInfo(fakeDatadogContext.accountInfo)
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
                    hasSessionType(
                        fakeRumSessionType?.toResource() ?: ResourceEvent.ResourceEventSessionType.SYNTHETICS
                    )
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
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
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
        val result = testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

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
                    hasAccountInfo(fakeDatadogContext.accountInfo)
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
                    hasSessionType(fakeRumSessionType?.toResource() ?: ResourceEvent.ResourceEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    containsExactlyContextAttributes(fakeResourceAttributes)
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
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
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
        val result = testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<ResourceEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(firstValue)
                .apply {
                    hasKind(kind)
                    hasStatusCode(statusCode)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasSessionType(fakeRumSessionType?.toResource() ?: ResourceEvent.ResourceEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(fakeResourceAttributes)
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
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
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
        val result = testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<Any> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue).isNotInstanceOf(ErrorEvent::class.java)
        }
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
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
        val result = testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<Any> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue).isNotInstanceOf(ErrorEvent::class.java)
        }
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
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
        val fakeParentAttributes = forge.aFilteredMap(excludedKeys = fakeResourceAttributes.keys) {
            anHexadecimalString() to anAsciiString()
        }
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeResourceAttributes)
        expectedAttributes.putAll(fakeParentAttributes)
        whenever(mockParentScope.getCustomAttributes()) doReturn fakeParentAttributes
        testedScope = RumResourceScope(
            parentScope = mockParentScope,
            sdkCore = rumMonitor.mockSdkCore,
            url = fakeUrl,
            method = fakeMethod,
            key = fakeKey,
            eventTime = fakeEventTime,
            initialAttributes = fakeResourceAttributes,
            serverTimeOffsetInMs = fakeServerOffset,
            firstPartyHostHeaderTypeResolver = mockResolver,
            featuresContextResolver = mockFeaturesContextResolver,
            sampleRate = fakeSampleRate,
            networkSettledMetricResolver = mockNetworkSettledMetricResolver,
            rumSessionTypeOverride = fakeRumSessionType,
            insightsCollector = mockInsightsCollector
        )
        whenever(rumMonitor.mockInstance.getAttributes()) doReturn emptyMap()

        // When
        Thread.sleep(RESOURCE_DURATION_MS)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, emptyMap())
        val result = testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

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
                    hasAccountInfo(fakeDatadogContext.accountInfo)
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
                    hasSessionType(fakeRumSessionType?.toResource() ?: ResourceEvent.ResourceEventSessionType.USER)
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
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
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
        val fakeParentAttributes = forge.aFilteredMap(excludedKeys = fakeResourceAttributes.keys) {
            anHexadecimalString() to anAsciiString()
        }
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeParentAttributes)
        expectedAttributes.putAll(fakeResourceAttributes)
        whenever(mockParentScope.getCustomAttributes()) doReturn fakeParentAttributes

        // When
        Thread.sleep(RESOURCE_DURATION_MS)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, emptyMap())
        val result = testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

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
                    hasAccountInfo(fakeDatadogContext.accountInfo)
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
                    hasSessionType(fakeRumSessionType?.toResource() ?: ResourceEvent.ResourceEventSessionType.USER)
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
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
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
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeResourceAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeResourceAttributes)
        expectedAttributes.putAll(attributes)

        // When
        mockEvent = RumRawEvent.AddResourceTiming(fakeKey, timing)
        val resultTiming = testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)
        Thread.sleep(RESOURCE_DURATION_MS)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)
        val result = testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

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
                    hasAccountInfo(fakeDatadogContext.accountInfo)
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
                    hasSessionType(fakeRumSessionType?.toResource() ?: ResourceEvent.ResourceEventSessionType.USER)
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
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
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
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeResourceAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeResourceAttributes)
        expectedAttributes.putAll(attributes)

        // When
        mockEvent = RumRawEvent.AddResourceTiming("not_the_$fakeKey", timing)
        val resultTiming = testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)
        Thread.sleep(RESOURCE_DURATION_MS)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)
        val result = testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

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
                    hasAccountInfo(fakeDatadogContext.accountInfo)
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
                    hasSessionType(fakeRumSessionType?.toResource() ?: ResourceEvent.ResourceEventSessionType.USER)
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
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
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
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeResourceAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeResourceAttributes)
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
        val result = testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

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
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasErrorType(throwable.javaClass.canonicalName)
                    hasErrorSourceType(ErrorEvent.SourceType.ANDROID)
                    hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                    hasTimeSinceAppStart(null)
                    hasSessionType(fakeRumSessionType?.toError() ?: ErrorEvent.ErrorEventSessionType.USER)
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
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
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
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeResourceAttributes.keys)

        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeResourceAttributes)
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
        val result = testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

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
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasErrorType(throwable.javaClass.canonicalName)
                    hasErrorSourceType(ErrorEvent.SourceType.ANDROID)
                    hasSessionType(fakeRumSessionType?.toError() ?: ErrorEvent.ErrorEventSessionType.USER)
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
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
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
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeResourceAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeResourceAttributes)
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
        val result = testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

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
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasErrorType(errorType)
                    hasErrorSourceType(ErrorEvent.SourceType.ANDROID)
                    hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                    hasTimeSinceAppStart(null)
                    hasSessionType(fakeRumSessionType?.toError() ?: ErrorEvent.ErrorEventSessionType.USER)
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
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
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
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeResourceAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeResourceAttributes)
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
            parentScope = mockParentScope,
            sdkCore = rumMonitor.mockSdkCore,
            url = fakeUrl,
            method = fakeMethod,
            key = fakeKey,
            eventTime = fakeEventTime,
            initialAttributes = fakeResourceAttributes,
            serverTimeOffsetInMs = fakeServerOffset,
            firstPartyHostHeaderTypeResolver = mockResolver,
            featuresContextResolver = mockFeaturesContextResolver,
            sampleRate = fakeSampleRate,
            networkSettledMetricResolver = mockNetworkSettledMetricResolver,
            rumSessionTypeOverride = fakeRumSessionType,
            insightsCollector = mockInsightsCollector
        )

        // When
        Thread.sleep(RESOURCE_DURATION_MS)
        val result = testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

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
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasErrorType(throwable.javaClass.canonicalName)
                    hasErrorSourceType(ErrorEvent.SourceType.ANDROID)
                    hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                    hasTimeSinceAppStart(null)
                    hasSessionType(fakeRumSessionType?.toError() ?: ErrorEvent.ErrorEventSessionType.SYNTHETICS)
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
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
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
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeResourceAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeResourceAttributes)
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
            parentScope = mockParentScope,
            sdkCore = rumMonitor.mockSdkCore,
            url = fakeUrl,
            method = fakeMethod,
            key = fakeKey,
            eventTime = fakeEventTime,
            initialAttributes = fakeResourceAttributes,
            serverTimeOffsetInMs = fakeServerOffset,
            firstPartyHostHeaderTypeResolver = mockResolver,
            featuresContextResolver = mockFeaturesContextResolver,
            sampleRate = fakeSampleRate,
            networkSettledMetricResolver = mockNetworkSettledMetricResolver,
            rumSessionTypeOverride = fakeRumSessionType,
            insightsCollector = mockInsightsCollector
        )

        // When
        Thread.sleep(RESOURCE_DURATION_MS)
        val result = testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

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
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasErrorType(errorType)
                    hasErrorSourceType(ErrorEvent.SourceType.ANDROID)
                    hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                    hasTimeSinceAppStart(null)
                    hasSessionType(fakeRumSessionType?.toError() ?: ErrorEvent.ErrorEventSessionType.SYNTHETICS)
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
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
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
            parentScope = mockParentScope,
            sdkCore = rumMonitor.mockSdkCore,
            url = brokenUrl,
            method = fakeMethod,
            key = fakeKey,
            eventTime = fakeEventTime,
            initialAttributes = fakeResourceAttributes,
            serverTimeOffsetInMs = fakeServerOffset,
            firstPartyHostHeaderTypeResolver = mockResolver,
            featuresContextResolver = mockFeaturesContextResolver,
            sampleRate = fakeSampleRate,
            networkSettledMetricResolver = mockNetworkSettledMetricResolver,
            rumSessionTypeOverride = fakeRumSessionType,
            insightsCollector = mockInsightsCollector
        )
        doAnswer { true }.whenever(mockResolver).isFirstPartyUrl(brokenUrl)
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeResourceAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeResourceAttributes)
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
        val result = testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

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
                    hasAccountInfo(fakeDatadogContext.accountInfo)
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
                    hasSessionType(fakeRumSessionType?.toError() ?: ErrorEvent.ErrorEventSessionType.USER)
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
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
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
            parentScope = mockParentScope,
            sdkCore = rumMonitor.mockSdkCore,
            url = brokenUrl,
            method = fakeMethod,
            key = fakeKey,
            eventTime = fakeEventTime,
            initialAttributes = fakeResourceAttributes,
            serverTimeOffsetInMs = fakeServerOffset,
            firstPartyHostHeaderTypeResolver = mockResolver,
            featuresContextResolver = mockFeaturesContextResolver,
            sampleRate = fakeSampleRate,
            networkSettledMetricResolver = mockNetworkSettledMetricResolver,
            rumSessionTypeOverride = fakeRumSessionType,
            insightsCollector = mockInsightsCollector
        )
        doAnswer { true }.whenever(mockResolver).isFirstPartyUrl(brokenUrl)
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeResourceAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeResourceAttributes)
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
        val result = testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

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
                    hasAccountInfo(fakeDatadogContext.accountInfo)
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
                    hasSessionType(fakeRumSessionType?.toError() ?: ErrorEvent.ErrorEventSessionType.USER)
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
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
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

        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeResourceAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeResourceAttributes)
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
        val result = testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

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
                    hasAccountInfo(fakeDatadogContext.accountInfo)
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
                    hasSessionType(fakeRumSessionType?.toError() ?: ErrorEvent.ErrorEventSessionType.USER)
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
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
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
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeResourceAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeResourceAttributes)
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
        val result = testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

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
                    hasAccountInfo(fakeDatadogContext.accountInfo)
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
                    hasSessionType(fakeRumSessionType?.toError() ?: ErrorEvent.ErrorEventSessionType.USER)
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
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
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
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeResourceAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeResourceAttributes)
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
        val result = testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

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
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasErrorType(throwable.javaClass.canonicalName)
                    hasErrorSourceType(ErrorEvent.SourceType.ANDROID)
                    hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                    hasTimeSinceAppStart(null)
                    hasSessionType(fakeRumSessionType?.toError() ?: ErrorEvent.ErrorEventSessionType.USER)
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
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
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
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeResourceAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeResourceAttributes)
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
        val result = testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

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
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasErrorType(errorType)
                    hasErrorSourceType(ErrorEvent.SourceType.ANDROID)
                    hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                    hasTimeSinceAppStart(null)
                    hasSessionType(fakeRumSessionType?.toError() ?: ErrorEvent.ErrorEventSessionType.USER)
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
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
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
        val fakeParentAttributes = forge.aFilteredMap(excludedKeys = fakeResourceAttributes.keys) {
            anHexadecimalString() to anAsciiString()
        }
        val errorAttributes = forge.exhaustiveAttributes(
            excludedKeys = fakeResourceAttributes.keys + fakeParentAttributes.keys
        )
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeParentAttributes)
        expectedAttributes.putAll(fakeResourceAttributes)
        expectedAttributes.putAll(errorAttributes)
        whenever(mockParentScope.getCustomAttributes()) doReturn fakeParentAttributes
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
        val result = testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

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
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasErrorType(throwable.javaClass.canonicalName)
                    hasErrorSourceType(ErrorEvent.SourceType.ANDROID)
                    hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                    hasTimeSinceAppStart(null)
                    hasSessionType(fakeRumSessionType?.toError() ?: ErrorEvent.ErrorEventSessionType.USER)
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
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
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
        val fakeParentAttributes = forge.aFilteredMap(excludedKeys = fakeResourceAttributes.keys) {
            anHexadecimalString() to anAsciiString()
        }
        val errorAttributes = forge.exhaustiveAttributes(
            excludedKeys = fakeResourceAttributes.keys + fakeParentAttributes.keys
        )
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeParentAttributes)
        expectedAttributes.putAll(fakeResourceAttributes)
        expectedAttributes.putAll(errorAttributes)
        whenever(mockParentScope.getCustomAttributes()) doReturn fakeParentAttributes
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
        val result = testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

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
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    hasErrorType(errorType)
                    hasErrorSourceType(ErrorEvent.SourceType.ANDROID)
                    hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                    hasTimeSinceAppStart(null)
                    hasSessionType(fakeRumSessionType?.toError() ?: ErrorEvent.ErrorEventSessionType.USER)
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
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
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
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeResourceAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeResourceAttributes)
        expectedAttributes.putAll(attributes)
        mockEvent = RumRawEvent.StopResource("not_the_$fakeKey", statusCode, size, kind, attributes)

        Thread.sleep(RESOURCE_DURATION_MS)
        val result = testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

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
        val result = testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

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
        val result = testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

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
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeResourceAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeResourceAttributes)
        expectedAttributes.putAll(attributes)

        mockEvent = RumRawEvent.WaitForResourceTiming(fakeKey)
        val resultWaitForTiming =
            testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)
        Thread.sleep(RESOURCE_DURATION_MS)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)
        val resultStop = testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

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
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeResourceAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeResourceAttributes)
        expectedAttributes.putAll(attributes)

        mockEvent = RumRawEvent.WaitForResourceTiming("not_the_$fakeKey")
        val resultWaitForTiming =
            testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)
        Thread.sleep(RESOURCE_DURATION_MS)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)
        val resultStop = testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

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
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    doesNotHaveAResourceProvider()
                    hasSessionType(fakeRumSessionType?.toResource() ?: ResourceEvent.ResourceEventSessionType.USER)
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
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
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
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeResourceAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeResourceAttributes)
        expectedAttributes.putAll(attributes)

        mockEvent = RumRawEvent.WaitForResourceTiming(fakeKey)
        val resultWaitForTiming =
            testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)
        mockEvent = RumRawEvent.AddResourceTiming(fakeKey, timing)
        val resultTiming = testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)
        Thread.sleep(RESOURCE_DURATION_MS)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)
        val resultStop = testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

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
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    doesNotHaveAResourceProvider()
                    hasSessionType(fakeRumSessionType?.toResource() ?: ResourceEvent.ResourceEventSessionType.USER)
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
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
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
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeResourceAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeResourceAttributes)
        expectedAttributes.putAll(attributes)

        mockEvent = RumRawEvent.WaitForResourceTiming(fakeKey)
        val resultWaitForTiming =
            testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)
        val resultStop = testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)
        Thread.sleep(RESOURCE_DURATION_MS)
        mockEvent = RumRawEvent.AddResourceTiming(fakeKey, timing)
        val resultTiming = testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

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
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    doesNotHaveAResourceProvider()
                    hasSessionType(fakeRumSessionType?.toResource() ?: ResourceEvent.ResourceEventSessionType.USER)
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
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
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
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeResourceAttributes.keys) +
            mapOf(
                "_dd.resource_timings"
                    to forge.getForgery(ResourceTiming::class.java).asTimingsPayload()
            )

        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)

        // When
        testedScope
            .handleEvent(
                RumRawEvent.AddResourceTiming(fakeKey, timing = timing),
                fakeDatadogContext,
                mockEventWriteScope,
                mockWriter
            )
            ?.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

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
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeResourceAttributes.keys) +
            mapOf("_dd.resource_timings" to timing.asTimingsPayload())

        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)

        // When
        testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

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
        val result = testedScope.handleEvent(fakeStopEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

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
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    doesNotHaveAResourceProvider()
                    hasSessionType(fakeRumSessionType?.toResource() ?: ResourceEvent.ResourceEventSessionType.USER)
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
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
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
        val result = testedScope.handleEvent(fakeStopEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

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
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                    doesNotHaveAResourceProvider()
                    hasSessionType(fakeRumSessionType?.toResource() ?: ResourceEvent.ResourceEventSessionType.USER)
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
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
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
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeResourceAttributes.keys) +
            mapOf(
                RumAttributes.GRAPHQL_OPERATION_TYPE to operationType.toString(),
                RumAttributes.GRAPHQL_OPERATION_NAME to operationName,
                RumAttributes.GRAPHQL_PAYLOAD to payload,
                RumAttributes.GRAPHQL_VARIABLES to variables
            )

        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)

        // When
        testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<ResourceEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(firstValue).hasGraphql(operationType, operationName, payload, variables)
        }
    }

    @Test
    fun `M truncate GraphQL payload W handleEvent { small GraphQL payload under size limit }`(
        @Forgery kind: RumResourceKind,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        forge: Forge
    ) {
        // Given
        val operationType = forge.aValueFrom(ResourceEvent.OperationType::class.java)
        val operationName = forge.aNullable { aString() }
        val variables = forge.aNullable { aString() }

        val payload = forge.aString()

        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeResourceAttributes.keys) +
            mapOf(
                RumAttributes.GRAPHQL_OPERATION_TYPE to operationType.toString(),
                RumAttributes.GRAPHQL_OPERATION_NAME to operationName,
                RumAttributes.GRAPHQL_PAYLOAD to payload,
                RumAttributes.GRAPHQL_VARIABLES to variables
            )

        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)

        // When
        testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<ResourceEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(firstValue).hasGraphql(operationType, operationName, payload, variables)
        }
    }

    @Test
    fun `M truncate GraphQL payload W handleEvent { large GraphQL payload over size limit }`(
        @Forgery kind: RumResourceKind,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        forge: Forge
    ) {
        // Given
        val operationName = forge.aNullable { aString() }
        val operationType = forge.aValueFrom(ResourceEvent.OperationType::class.java)
        val variables = forge.aNullable { aString() }

        // Generate a payload that exceeds the byte limit
        // Since forge.aString() generates character count, not byte count, we need to be careful
        val minSizeChars = RumResourceScope.MAX_GRAPHQL_PAYLOAD_SIZE_BYTES + 1000 // Ensure we exceed byte limit
        val maxSizeChars = RumResourceScope.MAX_GRAPHQL_PAYLOAD_SIZE_BYTES + 10000
        val originalPayload = forge.aString(size = forge.anInt(min = minSizeChars, max = maxSizeChars))

        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeResourceAttributes.keys) +
            mapOf(
                RumAttributes.GRAPHQL_OPERATION_NAME to operationName,
                RumAttributes.GRAPHQL_OPERATION_TYPE to operationType.toString(),
                RumAttributes.GRAPHQL_VARIABLES to variables,
                RumAttributes.GRAPHQL_PAYLOAD to originalPayload
            )

        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)

        // When
        testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<ResourceEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            val actualPayload = firstValue.resource.graphql?.payload

            checkNotNull(actualPayload)
            assertThat(actualPayload.length).isLessThan(originalPayload.length)
            assertThat(actualPayload.toByteArray(Charsets.UTF_8).size)
                .isLessThanOrEqualTo(RumResourceScope.MAX_GRAPHQL_PAYLOAD_SIZE_BYTES)

            assertThat(originalPayload).startsWith(actualPayload)

            assertThat(firstValue).hasGraphql(operationType, operationName, actualPayload, variables)
        }
    }

    @Test
    fun `M handle null GraphQL payload W handleEvent { payload is null }`(
        @Forgery kind: RumResourceKind,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        forge: Forge
    ) {
        // Given
        val operationType = forge.aValueFrom(ResourceEvent.OperationType::class.java)
        val operationName = forge.aNullable { aString() }
        val variables = forge.aNullable { aString() }

        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeResourceAttributes.keys) +
            mapOf(
                RumAttributes.GRAPHQL_OPERATION_TYPE to operationType.toString(),
                RumAttributes.GRAPHQL_OPERATION_NAME to operationName,
                RumAttributes.GRAPHQL_PAYLOAD to null, // Explicitly null
                RumAttributes.GRAPHQL_VARIABLES to variables
            )

        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)

        // When
        testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<ResourceEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(firstValue).hasGraphql(operationType, operationName, null, variables)
        }
    }

    @Test
    fun `M handle empty GraphQL payload W handleEvent { payload is empty string }`(
        @Forgery kind: RumResourceKind,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        forge: Forge
    ) {
        // Given
        val operationType = forge.aValueFrom(ResourceEvent.OperationType::class.java)
        val operationName = forge.aNullable { aString() }
        val variables = forge.aNullable { aString() }
        val payload = ""

        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeResourceAttributes.keys) +
            mapOf(
                RumAttributes.GRAPHQL_OPERATION_TYPE to operationType.toString(),
                RumAttributes.GRAPHQL_OPERATION_NAME to operationName,
                RumAttributes.GRAPHQL_PAYLOAD to payload,
                RumAttributes.GRAPHQL_VARIABLES to variables
            )

        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)

        // When
        testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<ResourceEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(firstValue).hasGraphql(operationType, operationName, payload, variables)
        }
    }

    @Test
    fun `M use fast path W handleEvent { GraphQL payload under fast path threshold }`(
        @Forgery kind: RumResourceKind,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        forge: Forge
    ) {
        // Given
        val operationType = forge.aValueFrom(ResourceEvent.OperationType::class.java)
        val operationName = forge.aNullable { aString() }
        val variables = forge.aNullable { aString() }

        // Create payload just under the fast path threshold
        // Fast path threshold is 7500 characters (30KB / 4 bytes per char)
        val fastPathThresholdChars = RumResourceScope.MAX_GRAPHQL_PAYLOAD_SIZE_BYTES / 4
        val payload = forge.aString(size = fastPathThresholdChars - 100)

        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeResourceAttributes.keys) +
            mapOf(
                RumAttributes.GRAPHQL_OPERATION_TYPE to operationType.toString(),
                RumAttributes.GRAPHQL_OPERATION_NAME to operationName,
                RumAttributes.GRAPHQL_PAYLOAD to payload,
                RumAttributes.GRAPHQL_VARIABLES to variables
            )

        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)

        // When
        testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<ResourceEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            // Payload should pass through unchanged (fast path)
            assertThat(firstValue).hasGraphql(operationType, operationName, payload, variables)
        }
    }

    @Test
    fun `M use slow path W handleEvent { GraphQL payload over fast path threshold but under limit }`(
        @Forgery kind: RumResourceKind,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        forge: Forge
    ) {
        // Given
        val operationType = forge.aValueFrom(ResourceEvent.OperationType::class.java)
        val operationName = forge.aNullable { aString() }
        val variables = forge.aNullable { aString() }

        // Create payload over fast path threshold but still under byte limit
        // Fast path threshold is 7500 characters (30KB / 4 bytes per char)
        val fastPathThresholdChars = RumResourceScope.MAX_GRAPHQL_PAYLOAD_SIZE_BYTES / 4
        val payload = forge.aString(size = fastPathThresholdChars + 100)

        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeResourceAttributes.keys) +
            mapOf(
                RumAttributes.GRAPHQL_OPERATION_TYPE to operationType.toString(),
                RumAttributes.GRAPHQL_OPERATION_NAME to operationName,
                RumAttributes.GRAPHQL_PAYLOAD to payload,
                RumAttributes.GRAPHQL_VARIABLES to variables
            )

        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)

        // When
        testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<ResourceEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            // Payload should pass through unchanged (slow path but under limit)
            assertThat(firstValue).hasGraphql(operationType, operationName, payload, variables)
        }
    }

    @Test
    fun `M truncate at UTF-8 boundary W handleEvent { GraphQL payload with multi-byte characters }`(
        @Forgery kind: RumResourceKind,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        forge: Forge
    ) {
        // Given
        val operationType = forge.aValueFrom(ResourceEvent.OperationType::class.java)
        val operationName = forge.aNullable { aString() }
        val variables = forge.aNullable { aString() }

        // Create a payload with multi-byte UTF-8 characters that exceeds the limit
        // Create base payload that's close to but under the byte limit (leave room for emojis)
        val baseSizeBytes = RumResourceScope.MAX_GRAPHQL_PAYLOAD_SIZE_BYTES - 150
        val basePayload = forge.aString(size = baseSizeBytes) // 1 byte per char in UTF-8
        // Generate random 4-byte emoji characters (Miscellaneous Symbols and Pictographs block)
        val emojiRangeStart = 0x1F300 // 🌀 (cyclone)
        val emojiRangeEnd = 0x1F5FF // 🗿 (moai)
        val remainingBytes = 150 // Space left after base payload
        val emojiByteSize = 4 // Each emoji is 4 bytes in UTF-8
        val emojiCount = (remainingBytes + 50) / emojiByteSize // Generate enough to exceed limit by ~50 bytes
        val emojiSuffix = (1..emojiCount).joinToString("") {
            val codePoint = forge.anInt(min = emojiRangeStart, max = emojiRangeEnd)
            String(Character.toChars(codePoint))
        }
        val originalPayload = basePayload + emojiSuffix

        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeResourceAttributes.keys) +
            mapOf(
                RumAttributes.GRAPHQL_OPERATION_TYPE to operationType.toString(),
                RumAttributes.GRAPHQL_OPERATION_NAME to operationName,
                RumAttributes.GRAPHQL_PAYLOAD to originalPayload,
                RumAttributes.GRAPHQL_VARIABLES to variables
            )

        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)

        // When
        testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<ResourceEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            val actualPayload = firstValue.resource.graphql?.payload

            checkNotNull(actualPayload)

            // Verify truncation occurred
            assertThat(actualPayload.length).isLessThan(originalPayload.length)

            // Verify UTF-8 byte size is within limit
            assertThat(actualPayload.toByteArray(Charsets.UTF_8).size)
                .isLessThanOrEqualTo(RumResourceScope.MAX_GRAPHQL_PAYLOAD_SIZE_BYTES)

            // Verify the truncated string is valid UTF-8 (no broken multi-byte sequences)
            assertThat(actualPayload.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8))
                .isEqualTo(actualPayload)

            // Verify original starts with truncated (proper prefix)
            assertThat(originalPayload).startsWith(actualPayload)

            assertThat(firstValue).hasGraphql(operationType, operationName, actualPayload, variables)
        }
    }

    @Test
    fun `M truncate at character boundary W handleEvent { GraphQL payload with accented characters }`(
        @Forgery kind: RumResourceKind,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        forge: Forge
    ) {
        // Given
        val operationType = forge.aValueFrom(ResourceEvent.OperationType::class.java)
        val operationName = forge.aNullable { aString() }
        val variables = forge.aNullable { aString() }

        // Create payload with 2-byte UTF-8 characters (accented characters)
        // Create base payload that's close to but under the byte limit (leave room for accented chars)
        val baseSizeBytes = RumResourceScope.MAX_GRAPHQL_PAYLOAD_SIZE_BYTES - 150
        val basePayload = forge.aString(size = baseSizeBytes) // 1 byte per char in UTF-8
        // Generate random 2-byte accented characters (Latin Extended-A block)
        val accentedRangeStart = 0x0100 // Ā (Latin Capital Letter A with Macron)
        val accentedRangeEnd = 0x017F // ſ (Latin Small Letter Long S)
        val remainingBytes = 150 // Space left after base payload
        val accentedByteSize = 2 // Each accented char is 2 bytes in UTF-8
        val accentedCount = (remainingBytes + 80) / accentedByteSize // Generate enough to exceed limit by ~80 bytes
        val accentedSuffix = (1..accentedCount).map {
            val codePoint = forge.anInt(min = accentedRangeStart, max = accentedRangeEnd)
            Char(codePoint)
        }.joinToString("")
        val originalPayload = basePayload + accentedSuffix

        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeResourceAttributes.keys) +
            mapOf(
                RumAttributes.GRAPHQL_OPERATION_TYPE to operationType.toString(),
                RumAttributes.GRAPHQL_OPERATION_NAME to operationName,
                RumAttributes.GRAPHQL_PAYLOAD to originalPayload,
                RumAttributes.GRAPHQL_VARIABLES to variables
            )

        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)

        // When
        testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<ResourceEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            val actualPayload = firstValue.resource.graphql?.payload

            checkNotNull(actualPayload)

            // Verify truncation occurred
            assertThat(actualPayload.length).isLessThan(originalPayload.length)

            // Verify UTF-8 byte size is within limit
            assertThat(actualPayload.toByteArray(Charsets.UTF_8).size)
                .isLessThanOrEqualTo(RumResourceScope.MAX_GRAPHQL_PAYLOAD_SIZE_BYTES)

            // Verify no broken UTF-8 sequences
            assertThat(actualPayload.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8))
                .isEqualTo(actualPayload)

            // Verify proper prefix
            assertThat(originalPayload).startsWith(actualPayload)

            assertThat(firstValue).hasGraphql(operationType, operationName, actualPayload, variables)
        }
    }

    @Test
    fun `M truncate at safe boundary W handleEvent { GraphQL payload with mixed UTF-8 characters }`(
        @Forgery kind: RumResourceKind,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        forge: Forge
    ) {
        // Given
        val operationType = forge.aValueFrom(ResourceEvent.OperationType::class.java)
        val operationName = forge.aNullable { aString() }
        val variables = forge.aNullable { aString() }

        // Create payload mixing ASCII, 2-byte, 3-byte, and 4-byte UTF-8 characters
        // Create base payload leaving room for mixed UTF-8 characters
        val baseSizeBytes = RumResourceScope.MAX_GRAPHQL_PAYLOAD_SIZE_BYTES - 300
        val basePayload = forge.aString(size = baseSizeBytes) // 1 byte per char in UTF-8
        // Generate mixed UTF-8 characters with different byte lengths
        val ascii = forge.aString(size = 5) // 1 byte per char

        // Latin Extended-A block (2 bytes per char in UTF-8)
        val accentedStart = 0x0100 // Ā
        val accentedEnd = 0x017F // ſ
        val accented = (1..5).map { Char(forge.anInt(min = accentedStart, max = accentedEnd)) }.joinToString("")

        // CJK Unified Ideographs block (3 bytes per char in UTF-8)
        val cjkStart = 0x4E00 // 一 (Chinese "one")
        val cjkEnd = 0x9FFF // 鿿 (end of CJK block)
        val cjk = (1..5).map { Char(forge.anInt(min = cjkStart, max = cjkEnd)) }.joinToString("")

        // Miscellaneous Symbols and Pictographs block (4 bytes per char in UTF-8)
        val emojiStart = 0x1F300 // 🌀
        val emojiEnd = 0x1F5FF // 🗿
        val emoji = (1..5).map {
            String(
                Character.toChars(forge.anInt(min = emojiStart, max = emojiEnd))
            )
        }.joinToString("")
        val mixedSuffix = ascii + accented + cjk + emoji
        val originalPayload = basePayload + mixedSuffix.repeat(10) // Repeat to ensure we exceed limit

        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeResourceAttributes.keys) +
            mapOf(
                RumAttributes.GRAPHQL_OPERATION_TYPE to operationType.toString(),
                RumAttributes.GRAPHQL_OPERATION_NAME to operationName,
                RumAttributes.GRAPHQL_PAYLOAD to originalPayload,
                RumAttributes.GRAPHQL_VARIABLES to variables
            )

        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)

        // When
        testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<ResourceEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            val actualPayload = firstValue.resource.graphql?.payload

            checkNotNull(actualPayload)

            // Verify truncation occurred
            assertThat(actualPayload.length).isLessThan(originalPayload.length)

            // Verify UTF-8 byte size is within limit
            val actualBytes = actualPayload.toByteArray(Charsets.UTF_8)
            assertThat(actualBytes.size).isLessThanOrEqualTo(RumResourceScope.MAX_GRAPHQL_PAYLOAD_SIZE_BYTES)

            // Verify no broken UTF-8 sequences by round-trip conversion
            assertThat(String(actualBytes, Charsets.UTF_8)).isEqualTo(actualPayload)

            // Verify proper prefix
            assertThat(originalPayload).startsWith(actualPayload)

            // Verify the last character is complete (not truncated mid-character)
            val lastChar = actualPayload.last()
            assertThat(lastChar).isNotEqualTo('\uFFFD') // Replacement character indicates broken UTF-8

            assertThat(firstValue).hasGraphql(operationType, operationName, actualPayload, variables)
        }
    }

    @Test
    fun `M handle edge case W handleEvent { GraphQL payload exactly at fast path threshold }`(
        @Forgery kind: RumResourceKind,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        forge: Forge
    ) {
        // Given
        val operationType = forge.aValueFrom(ResourceEvent.OperationType::class.java)
        val operationName = forge.aNullable { aString() }
        val variables = forge.aNullable { aString() }

        // Create payload exactly at fast path threshold
        // Fast path threshold is 7500 characters (30KB / 4 bytes per char)
        val fastPathThresholdChars = RumResourceScope.MAX_GRAPHQL_PAYLOAD_SIZE_BYTES / 4
        val payload = forge.aString(size = fastPathThresholdChars) // 1 byte per char in UTF-8

        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeResourceAttributes.keys) +
            mapOf(
                RumAttributes.GRAPHQL_OPERATION_TYPE to operationType.toString(),
                RumAttributes.GRAPHQL_OPERATION_NAME to operationName,
                RumAttributes.GRAPHQL_PAYLOAD to payload,
                RumAttributes.GRAPHQL_VARIABLES to variables
            )

        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)

        // When
        testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<ResourceEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            // Payload should pass through unchanged (at threshold, should use slow path but still under limit)
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
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeResourceAttributes.keys)

        // When
        Thread.sleep(RESOURCE_DURATION_MS)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)
        testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

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
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeResourceAttributes.keys)
        whenever(mockWriter.write(eq(mockEventBatchWriter), isA<ResourceEvent>(), eq(EventType.DEFAULT))) doReturn false

        // When
        Thread.sleep(RESOURCE_DURATION_MS)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)
        testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

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
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeResourceAttributes.keys)
        whenever(
            mockWriter.write(eq(mockEventBatchWriter), isA<ResourceEvent>(), eq(EventType.DEFAULT))
        ) doThrow forge.anException()

        // When
        Thread.sleep(RESOURCE_DURATION_MS)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)
        testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

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
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeResourceAttributes.keys)

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
        testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

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
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeResourceAttributes.keys)
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
        testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

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
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeResourceAttributes.keys)
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
        testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        verify(rumMonitor.mockInstance as AdvancedRumMonitor)
            .eventDropped(
                fakeParentContext.viewId.orEmpty(),
                StorageEvent.Error(testedScope.resourceId, mockEvent.eventTime.nanoTime)
            )
    }

    // endregion

    @Test
    fun `M call onNetworkRequest W handleEvent {}`(
        @Forgery kind: RumResourceKind,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeResourceAttributes.keys)

        // Wait to ensure duration > 0
        Thread.sleep(RESOURCE_DURATION_MS)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)

        // When
        testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        verify(mockInsightsCollector).onNetworkRequest(any(), any())
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

    // region GraphQL

    @ParameterizedTest
    @MethodSource("invalidOperationTypeTestCases")
    fun `M have no graphql data W handleEvent { invalid or null operation type }`(
        operationTypeValue: String?,
        hasOtherAttributes: Boolean,
        @Forgery kind: RumResourceKind,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        forge: Forge
    ) {
        // Given
        val operationName = forge.aNullable { aString() }
        val payload = forge.aNullable { aString() }
        val variables = forge.aNullable { aString() }

        val attributes = if (hasOtherAttributes) {
            forge.exhaustiveAttributes(excludedKeys = fakeResourceAttributes.keys) +
                buildMap {
                    if (operationTypeValue != null) {
                        put(RumAttributes.GRAPHQL_OPERATION_TYPE, operationTypeValue)
                    }
                    put(RumAttributes.GRAPHQL_OPERATION_NAME, operationName)
                    put(RumAttributes.GRAPHQL_PAYLOAD, payload)
                    put(RumAttributes.GRAPHQL_VARIABLES, variables)
                }
        } else {
            forge.exhaustiveAttributes(excludedKeys = fakeResourceAttributes.keys) +
                buildMap {
                    if (operationTypeValue != null) {
                        put(RumAttributes.GRAPHQL_OPERATION_TYPE, operationTypeValue)
                    }
                }
        }

        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)

        // When
        testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<ResourceEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(firstValue).hasNoGraphql()
        }
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

        @JvmStatic
        fun invalidOperationTypeTestCases() = listOf(
            // operationTypeValue, hasOtherAttributes
            Arguments.of(null, false), // Null operation type, no other attributes
            Arguments.of(null, true), // Null operation type, with other attributes
            Arguments.of("", false), // Empty operation type, no other attributes
            Arguments.of("", true), // Empty operation type, with other attributes
            Arguments.of("invalid_operation", false), // Invalid operation type, no other attributes
            Arguments.of("invalid_operation", true) // Invalid operation type, with other attributes
        )
    }
}
