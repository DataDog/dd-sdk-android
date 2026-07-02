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
import com.datadog.android.heatmaps.CrossPlatformHeatmapActionData
import com.datadog.android.internal.heatmaps.HeatmapIdentifier
import com.datadog.android.internal.heatmaps.HeatmapIdentifierRegistry
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.RumResourceMethod
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.assertj.ActionEventAssert.Companion.assertThat
import com.datadog.android.rum.internal.FeaturesContextResolver
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.heatmaps.HeatmapActionResolver
import com.datadog.android.rum.internal.heatmaps.NativeHeatmapActionData
import com.datadog.android.rum.internal.instrumentation.insights.InsightsCollector
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.internal.monitor.StorageEvent
import com.datadog.android.rum.internal.toAction
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.forge.aFilteredMap
import com.datadog.tools.unit.forge.anException
import com.datadog.tools.unit.forge.exhaustiveAttributes
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
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
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumActionScopeTest {

    lateinit var testedScope: RumActionScope

    @Mock
    lateinit var mockParentScope: RumScope

    @Mock
    lateinit var mockWriter: DataWriter<Any>

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    @Mock
    lateinit var mockEventWriteScope: EventWriteScope

    @Mock
    lateinit var mockFeaturesContextResolver: FeaturesContextResolver

    lateinit var fakeType: RumActionType

    @StringForgery
    lateinit var fakeName: String

    lateinit var fakeKey: ByteArray
    lateinit var fakeAttributes: Map<String, Any?>

    @Forgery
    lateinit var fakeParentContext: RumContext

    @Forgery
    lateinit var fakeNetworkInfoAtScopeStart: NetworkInfo

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    private var fakeSampleRate: Float = 0f

    lateinit var fakeEventTime: Time

    lateinit var fakeEvent: RumRawEvent

    var fakeServerOffset: Long = 0L

    var fakeSourceActionEvent: ActionEvent.ActionEventSource? = null

    @BoolForgery
    var fakeHasReplay: Boolean = false

    @BoolForgery
    var fakeTrackFrustrations: Boolean = true

    private var fakeRumSessionType: RumSessionType? = null

    @Mock
    private lateinit var mockInsightsCollector: InsightsCollector

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeSourceActionEvent = forge.aNullable { aValueFrom(ActionEvent.ActionEventSource::class.java) }

        fakeDatadogContext = fakeDatadogContext.copy(
            source = with(fakeSourceActionEvent) {
                this?.toJson()?.asString ?: forge.anAlphabeticalString()
            }
        )

        fakeParentContext = fakeParentContext.copy(syntheticsTestId = null, syntheticsResultId = null)

        fakeEventTime = Time()
        val maxLimit = 1000L
        val minLimit = -1000L
        fakeServerOffset =
            forge.aLong(min = minLimit, max = maxLimit)
        fakeType = forge.aValueFrom(
            RumActionType::class.java,
            exclude = listOf(RumActionType.CUSTOM)
        )

        fakeSampleRate = forge.aFloat(min = 0.0f, max = 100.0f)

        fakeAttributes = forge.exhaustiveAttributes()
        fakeKey = forge.anAsciiString().toByteArray()

        whenever(rumMonitor.mockSdkCore.networkInfo) doReturn fakeNetworkInfoAtScopeStart
        whenever(rumMonitor.mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockParentScope.getRumContext()) doReturn fakeParentContext
        whenever(mockEventWriteScope.invoke(any())) doAnswer {
            val callback = it.getArgument<(EventBatchWriter) -> Unit>(0)
            callback.invoke(mockEventBatchWriter)
        }
        whenever(
            mockFeaturesContextResolver.resolveViewHasReplay(
                fakeDatadogContext,
                fakeParentContext.viewId.orEmpty()
            )
        ).thenReturn(fakeHasReplay)
        whenever(mockWriter.write(eq(mockEventBatchWriter), any(), eq(EventType.DEFAULT))) doReturn true

        fakeRumSessionType = forge.aNullable { aValueFrom(RumSessionType::class.java) }

        testedScope = RumActionScope(
            parentScope = mockParentScope,
            sdkCore = rumMonitor.mockSdkCore,
            waitForStop = false,
            eventTime = fakeEventTime,
            initialType = fakeType,
            initialName = fakeName,
            initialAttributes = fakeAttributes,
            serverTimeOffsetInMs = fakeServerOffset,
            inactivityThresholdMs = TEST_INACTIVITY_MS,
            maxDurationMs = TEST_MAX_DURATION_MS,
            featuresContextResolver = mockFeaturesContextResolver,
            trackFrustrations = true,
            sampleRate = fakeSampleRate,
            rumSessionTypeOverride = fakeRumSessionType,
            insightsCollector = mockInsightsCollector
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
    fun `M send Action after threshold W handleEvent(StartResource+StopResource+any)`(
        @StringForgery key: String,
        @Forgery method: RumResourceMethod,
        @StringForgery(regex = "http(s?)://[a-z]+\\.com/[a-z]+") url: String,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        @Forgery kind: RumResourceKind
    ) {
        // When
        fakeEvent = RumRawEvent.StartResource(key, url, method, emptyMap(), fakeEventTime)
        val result = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)
        fakeEvent =
            RumRawEvent.StopResource(key, statusCode, size, kind, emptyMap(), timeWithOffset(TEST_INACTIVITY_MS * 2))
        val result2 = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)
        val result3 = testedScope.handleEvent(
            mockEvent(TEST_INACTIVITY_MS * 4 + 1),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasId(testedScope.actionId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(TEST_INACTIVITY_MS * 2))
                    hasDurationLowerThan(TimeUnit.MILLISECONDS.toNanos(TEST_INACTIVITY_MS * 4))
                    hasResourceCount(1)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasLongTaskCount(0)
                    hasNoFrustration()
                    hasReplay(fakeHasReplay)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toAction() ?: ActionEvent.ActionEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture,
                        fakeDatadogContext.deviceInfo.isLowRam,
                        fakeDatadogContext.deviceInfo.logicalCpuCount,
                        fakeDatadogContext.deviceInfo.totalRam
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasBuildVersion(fakeDatadogContext.versionCode)
                    hasBuildId(fakeDatadogContext.appBuildId)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isSameAs(testedScope)
        assertThat(result3).isNull()
    }

    @Test
    fun `M do nothing W handleEvent(StartResource+StopResource+any) {unknown key}`(
        @StringForgery key: String,
        @StringForgery key2: String,
        @Forgery method: RumResourceMethod,
        @StringForgery(regex = "http(s?)://[a-z]+\\.com/[a-z]+") url: String,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        @Forgery kind: RumResourceKind
    ) {
        // When
        fakeEvent = RumRawEvent.StartResource(key, url, method, emptyMap(), fakeEventTime)
        val result = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)
        fakeEvent =
            RumRawEvent.StopResource(key2, statusCode, size, kind, emptyMap(), timeWithOffset(TEST_INACTIVITY_MS * 2))
        val result2 = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)
        val result3 = testedScope.handleEvent(
            mockEvent(TEST_INACTIVITY_MS * 4 + 1),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )

        // Then
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
        verifyNoInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isSameAs(testedScope)
        assertThat(result3).isSameAs(testedScope)
    }

    @Test
    fun `M send Action after threshold W handleEvent(StartResource+StopResourceWithError+any)`(
        @StringForgery key: String,
        @Forgery method: RumResourceMethod,
        @StringForgery(regex = "http(s?)://[a-z]+\\.com/[a-z]+") url: String,
        @LongForgery(200, 600) statusCode: Long,
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable
    ) {
        // When
        fakeEvent = RumRawEvent.StartResource(key, url, method, emptyMap(), fakeEventTime)
        val result = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)
        fakeEvent = RumRawEvent.StopResourceWithError(
            key,
            statusCode,
            message,
            source,
            throwable,
            emptyMap(),
            timeWithOffset(TEST_INACTIVITY_MS * 2)
        )
        val result2 = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)
        val result3 = testedScope.handleEvent(
            mockEvent(TEST_INACTIVITY_MS * 4 + 1),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasId(testedScope.actionId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(TEST_INACTIVITY_MS * 2))
                    hasDurationLowerThan(TimeUnit.MILLISECONDS.toNanos(TEST_INACTIVITY_MS * 4))
                    hasResourceCount(0)
                    hasErrorCount(1)
                    hasCrashCount(0)
                    hasLongTaskCount(0)
                    if (fakeType == RumActionType.TAP) {
                        hasFrustration(ActionEvent.Type.ERROR_TAP)
                    } else {
                        hasNoFrustration()
                    }
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toAction() ?: ActionEvent.ActionEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture,
                        fakeDatadogContext.deviceInfo.isLowRam,
                        fakeDatadogContext.deviceInfo.logicalCpuCount,
                        fakeDatadogContext.deviceInfo.totalRam
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasBuildVersion(fakeDatadogContext.versionCode)
                    hasBuildId(fakeDatadogContext.appBuildId)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isSameAs(testedScope)
        assertThat(result3).isNull()
    }

    @Test
    fun `M send Action after threshold W handleEvent(StartResource+StopResourceWithStackTrace)`(
        @StringForgery key: String,
        @Forgery method: RumResourceMethod,
        @StringForgery(regex = "http(s?)://[a-z]+\\.com/[a-z]+") url: String,
        @LongForgery(200, 600) statusCode: Long,
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @StringForgery stackTrace: String,
        forge: Forge
    ) {
        // Given
        val errorType = forge.aNullable { anAlphabeticalString() }

        // When
        fakeEvent = RumRawEvent.StartResource(key, url, method, emptyMap(), fakeEventTime)
        val result = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)
        fakeEvent = RumRawEvent.StopResourceWithStackTrace(
            key,
            statusCode,
            message,
            source,
            stackTrace,
            errorType,
            emptyMap(),
            timeWithOffset(TEST_INACTIVITY_MS * 2)
        )
        val result2 = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)
        val result3 = testedScope.handleEvent(
            mockEvent(TEST_INACTIVITY_MS * 4 + 1),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasId(testedScope.actionId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(TEST_INACTIVITY_MS * 2))
                    hasDurationLowerThan(TimeUnit.MILLISECONDS.toNanos(TEST_INACTIVITY_MS * 4))
                    hasResourceCount(0)
                    hasErrorCount(1)
                    hasCrashCount(0)
                    hasLongTaskCount(0)
                    if (fakeType == RumActionType.TAP) {
                        hasFrustration(ActionEvent.Type.ERROR_TAP)
                    } else {
                        hasNoFrustration()
                    }
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toAction() ?: ActionEvent.ActionEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture,
                        fakeDatadogContext.deviceInfo.isLowRam,
                        fakeDatadogContext.deviceInfo.logicalCpuCount,
                        fakeDatadogContext.deviceInfo.totalRam
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasBuildVersion(fakeDatadogContext.versionCode)
                    hasBuildId(fakeDatadogContext.appBuildId)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isSameAs(testedScope)
        assertThat(result3).isNull()
    }

    @Test
    fun `M send Action W handleEvent(StartResource+StopResourceWithError+any) {unknown key}`(
        @StringForgery key: String,
        @StringForgery key2: String,
        @Forgery method: RumResourceMethod,
        @StringForgery(regex = "http(s?)://[a-z]+\\.com/[a-z]+") url: String,
        @LongForgery(200, 600) statusCode: Long,
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable
    ) {
        // When
        fakeEvent = RumRawEvent.StartResource(key, url, method, emptyMap(), fakeEventTime)
        val result = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)
        val fakeEvent2 = RumRawEvent.StopResourceWithError(
            key2,
            statusCode,
            message,
            source,
            throwable,
            emptyMap(),
            timeWithOffset(TEST_INACTIVITY_MS * 2)
        )
        val result2 = testedScope.handleEvent(fakeEvent2, fakeDatadogContext, mockEventWriteScope, mockWriter)
        val result3 = testedScope.handleEvent(
            mockEvent(TEST_INACTIVITY_MS * 4 + 1),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )

        // Then
        verifyNoInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isSameAs(testedScope)
        assertThat(result3).isSameAs(testedScope)
    }

    @Test
    fun `M send Action W handleEvent(StartResource+StopResourceWithStackTrace+any) {unknown key}`(
        @StringForgery key: String,
        @StringForgery key2: String,
        @Forgery method: RumResourceMethod,
        @StringForgery(regex = "http(s?)://[a-z]+\\.com/[a-z]+") url: String,
        @LongForgery(200, 600) statusCode: Long,
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @StringForgery stackTrace: String,
        forge: Forge
    ) {
        // Given
        val errorType = forge.aNullable { anAlphabeticalString() }

        // When
        fakeEvent = RumRawEvent.StartResource(key, url, method, emptyMap(), fakeEventTime)
        val result = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)
        val fakeEvent2 = RumRawEvent.StopResourceWithStackTrace(
            key2,
            statusCode,
            message,
            source,
            stackTrace,
            errorType,
            emptyMap(),
            timeWithOffset(TEST_INACTIVITY_MS * 2)
        )
        val result2 = testedScope.handleEvent(fakeEvent2, fakeDatadogContext, mockEventWriteScope, mockWriter)
        val result3 = testedScope.handleEvent(
            mockEvent(TEST_INACTIVITY_MS * 4 + 1),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )

        // Then
        verifyNoInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isSameAs(testedScope)
        assertThat(result3).isSameAs(testedScope)
    }

    @Test
    fun `M send Action after threshold W handleEvent(StartResource+any) missing resource key`(
        @Forgery method: RumResourceMethod,
        @StringForgery(regex = "http(s?)://[a-z]+\\.com/[a-z]+") url: String
    ) {
        // Given
        var key: Any? = Object()
        val keyRef = WeakReference(key)

        // When
        fakeEvent = RumRawEvent.StartResource(key!!, url, method, emptyMap(), fakeEventTime)
        val result = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Replace fakeEvent to remove strong reference to key via fakeEvent->StartResource->key field
        fakeEvent = mockEvent()
        @Suppress("UNUSED_VALUE", "AssignedValueIsNeverRead") // We are force null-ing to allow it to be GC'ed
        key = null
        System.gc()
        assertThat(keyRef.get())
            .describedAs("resource key should have been garbage collected by now")
            .isNull()

        val result2 = testedScope.handleEvent(
            mockEvent(TEST_INACTIVITY_MS * 2 + 1),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasId(testedScope.actionId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationLowerThan(TimeUnit.MILLISECONDS.toNanos(TEST_INACTIVITY_MS * 2))
                    hasResourceCount(1)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasLongTaskCount(0)
                    hasNoFrustration()
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toAction() ?: ActionEvent.ActionEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture,
                        fakeDatadogContext.deviceInfo.isLowRam,
                        fakeDatadogContext.deviceInfo.logicalCpuCount,
                        fakeDatadogContext.deviceInfo.totalRam
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasBuildVersion(fakeDatadogContext.versionCode)
                    hasBuildId(fakeDatadogContext.appBuildId)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isNull()
    }

    @Test
    fun `M send Action after threshold W handleEvent(AddError+any)`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable
    ) {
        // When
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace = null,
            isFatal = false,
            threads = emptyList(),
            attributes = emptyMap(),
            eventTime = fakeEventTime
        )
        val result = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)
        val result2 = testedScope.handleEvent(
            mockEvent(TEST_INACTIVITY_MS * 2 + 1),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasId(testedScope.actionId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationLowerThan(TimeUnit.MILLISECONDS.toNanos(TEST_INACTIVITY_MS * 2))
                    hasResourceCount(0)
                    hasErrorCount(1)
                    hasCrashCount(0)
                    hasLongTaskCount(0)
                    if (fakeType == RumActionType.TAP) {
                        hasFrustration(ActionEvent.Type.ERROR_TAP)
                    } else {
                        hasNoFrustration()
                    }
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toAction() ?: ActionEvent.ActionEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture,
                        fakeDatadogContext.deviceInfo.isLowRam,
                        fakeDatadogContext.deviceInfo.logicalCpuCount,
                        fakeDatadogContext.deviceInfo.totalRam
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasBuildVersion(fakeDatadogContext.versionCode)
                    hasBuildId(fakeDatadogContext.appBuildId)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isNull()
    }

    @Test
    fun `M send Action after threshold W handleEvent(AddLongTask+any)`(
        @LongForgery duration: Long,
        @StringForgery target: String
    ) {
        // When
        fakeEvent = RumRawEvent.AddLongTask(duration, target, eventTime = fakeEventTime)
        val result = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)
        val result2 = testedScope.handleEvent(
            mockEvent(TEST_INACTIVITY_MS * 2 + 1),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasId(testedScope.actionId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationLowerThan(TimeUnit.MILLISECONDS.toNanos(TEST_INACTIVITY_MS))
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasLongTaskCount(1)
                    hasNoFrustration()
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toAction() ?: ActionEvent.ActionEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture,
                        fakeDatadogContext.deviceInfo.isLowRam,
                        fakeDatadogContext.deviceInfo.logicalCpuCount,
                        fakeDatadogContext.deviceInfo.totalRam
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasBuildVersion(fakeDatadogContext.versionCode)
                    hasBuildId(fakeDatadogContext.appBuildId)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isNull()
    }

    @Test
    fun `M send Action immediately W handleEvent(AddError) {isFatal=true}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable
    ) {
        // When
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace = null,
            isFatal = true,
            threads = emptyList(),
            attributes = emptyMap(),
            eventTime = fakeEventTime
        )
        val result = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasId(testedScope.actionId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationLowerThan(TimeUnit.MILLISECONDS.toNanos(100))
                    hasResourceCount(0)
                    hasErrorCount(1)
                    hasCrashCount(1)
                    hasLongTaskCount(0)
                    if (fakeType == RumActionType.TAP) {
                        hasFrustration(ActionEvent.Type.ERROR_TAP)
                    } else {
                        hasNoFrustration()
                    }
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toAction() ?: ActionEvent.ActionEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture,
                        fakeDatadogContext.deviceInfo.isLowRam,
                        fakeDatadogContext.deviceInfo.logicalCpuCount,
                        fakeDatadogContext.deviceInfo.totalRam
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasBuildVersion(fakeDatadogContext.versionCode)
                    hasBuildId(fakeDatadogContext.appBuildId)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M send Action immediately W handleEvent(AddError{isFatal=false}+AddError{isFatal=true})`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable
    ) {
        // When
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace = null,
            isFatal = false,
            threads = emptyList(),
            attributes = emptyMap(),
            eventTime = fakeEventTime
        )
        val result = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace = null,
            isFatal = true,
            threads = emptyList(),
            attributes = emptyMap(),
            eventTime = fakeEventTime
        )
        val result2 = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasId(testedScope.actionId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationLowerThan(TimeUnit.MILLISECONDS.toNanos(100))
                    hasResourceCount(0)
                    hasErrorCount(2)
                    hasCrashCount(1)
                    hasLongTaskCount(0)
                    if (fakeType == RumActionType.TAP) {
                        hasFrustration(ActionEvent.Type.ERROR_TAP)
                    } else {
                        hasNoFrustration()
                    }
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toAction() ?: ActionEvent.ActionEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture,
                        fakeDatadogContext.deviceInfo.isLowRam,
                        fakeDatadogContext.deviceInfo.logicalCpuCount,
                        fakeDatadogContext.deviceInfo.totalRam
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasBuildVersion(fakeDatadogContext.versionCode)
                    hasBuildId(fakeDatadogContext.appBuildId)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isNull()
    }

    @Test
    fun `M send Action immediately W handleEvent(StartView) {resourceCount != 0}`(
        @LongForgery(1, 1024) count: Long
    ) {
        // Given
        testedScope.resourceCount = count

        // When
        fakeEvent = RumRawEvent.StartView(RumScopeKey.from(Object()), emptyMap())
        val result = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasId(testedScope.actionId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(count)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasLongTaskCount(0)
                    hasNoFrustration()
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toAction() ?: ActionEvent.ActionEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture,
                        fakeDatadogContext.deviceInfo.isLowRam,
                        fakeDatadogContext.deviceInfo.logicalCpuCount,
                        fakeDatadogContext.deviceInfo.totalRam
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasBuildVersion(fakeDatadogContext.versionCode)
                    hasBuildId(fakeDatadogContext.appBuildId)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M send Action immediately W handleEvent(StartView) {longTaskCount != 0}`(
        @LongForgery(1, 1024) count: Long
    ) {
        // Given
        testedScope.longTaskCount = count

        // When
        fakeEvent = RumRawEvent.StartView(RumScopeKey.from(Object()), emptyMap())
        val result = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasId(testedScope.actionId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasLongTaskCount(count)
                    hasNoFrustration()
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toAction() ?: ActionEvent.ActionEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture,
                        fakeDatadogContext.deviceInfo.isLowRam,
                        fakeDatadogContext.deviceInfo.logicalCpuCount,
                        fakeDatadogContext.deviceInfo.totalRam
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasBuildVersion(fakeDatadogContext.versionCode)
                    hasBuildId(fakeDatadogContext.appBuildId)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M send Action immediately W handleEvent(StartView) {errorCount != 0}`(
        @LongForgery(1, 1024) count: Long
    ) {
        // Given
        testedScope.errorCount = count

        // When
        fakeEvent = RumRawEvent.StartView(RumScopeKey.from(Object()), emptyMap())
        val result = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasId(testedScope.actionId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(0)
                    hasErrorCount(count)
                    hasCrashCount(0)
                    hasLongTaskCount(0)
                    if (fakeType == RumActionType.TAP) {
                        hasFrustration(ActionEvent.Type.ERROR_TAP)
                    } else {
                        hasNoFrustration()
                    }
                    hasView(fakeParentContext)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toAction() ?: ActionEvent.ActionEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture,
                        fakeDatadogContext.deviceInfo.isLowRam,
                        fakeDatadogContext.deviceInfo.logicalCpuCount,
                        fakeDatadogContext.deviceInfo.totalRam
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasBuildVersion(fakeDatadogContext.versionCode)
                    hasBuildId(fakeDatadogContext.appBuildId)
                    hasSampleRate(fakeSampleRate)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M send Action immediately W handleEvent(StartView) {crashCount != 0}`(
        @LongForgery(1, 1024) nonFatalCount: Long,
        @LongForgery(1, 1024) fatalCount: Long
    ) {
        // Given
        testedScope.errorCount = nonFatalCount + fatalCount
        testedScope.crashCount = fatalCount

        // When
        fakeEvent = RumRawEvent.StartView(RumScopeKey.from(Object()), emptyMap())
        val result = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasId(testedScope.actionId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(0)
                    hasErrorCount(nonFatalCount + fatalCount)
                    hasCrashCount(fatalCount)
                    hasLongTaskCount(0)
                    if (fakeType == RumActionType.TAP) {
                        hasFrustration(ActionEvent.Type.ERROR_TAP)
                    } else {
                        hasNoFrustration()
                    }
                    hasView(fakeParentContext)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toAction() ?: ActionEvent.ActionEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture,
                        fakeDatadogContext.deviceInfo.isLowRam,
                        fakeDatadogContext.deviceInfo.logicalCpuCount,
                        fakeDatadogContext.deviceInfo.totalRam
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasBuildVersion(fakeDatadogContext.versionCode)
                    hasBuildId(fakeDatadogContext.appBuildId)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M send Action immediately W handleEvent(StopView) {resourceCount != 0}`(
        @LongForgery(1, 1024) count: Long
    ) {
        // Given
        testedScope.resourceCount = count

        // When
        fakeEvent = RumRawEvent.StopView(RumScopeKey.from(Object()), emptyMap())
        val result = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasId(testedScope.actionId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(count)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasLongTaskCount(0)
                    hasNoFrustration()
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toAction() ?: ActionEvent.ActionEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture,
                        fakeDatadogContext.deviceInfo.isLowRam,
                        fakeDatadogContext.deviceInfo.logicalCpuCount,
                        fakeDatadogContext.deviceInfo.totalRam
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasBuildVersion(fakeDatadogContext.versionCode)
                    hasBuildId(fakeDatadogContext.appBuildId)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M send Action immediately W handleEvent(StopView) {longTaskCount != 0}`(
        @LongForgery(1, 1024) count: Long
    ) {
        // Given
        testedScope.longTaskCount = count

        // When
        fakeEvent = RumRawEvent.StopView(RumScopeKey.from(Object()), emptyMap())
        val result = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasId(testedScope.actionId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasLongTaskCount(count)
                    hasNoFrustration()
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toAction() ?: ActionEvent.ActionEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture,
                        fakeDatadogContext.deviceInfo.isLowRam,
                        fakeDatadogContext.deviceInfo.logicalCpuCount,
                        fakeDatadogContext.deviceInfo.totalRam
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasBuildVersion(fakeDatadogContext.versionCode)
                    hasBuildId(fakeDatadogContext.appBuildId)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M send Action immediately W handleEvent(StopView) {errorCount != 0}`(
        @LongForgery(1, 1024) count: Long
    ) {
        // Given
        testedScope.errorCount = count

        // When
        fakeEvent = RumRawEvent.StopView(RumScopeKey.from(Object()), emptyMap())
        val result = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasId(testedScope.actionId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(0)
                    hasErrorCount(count)
                    hasCrashCount(0)
                    hasLongTaskCount(0)
                    if (fakeType == RumActionType.TAP) {
                        hasFrustration(ActionEvent.Type.ERROR_TAP)
                    } else {
                        hasNoFrustration()
                    }
                    hasView(fakeParentContext)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toAction() ?: ActionEvent.ActionEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture,
                        fakeDatadogContext.deviceInfo.isLowRam,
                        fakeDatadogContext.deviceInfo.logicalCpuCount,
                        fakeDatadogContext.deviceInfo.totalRam
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasBuildVersion(fakeDatadogContext.versionCode)
                    hasBuildId(fakeDatadogContext.appBuildId)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M send Action immediately W handleEvent(StopView) {crashCount != 0}`(
        @LongForgery(1, 1024) nonFatalCount: Long,
        @LongForgery(1, 1024) fatalCount: Long
    ) {
        // Given
        testedScope.errorCount = nonFatalCount + fatalCount
        testedScope.crashCount = fatalCount

        // When
        fakeEvent = RumRawEvent.StopView(RumScopeKey.from(Object()), emptyMap())
        val result = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasId(testedScope.actionId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(0)
                    hasErrorCount(nonFatalCount + fatalCount)
                    hasCrashCount(fatalCount)
                    hasLongTaskCount(0)
                    if (fakeType == RumActionType.TAP) {
                        hasFrustration(ActionEvent.Type.ERROR_TAP)
                    } else {
                        hasNoFrustration()
                    }
                    hasView(fakeParentContext)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toAction() ?: ActionEvent.ActionEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture,
                        fakeDatadogContext.deviceInfo.isLowRam,
                        fakeDatadogContext.deviceInfo.logicalCpuCount,
                        fakeDatadogContext.deviceInfo.totalRam
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasBuildVersion(fakeDatadogContext.versionCode)
                    hasBuildId(fakeDatadogContext.appBuildId)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M send Action immediately W handleEvent(StopSession) {resourceCount != 0}`(
        @LongForgery(1, 1024) count: Long
    ) {
        // Given
        testedScope.resourceCount = count

        // When
        fakeEvent = RumRawEvent.StopSession()
        val result = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasId(testedScope.actionId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(count)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasLongTaskCount(0)
                    hasNoFrustration()
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toAction() ?: ActionEvent.ActionEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture,
                        fakeDatadogContext.deviceInfo.isLowRam,
                        fakeDatadogContext.deviceInfo.logicalCpuCount,
                        fakeDatadogContext.deviceInfo.totalRam
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasBuildVersion(fakeDatadogContext.versionCode)
                    hasBuildId(fakeDatadogContext.appBuildId)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M send Action immediately W handleEvent(StopSession) {longTaskCount != 0}`(
        @LongForgery(1, 1024) count: Long
    ) {
        // Given
        testedScope.longTaskCount = count

        // When
        fakeEvent = RumRawEvent.StopSession()
        val result = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasId(testedScope.actionId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasLongTaskCount(count)
                    hasNoFrustration()
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toAction() ?: ActionEvent.ActionEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture,
                        fakeDatadogContext.deviceInfo.isLowRam,
                        fakeDatadogContext.deviceInfo.logicalCpuCount,
                        fakeDatadogContext.deviceInfo.totalRam
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasBuildVersion(fakeDatadogContext.versionCode)
                    hasBuildId(fakeDatadogContext.appBuildId)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M send Action immediately W handleEvent(StopSession) {errorCount != 0}`(
        @LongForgery(1, 1024) count: Long
    ) {
        // Given
        testedScope.errorCount = count

        // When
        fakeEvent = RumRawEvent.StopSession()
        val result = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasId(testedScope.actionId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(0)
                    hasErrorCount(count)
                    hasCrashCount(0)
                    hasLongTaskCount(0)
                    if (fakeType == RumActionType.TAP) {
                        hasFrustration(ActionEvent.Type.ERROR_TAP)
                    } else {
                        hasNoFrustration()
                    }
                    hasView(fakeParentContext)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toAction() ?: ActionEvent.ActionEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture,
                        fakeDatadogContext.deviceInfo.isLowRam,
                        fakeDatadogContext.deviceInfo.logicalCpuCount,
                        fakeDatadogContext.deviceInfo.totalRam
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasBuildVersion(fakeDatadogContext.versionCode)
                    hasBuildId(fakeDatadogContext.appBuildId)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M send Action immediately W handleEvent(StopSession) {crashCount != 0}`(
        @LongForgery(1, 1024) nonFatalCount: Long,
        @LongForgery(1, 1024) fatalCount: Long
    ) {
        // Given
        testedScope.errorCount = nonFatalCount + fatalCount
        testedScope.crashCount = fatalCount

        // When
        fakeEvent = RumRawEvent.StopSession()
        val result = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasId(testedScope.actionId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(0)
                    hasErrorCount(nonFatalCount + fatalCount)
                    hasCrashCount(fatalCount)
                    hasLongTaskCount(0)
                    if (fakeType == RumActionType.TAP) {
                        hasFrustration(ActionEvent.Type.ERROR_TAP)
                    } else {
                        hasNoFrustration()
                    }
                    hasView(fakeParentContext)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toAction() ?: ActionEvent.ActionEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture,
                        fakeDatadogContext.deviceInfo.isLowRam,
                        fakeDatadogContext.deviceInfo.logicalCpuCount,
                        fakeDatadogContext.deviceInfo.totalRam
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasBuildVersion(fakeDatadogContext.versionCode)
                    hasBuildId(fakeDatadogContext.appBuildId)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M send Action with synthetics info after threshold W init()+handleEvent(any) `(
        @StringForgery fakeTestId: String,
        @StringForgery fakeResultId: String,
        forge: Forge
    ) {
        // Given
        val fakeParentAttributes = forge.aFilteredMap(excludedKeys = fakeAttributes.keys) {
            anHexadecimalString() to anAsciiString()
        }
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeParentAttributes)
        expectedAttributes.putAll(fakeAttributes)
        whenever(mockParentScope.getCustomAttributes()) doReturn fakeParentAttributes

        testedScope = RumActionScope(
            parentScope = mockParentScope,
            sdkCore = rumMonitor.mockSdkCore,
            waitForStop = false,
            eventTime = fakeEventTime,
            initialType = fakeType,
            initialName = fakeName,
            initialAttributes = fakeAttributes,
            serverTimeOffsetInMs = fakeServerOffset,
            inactivityThresholdMs = TEST_INACTIVITY_MS,
            maxDurationMs = TEST_MAX_DURATION_MS,
            featuresContextResolver = mockFeaturesContextResolver,
            trackFrustrations = fakeTrackFrustrations,
            sampleRate = fakeSampleRate,
            rumSessionTypeOverride = fakeRumSessionType,
            insightsCollector = mockInsightsCollector
        )
        fakeParentContext = fakeParentContext.copy(
            syntheticsTestId = fakeTestId,
            syntheticsResultId = fakeResultId
        )
        whenever(mockParentScope.getRumContext()) doReturn fakeParentContext

        // When
        val result = testedScope.handleEvent(
            mockEvent(TEST_INACTIVITY_MS + 1),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasId(testedScope.actionId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasLongTaskCount(0)
                    hasNoFrustration()
                    hasView(fakeParentContext)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toAction() ?: ActionEvent.ActionEventSessionType.SYNTHETICS)
                    hasSyntheticsTest(fakeTestId, fakeResultId)
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(expectedAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture,
                        fakeDatadogContext.deviceInfo.isLowRam,
                        fakeDatadogContext.deviceInfo.logicalCpuCount,
                        fakeDatadogContext.deviceInfo.totalRam
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasBuildVersion(fakeDatadogContext.versionCode)
                    hasBuildId(fakeDatadogContext.appBuildId)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M send Action with parent attributes after threshold W init()+handleEvent(any) `(
        forge: Forge
    ) {
        // Given
        val fakeParentAttributes = forge.aFilteredMap(excludedKeys = fakeAttributes.keys) {
            anHexadecimalString() to anAsciiString()
        }
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeParentAttributes)
        expectedAttributes.putAll(fakeAttributes)
        whenever(mockParentScope.getCustomAttributes()) doReturn fakeParentAttributes
        testedScope = RumActionScope(
            parentScope = mockParentScope,
            sdkCore = rumMonitor.mockSdkCore,
            waitForStop = false,
            eventTime = fakeEventTime,
            initialType = fakeType,
            initialName = fakeName,
            initialAttributes = fakeAttributes,
            serverTimeOffsetInMs = fakeServerOffset,
            inactivityThresholdMs = TEST_INACTIVITY_MS,
            maxDurationMs = TEST_MAX_DURATION_MS,
            featuresContextResolver = mockFeaturesContextResolver,
            trackFrustrations = fakeTrackFrustrations,
            sampleRate = fakeSampleRate,
            rumSessionTypeOverride = fakeRumSessionType,
            insightsCollector = mockInsightsCollector
        )

        // When
        val result = testedScope.handleEvent(
            mockEvent(TEST_INACTIVITY_MS + 1),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasId(testedScope.actionId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasLongTaskCount(0)
                    hasNoFrustration()
                    hasView(fakeParentContext)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toAction() ?: ActionEvent.ActionEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(expectedAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture,
                        fakeDatadogContext.deviceInfo.isLowRam,
                        fakeDatadogContext.deviceInfo.logicalCpuCount,
                        fakeDatadogContext.deviceInfo.totalRam
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasBuildVersion(fakeDatadogContext.versionCode)
                    hasBuildId(fakeDatadogContext.appBuildId)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M send Action with parent attributes after threshold W handleEvent(any)`(
        forge: Forge
    ) {
        // Given
        val fakeParentAttributes = forge.aFilteredMap(excludedKeys = fakeAttributes.keys) {
            anHexadecimalString() to anAsciiString()
        }
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeParentAttributes)
        expectedAttributes.putAll(fakeAttributes)
        whenever(mockParentScope.getCustomAttributes()) doReturn fakeParentAttributes

        // When
        val result = testedScope.handleEvent(
            mockEvent(TEST_INACTIVITY_MS + 1),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasId(testedScope.actionId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasLongTaskCount(0)
                    hasNoFrustration()
                    hasView(fakeParentContext)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toAction() ?: ActionEvent.ActionEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(expectedAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture,
                        fakeDatadogContext.deviceInfo.isLowRam,
                        fakeDatadogContext.deviceInfo.logicalCpuCount,
                        fakeDatadogContext.deviceInfo.totalRam
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasBuildVersion(fakeDatadogContext.versionCode)
                    hasBuildId(fakeDatadogContext.appBuildId)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M send event with user extra attributes W handleEvent(any)`() {
        // When
        val result = testedScope.handleEvent(
            mockEvent(TEST_INACTIVITY_MS + 1),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasId(testedScope.actionId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasLongTaskCount(0)
                    hasNoFrustration()
                    hasView(fakeParentContext)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toAction() ?: ActionEvent.ActionEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture,
                        fakeDatadogContext.deviceInfo.isLowRam,
                        fakeDatadogContext.deviceInfo.logicalCpuCount,
                        fakeDatadogContext.deviceInfo.totalRam
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasBuildVersion(fakeDatadogContext.versionCode)
                    hasBuildId(fakeDatadogContext.appBuildId)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M send Action after threshold W handleEvent(any) {resourceCount != 0}`(
        @LongForgery(1, 1024) count: Long
    ) {
        // Given
        testedScope.resourceCount = count

        // When
        val result = testedScope.handleEvent(
            mockEvent(TEST_INACTIVITY_MS + 1),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasId(testedScope.actionId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(count)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasLongTaskCount(0)
                    hasNoFrustration()
                    hasView(fakeParentContext)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toAction() ?: ActionEvent.ActionEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture,
                        fakeDatadogContext.deviceInfo.isLowRam,
                        fakeDatadogContext.deviceInfo.logicalCpuCount,
                        fakeDatadogContext.deviceInfo.totalRam
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasBuildVersion(fakeDatadogContext.versionCode)
                    hasBuildId(fakeDatadogContext.appBuildId)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M send Action after threshold W handleEvent(any) {errorCount != 0}`(
        @LongForgery(1, 1024) count: Long
    ) {
        // Given
        testedScope.errorCount = count

        // When
        val result = testedScope.handleEvent(
            mockEvent(TEST_INACTIVITY_MS + 1),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasId(testedScope.actionId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(0)
                    hasErrorCount(count)
                    hasCrashCount(0)
                    hasLongTaskCount(0)
                    if (fakeType == RumActionType.TAP) {
                        hasFrustration(ActionEvent.Type.ERROR_TAP)
                    } else {
                        hasNoFrustration()
                    }
                    hasView(fakeParentContext)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toAction() ?: ActionEvent.ActionEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture,
                        fakeDatadogContext.deviceInfo.isLowRam,
                        fakeDatadogContext.deviceInfo.logicalCpuCount,
                        fakeDatadogContext.deviceInfo.totalRam
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasBuildVersion(fakeDatadogContext.versionCode)
                    hasBuildId(fakeDatadogContext.appBuildId)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M send Action after threshold W handleEvent(any) {crashCount != 0}`(
        @LongForgery(1, 1024) nonFatalCount: Long,
        @LongForgery(1, 1024) fatalCount: Long
    ) {
        // Given
        testedScope.errorCount = nonFatalCount + fatalCount
        testedScope.crashCount = fatalCount

        // When
        val result = testedScope.handleEvent(
            mockEvent(TEST_INACTIVITY_MS + 1),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasId(testedScope.actionId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasErrorCount(nonFatalCount + fatalCount)
                    hasCrashCount(fatalCount)
                    hasLongTaskCount(0)
                    if (fakeType == RumActionType.TAP) {
                        hasFrustration(ActionEvent.Type.ERROR_TAP)
                    } else {
                        hasNoFrustration()
                    }
                    hasView(fakeParentContext)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toAction() ?: ActionEvent.ActionEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture,
                        fakeDatadogContext.deviceInfo.isLowRam,
                        fakeDatadogContext.deviceInfo.logicalCpuCount,
                        fakeDatadogContext.deviceInfo.totalRam
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasBuildVersion(fakeDatadogContext.versionCode)
                    hasBuildId(fakeDatadogContext.appBuildId)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M send Action only once W handleEvent(any) twice`() {
        // When
        val result = testedScope.handleEvent(
            mockEvent(TEST_INACTIVITY_MS + 1),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )
        val result2 = testedScope.handleEvent(mockEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasId(testedScope.actionId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasLongTaskCount(0)
                    hasNoFrustration()
                    hasView(fakeParentContext)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toAction() ?: ActionEvent.ActionEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture,
                        fakeDatadogContext.deviceInfo.isLowRam,
                        fakeDatadogContext.deviceInfo.logicalCpuCount,
                        fakeDatadogContext.deviceInfo.totalRam
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasBuildVersion(fakeDatadogContext.versionCode)
                    hasBuildId(fakeDatadogContext.appBuildId)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
        assertThat(result2).isNull()
    }

    @Test
    fun `M send Action W handleEvent(StartView) {no side effect}`() {
        // Given
        testedScope.resourceCount = 0
        testedScope.errorCount = 0
        testedScope.crashCount = 0
        fakeEvent = RumRawEvent.StartView(RumScopeKey.from(Object()), emptyMap())

        // When
        val result = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasId(testedScope.actionId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasLongTaskCount(0)
                    hasNoFrustration()
                    hasView(fakeParentContext)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toAction() ?: ActionEvent.ActionEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture,
                        fakeDatadogContext.deviceInfo.isLowRam,
                        fakeDatadogContext.deviceInfo.logicalCpuCount,
                        fakeDatadogContext.deviceInfo.totalRam
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasBuildVersion(fakeDatadogContext.versionCode)
                    hasBuildId(fakeDatadogContext.appBuildId)
                    hasSampleRate(fakeSampleRate)
                }
        }
        assertThat(result).isNull()
    }

    @Test
    fun `M send Action event W handleEvent(StartView) {no side effect}`() {
        // Given
        testedScope.resourceCount = 0
        testedScope.errorCount = 0
        testedScope.crashCount = 0
        testedScope.longTaskCount = 0
        fakeEvent = RumRawEvent.StartView(RumScopeKey.from(Object()), emptyMap())

        // When
        val result = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasId(testedScope.actionId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasLongTaskCount(0)
                    hasNoFrustration()
                    hasView(fakeParentContext)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toAction() ?: ActionEvent.ActionEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture,
                        fakeDatadogContext.deviceInfo.isLowRam,
                        fakeDatadogContext.deviceInfo.logicalCpuCount,
                        fakeDatadogContext.deviceInfo.totalRam
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasBuildVersion(fakeDatadogContext.versionCode)
                    hasBuildId(fakeDatadogContext.appBuildId)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
        assertThat(result).isNull()
    }

    @Test
    fun `M send Action W handleEvent(StopView) {no side effect}`() {
        // Given
        testedScope.resourceCount = 0
        testedScope.errorCount = 0
        testedScope.crashCount = 0
        fakeEvent = RumRawEvent.StopView(RumScopeKey.from(Object()), emptyMap())

        // When
        val result = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasId(testedScope.actionId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasLongTaskCount(0)
                    hasNoFrustration()
                    hasView(fakeParentContext)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toAction() ?: ActionEvent.ActionEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture,
                        fakeDatadogContext.deviceInfo.isLowRam,
                        fakeDatadogContext.deviceInfo.logicalCpuCount,
                        fakeDatadogContext.deviceInfo.totalRam
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasBuildVersion(fakeDatadogContext.versionCode)
                    hasBuildId(fakeDatadogContext.appBuildId)
                    hasSampleRate(fakeSampleRate)
                }
        }
        assertThat(result).isNull()
    }

    @Test
    fun `M send Action W handleEvent(StopSession) {no side effect}`() {
        // Given
        testedScope.resourceCount = 0
        testedScope.errorCount = 0
        testedScope.crashCount = 0
        testedScope.longTaskCount = 0
        fakeEvent = RumRawEvent.StopSession()

        // When
        val result = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasId(testedScope.actionId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasLongTaskCount(0)
                    hasNoFrustration()
                    hasView(fakeParentContext)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toAction() ?: ActionEvent.ActionEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture,
                        fakeDatadogContext.deviceInfo.isLowRam,
                        fakeDatadogContext.deviceInfo.logicalCpuCount,
                        fakeDatadogContext.deviceInfo.totalRam
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasBuildVersion(fakeDatadogContext.versionCode)
                    hasBuildId(fakeDatadogContext.appBuildId)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
        assertThat(result).isNull()
    }

    @Test
    fun `M send Action after threshold W handleEvent(any) {no side effect}`() {
        // Given
        testedScope.resourceCount = 0
        testedScope.errorCount = 0
        testedScope.crashCount = 0
        testedScope.longTaskCount = 0
        fakeEvent = mockEvent(TEST_INACTIVITY_MS + 1)

        // When
        val result = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasId(testedScope.actionId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasLongTaskCount(0)
                    hasNoFrustration()
                    hasView(fakeParentContext)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toAction() ?: ActionEvent.ActionEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture,
                        fakeDatadogContext.deviceInfo.isLowRam,
                        fakeDatadogContext.deviceInfo.logicalCpuCount,
                        fakeDatadogContext.deviceInfo.totalRam
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasBuildVersion(fakeDatadogContext.versionCode)
                    hasBuildId(fakeDatadogContext.appBuildId)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
        assertThat(result).isNull()
    }

    @Test
    fun `M doNothing W handleEvent(any) before threshold`() {
        // Given
        fakeEvent = mockEvent()

        // When
        val result = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        verifyNoInteractions(mockWriter, mockParentScope)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M doNothing W handleEvent(StartResource+any)`(
        @StringForgery key: String,
        @Forgery method: RumResourceMethod,
        @StringForgery(regex = "http(s?)://[a-z]+\\.com/[a-z]+") url: String
    ) {
        // When
        fakeEvent = RumRawEvent.StartResource(key, url, method, emptyMap(), fakeEventTime)
        val result = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)
        val result2 = testedScope.handleEvent(
            mockEvent(TEST_INACTIVITY_MS * 2 + 1),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )

        // Then
        verifyNoInteractions(mockWriter, mockParentScope)
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isSameAs(testedScope)
    }

    @Test
    fun `M send Action after timeout W handleEvent(StartResource+any)`(
        @StringForgery key: String,
        @Forgery method: RumResourceMethod,
        @StringForgery(regex = "http(s?)://[a-z]+\\.com/[a-z]+") url: String
    ) {
        // When
        fakeEvent = RumRawEvent.StartResource(key, url, method, emptyMap(), fakeEventTime)
        val result = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)
        val result2 = testedScope.handleEvent(
            mockEvent(TEST_MAX_DURATION_MS + 1),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasId(testedScope.actionId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(TEST_MAX_DURATION_NS)
                    hasResourceCount(1)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasLongTaskCount(0)
                    hasNoFrustration()
                    hasView(fakeParentContext)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toAction() ?: ActionEvent.ActionEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture,
                        fakeDatadogContext.deviceInfo.isLowRam,
                        fakeDatadogContext.deviceInfo.logicalCpuCount,
                        fakeDatadogContext.deviceInfo.totalRam
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasBuildVersion(fakeDatadogContext.versionCode)
                    hasBuildId(fakeDatadogContext.appBuildId)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isNull()
    }

    @Test
    fun `M send Action after timeout W handleEvent(any)`() {
        // When
        val result = testedScope.handleEvent(
            mockEvent(TEST_INACTIVITY_MS + 1),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasId(testedScope.actionId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasLongTaskCount(0)
                    hasNoFrustration()
                    hasView(fakeParentContext)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toAction() ?: ActionEvent.ActionEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture,
                        fakeDatadogContext.deviceInfo.isLowRam,
                        fakeDatadogContext.deviceInfo.logicalCpuCount,
                        fakeDatadogContext.deviceInfo.totalRam
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasBuildVersion(fakeDatadogContext.versionCode)
                    hasBuildId(fakeDatadogContext.appBuildId)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M send custom Action after timeout W handleEvent(any) and no side effect`() {
        // When
        testedScope.type = RumActionType.CUSTOM
        val result = testedScope.handleEvent(
            mockEvent(TEST_INACTIVITY_MS + 1),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasId(testedScope.actionId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasType(RumActionType.CUSTOM)
                    hasTargetName(fakeName)
                    hasDuration(1)
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasLongTaskCount(0)
                    hasNoFrustration()
                    hasView(fakeParentContext)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toAction() ?: ActionEvent.ActionEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture,
                        fakeDatadogContext.deviceInfo.isLowRam,
                        fakeDatadogContext.deviceInfo.logicalCpuCount,
                        fakeDatadogContext.deviceInfo.totalRam
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasBuildVersion(fakeDatadogContext.versionCode)
                    hasBuildId(fakeDatadogContext.appBuildId)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M send Action W handleEvent(SendCustomActionNow)`() {
        // When
        testedScope.type = RumActionType.CUSTOM
        val event = RumRawEvent.SendCustomActionNow()
        val result = testedScope.handleEvent(event, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasId(testedScope.actionId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasType(ActionEvent.ActionEventActionType.CUSTOM)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasLongTaskCount(0)
                    hasNoFrustration()
                    hasView(fakeParentContext)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toAction() ?: ActionEvent.ActionEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture,
                        fakeDatadogContext.deviceInfo.isLowRam,
                        fakeDatadogContext.deviceInfo.logicalCpuCount,
                        fakeDatadogContext.deviceInfo.totalRam
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasBuildVersion(fakeDatadogContext.versionCode)
                    hasBuildId(fakeDatadogContext.appBuildId)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M send Action without frustrations W handleEvent(AddError+Tap) {trackFrustration = false}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @StringForgery name: String
    ) {
        // Given
        testedScope = RumActionScope(
            parentScope = mockParentScope,
            sdkCore = rumMonitor.mockSdkCore,
            waitForStop = false,
            eventTime = fakeEventTime,
            initialType = fakeType,
            initialName = fakeName,
            initialAttributes = fakeAttributes,
            serverTimeOffsetInMs = fakeServerOffset,
            inactivityThresholdMs = TEST_INACTIVITY_MS,
            maxDurationMs = TEST_MAX_DURATION_MS,
            trackFrustrations = false,
            sampleRate = fakeSampleRate,
            rumSessionTypeOverride = fakeRumSessionType,
            insightsCollector = mockInsightsCollector
        )

        // When
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace = null,
            isFatal = false,
            threads = emptyList(),
            attributes = emptyMap(),
            eventTime = fakeEventTime
        )
        val result = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        fakeEvent = RumRawEvent.StartAction(
            type = RumActionType.TAP,
            name = name,
            waitForStop = false,
            attributes = emptyMap(),
            eventTime = timeWithOffset(TEST_INACTIVITY_MS * 2 + 1)
        )
        val result2 = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasId(testedScope.actionId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(0)
                    hasErrorCount(1)
                    hasCrashCount(0)
                    hasLongTaskCount(0)
                    hasNoFrustration()
                    hasView(fakeParentContext)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toAction() ?: ActionEvent.ActionEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture,
                        fakeDatadogContext.deviceInfo.isLowRam,
                        fakeDatadogContext.deviceInfo.logicalCpuCount,
                        fakeDatadogContext.deviceInfo.totalRam
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasBuildVersion(fakeDatadogContext.versionCode)
                    hasBuildId(fakeDatadogContext.appBuildId)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any(), any(), any())
        verifyNoMoreInteractions(mockWriter)

        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isNull()
    }

    @Test
    fun `M notify about success W handleEvent() { write succeeded }`() {
        // Given
        val event = RumRawEvent.SendCustomActionNow()

        // When
        testedScope.type = RumActionType.CUSTOM
        testedScope.handleEvent(event, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        verify(rumMonitor.mockInstance as AdvancedRumMonitor)
            .eventSent(
                fakeParentContext.viewId.orEmpty(),
                StorageEvent.Action(0, ActionEvent.ActionEventActionType.CUSTOM, testedScope.startedNanos)
            )
    }

    @Test
    fun `M notify about error W handleEvent() { write failed }`() {
        // Given
        val event = RumRawEvent.SendCustomActionNow()

        // When
        testedScope.type = RumActionType.CUSTOM
        whenever(mockWriter.write(eq(mockEventBatchWriter), isA<ActionEvent>(), eq(EventType.DEFAULT))) doReturn false
        testedScope.handleEvent(event, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        verify(rumMonitor.mockInstance as AdvancedRumMonitor)
            .eventDropped(
                fakeParentContext.viewId.orEmpty(),
                StorageEvent.Action(0, ActionEvent.ActionEventActionType.CUSTOM, testedScope.startedNanos)
            )
    }

    @Test
    fun `M notify about error W handleEvent() { write throws }`(
        forge: Forge
    ) {
        // Given
        val event = RumRawEvent.SendCustomActionNow()

        // When
        testedScope.type = RumActionType.CUSTOM
        whenever(mockWriter.write(eq(mockEventBatchWriter), isA<ActionEvent>(), eq(EventType.DEFAULT)))
            .doThrow(forge.anException())
        testedScope.handleEvent(event, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        verify(rumMonitor.mockInstance as AdvancedRumMonitor)
            .eventDropped(
                fakeParentContext.viewId.orEmpty(),
                StorageEvent.Action(0, ActionEvent.ActionEventActionType.CUSTOM, testedScope.startedNanos)
            )
    }

    @Test
    fun `M populate heatmap fields W sendAction() {viewIdentity present}`(
        @StringForgery fakeViewIdentity: String,
        @LongForgery fakeViewKey: Long,
        @LongForgery fakeWidth: Long,
        @LongForgery fakeHeight: Long,
        @LongForgery fakePosX: Long,
        @LongForgery fakePosY: Long
    ) {
        // Given
        val mockHeatmapRegistry: HeatmapIdentifierRegistry = mock()
        whenever(mockHeatmapRegistry.getHeatmapIdentifier(fakeViewKey, fakeParentContext.viewUrl.orEmpty()))
            .thenReturn(HeatmapIdentifier(fakeViewIdentity))

        testedScope = RumActionScope(
            parentScope = mockParentScope,
            sdkCore = rumMonitor.mockSdkCore,
            waitForStop = false,
            eventTime = fakeEventTime,
            initialType = fakeType,
            initialName = fakeName,
            initialAttributes = fakeAttributes,
            serverTimeOffsetInMs = fakeServerOffset,
            inactivityThresholdMs = TEST_INACTIVITY_MS,
            maxDurationMs = TEST_MAX_DURATION_MS,
            featuresContextResolver = mockFeaturesContextResolver,
            trackFrustrations = true,
            sampleRate = fakeSampleRate,
            rumSessionTypeOverride = fakeRumSessionType,
            insightsCollector = mockInsightsCollector,
            heatmapResolver = HeatmapActionResolver.Native(
                data = NativeHeatmapActionData(
                    viewKey = fakeViewKey,
                    positionX = fakePosX,
                    positionY = fakePosY,
                    targetWidth = fakeWidth,
                    targetHeight = fakeHeight
                ),
                registry = mockHeatmapRegistry
            )
        )

        // When
        testedScope.handleEvent(
            mockEvent(TEST_INACTIVITY_MS + 1),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .hasPermanentId(fakeViewIdentity)
                .hasTargetWidth(fakeWidth)
                .hasTargetHeight(fakeHeight)
                .hasPositionX(fakePosX)
                .hasPositionY(fakePosY)
        }
    }

    @Test
    fun `M omit dd action W sendAction() {registry returns null for viewKey}`(
        @LongForgery fakeViewKey: Long,
        @LongForgery fakeWidth: Long,
        @LongForgery fakeHeight: Long,
        @LongForgery fakePosX: Long,
        @LongForgery fakePosY: Long
    ) {
        // Given
        val mockHeatmapRegistry: HeatmapIdentifierRegistry = mock()
        whenever(mockHeatmapRegistry.getHeatmapIdentifier(any(), any())).thenReturn(null)

        testedScope = RumActionScope(
            parentScope = mockParentScope,
            sdkCore = rumMonitor.mockSdkCore,
            waitForStop = false,
            eventTime = fakeEventTime,
            initialType = fakeType,
            initialName = fakeName,
            initialAttributes = fakeAttributes,
            serverTimeOffsetInMs = fakeServerOffset,
            inactivityThresholdMs = TEST_INACTIVITY_MS,
            maxDurationMs = TEST_MAX_DURATION_MS,
            featuresContextResolver = mockFeaturesContextResolver,
            trackFrustrations = true,
            sampleRate = fakeSampleRate,
            rumSessionTypeOverride = fakeRumSessionType,
            insightsCollector = mockInsightsCollector,
            heatmapResolver = HeatmapActionResolver.Native(
                data = NativeHeatmapActionData(
                    viewKey = fakeViewKey,
                    positionX = fakePosX,
                    positionY = fakePosY,
                    targetWidth = fakeWidth,
                    targetHeight = fakeHeight
                ),
                registry = mockHeatmapRegistry
            )
        )

        // When
        testedScope.handleEvent(
            mockEvent(TEST_INACTIVITY_MS + 1),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue.dd.action).isNull()
        }
    }

    @Test
    fun `M omit dd action W sendAction() {registry is null}`(
        @LongForgery fakeViewKey: Long,
        @LongForgery fakePosX: Long,
        @LongForgery fakePosY: Long
    ) {
        // Given — heatmapData is set but no registry is provided
        testedScope = RumActionScope(
            parentScope = mockParentScope,
            sdkCore = rumMonitor.mockSdkCore,
            waitForStop = false,
            eventTime = fakeEventTime,
            initialType = fakeType,
            initialName = fakeName,
            initialAttributes = fakeAttributes,
            serverTimeOffsetInMs = fakeServerOffset,
            inactivityThresholdMs = TEST_INACTIVITY_MS,
            maxDurationMs = TEST_MAX_DURATION_MS,
            featuresContextResolver = mockFeaturesContextResolver,
            trackFrustrations = true,
            sampleRate = fakeSampleRate,
            rumSessionTypeOverride = fakeRumSessionType,
            insightsCollector = mockInsightsCollector,
            heatmapResolver = HeatmapActionResolver.Native(
                data = NativeHeatmapActionData(
                    viewKey = fakeViewKey,
                    positionX = fakePosX,
                    positionY = fakePosY,
                    targetWidth = null,
                    targetHeight = null
                ),
                registry = null
            )
        )

        // When
        testedScope.handleEvent(
            mockEvent(TEST_INACTIVITY_MS + 1),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue.dd.action).isNull()
        }
    }

    @Test
    fun `M omit dd action W sendAction() {no heatmap attributes}`() {
        // Given
        testedScope = RumActionScope(
            parentScope = mockParentScope,
            sdkCore = rumMonitor.mockSdkCore,
            waitForStop = false,
            eventTime = fakeEventTime,
            initialType = fakeType,
            initialName = fakeName,
            initialAttributes = fakeAttributes,
            serverTimeOffsetInMs = fakeServerOffset,
            inactivityThresholdMs = TEST_INACTIVITY_MS,
            maxDurationMs = TEST_MAX_DURATION_MS,
            featuresContextResolver = mockFeaturesContextResolver,
            trackFrustrations = true,
            sampleRate = fakeSampleRate,
            rumSessionTypeOverride = fakeRumSessionType,
            insightsCollector = mockInsightsCollector
        )

        // When
        testedScope.handleEvent(
            mockEvent(TEST_INACTIVITY_MS + 1),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue.dd.action).isNull()
        }
    }

    @Test
    fun `M populate heatmap fields W sendAction() {crossPlatformHeatmapActionData set}`(
        @Forgery fakeHeatmapAttributes: CrossPlatformHeatmapActionData,
        @StringForgery fakeAppPackageName: String
    ) {
        val matchingHeatmapAttributes = fakeHeatmapAttributes.copy(
            viewUrl = fakeParentContext.viewUrl.orEmpty()
        )
        val expectedPermanentId = HeatmapIdentifier.create(
            elementPath = matchingHeatmapAttributes.elementPath,
            screenName = fakeParentContext.viewUrl.orEmpty(),
            appPackageName = fakeAppPackageName
        )?.rawValue
        testedScope = RumActionScope(
            parentScope = mockParentScope,
            sdkCore = rumMonitor.mockSdkCore,
            waitForStop = false,
            eventTime = fakeEventTime,
            initialType = fakeType,
            initialName = fakeName,
            initialAttributes = fakeAttributes,
            serverTimeOffsetInMs = fakeServerOffset,
            inactivityThresholdMs = TEST_INACTIVITY_MS,
            maxDurationMs = TEST_MAX_DURATION_MS,
            featuresContextResolver = mockFeaturesContextResolver,
            trackFrustrations = true,
            sampleRate = fakeSampleRate,
            rumSessionTypeOverride = fakeRumSessionType,
            insightsCollector = mockInsightsCollector,
            heatmapResolver = HeatmapActionResolver.CrossPlatform(
                data = matchingHeatmapAttributes,
                appPackageName = fakeAppPackageName,
                logger = rumMonitor.mockSdkCore.internalLogger
            )
        )

        // When
        testedScope.handleEvent(
            mockEvent(TEST_INACTIVITY_MS + 1),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .hasPermanentId(checkNotNull(expectedPermanentId))
                .hasPositionX(fakeHeatmapAttributes.positionX)
                .hasPositionY(fakeHeatmapAttributes.positionY)
                .hasTargetWidth(fakeHeatmapAttributes.targetWidth)
                .hasTargetHeight(fakeHeatmapAttributes.targetHeight)
        }
    }

    @Test
    fun `M prefer crossPlatformHeatmapActionData over nativeHeatmapActionData W sendAction() {both set}`(
        @Forgery fakeHeatmapAttributes: CrossPlatformHeatmapActionData,
        @StringForgery fakeAppPackageName: String
    ) {
        // StartAction.init enforces mutual exclusivity, so RumActionScope is constructed directly here.
        val mockHeatmapRegistry: HeatmapIdentifierRegistry = mock()
        whenever(mockHeatmapRegistry.getHeatmapIdentifier(any(), any()))
            .thenReturn(HeatmapIdentifier("native-id"))
        val matchingHeatmapAttributes = fakeHeatmapAttributes.copy(
            viewUrl = fakeParentContext.viewUrl.orEmpty()
        )
        val expectedPermanentId = HeatmapIdentifier.create(
            elementPath = matchingHeatmapAttributes.elementPath,
            screenName = fakeParentContext.viewUrl.orEmpty(),
            appPackageName = fakeAppPackageName
        )?.rawValue

        testedScope = RumActionScope(
            parentScope = mockParentScope,
            sdkCore = rumMonitor.mockSdkCore,
            waitForStop = false,
            eventTime = fakeEventTime,
            initialType = fakeType,
            initialName = fakeName,
            initialAttributes = fakeAttributes,
            serverTimeOffsetInMs = fakeServerOffset,
            inactivityThresholdMs = TEST_INACTIVITY_MS,
            maxDurationMs = TEST_MAX_DURATION_MS,
            featuresContextResolver = mockFeaturesContextResolver,
            trackFrustrations = true,
            sampleRate = fakeSampleRate,
            rumSessionTypeOverride = fakeRumSessionType,
            insightsCollector = mockInsightsCollector,
            heatmapResolver = HeatmapActionResolver.CrossPlatform(
                data = matchingHeatmapAttributes,
                appPackageName = fakeAppPackageName,
                logger = rumMonitor.mockSdkCore.internalLogger
            )
        )

        // When
        testedScope.handleEvent(
            mockEvent(TEST_INACTIVITY_MS + 1),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )

        verifyNoInteractions(mockHeatmapRegistry)
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue).hasPermanentId(checkNotNull(expectedPermanentId))
        }
    }

    @Test
    fun `M drop heatmap data and warn W sendAction() {heatmapAttributes viewUrl mismatches current view}`(
        @Forgery fakeHeatmapAttributes: CrossPlatformHeatmapActionData,
        @StringForgery fakeAppPackageName: String,
        @StringForgery fakeDifferentViewUrl: String
    ) {
        // Given — simulate a race: the action was recorded for a different view than the one
        // that is active when sendAction() fires (e.g. navigation happened on the async hop
        // between the cross-platform tap and Android's RUM event queue processing).
        val staleViewUrl = if (fakeDifferentViewUrl != fakeParentContext.viewUrl.orEmpty()) {
            fakeDifferentViewUrl
        } else {
            "$fakeDifferentViewUrl#other"
        }
        val staleHeatmapAttributes = fakeHeatmapAttributes.copy(viewUrl = staleViewUrl)

        testedScope = RumActionScope(
            parentScope = mockParentScope,
            sdkCore = rumMonitor.mockSdkCore,
            waitForStop = false,
            eventTime = fakeEventTime,
            initialType = fakeType,
            initialName = fakeName,
            initialAttributes = fakeAttributes,
            serverTimeOffsetInMs = fakeServerOffset,
            inactivityThresholdMs = TEST_INACTIVITY_MS,
            maxDurationMs = TEST_MAX_DURATION_MS,
            featuresContextResolver = mockFeaturesContextResolver,
            trackFrustrations = true,
            sampleRate = fakeSampleRate,
            rumSessionTypeOverride = fakeRumSessionType,
            insightsCollector = mockInsightsCollector,
            heatmapResolver = HeatmapActionResolver.CrossPlatform(
                data = staleHeatmapAttributes,
                appPackageName = fakeAppPackageName,
                logger = rumMonitor.mockSdkCore.internalLogger
            )
        )

        // When
        testedScope.handleEvent(
            mockEvent(TEST_INACTIVITY_MS + 1),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )

        // Then — heatmap fields are absent; a developer warning was logged
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue.dd.action).isNull()
        }
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.WARN),
            eq(InternalLogger.Target.USER),
            any(),
            isNull(),
            eq(false),
            isNull()
        )
    }

    @Test
    fun `M drop heatmap data and log user error W sendAction() {empty elementPath}`(
        @Forgery fakeHeatmapAttributes: CrossPlatformHeatmapActionData,
        @StringForgery fakeAppPackageName: String
    ) {
        // Given — elementPath is empty; every tap on the same screen would collide to the same hash
        val emptyPathAttributes = fakeHeatmapAttributes.copy(
            elementPath = emptyList(),
            viewUrl = fakeParentContext.viewUrl.orEmpty()
        )
        testedScope = RumActionScope(
            parentScope = mockParentScope,
            sdkCore = rumMonitor.mockSdkCore,
            waitForStop = false,
            eventTime = fakeEventTime,
            initialType = fakeType,
            initialName = fakeName,
            initialAttributes = fakeAttributes,
            serverTimeOffsetInMs = fakeServerOffset,
            inactivityThresholdMs = TEST_INACTIVITY_MS,
            maxDurationMs = TEST_MAX_DURATION_MS,
            featuresContextResolver = mockFeaturesContextResolver,
            trackFrustrations = true,
            sampleRate = fakeSampleRate,
            rumSessionTypeOverride = fakeRumSessionType,
            insightsCollector = mockInsightsCollector,
            heatmapResolver = HeatmapActionResolver.CrossPlatform(
                data = emptyPathAttributes,
                appPackageName = fakeAppPackageName,
                logger = rumMonitor.mockSdkCore.internalLogger
            )
        )

        // When
        testedScope.handleEvent(
            mockEvent(TEST_INACTIVITY_MS + 1),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue.dd.action).isNull()
        }
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.ERROR),
            eq(InternalLogger.Target.USER),
            any(),
            isNull(),
            eq(false),
            isNull()
        )
    }

    // region Internal

    private fun mockEvent(timeOffset: Long = 0L): RumRawEvent {
        val event: RumRawEvent = mock()
        whenever(event.eventTime) doReturn timeWithOffset(timeOffset)
        return event
    }

    private fun timeWithOffset(offsetMs: Long): Time {
        return Time(
            fakeEventTime.timestamp + offsetMs,
            fakeEventTime.nanoTime + TimeUnit.MILLISECONDS.toNanos(offsetMs)
        )
    }

    private fun resolveExpectedTimestamp(): Long {
        return fakeEventTime.timestamp + fakeServerOffset
    }

    // endregion

    companion object {
        internal const val TEST_INACTIVITY_MS = 50L
        internal const val TEST_MAX_DURATION_MS = 500L
        internal val TEST_MAX_DURATION_NS = TimeUnit.MILLISECONDS.toNanos(TEST_MAX_DURATION_MS)

        val rumMonitor = GlobalRumMonitorTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(rumMonitor)
        }
    }
}
