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
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.RumResourceMethod
import com.datadog.android.rum.assertj.ActionEventAssert.Companion.assertThat
import com.datadog.android.rum.internal.FeaturesContextResolver
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.internal.monitor.StorageEvent
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.TimeUnit
import kotlin.math.max

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
    lateinit var mockRumFeatureScope: FeatureScope

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

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

    @Mock
    lateinit var mockFeaturesContextResolver: FeaturesContextResolver

    @BoolForgery
    var fakeTrackFrustrations: Boolean = true

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
        whenever(rumMonitor.mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope
        whenever(mockRumFeatureScope.withWriteContext(any(), any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventBatchWriter) -> Unit>(1)
            callback.invoke(fakeDatadogContext, mockEventBatchWriter)
        }
        whenever(
            mockFeaturesContextResolver.resolveViewHasReplay(
                fakeDatadogContext,
                fakeParentContext.viewId.orEmpty()
            )
        ).thenReturn(fakeHasReplay)
        whenever(mockWriter.write(eq(mockEventBatchWriter), any(), eq(EventType.DEFAULT))) doReturn true

        testedScope = RumActionScope(
            mockParentScope,
            rumMonitor.mockSdkCore,
            false,
            fakeEventTime,
            fakeType,
            fakeName,
            fakeAttributes,
            fakeServerOffset,
            TEST_INACTIVITY_MS,
            TEST_MAX_DURATION_MS,
            mockFeaturesContextResolver,
            true,
            fakeSampleRate
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
        fakeEvent = RumRawEvent.StartResource(key, url, method, emptyMap())
        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_INACTIVITY_MS * 2)
        fakeEvent = RumRawEvent.StopResource(key, statusCode, size, kind, emptyMap())
        val result2 = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_INACTIVITY_MS * 2)
        val result3 = testedScope.handleEvent(mockEvent(), mockWriter)

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
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
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
        fakeEvent = RumRawEvent.StartResource(key, url, method, emptyMap())
        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_INACTIVITY_MS * 2)
        fakeEvent = RumRawEvent.StopResource(key2, statusCode, size, kind, emptyMap())
        val result2 = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_INACTIVITY_MS * 2)
        val result3 = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        verify(mockParentScope, never()).handleEvent(any(), any())
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
        fakeEvent = RumRawEvent.StartResource(key, url, method, emptyMap())
        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_INACTIVITY_MS * 2)
        fakeEvent = RumRawEvent.StopResourceWithError(
            key,
            statusCode,
            message,
            source,
            throwable,
            emptyMap()
        )
        val result2 = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_INACTIVITY_MS * 2)
        val result3 = testedScope.handleEvent(mockEvent(), mockWriter)

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
                    hasUserSession()
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
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
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
        fakeEvent = RumRawEvent.StartResource(key, url, method, emptyMap())
        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_INACTIVITY_MS * 2)
        fakeEvent = RumRawEvent.StopResourceWithStackTrace(
            key,
            statusCode,
            message,
            source,
            stackTrace,
            errorType,
            emptyMap()
        )
        val result2 = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_INACTIVITY_MS * 2)
        val result3 = testedScope.handleEvent(mockEvent(), mockWriter)

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
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
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
        fakeEvent = RumRawEvent.StartResource(key, url, method, emptyMap())
        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_INACTIVITY_MS * 2)
        val fakeEvent2 = RumRawEvent.StopResourceWithError(
            key2,
            statusCode,
            message,
            source,
            throwable,
            emptyMap()
        )
        val result2 = testedScope.handleEvent(fakeEvent2, mockWriter)
        Thread.sleep(TEST_INACTIVITY_MS * 2)
        val result3 = testedScope.handleEvent(mockEvent(), mockWriter)

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
        fakeEvent = RumRawEvent.StartResource(key, url, method, emptyMap())
        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_INACTIVITY_MS * 2)
        val fakeEvent2 = RumRawEvent.StopResourceWithStackTrace(
            key2,
            statusCode,
            message,
            source,
            stackTrace,
            errorType,
            emptyMap()
        )
        val result2 = testedScope.handleEvent(fakeEvent2, mockWriter)
        Thread.sleep(TEST_INACTIVITY_MS * 2)
        val result3 = testedScope.handleEvent(mockEvent(), mockWriter)

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

        // When
        fakeEvent = RumRawEvent.StartResource(key.toString(), url, method, emptyMap())
        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_INACTIVITY_MS * 2)
        fakeEvent = mockEvent()
        key = null
        System.gc()
        val result2 = testedScope.handleEvent(mockEvent(), mockWriter)

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
                    hasUserSession()
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
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isNull()
        assertThat(key as? Any).isNull()
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
            attributes = emptyMap()
        )
        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_INACTIVITY_MS * 2)
        val result2 = testedScope.handleEvent(mockEvent(), mockWriter)

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
                    hasUserSession()
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
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
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
        fakeEvent = RumRawEvent.AddLongTask(duration, target)
        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_INACTIVITY_MS * 2)
        val result2 = testedScope.handleEvent(mockEvent(), mockWriter)

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
                    hasUserSession()
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
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
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
            attributes = emptyMap()
        )
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

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
                    hasUserSession()
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
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
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
            attributes = emptyMap()
        )
        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace = null,
            isFatal = true,
            threads = emptyList(),
            attributes = emptyMap()
        )
        val result2 = testedScope.handleEvent(fakeEvent, mockWriter)

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
                    hasUserSession()
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
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
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
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

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
                    hasUserSession()
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
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
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
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

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
                    hasUserSession()
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
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
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
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

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
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
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
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
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
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

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
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
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
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
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
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

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
                    hasUserSession()
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
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
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
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

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
                    hasUserSession()
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
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
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
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

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
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
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
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
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
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

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
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
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
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
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
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

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
                    hasUserSession()
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
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
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
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

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
                    hasUserSession()
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
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
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
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

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
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
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
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
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
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

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
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
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
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
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
        val fakeGlobalAttributes = forge.aFilteredMap(excludedKeys = fakeAttributes.keys) {
            anHexadecimalString() to anAsciiString()
        }
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(fakeGlobalAttributes)
        whenever(rumMonitor.mockInstance.getAttributes()) doReturn fakeGlobalAttributes
        testedScope = RumActionScope(
            mockParentScope,
            rumMonitor.mockSdkCore,
            false,
            fakeEventTime,
            fakeType,
            fakeName,
            fakeAttributes,
            fakeServerOffset,
            TEST_INACTIVITY_MS,
            TEST_MAX_DURATION_MS,
            mockFeaturesContextResolver,
            fakeTrackFrustrations,
            fakeSampleRate
        )
        whenever(rumMonitor.mockInstance.getAttributes()) doReturn emptyMap()
        fakeParentContext = fakeParentContext.copy(
            syntheticsTestId = fakeTestId,
            syntheticsResultId = fakeResultId
        )
        whenever(mockParentScope.getRumContext()) doReturn fakeParentContext

        // When
        Thread.sleep(TEST_INACTIVITY_MS)
        val result = testedScope.handleEvent(mockEvent(), mockWriter)

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
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSyntheticsSession()
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
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M send Action with initial global attributes after threshold W init()+handleEvent(any) `(
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
        testedScope = RumActionScope(
            mockParentScope,
            rumMonitor.mockSdkCore,
            false,
            fakeEventTime,
            fakeType,
            fakeName,
            fakeAttributes,
            fakeServerOffset,
            TEST_INACTIVITY_MS,
            TEST_MAX_DURATION_MS,
            mockFeaturesContextResolver,
            fakeTrackFrustrations,
            fakeSampleRate
        )
        whenever(rumMonitor.mockInstance.getAttributes()) doReturn emptyMap()

        // When
        Thread.sleep(TEST_INACTIVITY_MS)
        val result = testedScope.handleEvent(mockEvent(), mockWriter)

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
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
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
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M send Action with global attributes after threshold W handleEvent(any)`(
        forge: Forge
    ) {
        // Given
        val fakeGlobalAttributes = forge.aFilteredMap(excludedKeys = fakeAttributes.keys) {
            anHexadecimalString() to anAsciiString()
        }
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(fakeGlobalAttributes)
        Thread.sleep(TEST_INACTIVITY_MS)
        whenever(rumMonitor.mockInstance.getAttributes()) doReturn fakeGlobalAttributes

        // When
        val result = testedScope.handleEvent(mockEvent(), mockWriter)

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
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
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
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M send event with user extra attributes W handleEvent(any)`() {
        // Given
        Thread.sleep(TEST_INACTIVITY_MS)

        // When
        val result = testedScope.handleEvent(mockEvent(), mockWriter)

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
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
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
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
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
        Thread.sleep(TEST_INACTIVITY_MS)
        val result = testedScope.handleEvent(mockEvent(), mockWriter)

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
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
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
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
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
        Thread.sleep(TEST_INACTIVITY_MS)
        val result = testedScope.handleEvent(mockEvent(), mockWriter)

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
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
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
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
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
        Thread.sleep(TEST_INACTIVITY_MS)
        val result = testedScope.handleEvent(mockEvent(), mockWriter)

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
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
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
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M send Action only once W handleEvent(any) twice`() {
        // When
        Thread.sleep(TEST_INACTIVITY_MS)
        val result = testedScope.handleEvent(mockEvent(), mockWriter)
        val result2 = testedScope.handleEvent(mockEvent(), mockWriter)

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
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
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
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
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
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

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
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
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
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
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
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

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
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
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
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
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
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

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
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
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
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
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
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

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
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
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
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        assertThat(result).isNull()
    }

    @Test
    fun `M send Action after threshold W handleEvent(any) {no side effect}`() {
        // Given
        testedScope.resourceCount = 0
        testedScope.errorCount = 0
        testedScope.crashCount = 0
        testedScope.longTaskCount = 0
        Thread.sleep(TEST_INACTIVITY_MS)
        fakeEvent = mockEvent()

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

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
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
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
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        assertThat(result).isNull()
    }

    @Test
    fun `M doNothing W handleEvent(any) before threshold`() {
        // Given
        fakeEvent = mockEvent()

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

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
        fakeEvent = RumRawEvent.StartResource(key, url, method, emptyMap())
        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_INACTIVITY_MS * 2)
        val result2 = testedScope.handleEvent(mockEvent(), mockWriter)

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
        fakeEvent = RumRawEvent.StartResource(key, url, method, emptyMap())
        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_MAX_DURATION_MS)
        val result2 = testedScope.handleEvent(mockEvent(), mockWriter)

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
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
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
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isNull()
    }

    @Test
    fun `M send Action after timeout W handleEvent(any)`() {
        // When
        Thread.sleep(TEST_INACTIVITY_MS)
        val result = testedScope.handleEvent(mockEvent(), mockWriter)

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
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
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
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M send custom Action after timeout W handleEvent(any) and no side effect`() {
        // When
        Thread.sleep(TEST_INACTIVITY_MS)
        testedScope.type = RumActionType.CUSTOM
        val result = testedScope.handleEvent(mockEvent(), mockWriter)

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
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
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
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M send Action W handleEvent(SendCustomActionNow)`() {
        // When
        testedScope.type = RumActionType.CUSTOM
        val event = RumRawEvent.SendCustomActionNow()
        val result = testedScope.handleEvent(event, mockWriter)

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
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
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
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
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
            mockParentScope,
            rumMonitor.mockSdkCore,
            false,
            fakeEventTime,
            fakeType,
            fakeName,
            fakeAttributes,
            fakeServerOffset,
            TEST_INACTIVITY_MS,
            TEST_MAX_DURATION_MS,
            trackFrustrations = false,
            sampleRate = fakeSampleRate
        )

        // When
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace = null,
            isFatal = false,
            threads = emptyList(),
            attributes = emptyMap()
        )
        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_INACTIVITY_MS * 2)

        fakeEvent = RumRawEvent.StartAction(
            RumActionType.TAP,
            name,
            false,
            emptyMap()
        )
        val result2 = testedScope.handleEvent(fakeEvent, mockWriter)

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
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
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
        testedScope.handleEvent(event, mockWriter)

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
        testedScope.handleEvent(event, mockWriter)

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
        testedScope.handleEvent(event, mockWriter)

        // Then
        verify(rumMonitor.mockInstance as AdvancedRumMonitor)
            .eventDropped(
                fakeParentContext.viewId.orEmpty(),
                StorageEvent.Action(0, ActionEvent.ActionEventActionType.CUSTOM, testedScope.startedNanos)
            )
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
