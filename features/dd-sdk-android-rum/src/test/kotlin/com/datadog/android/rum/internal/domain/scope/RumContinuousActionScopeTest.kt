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
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.RumResourceMethod
import com.datadog.android.rum.assertj.ActionEventAssert.Companion.assertThat
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.forge.aFilteredMap
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumContinuousActionScopeTest {

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

    @Forgery
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

    lateinit var fakeEventTime: Time

    lateinit var fakeEvent: RumRawEvent

    var fakeServerOffset: Long = 0L

    var fakeSampleRate: Float = 0.0f

    var fakeSourceActionEvent: ActionEvent.ActionEventSource? = null

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
        val maxLimit = Long.MAX_VALUE - fakeEventTime.timestamp
        val minLimit = -fakeEventTime.timestamp
        fakeServerOffset =
            forge.aLong(min = minLimit, max = maxLimit)
        fakeAttributes = forge.exhaustiveAttributes()
        fakeKey = forge.anAsciiString().toByteArray()
        fakeSampleRate = forge.aFloat(min = 0.0f, max = 100.0f)

        whenever(mockParentScope.getRumContext()) doReturn fakeParentContext
        whenever(rumMonitor.mockSdkCore.networkInfo) doReturn fakeNetworkInfoAtScopeStart
        whenever(rumMonitor.mockSdkCore.internalLogger) doReturn mockInternalLogger

        whenever(rumMonitor.mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope
        whenever(mockRumFeatureScope.withWriteContext(any(), any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventBatchWriter) -> Unit>(1)
            callback.invoke(fakeDatadogContext, mockEventBatchWriter)
        }
        whenever(mockWriter.write(eq(mockEventBatchWriter), any())) doReturn true

        testedScope = RumActionScope(
            mockParentScope,
            rumMonitor.mockSdkCore,
            true,
            fakeEventTime,
            fakeType,
            fakeName,
            fakeAttributes,
            fakeServerOffset,
            TEST_INACTIVITY_MS,
            TEST_MAX_DURATION_MS,
            trackFrustrations = true,
            sampleRate = fakeSampleRate
        )
    }

    @Test
    fun `M do nothing W handleEvent(any) {resourceCount != 0}`(
        @LongForgery(1) count: Long
    ) {
        // Given
        Thread.sleep(RumActionScope.ACTION_INACTIVITY_MS)
        testedScope.resourceCount = count

        // When
        val result = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        verifyNoInteractions(mockWriter, mockParentScope)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M do nothing W handleEvent(any) {errorCount != 0}`(
        @LongForgery(1) count: Long
    ) {
        // Given
        Thread.sleep(RumActionScope.ACTION_INACTIVITY_MS)
        testedScope.errorCount = count

        // When
        val result = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        verifyNoInteractions(mockWriter, mockParentScope)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M do nothing W handleEvent(any) {crashCount != 0}`(
        @LongForgery(1) nonFatalCount: Long,
        @LongForgery(1) fatalCount: Long
    ) {
        // Given
        Thread.sleep(RumActionScope.ACTION_INACTIVITY_MS)
        testedScope.errorCount = nonFatalCount + fatalCount
        testedScope.crashCount = fatalCount

        // When
        val result = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        verifyNoInteractions(mockWriter, mockParentScope)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M send Action after timeout W handleEvent(any)`() {
        // Given
        Thread.sleep(TEST_MAX_DURATION_MS)

        // When
        val result = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue)
                .apply {
                    hasId(testedScope.actionId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(TEST_MAX_DURATION_NS)
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
    fun `M send Action with updated data W handleEvent(StopAction+any) {viewTreeChangeCount!=0}`(
        @StringForgery name: String,
        forge: Forge
    ) {
        // Given
        val type = forge.aValueFrom(RumActionType::class.java, listOf(fakeType))
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        // When
        Thread.sleep(TEST_INACTIVITY_MS * 2)
        fakeEvent = RumRawEvent.StopAction(type, name, attributes)
        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_INACTIVITY_MS * 2)
        val result2 = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue)
                .apply {
                    hasId(testedScope.actionId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasTargetName(name)
                    hasType(type)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(TEST_INACTIVITY_MS * 2))
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
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isNull()
    }

    @Test
    fun `M send Action with original data W handleEvent(StopAction) {viewTreeChangeCount!=0}`(
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        // When
        Thread.sleep(TEST_INACTIVITY_MS * 2)
        fakeEvent = RumRawEvent.StopAction(null, null, attributes)
        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_INACTIVITY_MS * 2)
        val result2 = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue)
                .apply {
                    hasId(testedScope.actionId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasTargetName(fakeName)
                    hasType(fakeType)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(TEST_INACTIVITY_MS * 2))
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
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isNull()
    }

    @Test
    fun `M send Action after threshold W handleEvent(StartResource+StopAction+StopResource+any)`(
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
        fakeEvent = RumRawEvent.StopAction(fakeType, fakeName, emptyMap())
        val result2 = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_INACTIVITY_MS * 2)
        fakeEvent = RumRawEvent.StopResource(key, statusCode, size, kind, emptyMap())
        val result3 = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_INACTIVITY_MS * 2)
        val result4 = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
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
        assertThat(result3).isSameAs(testedScope)
        assertThat(result4).isNull()
    }

    @Test
    fun `M send Action W handleEvent(StartResource+StopAction+StopResourceWithError+any)`(
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
        fakeEvent = RumRawEvent.StopAction(fakeType, fakeName, emptyMap())
        val result2 = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_INACTIVITY_MS * 2)
        fakeEvent = RumRawEvent.StopResourceWithError(
            key,
            statusCode,
            message,
            source,
            throwable,
            emptyMap()
        )
        val result3 = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_INACTIVITY_MS * 2)
        val result4 = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
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
        assertThat(result3).isSameAs(testedScope)
        assertThat(result4).isNull()
    }

    @Test
    fun `M send Action W handleEvent(StartResource+StopAction+StopResourceWithStackTrace+any)`(
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
        fakeEvent = RumRawEvent.StopAction(fakeType, fakeName, emptyMap())
        val result2 = testedScope.handleEvent(fakeEvent, mockWriter)
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
        val result3 = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_INACTIVITY_MS * 2)
        val result4 = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
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
        assertThat(result3).isSameAs(testedScope)
        assertThat(result4).isNull()
    }

    @Test
    fun `M send Action W handleEvent(StartResource+StopAction+any) missing resource key`(
        @Forgery method: RumResourceMethod,
        @StringForgery(regex = "http(s?)://[a-z]+\\.com/[a-z]+") url: String
    ) {
        // Given
        var key: Any? = Object()

        // When
        fakeEvent = RumRawEvent.StartResource(key.toString(), url, method, emptyMap())
        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        fakeEvent = RumRawEvent.StopAction(fakeType, fakeName, emptyMap())
        val result2 = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_INACTIVITY_MS * 2)
        fakeEvent = mockEvent()
        @Suppress("UNUSED_VALUE")
        key = null
        System.gc()
        val result3 = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
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
    fun `M send Action after threshold W handleEvent(AddError+StopAction+any)`(
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
        fakeEvent = RumRawEvent.StopAction(fakeType, fakeName, emptyMap())
        val result2 = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_INACTIVITY_MS * 2)
        val result3 = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
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
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
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
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
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
    fun `M send Action immediately W handleEvent(StopView) {viewTreeChangeCount != 0}`() {
        // When
        fakeEvent = RumRawEvent.StopView(RumScopeKey.from(Object()), emptyMap())
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
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
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
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
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
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
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
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
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
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
    fun `M send Action after threshold W handleEvent(StopAction+any) {viewTreeChangeCount!=0}`() {
        // When
        fakeEvent = RumRawEvent.StopAction(fakeType, fakeName, emptyMap())
        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_INACTIVITY_MS)
        val result2 = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
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
    fun `M send Action with synthetics info W handleEvent(StopAction+any)`(
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
            true,
            fakeEventTime,
            fakeType,
            fakeName,
            fakeAttributes,
            fakeServerOffset,
            TEST_INACTIVITY_MS,
            TEST_MAX_DURATION_MS,
            trackFrustrations = fakeTrackFrustrations,
            sampleRate = fakeSampleRate
        )
        whenever(rumMonitor.mockInstance.getAttributes()) doReturn emptyMap()
        fakeEvent = RumRawEvent.StopAction(fakeType, fakeName, emptyMap())
        fakeParentContext = fakeParentContext.copy(
            syntheticsTestId = fakeTestId,
            syntheticsResultId = fakeResultId
        )
        whenever(mockParentScope.getRumContext()) doReturn fakeParentContext

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_INACTIVITY_MS)
        val result2 = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
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
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isNull()
    }

    @Test
    fun `M send Action with initial global attributes W handleEvent(StopAction+any)`(
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
            true,
            fakeEventTime,
            fakeType,
            fakeName,
            fakeAttributes,
            fakeServerOffset,
            TEST_INACTIVITY_MS,
            TEST_MAX_DURATION_MS,
            trackFrustrations = fakeTrackFrustrations,
            sampleRate = fakeSampleRate
        )
        whenever(rumMonitor.mockInstance.getAttributes()) doReturn emptyMap()
        fakeEvent = RumRawEvent.StopAction(fakeType, fakeName, emptyMap())

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_INACTIVITY_MS)
        val result2 = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
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
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isNull()
    }

    @Test
    fun `M send Action with global attributes after threshold W handleEvent(StopAction+any)`(
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
        fakeEvent = RumRawEvent.StopAction(fakeType, fakeName, emptyMap())

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_INACTIVITY_MS)
        val result2 = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
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
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isNull()
    }

    @Test
    fun `M send Action after threshold W handleEvent(StopAction+any) {resourceCount != 0}`(
        @LongForgery(1, 1024) count: Long
    ) {
        // Given
        testedScope.resourceCount = count
        fakeEvent = RumRawEvent.StopAction(fakeType, fakeName, emptyMap())

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_INACTIVITY_MS)
        val result2 = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
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
    fun `M send Action after threshold W handleEvent(StopAction+any) {errorCount != 0}`(
        @LongForgery(1, 1024) count: Long
    ) {
        // Given
        testedScope.errorCount = count
        fakeEvent = RumRawEvent.StopAction(fakeType, fakeName, emptyMap())

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_INACTIVITY_MS)
        val result2 = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
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
    fun `M send Action after threshold W handleEvent(StopAction+any) {crashCount != 0}`(
        @LongForgery(1, 1024) nonFatalCount: Long,
        @LongForgery(1, 1024) fatalCount: Long
    ) {
        // Given
        testedScope.errorCount = nonFatalCount + fatalCount
        testedScope.crashCount = fatalCount
        fakeEvent = RumRawEvent.StopAction(fakeType, fakeName, emptyMap())

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_INACTIVITY_MS)
        val result2 = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
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
    fun `M send Action only once W handleEvent(StopAction) + handleEvent(any) twice`() {
        // Given
        fakeEvent = RumRawEvent.StopAction(fakeType, fakeName, emptyMap())

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_INACTIVITY_MS)
        val result2 = testedScope.handleEvent(mockEvent(), mockWriter)
        val result3 = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
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
        assertThat(result3).isNull()
    }

    @ParameterizedTest
    @EnumSource(RumActionType::class, names = ["CUSTOM"], mode = EnumSource.Mode.EXCLUDE)
    fun `M send Action W handleEvent(StopView) {no side effect}`(actionType: RumActionType) {
        testedScope.type = actionType

        // Given
        testedScope.resourceCount = 0
        testedScope.errorCount = 0
        testedScope.crashCount = 0
        testedScope.longTaskCount = 0
        fakeEvent = RumRawEvent.StopView(RumScopeKey.from(Object()), emptyMap())

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue)
                .apply {
                    hasId(testedScope.actionId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasType(testedScope.type)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(0)
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
    fun `M send custom Action immediately W handleEvent(StopView) {no side effect}`() {
        // Given
        testedScope.type = RumActionType.CUSTOM
        testedScope.resourceCount = 0
        testedScope.errorCount = 0
        testedScope.crashCount = 0
        testedScope.longTaskCount = 0
        fakeEvent = RumRawEvent.StopView(RumScopeKey.from(Object()), emptyMap())

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue)
                .apply {
                    hasId(testedScope.actionId)
                    hasTimestamp(resolveExpectedTimestamp())
                    hasType(RumActionType.CUSTOM)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(0)
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
    fun `M do nothing after threshold W handleEvent(any) {no side effect}`() {
        // Given
        testedScope.resourceCount = 0
        testedScope.errorCount = 0
        testedScope.crashCount = 0
        testedScope.longTaskCount = 0
        Thread.sleep(TEST_INACTIVITY_MS)

        // When
        val result = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        verifyNoInteractions(mockWriter, mockParentScope)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M do nothing W handleEvent(any) before threshold`() {
        // When
        val result = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        verifyNoInteractions(mockWriter, mockParentScope)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M do nothing W handleEvent(StartResource+any)`(
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
    fun `M do nothing W handleEvent(StartResource+StopAction+any)`(
        @StringForgery key: String,
        @Forgery method: RumResourceMethod,
        @StringForgery(regex = "http(s?)://[a-z]+\\.com/[a-z]+") url: String
    ) {
        // When
        fakeEvent = RumRawEvent.StartResource(key, url, method, emptyMap())
        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_INACTIVITY_MS * 2)
        fakeEvent = RumRawEvent.StopAction(fakeType, fakeName, emptyMap())
        val result2 = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_INACTIVITY_MS * 2)
        val result3 = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        verifyNoInteractions(mockWriter, mockParentScope)
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isSameAs(testedScope)
        assertThat(result3).isSameAs(testedScope)
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
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
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
    fun `M send Action after timeout W handleEvent(StartResource+StopAction+any)`(
        @StringForgery key: String,
        @Forgery method: RumResourceMethod,
        @StringForgery(regex = "http(s?)://[a-z]+\\.com/[a-z]+") url: String
    ) {
        // When
        fakeEvent = RumRawEvent.StartResource(key, url, method, emptyMap())
        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_INACTIVITY_MS * 2)
        fakeEvent = RumRawEvent.StopAction(fakeType, fakeName, emptyMap())
        val result2 = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_MAX_DURATION_MS)
        val result3 = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
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
    fun `M send Action W handleEvent(SendCustomActionNow)`() {
        // When
        testedScope.type = RumActionType.CUSTOM
        val event = RumRawEvent.SendCustomActionNow()
        val result = testedScope.handleEvent(event, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
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
        assertThat(result).isNull()
    }

    // region Internal

    private fun resolveExpectedTimestamp(): Long {
        return fakeEventTime.timestamp + fakeServerOffset
    }

    private fun mockEvent(): RumRawEvent {
        val event: RumRawEvent = mock()
        whenever(event.eventTime) doReturn Time()
        return event
    }

    // endregion

    companion object {
        internal const val TEST_INACTIVITY_MS = 30L
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
