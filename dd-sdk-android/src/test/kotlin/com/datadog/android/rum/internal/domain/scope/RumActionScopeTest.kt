/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import android.content.Context
import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.system.AndroidInfoProvider
import com.datadog.android.core.model.NetworkInfo
import com.datadog.android.core.model.UserInfo
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.assertj.ActionEventAssert.Companion.assertThat
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.domain.event.RumEventSourceProvider
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.config.CoreFeatureTestConfiguration
import com.datadog.android.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.forge.aFilteredMap
import com.datadog.android.utils.forge.exhaustiveAttributes
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
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
import org.mockito.quality.Strictness
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

    lateinit var fakeType: RumActionType

    @StringForgery
    lateinit var fakeName: String

    lateinit var fakeKey: ByteArray
    lateinit var fakeAttributes: Map<String, Any?>

    @Forgery
    lateinit var fakeParentContext: RumContext

    @Forgery
    lateinit var fakeUserInfo: UserInfo

    @Forgery
    lateinit var fakeAndroidInfoProvider: AndroidInfoProvider

    @Forgery
    lateinit var fakeNetworkInfo: NetworkInfo

    lateinit var fakeEventTime: Time

    lateinit var fakeEvent: RumRawEvent

    var fakeServerOffset: Long = 0L

    var fakeSourceActionEvent: ActionEvent.Source? = null

    @Mock
    lateinit var mockRumEventSourceProvider: RumEventSourceProvider

    @BoolForgery
    var fakeTrackFrustrations: Boolean = true

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeSourceActionEvent = forge.aNullable { aValueFrom(ActionEvent.Source::class.java) }
        whenever(mockRumEventSourceProvider.actionEventSource)
            .thenReturn(fakeSourceActionEvent)
        fakeEventTime = Time()
        val maxLimit = Long.MAX_VALUE - fakeEventTime.timestamp
        val minLimit = -fakeEventTime.timestamp
        fakeServerOffset =
            forge.aLong(min = minLimit, max = maxLimit)
        fakeType = forge.aValueFrom(
            RumActionType::class.java,
            exclude = listOf(RumActionType.CUSTOM)
        )

        fakeAttributes = forge.exhaustiveAttributes()
        fakeKey = forge.anAsciiString().toByteArray()

        whenever(coreFeature.mockUserInfoProvider.getUserInfo()) doReturn fakeUserInfo
        whenever(mockParentScope.getRumContext()) doReturn fakeParentContext
        whenever(coreFeature.mockNetworkInfoProvider.getLatestNetworkInfo()) doReturn fakeNetworkInfo

        testedScope = RumActionScope(
            mockParentScope,
            false,
            fakeEventTime,
            fakeType,
            fakeName,
            fakeAttributes,
            fakeServerOffset,
            TEST_INACTIVITY_MS,
            TEST_MAX_DURATION_MS,
            mockRumEventSourceProvider,
            fakeAndroidInfoProvider,
            true
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
    fun `𝕄 send Action after threshold 𝕎 handleEvent(StartResource+StopResource+any)`(
        @StringForgery key: String,
        @StringForgery method: String,
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
            verify(mockWriter).write(capture())
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
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeAndroidInfoProvider.deviceName,
                        fakeAndroidInfoProvider.deviceModel,
                        fakeAndroidInfoProvider.deviceBrand,
                        fakeAndroidInfoProvider.deviceType.toActionSchemaType(),
                        fakeAndroidInfoProvider.architecture
                    )
                    hasOsInfo(
                        fakeAndroidInfoProvider.osName,
                        fakeAndroidInfoProvider.osVersion,
                        fakeAndroidInfoProvider.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasServiceName(coreFeature.fakeServiceName)
                    hasVersion(coreFeature.mockAppVersionProvider.version)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isSameAs(testedScope)
        assertThat(result3).isNull()
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(StartResource+StopResource+any) {unknown key}`(
        @StringForgery key: String,
        @StringForgery key2: String,
        @StringForgery method: String,
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
        verifyZeroInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isSameAs(testedScope)
        assertThat(result3).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send Action after threshold 𝕎 handleEvent(StartResource+StopResourceWithError+any)`(
        @StringForgery key: String,
        @StringForgery method: String,
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
            verify(mockWriter).write(capture())
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
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeAndroidInfoProvider.deviceName,
                        fakeAndroidInfoProvider.deviceModel,
                        fakeAndroidInfoProvider.deviceBrand,
                        fakeAndroidInfoProvider.deviceType.toActionSchemaType(),
                        fakeAndroidInfoProvider.architecture
                    )
                    hasOsInfo(
                        fakeAndroidInfoProvider.osName,
                        fakeAndroidInfoProvider.osVersion,
                        fakeAndroidInfoProvider.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasServiceName(coreFeature.fakeServiceName)
                    hasVersion(coreFeature.mockAppVersionProvider.version)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isSameAs(testedScope)
        assertThat(result3).isNull()
    }

    @Test
    fun `𝕄 send Action after threshold 𝕎 handleEvent(StartResource+StopResourceWithStackTrace)`(
        @StringForgery key: String,
        @StringForgery method: String,
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
            verify(mockWriter).write(capture())
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
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                    hasDeviceInfo(
                        fakeAndroidInfoProvider.deviceName,
                        fakeAndroidInfoProvider.deviceModel,
                        fakeAndroidInfoProvider.deviceBrand,
                        fakeAndroidInfoProvider.deviceType.toActionSchemaType(),
                        fakeAndroidInfoProvider.architecture
                    )
                    hasOsInfo(
                        fakeAndroidInfoProvider.osName,
                        fakeAndroidInfoProvider.osVersion,
                        fakeAndroidInfoProvider.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasServiceName(coreFeature.fakeServiceName)
                    hasVersion(coreFeature.mockAppVersionProvider.version)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isSameAs(testedScope)
        assertThat(result3).isNull()
    }

    @Test
    fun `𝕄 send Action 𝕎 handleEvent(StartResource+StopResourceWithError+any) {unknown key}`(
        @StringForgery key: String,
        @StringForgery key2: String,
        @StringForgery method: String,
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
        verifyZeroInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isSameAs(testedScope)
        assertThat(result3).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send Action 𝕎 handleEvent(StartResource+StopResourceWithStackTrace+any) {unknown key}`(
        @StringForgery key: String,
        @StringForgery key2: String,
        @StringForgery method: String,
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
        verifyZeroInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isSameAs(testedScope)
        assertThat(result3).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send Action after threshold 𝕎 handleEvent(StartResource+any) missing resource key`(
        @StringForgery method: String,
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
            verify(mockWriter).write(capture())
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
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeAndroidInfoProvider.deviceName,
                        fakeAndroidInfoProvider.deviceModel,
                        fakeAndroidInfoProvider.deviceBrand,
                        fakeAndroidInfoProvider.deviceType.toActionSchemaType(),
                        fakeAndroidInfoProvider.architecture
                    )
                    hasOsInfo(
                        fakeAndroidInfoProvider.osName,
                        fakeAndroidInfoProvider.osVersion,
                        fakeAndroidInfoProvider.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasServiceName(coreFeature.fakeServiceName)
                    hasVersion(coreFeature.mockAppVersionProvider.version)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isNull()
        assertThat(key as Any?).isNull()
    }

    @Test
    fun `𝕄 send Action after threshold 𝕎 handleEvent(AddError+any)`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable
    ) {
        // When
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            null,
            false,
            emptyMap()
        )
        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_INACTIVITY_MS * 2)
        val result2 = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(capture())
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
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeAndroidInfoProvider.deviceName,
                        fakeAndroidInfoProvider.deviceModel,
                        fakeAndroidInfoProvider.deviceBrand,
                        fakeAndroidInfoProvider.deviceType.toActionSchemaType(),
                        fakeAndroidInfoProvider.architecture
                    )
                    hasOsInfo(
                        fakeAndroidInfoProvider.osName,
                        fakeAndroidInfoProvider.osVersion,
                        fakeAndroidInfoProvider.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasServiceName(coreFeature.fakeServiceName)
                    hasVersion(coreFeature.mockAppVersionProvider.version)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isNull()
    }

    @Test
    fun `𝕄 send Action after threshold 𝕎 handleEvent(AddLongTask+any)`(
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
            verify(mockWriter).write(capture())
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
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeAndroidInfoProvider.deviceName,
                        fakeAndroidInfoProvider.deviceModel,
                        fakeAndroidInfoProvider.deviceBrand,
                        fakeAndroidInfoProvider.deviceType.toActionSchemaType(),
                        fakeAndroidInfoProvider.architecture
                    )
                    hasOsInfo(
                        fakeAndroidInfoProvider.osName,
                        fakeAndroidInfoProvider.osVersion,
                        fakeAndroidInfoProvider.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasServiceName(coreFeature.fakeServiceName)
                    hasVersion(coreFeature.mockAppVersionProvider.version)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isNull()
    }

    @Test
    fun `𝕄 send Action immediately 𝕎 handleEvent(AddError) {isFatal=true}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable
    ) {
        // When
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            null,
            true,
            emptyMap()
        )
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(capture())
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
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeAndroidInfoProvider.deviceName,
                        fakeAndroidInfoProvider.deviceModel,
                        fakeAndroidInfoProvider.deviceBrand,
                        fakeAndroidInfoProvider.deviceType.toActionSchemaType(),
                        fakeAndroidInfoProvider.architecture
                    )
                    hasOsInfo(
                        fakeAndroidInfoProvider.osName,
                        fakeAndroidInfoProvider.osVersion,
                        fakeAndroidInfoProvider.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasServiceName(coreFeature.fakeServiceName)
                    hasVersion(coreFeature.mockAppVersionProvider.version)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 send Action immediately 𝕎 handleEvent(AddError{isFatal=false}+AddError{isFatal=true})`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable
    ) {
        // When
        fakeEvent = RumRawEvent.AddError(message, source, throwable, null, false, emptyMap())
        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        fakeEvent = RumRawEvent.AddError(message, source, throwable, null, true, emptyMap())
        val result2 = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(capture())
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
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeAndroidInfoProvider.deviceName,
                        fakeAndroidInfoProvider.deviceModel,
                        fakeAndroidInfoProvider.deviceBrand,
                        fakeAndroidInfoProvider.deviceType.toActionSchemaType(),
                        fakeAndroidInfoProvider.architecture
                    )
                    hasOsInfo(
                        fakeAndroidInfoProvider.osName,
                        fakeAndroidInfoProvider.osVersion,
                        fakeAndroidInfoProvider.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasServiceName(coreFeature.fakeServiceName)
                    hasVersion(coreFeature.mockAppVersionProvider.version)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isNull()
    }

    @Test
    fun `𝕄 send Action immediately 𝕎 handleEvent(StartView) {resourceCount != 0}`(
        @StringForgery fakeActionName: String,
        @LongForgery(1, 1024) count: Long
    ) {
        // Given
        testedScope.resourceCount = count

        // When
        fakeEvent = RumRawEvent.StartView(Object(), fakeActionName, emptyMap())
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(capture())
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
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeAndroidInfoProvider.deviceName,
                        fakeAndroidInfoProvider.deviceModel,
                        fakeAndroidInfoProvider.deviceBrand,
                        fakeAndroidInfoProvider.deviceType.toActionSchemaType(),
                        fakeAndroidInfoProvider.architecture
                    )
                    hasOsInfo(
                        fakeAndroidInfoProvider.osName,
                        fakeAndroidInfoProvider.osVersion,
                        fakeAndroidInfoProvider.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasServiceName(coreFeature.fakeServiceName)
                    hasVersion(coreFeature.mockAppVersionProvider.version)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 send Action immediately 𝕎 handleEvent(StartView) {longTaskCount != 0}`(
        @StringForgery fakeActionName: String,
        @LongForgery(1, 1024) count: Long
    ) {
        // Given
        testedScope.longTaskCount = count

        // When
        fakeEvent = RumRawEvent.StartView(Object(), fakeActionName, emptyMap())
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(capture())
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
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeAndroidInfoProvider.deviceName,
                        fakeAndroidInfoProvider.deviceModel,
                        fakeAndroidInfoProvider.deviceBrand,
                        fakeAndroidInfoProvider.deviceType.toActionSchemaType(),
                        fakeAndroidInfoProvider.architecture
                    )
                    hasOsInfo(
                        fakeAndroidInfoProvider.osName,
                        fakeAndroidInfoProvider.osVersion,
                        fakeAndroidInfoProvider.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasServiceName(coreFeature.fakeServiceName)
                    hasVersion(coreFeature.mockAppVersionProvider.version)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 send Action immediately 𝕎 handleEvent(StartView) {errorCount != 0}`(
        @StringForgery fakeActionName: String,
        @LongForgery(1, 1024) count: Long
    ) {
        // Given
        testedScope.errorCount = count

        // When
        fakeEvent = RumRawEvent.StartView(Object(), fakeActionName, emptyMap())
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(capture())
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
                    hasUserInfo(fakeUserInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeAndroidInfoProvider.deviceName,
                        fakeAndroidInfoProvider.deviceModel,
                        fakeAndroidInfoProvider.deviceBrand,
                        fakeAndroidInfoProvider.deviceType.toActionSchemaType(),
                        fakeAndroidInfoProvider.architecture
                    )
                    hasOsInfo(
                        fakeAndroidInfoProvider.osName,
                        fakeAndroidInfoProvider.osVersion,
                        fakeAndroidInfoProvider.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasServiceName(coreFeature.fakeServiceName)
                    hasVersion(coreFeature.mockAppVersionProvider.version)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 send Action immediately 𝕎 handleEvent(StartView) {crashCount != 0}`(
        @StringForgery fakeActionName: String,
        @LongForgery(1, 1024) nonFatalCount: Long,
        @LongForgery(1, 1024) fatalCount: Long
    ) {
        // Given
        testedScope.errorCount = nonFatalCount + fatalCount
        testedScope.crashCount = fatalCount

        // When
        fakeEvent = RumRawEvent.StartView(Object(), fakeActionName, emptyMap())
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(capture())
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
                    hasUserInfo(fakeUserInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeAndroidInfoProvider.deviceName,
                        fakeAndroidInfoProvider.deviceModel,
                        fakeAndroidInfoProvider.deviceBrand,
                        fakeAndroidInfoProvider.deviceType.toActionSchemaType(),
                        fakeAndroidInfoProvider.architecture
                    )
                    hasOsInfo(
                        fakeAndroidInfoProvider.osName,
                        fakeAndroidInfoProvider.osVersion,
                        fakeAndroidInfoProvider.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasServiceName(coreFeature.fakeServiceName)
                    hasVersion(coreFeature.mockAppVersionProvider.version)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 send Action immediately 𝕎 handleEvent(StopView) {resourceCount != 0}`(
        @LongForgery(1, 1024) count: Long
    ) {
        // Given
        testedScope.resourceCount = count

        // When
        fakeEvent = RumRawEvent.StopView(Object(), emptyMap())
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(capture())
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
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeAndroidInfoProvider.deviceName,
                        fakeAndroidInfoProvider.deviceModel,
                        fakeAndroidInfoProvider.deviceBrand,
                        fakeAndroidInfoProvider.deviceType.toActionSchemaType(),
                        fakeAndroidInfoProvider.architecture
                    )
                    hasOsInfo(
                        fakeAndroidInfoProvider.osName,
                        fakeAndroidInfoProvider.osVersion,
                        fakeAndroidInfoProvider.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasServiceName(coreFeature.fakeServiceName)
                    hasVersion(coreFeature.mockAppVersionProvider.version)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 send Action immediately 𝕎 handleEvent(StopView) {longTaskCount != 0}`(
        @LongForgery(1, 1024) count: Long
    ) {
        // Given
        testedScope.longTaskCount = count

        // When
        fakeEvent = RumRawEvent.StopView(Object(), emptyMap())
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(capture())
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
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeAndroidInfoProvider.deviceName,
                        fakeAndroidInfoProvider.deviceModel,
                        fakeAndroidInfoProvider.deviceBrand,
                        fakeAndroidInfoProvider.deviceType.toActionSchemaType(),
                        fakeAndroidInfoProvider.architecture
                    )
                    hasOsInfo(
                        fakeAndroidInfoProvider.osName,
                        fakeAndroidInfoProvider.osVersion,
                        fakeAndroidInfoProvider.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasServiceName(coreFeature.fakeServiceName)
                    hasVersion(coreFeature.mockAppVersionProvider.version)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 send Action immediately 𝕎 handleEvent(StopView) {errorCount != 0}`(
        @LongForgery(1, 1024) count: Long
    ) {
        // Given
        testedScope.errorCount = count

        // When
        fakeEvent = RumRawEvent.StopView(Object(), emptyMap())
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(capture())
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
                    hasUserInfo(fakeUserInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeAndroidInfoProvider.deviceName,
                        fakeAndroidInfoProvider.deviceModel,
                        fakeAndroidInfoProvider.deviceBrand,
                        fakeAndroidInfoProvider.deviceType.toActionSchemaType(),
                        fakeAndroidInfoProvider.architecture
                    )
                    hasOsInfo(
                        fakeAndroidInfoProvider.osName,
                        fakeAndroidInfoProvider.osVersion,
                        fakeAndroidInfoProvider.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasServiceName(coreFeature.fakeServiceName)
                    hasVersion(coreFeature.mockAppVersionProvider.version)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 send Action immediately 𝕎 handleEvent(StopView) {crashCount != 0}`(
        @LongForgery(1, 1024) nonFatalCount: Long,
        @LongForgery(1, 1024) fatalCount: Long
    ) {
        // Given
        testedScope.errorCount = nonFatalCount + fatalCount
        testedScope.crashCount = fatalCount

        // When
        fakeEvent = RumRawEvent.StopView(Object(), emptyMap())
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(capture())
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
                    hasUserInfo(fakeUserInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeAndroidInfoProvider.deviceName,
                        fakeAndroidInfoProvider.deviceModel,
                        fakeAndroidInfoProvider.deviceBrand,
                        fakeAndroidInfoProvider.deviceType.toActionSchemaType(),
                        fakeAndroidInfoProvider.architecture
                    )
                    hasOsInfo(
                        fakeAndroidInfoProvider.osName,
                        fakeAndroidInfoProvider.osVersion,
                        fakeAndroidInfoProvider.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasServiceName(coreFeature.fakeServiceName)
                    hasVersion(coreFeature.mockAppVersionProvider.version)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 send Action with initial global attributes after threshold 𝕎 init()+handleEvent(any) `(
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
        testedScope = RumActionScope(
            mockParentScope,
            false,
            fakeEventTime,
            fakeType,
            fakeName,
            fakeAttributes,
            fakeServerOffset,
            TEST_INACTIVITY_MS,
            TEST_MAX_DURATION_MS,
            mockRumEventSourceProvider,
            fakeAndroidInfoProvider,
            fakeTrackFrustrations
        )
        fakeGlobalAttributes.keys.forEach { GlobalRum.globalAttributes.remove(it) }

        // When
        Thread.sleep(TEST_INACTIVITY_MS)
        val result = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(capture())
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
                    hasUserInfo(fakeUserInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(expectedAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeAndroidInfoProvider.deviceName,
                        fakeAndroidInfoProvider.deviceModel,
                        fakeAndroidInfoProvider.deviceBrand,
                        fakeAndroidInfoProvider.deviceType.toActionSchemaType(),
                        fakeAndroidInfoProvider.architecture
                    )
                    hasOsInfo(
                        fakeAndroidInfoProvider.osName,
                        fakeAndroidInfoProvider.osVersion,
                        fakeAndroidInfoProvider.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasServiceName(coreFeature.fakeServiceName)
                    hasVersion(coreFeature.mockAppVersionProvider.version)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 send Action with global attributes after threshold 𝕎 handleEvent(any)`(
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
        GlobalRum.globalAttributes.putAll(fakeGlobalAttributes)

        // When
        val result = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(capture())
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
                    hasUserInfo(fakeUserInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(expectedAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeAndroidInfoProvider.deviceName,
                        fakeAndroidInfoProvider.deviceModel,
                        fakeAndroidInfoProvider.deviceBrand,
                        fakeAndroidInfoProvider.deviceType.toActionSchemaType(),
                        fakeAndroidInfoProvider.architecture
                    )
                    hasOsInfo(
                        fakeAndroidInfoProvider.osName,
                        fakeAndroidInfoProvider.osVersion,
                        fakeAndroidInfoProvider.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasServiceName(coreFeature.fakeServiceName)
                    hasVersion(coreFeature.mockAppVersionProvider.version)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 send event with user extra attributes 𝕎 handleEvent(any)`() {
        // Given
        Thread.sleep(TEST_INACTIVITY_MS)

        // When
        val result = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(capture())
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
                    hasUserInfo(fakeUserInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeAndroidInfoProvider.deviceName,
                        fakeAndroidInfoProvider.deviceModel,
                        fakeAndroidInfoProvider.deviceBrand,
                        fakeAndroidInfoProvider.deviceType.toActionSchemaType(),
                        fakeAndroidInfoProvider.architecture
                    )
                    hasOsInfo(
                        fakeAndroidInfoProvider.osName,
                        fakeAndroidInfoProvider.osVersion,
                        fakeAndroidInfoProvider.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasServiceName(coreFeature.fakeServiceName)
                    hasVersion(coreFeature.mockAppVersionProvider.version)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 send Action after threshold 𝕎 handleEvent(any) {resourceCount != 0}`(
        @LongForgery(1, 1024) count: Long
    ) {
        // Given
        testedScope.resourceCount = count

        // When
        Thread.sleep(TEST_INACTIVITY_MS)
        val result = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(capture())
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
                    hasUserInfo(fakeUserInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeAndroidInfoProvider.deviceName,
                        fakeAndroidInfoProvider.deviceModel,
                        fakeAndroidInfoProvider.deviceBrand,
                        fakeAndroidInfoProvider.deviceType.toActionSchemaType(),
                        fakeAndroidInfoProvider.architecture
                    )
                    hasOsInfo(
                        fakeAndroidInfoProvider.osName,
                        fakeAndroidInfoProvider.osVersion,
                        fakeAndroidInfoProvider.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasServiceName(coreFeature.fakeServiceName)
                    hasVersion(coreFeature.mockAppVersionProvider.version)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 send Action after threshold 𝕎 handleEvent(any) {errorCount != 0}`(
        @LongForgery(1, 1024) count: Long
    ) {
        // Given
        testedScope.errorCount = count

        // When
        Thread.sleep(TEST_INACTIVITY_MS)
        val result = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(capture())
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
                    hasUserInfo(fakeUserInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeAndroidInfoProvider.deviceName,
                        fakeAndroidInfoProvider.deviceModel,
                        fakeAndroidInfoProvider.deviceBrand,
                        fakeAndroidInfoProvider.deviceType.toActionSchemaType(),
                        fakeAndroidInfoProvider.architecture
                    )
                    hasOsInfo(
                        fakeAndroidInfoProvider.osName,
                        fakeAndroidInfoProvider.osVersion,
                        fakeAndroidInfoProvider.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasServiceName(coreFeature.fakeServiceName)
                    hasVersion(coreFeature.mockAppVersionProvider.version)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 send Action after threshold 𝕎 handleEvent(any) {crashCount != 0}`(
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
            verify(mockWriter).write(capture())
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
                    hasUserInfo(fakeUserInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeAndroidInfoProvider.deviceName,
                        fakeAndroidInfoProvider.deviceModel,
                        fakeAndroidInfoProvider.deviceBrand,
                        fakeAndroidInfoProvider.deviceType.toActionSchemaType(),
                        fakeAndroidInfoProvider.architecture
                    )
                    hasOsInfo(
                        fakeAndroidInfoProvider.osName,
                        fakeAndroidInfoProvider.osVersion,
                        fakeAndroidInfoProvider.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasServiceName(coreFeature.fakeServiceName)
                    hasVersion(coreFeature.mockAppVersionProvider.version)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 send Action only once 𝕎 handleEvent(any) twice`() {
        // When
        Thread.sleep(TEST_INACTIVITY_MS)
        val result = testedScope.handleEvent(mockEvent(), mockWriter)
        val result2 = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(capture())
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
                    hasUserInfo(fakeUserInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeAndroidInfoProvider.deviceName,
                        fakeAndroidInfoProvider.deviceModel,
                        fakeAndroidInfoProvider.deviceBrand,
                        fakeAndroidInfoProvider.deviceType.toActionSchemaType(),
                        fakeAndroidInfoProvider.architecture
                    )
                    hasOsInfo(
                        fakeAndroidInfoProvider.osName,
                        fakeAndroidInfoProvider.osVersion,
                        fakeAndroidInfoProvider.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasServiceName(coreFeature.fakeServiceName)
                    hasVersion(coreFeature.mockAppVersionProvider.version)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
        assertThat(result2).isNull()
    }

    @Test
    fun `𝕄 send Action 𝕎 handleEvent(StartView) {no side effect}`(
        @StringForgery fakeActionName: String
    ) {
        // Given
        testedScope.resourceCount = 0
        testedScope.errorCount = 0
        testedScope.crashCount = 0
        fakeEvent = RumRawEvent.StartView(Object(), fakeActionName, emptyMap())

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(capture())
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
                    hasUserInfo(fakeUserInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeAndroidInfoProvider.deviceName,
                        fakeAndroidInfoProvider.deviceModel,
                        fakeAndroidInfoProvider.deviceBrand,
                        fakeAndroidInfoProvider.deviceType.toActionSchemaType(),
                        fakeAndroidInfoProvider.architecture
                    )
                    hasOsInfo(
                        fakeAndroidInfoProvider.osName,
                        fakeAndroidInfoProvider.osVersion,
                        fakeAndroidInfoProvider.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasServiceName(coreFeature.fakeServiceName)
                    hasVersion(coreFeature.mockAppVersionProvider.version)
                }
        }
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 send Action event 𝕎 handleEvent(StartView) {no side effect}`(
        @StringForgery fakeActionName: String
    ) {
        // Given
        testedScope.resourceCount = 0
        testedScope.errorCount = 0
        testedScope.crashCount = 0
        testedScope.longTaskCount = 0
        fakeEvent = RumRawEvent.StartView(Object(), fakeActionName, emptyMap())

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(capture())
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
                    hasUserInfo(fakeUserInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeAndroidInfoProvider.deviceName,
                        fakeAndroidInfoProvider.deviceModel,
                        fakeAndroidInfoProvider.deviceBrand,
                        fakeAndroidInfoProvider.deviceType.toActionSchemaType(),
                        fakeAndroidInfoProvider.architecture
                    )
                    hasOsInfo(
                        fakeAndroidInfoProvider.osName,
                        fakeAndroidInfoProvider.osVersion,
                        fakeAndroidInfoProvider.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasServiceName(coreFeature.fakeServiceName)
                    hasVersion(coreFeature.mockAppVersionProvider.version)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 send Action 𝕎 handleEvent(StopView) {no side effect}`() {
        // Given
        testedScope.resourceCount = 0
        testedScope.errorCount = 0
        testedScope.crashCount = 0
        fakeEvent = RumRawEvent.StopView(Object(), emptyMap())

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(capture())
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
                    hasUserInfo(fakeUserInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeAndroidInfoProvider.deviceName,
                        fakeAndroidInfoProvider.deviceModel,
                        fakeAndroidInfoProvider.deviceBrand,
                        fakeAndroidInfoProvider.deviceType.toActionSchemaType(),
                        fakeAndroidInfoProvider.architecture
                    )
                    hasOsInfo(
                        fakeAndroidInfoProvider.osName,
                        fakeAndroidInfoProvider.osVersion,
                        fakeAndroidInfoProvider.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasServiceName(coreFeature.fakeServiceName)
                    hasVersion(coreFeature.mockAppVersionProvider.version)
                }
        }
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 send Action event 𝕎 handleEvent(StopView) {no side effect}`() {
        // Given
        testedScope.resourceCount = 0
        testedScope.errorCount = 0
        testedScope.crashCount = 0
        testedScope.longTaskCount = 0
        fakeEvent = RumRawEvent.StopView(Object(), emptyMap())

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(capture())
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
                    hasUserInfo(fakeUserInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeAndroidInfoProvider.deviceName,
                        fakeAndroidInfoProvider.deviceModel,
                        fakeAndroidInfoProvider.deviceBrand,
                        fakeAndroidInfoProvider.deviceType.toActionSchemaType(),
                        fakeAndroidInfoProvider.architecture
                    )
                    hasOsInfo(
                        fakeAndroidInfoProvider.osName,
                        fakeAndroidInfoProvider.osVersion,
                        fakeAndroidInfoProvider.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasServiceName(coreFeature.fakeServiceName)
                    hasVersion(coreFeature.mockAppVersionProvider.version)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 send Action after threshold 𝕎 handleEvent(any) {no side effect}`() {
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
            verify(mockWriter).write(capture())
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
                    hasUserInfo(fakeUserInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeAndroidInfoProvider.deviceName,
                        fakeAndroidInfoProvider.deviceModel,
                        fakeAndroidInfoProvider.deviceBrand,
                        fakeAndroidInfoProvider.deviceType.toActionSchemaType(),
                        fakeAndroidInfoProvider.architecture
                    )
                    hasOsInfo(
                        fakeAndroidInfoProvider.osName,
                        fakeAndroidInfoProvider.osVersion,
                        fakeAndroidInfoProvider.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasServiceName(coreFeature.fakeServiceName)
                    hasVersion(coreFeature.mockAppVersionProvider.version)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 doNothing 𝕎 handleEvent(any) before threshold`() {
        // Given
        fakeEvent = mockEvent()

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyZeroInteractions(mockWriter, mockParentScope)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 doNothing 𝕎 handleEvent(StartResource+any)`(
        @StringForgery key: String,
        @StringForgery method: String,
        @StringForgery(regex = "http(s?)://[a-z]+\\.com/[a-z]+") url: String
    ) {
        // When
        fakeEvent = RumRawEvent.StartResource(key, url, method, emptyMap())
        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_INACTIVITY_MS * 2)
        val result2 = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        verifyZeroInteractions(mockWriter, mockParentScope)
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send Action after timeout 𝕎 handleEvent(StartResource+any)`(
        @StringForgery key: String,
        @StringForgery method: String,
        @StringForgery(regex = "http(s?)://[a-z]+\\.com/[a-z]+") url: String
    ) {
        // When
        fakeEvent = RumRawEvent.StartResource(key, url, method, emptyMap())
        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_MAX_DURATION_MS)
        val result2 = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(capture())
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
                    hasUserInfo(fakeUserInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeAndroidInfoProvider.deviceName,
                        fakeAndroidInfoProvider.deviceModel,
                        fakeAndroidInfoProvider.deviceBrand,
                        fakeAndroidInfoProvider.deviceType.toActionSchemaType(),
                        fakeAndroidInfoProvider.architecture
                    )
                    hasOsInfo(
                        fakeAndroidInfoProvider.osName,
                        fakeAndroidInfoProvider.osVersion,
                        fakeAndroidInfoProvider.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasServiceName(coreFeature.fakeServiceName)
                    hasVersion(coreFeature.mockAppVersionProvider.version)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isNull()
    }

    @Test
    fun `𝕄 send Action after timeout 𝕎 handleEvent(any)`() {
        // When
        Thread.sleep(TEST_INACTIVITY_MS)
        val result = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(capture())
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
                    hasUserInfo(fakeUserInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeAndroidInfoProvider.deviceName,
                        fakeAndroidInfoProvider.deviceModel,
                        fakeAndroidInfoProvider.deviceBrand,
                        fakeAndroidInfoProvider.deviceType.toActionSchemaType(),
                        fakeAndroidInfoProvider.architecture
                    )
                    hasOsInfo(
                        fakeAndroidInfoProvider.osName,
                        fakeAndroidInfoProvider.osVersion,
                        fakeAndroidInfoProvider.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasServiceName(coreFeature.fakeServiceName)
                    hasVersion(coreFeature.mockAppVersionProvider.version)
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
            verify(mockWriter).write(capture())
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
                    hasUserInfo(fakeUserInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeAndroidInfoProvider.deviceName,
                        fakeAndroidInfoProvider.deviceModel,
                        fakeAndroidInfoProvider.deviceBrand,
                        fakeAndroidInfoProvider.deviceType.toActionSchemaType(),
                        fakeAndroidInfoProvider.architecture
                    )
                    hasOsInfo(
                        fakeAndroidInfoProvider.osName,
                        fakeAndroidInfoProvider.osVersion,
                        fakeAndroidInfoProvider.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasServiceName(coreFeature.fakeServiceName)
                    hasVersion(coreFeature.mockAppVersionProvider.version)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 send Action 𝕎 handleEvent(SendCustomActionNow)`() {
        // When
        testedScope.type = RumActionType.CUSTOM
        val event = RumRawEvent.SendCustomActionNow()
        val result = testedScope.handleEvent(event, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(capture())
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
                    hasUserInfo(fakeUserInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeAndroidInfoProvider.deviceName,
                        fakeAndroidInfoProvider.deviceModel,
                        fakeAndroidInfoProvider.deviceBrand,
                        fakeAndroidInfoProvider.deviceType.toActionSchemaType(),
                        fakeAndroidInfoProvider.architecture
                    )
                    hasOsInfo(
                        fakeAndroidInfoProvider.osName,
                        fakeAndroidInfoProvider.osVersion,
                        fakeAndroidInfoProvider.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasServiceName(coreFeature.fakeServiceName)
                    hasVersion(coreFeature.mockAppVersionProvider.version)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 send Action without frustrations 𝕎 handleEvent(AddError+Tap) {trackFrustration = false}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @StringForgery name: String
    ) {
        // Given
        testedScope = RumActionScope(
            mockParentScope,
            false,
            fakeEventTime,
            fakeType,
            fakeName,
            fakeAttributes,
            fakeServerOffset,
            TEST_INACTIVITY_MS,
            TEST_MAX_DURATION_MS,
            mockRumEventSourceProvider,
            fakeAndroidInfoProvider,
            false
        )

        // When
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            null,
            false,
            emptyMap()
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
            verify(mockWriter).write(capture())
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
                    hasUserInfo(fakeUserInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeAndroidInfoProvider.deviceName,
                        fakeAndroidInfoProvider.deviceModel,
                        fakeAndroidInfoProvider.deviceBrand,
                        fakeAndroidInfoProvider.deviceType.toActionSchemaType(),
                        fakeAndroidInfoProvider.architecture
                    )
                    hasOsInfo(
                        fakeAndroidInfoProvider.osName,
                        fakeAndroidInfoProvider.osVersion,
                        fakeAndroidInfoProvider.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasServiceName(coreFeature.fakeServiceName)
                    hasVersion(coreFeature.mockAppVersionProvider.version)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)

        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isNull()
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

        val appContext = ApplicationContextTestConfiguration(Context::class.java)
        val coreFeature = CoreFeatureTestConfiguration(appContext)
        val rumMonitor = GlobalRumMonitorTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(appContext, coreFeature, rumMonitor)
        }
    }
}
