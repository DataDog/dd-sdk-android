/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.Log
import android.view.Display
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.datadog.android.core.internal.net.FirstPartyHostDetector
import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.core.internal.utils.loggableStackTrace
import com.datadog.android.core.internal.utils.resolveViewUrl
import com.datadog.android.core.model.NetworkInfo
import com.datadog.android.core.model.UserInfo
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.assertj.ActionEventAssert.Companion.assertThat
import com.datadog.android.rum.assertj.ErrorEventAssert.Companion.assertThat
import com.datadog.android.rum.assertj.LongTaskEventAssert.Companion.assertThat
import com.datadog.android.rum.assertj.ViewEventAssert.Companion.assertThat
import com.datadog.android.rum.internal.RumErrorSourceType
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.vitals.VitalInfo
import com.datadog.android.rum.internal.vitals.VitalListener
import com.datadog.android.rum.internal.vitals.VitalMonitor
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.config.CoreFeatureTestConfiguration
import com.datadog.android.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.forge.aFilteredMap
import com.datadog.android.utils.forge.exhaustiveAttributes
import com.datadog.android.utils.mockDevLogHandler
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.reset
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.DoubleForgery
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.lang.RuntimeException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class),
    ExtendWith(ApiLevelExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumViewScopeTest {

    lateinit var testedScope: RumViewScope

    @Mock
    lateinit var mockParentScope: RumScope

    @Mock
    lateinit var mockChildScope: RumScope

    @Mock
    lateinit var mockActionScope: RumActionScope

    @Mock
    lateinit var mockWriter: DataWriter<Any>

    @Mock
    lateinit var mockDetector: FirstPartyHostDetector

    @Mock
    lateinit var mockCpuVitalMonitor: VitalMonitor

    @Mock
    lateinit var mockMemoryVitalMonitor: VitalMonitor

    @Mock
    lateinit var mockFrameRateVitalMonitor: VitalMonitor

    @StringForgery(regex = "([a-z]+\\.)+[A-Z][a-z]+")
    lateinit var fakeName: String

    @StringForgery(regex = "[a-f0-9]{8}-([a-f0-9]{4}-){3}[a-f0-9]{12}")
    lateinit var fakeActionId: String

    lateinit var fakeUrl: String
    lateinit var fakeKey: ByteArray
    lateinit var fakeAttributes: Map<String, Any?>

    @Forgery
    lateinit var fakeParentContext: RumContext

    @Forgery
    lateinit var fakeUserInfo: UserInfo

    @Forgery
    lateinit var fakeNetworkInfo: NetworkInfo

    lateinit var fakeEventTime: Time

    var fakeServerOffset: Long = 0L

    var fakeServerOffsetSecond: Long = 0L

    lateinit var fakeEvent: RumRawEvent

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @BeforeEach
    fun `set up`(forge: Forge) {

        val fakeOffset = -forge.aLong(1000, 50000)
        val fakeTimestamp = System.currentTimeMillis() + fakeOffset
        val fakeNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(fakeOffset)
        val maxLimit = max(Long.MAX_VALUE - fakeTimestamp, Long.MAX_VALUE)
        val minLimit = min(-fakeTimestamp, maxLimit)
        fakeServerOffset =
            forge.aLong(min = minLimit, max = maxLimit)
        fakeServerOffsetSecond =
            forge.aLong(min = minLimit, max = maxLimit)
        whenever(mockTimeProvider.getServerOffsetMillis())
            .thenReturn(fakeServerOffset)
            .thenReturn(fakeServerOffsetSecond)
        fakeEventTime = Time(fakeTimestamp, fakeNanos)
        fakeAttributes = forge.exhaustiveAttributes()
        fakeKey = forge.anAsciiString().toByteArray()
        fakeEvent = mockEvent()
        fakeUrl = fakeKey.resolveViewUrl().replace('.', '/')

        whenever(coreFeature.mockUserInfoProvider.getUserInfo()) doReturn fakeUserInfo
        whenever(coreFeature.mockNetworkInfoProvider.getLatestNetworkInfo())
            .doReturn(fakeNetworkInfo)
        whenever(mockParentScope.getRumContext()) doReturn fakeParentContext
        whenever(mockChildScope.handleEvent(any(), any())) doReturn mockChildScope
        whenever(mockActionScope.handleEvent(any(), any())) doReturn mockActionScope
        whenever(mockActionScope.actionId) doReturn fakeActionId

        testedScope = RumViewScope(
            mockParentScope,
            fakeKey,
            fakeName,
            fakeEventTime,
            fakeAttributes,
            mockDetector,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            mockTimeProvider
        )

        assertThat(GlobalRum.getRumContext()).isEqualTo(testedScope.getRumContext())
    }

    // region Context

    @Test
    fun `𝕄 return valid RumContext 𝕎 getRumContext()`() {
        // When
        val context = testedScope.getRumContext()

        // Then
        assertThat(context.actionId).isNull()
        assertThat(context.viewId).isEqualTo(testedScope.viewId)
        assertThat(context.viewName).isEqualTo(fakeName)
        assertThat(context.viewUrl).isEqualTo(fakeUrl)
        assertThat(context.applicationId).isEqualTo(fakeParentContext.applicationId)
        assertThat(context.sessionId).isEqualTo(fakeParentContext.sessionId)
    }

    @Test
    fun `𝕄 return active actionId 𝕎 getRumContext() with child ActionScope`() {
        // Given
        testedScope.activeActionScope = mockActionScope

        // When
        val context = testedScope.getRumContext()

        // Then
        assertThat(context.actionId).isEqualTo(fakeActionId)
        assertThat(context.viewId).isEqualTo(testedScope.viewId)
        assertThat(context.viewName).isEqualTo(fakeName)
        assertThat(context.viewUrl).isEqualTo(fakeUrl)
        assertThat(context.applicationId).isEqualTo(fakeParentContext.applicationId)
        assertThat(context.sessionId).isEqualTo(fakeParentContext.sessionId)
    }

    @Test
    fun `𝕄 update the viewId 𝕎 getRumContext() with parent sessionId changed`(
        @Forgery newSessionId: UUID
    ) {
        // Given
        val initialViewId = testedScope.viewId
        val context = testedScope.getRumContext()
        whenever(mockParentScope.getRumContext())
            .doReturn(fakeParentContext.copy(sessionId = newSessionId.toString()))

        // When
        val updatedContext = testedScope.getRumContext()

        // Then
        assertThat(context.actionId).isNull()
        assertThat(context.viewId).isEqualTo(initialViewId)
        assertThat(context.viewName).isEqualTo(fakeName)
        assertThat(context.viewUrl).isEqualTo(fakeUrl)
        assertThat(context.sessionId).isEqualTo(fakeParentContext.sessionId)
        assertThat(context.applicationId).isEqualTo(fakeParentContext.applicationId)

        assertThat(updatedContext.actionId).isNull()
        assertThat(updatedContext.viewId).isNotEqualTo(initialViewId)
        assertThat(updatedContext.viewName).isEqualTo(fakeName)
        assertThat(updatedContext.viewUrl).isEqualTo(fakeUrl)
        assertThat(updatedContext.sessionId).isEqualTo(newSessionId.toString())
        assertThat(updatedContext.applicationId).isEqualTo(fakeParentContext.applicationId)
    }

    // endregion

    // region View

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(StartView) on stopped view`(
        @StringForgery key: String,
        @StringForgery name: String
    ) {
        // Given
        testedScope.stopped = true

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StartView(key, name, emptyMap()),
            mockWriter
        )

        // Then
        verifyZeroInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(StartView) on active view`(
        @StringForgery key: String,
        @StringForgery name: String
    ) {
        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StartView(key, name, emptyMap()),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue).apply {
                hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                hasName(fakeName)
                hasUrl(fakeUrl)
                hasDurationGreaterThan(1)
                hasVersion(2)
                hasErrorCount(0)
                hasCrashCount(0)
                hasResourceCount(0)
                hasActionCount(0)
                hasLongTaskCount(0)
                hasFrozenFrameCount(0)
                hasCpuMetric(null)
                hasMemoryMetric(null, null)
                hasRefreshRateMetric(null, null)
                isActive(false)
                isSlowRendered(null)
                hasNoCustomTimings()
                hasUserInfo(fakeUserInfo)
                hasViewId(testedScope.viewId)
                hasApplicationId(fakeParentContext.applicationId)
                hasSessionId(fakeParentContext.sessionId)
                hasLiteSessionPlan()
                containsExactlyContextAttributes(fakeAttributes)
            }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 send event once 𝕎 handleEvent(StartView) twice on active view`(
        @StringForgery key: String,
        @StringForgery name: String,
        @StringForgery key2: String,
        @StringForgery name2: String
    ) {
        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StartView(key, name, emptyMap()),
            mockWriter
        )
        val result2 = testedScope.handleEvent(
            RumRawEvent.StartView(key2, name2, emptyMap()),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(false)
                    isSlowRendered(null)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasLiteSessionPlan()
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
        assertThat(result2).isNull()
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(StopView) on active view`(
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, attributes),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(false)
                    isSlowRendered(null)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    containsExactlyContextAttributes(expectedAttributes)
                    hasLiteSessionPlan()
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(StopView) on active view { pending attributes are negative }`(
        forge: Forge
    ) {
        // Given
        testedScope.pendingActionCount = forge.aLong(min = 0, max = 100) * (-1)
        testedScope.pendingResourceCount = forge.aLong(min = 0, max = 100) * (-1)
        testedScope.pendingErrorCount = forge.aLong(min = 0, max = 100) * (-1)
        testedScope.pendingLongTaskCount = forge.aLong(min = 0, max = 100) * (-1)

        // we limit it to 100 to avoid overflow and when we add those and end up with a positive
        // number
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, attributes),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(false)
                    isSlowRendered(null)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(expectedAttributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(StopView) on active view { pending attributes are positive }`(
        forge: Forge
    ) {
        // Given
        testedScope.pendingActionCount = forge.aLong(min = 0, max = 100)
        testedScope.pendingResourceCount = forge.aLong(min = 0, max = 100)
        testedScope.pendingErrorCount = forge.aLong(min = 0, max = 100)
        testedScope.pendingLongTaskCount = forge.aLong(min = 0, max = 100)
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, attributes),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(null)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(expectedAttributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(StopView) on active view { still has ongoing resources }`(
        forge: Forge,
        @StringForgery key: String
    ) {
        // Given
        val mockResourceScope: RumScope = mock()
        whenever(mockResourceScope.handleEvent(any(), any())) doReturn mockResourceScope
        testedScope.activeResourceScopes[key] = mockResourceScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, attributes),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(null)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(expectedAttributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event with user extra attributes 𝕎 handleEvent(StopView) on active view`() {
        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, emptyMap()),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(false)
                    isSlowRendered(null)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 send event with initial global attributes 𝕎 handleEvent(StopView) on active view`(
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
        whenever(mockTimeProvider.getServerOffsetMillis())
            .thenReturn(fakeServerOffset)
            .thenReturn(fakeServerOffsetSecond)
        testedScope = RumViewScope(
            mockParentScope,
            fakeKey,
            fakeName,
            fakeEventTime,
            fakeAttributes,
            mockDetector,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            mockTimeProvider
        )
        fakeGlobalAttributes.keys.forEach { GlobalRum.removeAttribute(it) }

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, emptyMap()),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(false)
                    isSlowRendered(null)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(expectedAttributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 send event with global attributes 𝕎 handleEvent(StopView) on active view`(
        forge: Forge
    ) {
        // Given
        val fakeGlobalAttributes = forge.aFilteredMap(excludedKeys = fakeAttributes.keys) {
            anHexadecimalString() to anAsciiString()
        }
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(fakeGlobalAttributes)

        // When
        GlobalRum.globalAttributes.putAll(fakeGlobalAttributes)
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, emptyMap()),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(false)
                    isSlowRendered(null)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(expectedAttributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 not take into account global attribute removal 𝕎 handleEvent(StopView) on active view`(
        forge: Forge
    ) {
        // Given
        GlobalRum.globalAttributes.clear()
        val fakeGlobalAttributeKey = forge.anAlphabeticalString()
        val fakeGlobalAttributeValue = forge.anAlphabeticalString()
        GlobalRum.addAttribute(fakeGlobalAttributeKey, fakeGlobalAttributeValue)
        reset(mockTimeProvider)
        whenever(mockTimeProvider.getServerOffsetMillis())
            .thenReturn(fakeServerOffset)
            .thenReturn(fakeServerOffsetSecond)
        testedScope = RumViewScope(
            mockParentScope,
            fakeKey,
            fakeName,
            fakeEventTime,
            fakeAttributes,
            mockDetector,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            mockTimeProvider
        )
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.put(fakeGlobalAttributeKey, fakeGlobalAttributeValue)

        // When
        GlobalRum.removeAttribute(fakeGlobalAttributeKey)
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, emptyMap()),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(false)
                    isSlowRendered(null)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(expectedAttributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 take into account global attribute update 𝕎 handleEvent(StopView) on active view`(
        forge: Forge
    ) {
        // Given
        GlobalRum.globalAttributes.clear()
        val fakeGlobalAttributeKey = forge.anAlphabeticalString()
        val fakeGlobalAttributeValue = forge.anAlphabeticalString()
        val fakeGlobalAttributeNewValue =
            fakeGlobalAttributeValue + forge.anAlphabeticalString(size = 2)
        GlobalRum.addAttribute(fakeGlobalAttributeKey, fakeGlobalAttributeValue)
        reset(mockTimeProvider)
        whenever(mockTimeProvider.getServerOffsetMillis())
            .thenReturn(fakeServerOffset)
            .thenReturn(fakeServerOffsetSecond)
        testedScope = RumViewScope(
            mockParentScope,
            fakeKey,
            fakeName,
            fakeEventTime,
            fakeAttributes,
            mockDetector,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            mockTimeProvider
        )
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.put(fakeGlobalAttributeKey, fakeGlobalAttributeNewValue)

        // When
        GlobalRum.addAttribute(fakeGlobalAttributeKey, fakeGlobalAttributeNewValue)
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, emptyMap()),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(false)
                    isSlowRendered(null)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(expectedAttributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 send event once 𝕎 handleEvent(StopView) twice on active view`(
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, attributes),
            mockWriter
        )
        val result2 = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, attributes),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(false)
                    isSlowRendered(null)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(expectedAttributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
        assertThat(result2).isNull()
    }

    @Test
    fun `𝕄 returns not null 𝕎 handleEvent(StopView) and a resource is still active`(
        @StringForgery key: String,
        forge: Forge
    ) {
        // Given
        testedScope.activeResourceScopes.put(key, mockChildScope)
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, attributes),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(null)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(expectedAttributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(StopView) on active view with missing key`() {
        // Given
        fakeKey = ByteArray(0)
        System.gc()

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, emptyMap()),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(false)
                    isSlowRendered(null)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(StopView) on active view without matching key`(
        @StringForgery key: String,
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(key, attributes),
            mockWriter
        )

        // Then
        verifyZeroInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(StopView) on stopped view`(
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)
        testedScope.stopped = true

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, attributes),
            mockWriter
        )

        // Then
        verifyZeroInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(ErrorSent) on active view`(
        @LongForgery(1) pending: Long
    ) {
        // Given
        fakeEvent = RumRawEvent.ErrorSent(testedScope.viewId)
        testedScope.pendingErrorCount = pending

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(1)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(null)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.pendingErrorCount).isEqualTo(pending - 1)
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(ErrorSent) on active view {unknown viewId}`(
        @Forgery viewUuid: UUID,
        @LongForgery(1) pending: Long
    ) {
        // Given
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        fakeEvent = RumRawEvent.ErrorSent(viewId)
        testedScope.pendingErrorCount = pending

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyZeroInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.pendingErrorCount).isEqualTo(pending)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(ResourceSent) on active view`(
        @LongForgery(1) pending: Long
    ) {
        // Given
        testedScope.pendingResourceCount = pending
        fakeEvent = RumRawEvent.ResourceSent(testedScope.viewId)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(1)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(null)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.pendingResourceCount).isEqualTo(pending - 1)
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(ResourceSent) on active view {unknown viewId}`(
        @Forgery viewUuid: UUID,
        @LongForgery(1) pending: Long
    ) {
        // Given
        testedScope.pendingResourceCount = pending
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        fakeEvent = RumRawEvent.ResourceSent(viewId)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyZeroInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.pendingResourceCount).isEqualTo(pending)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(ActionSent) on active view`(
        @LongForgery(1) pending: Long
    ) {
        // Given
        fakeEvent = RumRawEvent.ActionSent(testedScope.viewId)
        testedScope.pendingActionCount = pending

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(1)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(null)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.pendingActionCount).isEqualTo(pending - 1)
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(ActionSent) on active view {unknown viewId}`(
        @Forgery viewUuid: UUID,
        @LongForgery(1) pending: Long
    ) {
        // Given
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        fakeEvent = RumRawEvent.ActionSent(viewId)
        testedScope.pendingActionCount = pending

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyZeroInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.pendingActionCount).isEqualTo(pending)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(LongTaskSent) on active view {not frozen}`(
        @LongForgery(1) pendingLongTask: Long,
        @LongForgery(1) pendingFrozenFrame: Long
    ) {
        // Given
        fakeEvent = RumRawEvent.LongTaskSent(testedScope.viewId)
        testedScope.pendingLongTaskCount = pendingLongTask
        testedScope.pendingFrozenFrameCount = pendingFrozenFrame

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(1)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(null)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.pendingLongTaskCount).isEqualTo(pendingLongTask - 1)
        assertThat(testedScope.pendingFrozenFrameCount).isEqualTo(pendingFrozenFrame)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(LongTaskSent) on active view {frozen}`(
        @LongForgery(1) pendingLongTask: Long,
        @LongForgery(1) pendingFrozenFrame: Long
    ) {
        // Given
        fakeEvent = RumRawEvent.LongTaskSent(testedScope.viewId, true)
        testedScope.pendingLongTaskCount = pendingLongTask
        testedScope.pendingFrozenFrameCount = pendingFrozenFrame

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(1)
                    hasFrozenFrameCount(1)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(null)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.pendingLongTaskCount).isEqualTo(pendingLongTask - 1)
        assertThat(testedScope.pendingFrozenFrameCount).isEqualTo(pendingFrozenFrame - 1)
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(LongTaskSent) on active view {unknown viewId}`(
        @Forgery viewUuid: UUID,
        @LongForgery(1) pending: Long
    ) {
        // Given
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        fakeEvent = RumRawEvent.LongTaskSent(viewId)
        testedScope.pendingLongTaskCount = pending

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyZeroInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.pendingLongTaskCount).isEqualTo(pending)
    }

    @Test
    fun `𝕄 send event with global attributes 𝕎 handleEvent(ApplicationStarted) on active view`(
        @LongForgery(0) duration: Long,
        forge: Forge
    ) {
        // Given
        val eventTime = Time()
        val startedNanos = eventTime.nanoTime - duration
        fakeEvent = RumRawEvent.ApplicationStarted(eventTime, startedNanos)
        val attributes = forgeGlobalAttributes(forge, fakeAttributes)
        GlobalRum.globalAttributes.putAll(attributes)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(capture())
            assertThat(firstValue)
                .apply {
                    hasNonNullId()
                    hasTimestamp(testedScope.eventTimestamp)
                    hasType(ActionEvent.ActionType.APPLICATION_START)
                    hasNoTarget()
                    hasDuration(duration)
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasUserInfo(fakeUserInfo)
                    hasView(testedScope.viewId, testedScope.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(ErrorSent) on stopped view`() {
        // Given
        testedScope.stopped = true
        testedScope.pendingErrorCount = 1
        fakeEvent = RumRawEvent.ErrorSent(testedScope.viewId)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(1)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(false)
                    isSlowRendered(null)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
        assertThat(testedScope.pendingErrorCount).isEqualTo(0)
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(ErrorSent) on stopped view {unknown viewId}`(
        @Forgery viewUuid: UUID,
        @LongForgery(1) pending: Long
    ) {
        // Given
        testedScope.stopped = true
        testedScope.pendingErrorCount = pending
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        fakeEvent = RumRawEvent.ErrorSent(viewId)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyZeroInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.pendingErrorCount).isEqualTo(pending)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(ResourceSent) on stopped view`() {
        // Given
        testedScope.stopped = true
        testedScope.pendingResourceCount = 1
        fakeEvent = RumRawEvent.ResourceSent(testedScope.viewId)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(1)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(false)
                    isSlowRendered(null)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
        assertThat(testedScope.pendingResourceCount).isEqualTo(0)
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(ResourceSent) on stopped view {unknown viewId}`(
        @Forgery viewUuid: UUID,
        @LongForgery(1) pending: Long
    ) {
        // Given
        testedScope.stopped = true
        testedScope.pendingResourceCount = pending
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        fakeEvent = RumRawEvent.ResourceSent(viewId)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyZeroInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.pendingResourceCount).isEqualTo(pending)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(ActionSent) on stopped view`() {
        // Given
        testedScope.stopped = true
        testedScope.pendingActionCount = 1
        fakeEvent = RumRawEvent.ActionSent(testedScope.viewId)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(1)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(false)
                    isSlowRendered(null)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
        assertThat(testedScope.pendingActionCount).isEqualTo(0)
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(ActionSent) on stopped view {unknown viewId}`(
        @Forgery viewUuid: UUID,
        @LongForgery(1) pending: Long
    ) {
        // Given
        testedScope.stopped = true
        testedScope.pendingActionCount = pending
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        fakeEvent = RumRawEvent.ActionSent(viewId)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyZeroInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.pendingActionCount).isEqualTo(pending)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(LongTaskSent) on stopped view`() {
        // Given
        testedScope.stopped = true
        testedScope.pendingLongTaskCount = 1
        fakeEvent = RumRawEvent.LongTaskSent(testedScope.viewId)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(1)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(false)
                    isSlowRendered(null)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
        assertThat(testedScope.pendingLongTaskCount).isEqualTo(0)
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(LongTaskSent) on stopped view {unknown viewId}`(
        @Forgery viewUuid: UUID,
        @LongForgery(1) pending: Long
    ) {
        // Given
        testedScope.stopped = true
        testedScope.pendingLongTaskCount = pending
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        fakeEvent = RumRawEvent.LongTaskSent(viewId)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyZeroInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.pendingLongTaskCount).isEqualTo(pending)
    }

    @Test
    fun `𝕄 close the scope 𝕎 handleEvent(ActionSent) on stopped view { ApplicationStarted }`(
        @LongForgery(0) duration: Long
    ) {
        // Given
        testedScope.stopped = true
        val eventTime = Time()
        val startedNanos = eventTime.nanoTime - duration
        fakeEvent = RumRawEvent.ApplicationStarted(eventTime, startedNanos)
        val fakeActionSent = RumRawEvent.ActionSent(testedScope.viewId)

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)
        val result = testedScope.handleEvent(fakeActionSent, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter, times(2)).write(capture())
            assertThat(firstValue)
                .apply {
                    hasNonNullId()
                    hasTimestamp(testedScope.eventTimestamp)
                    hasType(ActionEvent.ActionType.APPLICATION_START)
                    hasNoTarget()
                    hasDuration(duration)
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasUserInfo(fakeUserInfo)
                    hasView(testedScope.viewId, testedScope.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 close the scope 𝕎 handleEvent(ActionDropped) on stopped view { ApplicationStarted }`(
        @LongForgery(0) duration: Long
    ) {
        // Given
        testedScope.stopped = true
        val eventTime = Time()
        val startedNanos = eventTime.nanoTime - duration
        fakeEvent = RumRawEvent.ApplicationStarted(eventTime, startedNanos)
        val fakeActionSent = RumRawEvent.ActionDropped(testedScope.viewId)

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)
        val result = testedScope.handleEvent(fakeActionSent, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(capture())
            assertThat(firstValue)
                .apply {
                    hasNonNullId()
                    hasTimestamp(testedScope.eventTimestamp)
                    hasType(ActionEvent.ActionType.APPLICATION_START)
                    hasNoTarget()
                    hasDuration(duration)
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasUserInfo(fakeUserInfo)
                    hasView(testedScope.viewId, testedScope.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(KeepAlive) on stopped view`() {
        // Given
        testedScope.stopped = true

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.KeepAlive(),
            mockWriter
        )

        // Then
        verifyZeroInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(KeepAlive) on active view`() {
        // When
        val result = testedScope.handleEvent(
            RumRawEvent.KeepAlive(),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(null)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 returns null 𝕎 handleEvent(any) on stopped view {no pending event}`() {
        // Given
        testedScope.stopped = true
        fakeEvent = mock()

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyZeroInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 returns self 𝕎 handleEvent(any) on stopped view {pending action event}`(
        @LongForgery(1, 32) pendingEvents: Long
    ) {
        // Given
        testedScope.stopped = true
        testedScope.pendingActionCount = pendingEvents
        fakeEvent = mock()

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyZeroInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 returns self 𝕎 handleEvent(any) on stopped view {pending resource event}`(
        @LongForgery(1, 32) pendingEvents: Long
    ) {
        // Given
        testedScope.stopped = true
        testedScope.pendingResourceCount = pendingEvents
        fakeEvent = mock()

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyZeroInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 returns self 𝕎 handleEvent(any) on stopped view {pending error event}`(
        @LongForgery(1, 32) pendingEvents: Long
    ) {
        // Given
        testedScope.stopped = true
        testedScope.pendingErrorCount = pendingEvents
        fakeEvent = mock()

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyZeroInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 returns self 𝕎 handleEvent(any) on stopped view {pending long task event}`(
        @LongForgery(1, 32) pendingEvents: Long
    ) {
        // Given
        testedScope.stopped = true
        testedScope.pendingLongTaskCount = pendingEvents
        fakeEvent = mock()

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyZeroInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    // endregion

    // region Action

    @Test
    fun `𝕄 create ActionScope 𝕎 handleEvent(StartAction)`(
        @Forgery type: RumActionType,
        @StringForgery name: String,
        @BoolForgery waitForStop: Boolean,
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)

        // When
        val fakeStartActionEvent = RumRawEvent.StartAction(type, name, waitForStop, attributes)
        val result = testedScope.handleEvent(
            fakeStartActionEvent,
            mockWriter
        )

        // Then
        verifyZeroInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.activeActionScope).isInstanceOf(RumActionScope::class.java)
        val actionScope = testedScope.activeActionScope as RumActionScope
        assertThat(actionScope.name).isEqualTo(name)
        assertThat(actionScope.eventTimestamp)
            .isEqualTo(resolveExpectedTimestamp(fakeStartActionEvent.eventTime.timestamp))
        assertThat(actionScope.waitForStop).isEqualTo(waitForStop)
        assertThat(actionScope.attributes).containsAllEntriesOf(attributes)
        assertThat(actionScope.parentScope).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 update the RumContext in GlobalRum W ActionScope created`(
        @Forgery type: RumActionType,
        @StringForgery name: String,
        @BoolForgery waitForStop: Boolean,
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)

        // When
        val fakeStartActionEvent = RumRawEvent.StartAction(type, name, waitForStop, attributes)
        testedScope.handleEvent(
            fakeStartActionEvent,
            mockWriter
        )

        // Then
        assertThat(GlobalRum.getRumContext().actionId)
            .isEqualTo((testedScope.activeActionScope as RumActionScope).actionId)
    }

    @ParameterizedTest
    @EnumSource(RumActionType::class, names = ["CUSTOM"], mode = EnumSource.Mode.EXCLUDE)
    fun `𝕄 do nothing + log warning 𝕎 handleEvent(StartAction+!CUSTOM)+active child ActionScope`(
        actionType: RumActionType,
        @StringForgery name: String,
        @BoolForgery waitForStop: Boolean,
        forge: Forge
    ) {
        // Given
        val mockDevLogHandler = mockDevLogHandler()
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        testedScope.activeActionScope = mockChildScope
        fakeEvent = RumRawEvent.StartAction(actionType, name, waitForStop, attributes)
        whenever(mockChildScope.handleEvent(fakeEvent, mockWriter)) doReturn mockChildScope

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(mockChildScope).handleEvent(fakeEvent, mockWriter)
        verifyZeroInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.activeActionScope).isSameAs(mockChildScope)

        verify(mockDevLogHandler).handleLog(
            Log.WARN,
            RumViewScope.ACTION_DROPPED_WARNING.format(
                Locale.US,
                (fakeEvent as RumRawEvent.StartAction).type,
                (fakeEvent as RumRawEvent.StartAction).name
            )
        )

        verifyNoMoreInteractions(mockDevLogHandler)
    }

    @Test
    fun `𝕄 do nothing + log warning 𝕎 handleEvent(StartAction+CUSTOM+cont) + child ActionScope`(
        @StringForgery name: String,
        forge: Forge
    ) {

        // Given
        val mockDevLogHandler = mockDevLogHandler()
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        testedScope.activeActionScope = mockChildScope
        fakeEvent =
            RumRawEvent.StartAction(RumActionType.CUSTOM, name, waitForStop = true, attributes)
        whenever(mockChildScope.handleEvent(fakeEvent, mockWriter)) doReturn mockChildScope

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(mockChildScope).handleEvent(fakeEvent, mockWriter)
        verifyZeroInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.activeActionScope).isSameAs(mockChildScope)

        verify(mockDevLogHandler).handleLog(
            Log.WARN,
            RumViewScope.ACTION_DROPPED_WARNING.format(
                Locale.US,
                (fakeEvent as RumRawEvent.StartAction).type,
                (fakeEvent as RumRawEvent.StartAction).name
            )
        )

        verifyNoMoreInteractions(mockDevLogHandler)
    }

    @Test
    fun `𝕄 send action 𝕎 handleEvent(StartAction+CUSTOM+instant) + active child ActionScope`(
        @StringForgery name: String,
        forge: Forge
    ) {
        // Given
        val mockDevLogHandler = mockDevLogHandler()
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        testedScope.activeActionScope = mockChildScope
        fakeEvent =
            RumRawEvent.StartAction(RumActionType.CUSTOM, name, waitForStop = false, attributes)

        whenever(mockChildScope.handleEvent(fakeEvent, mockWriter)) doReturn mockChildScope

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter, times(1)).write(capture())
            assertThat(firstValue)
                .apply {
                    hasNonNullId()
                    hasType(RumActionType.CUSTOM)
                    hasTargetName(name)
                    hasDuration(1)
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasLongTaskCount(0)
                    hasView(testedScope.getRumContext())
                    hasUserInfo(fakeUserInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(attributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        verifyZeroInteractions(mockDevLogHandler)

        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.activeActionScope).isSameAs(mockChildScope)
        assertThat(testedScope.pendingActionCount).isEqualTo(1)
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(StartAction) on stopped view`(
        @Forgery type: RumActionType,
        @StringForgery name: String,
        @BoolForgery waitForStop: Boolean,
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        testedScope.stopped = true
        fakeEvent = RumRawEvent.StartAction(type, name, waitForStop, attributes)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyZeroInteractions(mockWriter)
        assertThat(result).isNull()
        assertThat(testedScope.activeActionScope).isNull()
    }

    @Test
    fun `𝕄 send event to child ActionScope 𝕎 handleEvent(StartView) on active view`() {
        // Given
        testedScope.activeActionScope = mockChildScope

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(mockChildScope).handleEvent(fakeEvent, mockWriter)
        verifyZeroInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event to child ActionScope 𝕎 handleEvent() on stopped view`() {
        // Given
        testedScope.stopped = true
        testedScope.activeActionScope = mockChildScope

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(mockChildScope).handleEvent(fakeEvent, mockWriter)
        verifyZeroInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 remove child ActionScope 𝕎 handleEvent() returns null`() {
        // Given
        testedScope.activeActionScope = mockChildScope
        whenever(mockChildScope.handleEvent(fakeEvent, mockWriter)) doReturn null

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(mockChildScope).handleEvent(fakeEvent, mockWriter)
        verifyZeroInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.activeActionScope).isNull()
    }

    @Test
    fun `𝕄 update the RumContext in GlobalRum when removing the ActionScope`() {
        // Given
        testedScope.activeActionScope = mockChildScope
        whenever(mockChildScope.handleEvent(fakeEvent, mockWriter)) doReturn null

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(GlobalRum.getRumContext().actionId).isNull()
    }

    @Test
    fun `𝕄 wait for pending 𝕎 handleEvent(StartAction) on active view`(
        @Forgery type: RumActionType,
        @StringForgery name: String,
        @BoolForgery waitForStop: Boolean
    ) {
        // Given
        testedScope.activeActionScope = null
        testedScope.pendingActionCount = 0
        fakeEvent = RumRawEvent.StartAction(type, name, waitForStop, emptyMap())

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingActionCount).isEqualTo(1)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 wait for pending 𝕎 handleEvent(ApplicationStarted) on active view`(
        @LongForgery(0) duration: Long
    ) {
        // Given
        val eventTime = Time()
        val startedNanos = eventTime.nanoTime - duration
        fakeEvent = RumRawEvent.ApplicationStarted(eventTime, startedNanos)
        testedScope.activeActionScope = null
        testedScope.pendingActionCount = 0

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingActionCount).isEqualTo(1)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 decrease pending Action 𝕎 handleEvent(ActionDropped) on active view`(
        @LongForgery(1) pending: Long
    ) {
        // Given
        testedScope.pendingActionCount = pending
        fakeEvent = RumRawEvent.ActionDropped(testedScope.viewId)

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingActionCount).isEqualTo(pending - 1)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 decrease pending Action 𝕎 handleEvent(ActionDropped) on stopped view`() {
        // Given
        testedScope.pendingActionCount = 1
        fakeEvent = RumRawEvent.ActionDropped(testedScope.viewId)
        testedScope.stopped = true

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingActionCount).isEqualTo(0)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(ActionDropped) on active view {unknown viewId}`(
        @LongForgery(1) pending: Long,
        @Forgery viewUuid: UUID
    ) {
        // Given
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        testedScope.pendingActionCount = pending
        fakeEvent = RumRawEvent.ActionDropped(viewId)

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingActionCount).isEqualTo(pending)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(ActionDropped) on stopped view {unknown viewId}`(
        @LongForgery(1) pending: Long,
        @Forgery viewUuid: UUID
    ) {
        // Given
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        testedScope.pendingActionCount = pending
        fakeEvent = RumRawEvent.ActionDropped(viewId)
        testedScope.stopped = true

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingActionCount).isEqualTo(pending)
        assertThat(result).isSameAs(testedScope)
    }

    // endregion

    // region Resource

    @Test
    fun `𝕄 create ResourceScope 𝕎 handleEvent(StartResource)`(
        @StringForgery key: String,
        @StringForgery method: String,
        @StringForgery(regex = "http(s?)://[a-z]+\\.com/[a-z]+") url: String,
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)

        // When
        val fakeEvent = RumRawEvent.StartResource(key, url, method, attributes)
        val result = testedScope.handleEvent(
            fakeEvent,
            mockWriter
        )

        // Then
        verifyZeroInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.activeResourceScopes).isNotEmpty()
        val entry = testedScope.activeResourceScopes.entries.first()
        assertThat(entry.key).isEqualTo(key)
        assertThat(entry.value).isInstanceOf(RumResourceScope::class.java)
        val resourceScope = entry.value as RumResourceScope
        assertThat(resourceScope.parentScope).isSameAs(testedScope)
        assertThat(resourceScope.attributes).containsAllEntriesOf(attributes)
        assertThat(resourceScope.key).isSameAs(key)
        assertThat(resourceScope.url).isEqualTo(url)
        assertThat(resourceScope.method).isSameAs(method)
        assertThat(resourceScope.eventTimestamp)
            .isEqualTo(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
        assertThat(resourceScope.firstPartyHostDetector).isSameAs(mockDetector)
    }

    @Test
    fun `𝕄 create ResourceScope with active actionId 𝕎 handleEvent(StartResource)`(
        @StringForgery key: String,
        @StringForgery method: String,
        @StringForgery(regex = "http(s?)://[a-z]+\\.com/[a-z]+") url: String,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.StartResource(key, url, method, attributes)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(mockActionScope).handleEvent(fakeEvent, mockWriter)
        verifyZeroInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.activeResourceScopes).isNotEmpty()
        val entry = testedScope.activeResourceScopes.entries.first()
        assertThat(entry.key).isEqualTo(key)
        val resourceScope = entry.value as RumResourceScope
        assertThat(resourceScope.parentScope).isSameAs(testedScope)
        assertThat(resourceScope.attributes).containsAllEntriesOf(attributes)
        assertThat(resourceScope.key).isSameAs(key)
        assertThat(resourceScope.url).isEqualTo(url)
        assertThat(resourceScope.method).isSameAs(method)
        assertThat(resourceScope.eventTimestamp)
            .isEqualTo(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
        assertThat(resourceScope.firstPartyHostDetector).isSameAs(mockDetector)
    }

    @Test
    fun `𝕄 send event to children ResourceScopes 𝕎 handleEvent(StartView) on active view`(
        @StringForgery key: String
    ) {
        // Given
        testedScope.activeResourceScopes[key] = mockChildScope
        whenever(mockChildScope.handleEvent(fakeEvent, mockWriter)) doReturn mockChildScope

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(mockChildScope).handleEvent(fakeEvent, mockWriter)
        verifyZeroInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event to children ResourceScopes 𝕎 handleEvent(StartView) on stopped view`(
        @StringForgery key: String
    ) {
        // Given
        testedScope.stopped = true
        testedScope.activeResourceScopes[key] = mockChildScope
        whenever(mockChildScope.handleEvent(fakeEvent, mockWriter)) doReturn mockChildScope

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(mockChildScope).handleEvent(fakeEvent, mockWriter)
        verifyZeroInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 remove child ResourceScope 𝕎 handleEvent() returns null`(
        @StringForgery key: String
    ) {
        // Given
        testedScope.activeResourceScopes[key] = mockChildScope
        whenever(mockChildScope.handleEvent(fakeEvent, mockWriter)) doReturn null

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(mockChildScope).handleEvent(fakeEvent, mockWriter)
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.activeResourceScopes).isEmpty()
    }

    @Test
    fun `𝕄 wait for pending Resource 𝕎 handleEvent(StartResource) on active view`(
        @StringForgery key: String,
        @StringForgery method: String,
        @StringForgery(regex = "http(s?)://[a-z]+\\.com/[a-z]+") url: String
    ) {
        // Given
        testedScope.pendingResourceCount = 0
        fakeEvent = RumRawEvent.StartResource(key, url, method, emptyMap())

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingResourceCount).isEqualTo(1)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 decrease pending Resource 𝕎 handleEvent(ResourceDropped) on active view`(
        @LongForgery(1) pending: Long
    ) {
        // Given
        testedScope.pendingResourceCount = pending
        fakeEvent = RumRawEvent.ResourceDropped(testedScope.viewId)

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingResourceCount).isEqualTo(pending - 1)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 decrease pending Resource 𝕎 handleEvent(ResourceDropped) on stopped view`() {
        // Given
        testedScope.pendingResourceCount = 1
        fakeEvent = RumRawEvent.ResourceDropped(testedScope.viewId)
        testedScope.stopped = true

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingResourceCount).isEqualTo(0)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(ResourceDropped) on active view {unknown viewId}`(
        @LongForgery(1) pending: Long,
        @Forgery viewUuid: UUID
    ) {
        // Given
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        testedScope.pendingResourceCount = pending
        fakeEvent = RumRawEvent.ResourceDropped(viewId)

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingResourceCount).isEqualTo(pending)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(ResourceDropped) on stopped view {unknown viewId}`(
        @LongForgery(1) pending: Long,
        @Forgery viewUuid: UUID
    ) {
        // Given
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        testedScope.pendingResourceCount = pending
        fakeEvent = RumRawEvent.ResourceDropped(viewId)
        testedScope.stopped = true

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingResourceCount).isEqualTo(pending)
        assertThat(result).isSameAs(testedScope)
    }

    // endregion

    // region Error

    @Test
    fun `𝕄 send event 𝕎 handleEvent(AddError) on active view`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @StringForgery stacktrace: String,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace,
            false,
            attributes
        )

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        val expectedMessage = "$message: ${throwable.message}"
        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(capture())

            assertThat(firstValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(expectedMessage)
                    hasSource(source)
                    hasStackTrace(stacktrace)
                    isCrash(false)
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(testedScope.viewId, testedScope.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(attributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(AddError) on active view {throwable_message == null}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @StringForgery stacktrace: String,
        forge: Forge
    ) {
        // Given
        val throwable = RuntimeException()
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace,
            false,
            attributes
        )

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(capture())

            assertThat(firstValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(message)
                    hasSource(source)
                    hasStackTrace(stacktrace)
                    isCrash(false)
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(testedScope.viewId, testedScope.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(attributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(AddError) on active view {throwable_message == blank}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @StringForgery(StringForgeryType.WHITESPACE) blankMessage: String,
        @StringForgery stacktrace: String,
        forge: Forge
    ) {
        // Given
        val throwable = RuntimeException(blankMessage)
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace,
            false,
            attributes
        )

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(capture())

            assertThat(firstValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(message)
                    hasSource(source)
                    hasStackTrace(stacktrace)
                    isCrash(false)
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(testedScope.viewId, testedScope.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(attributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(AddError) on active view {message = throwable_message}`(
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @StringForgery stacktrace: String,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val throwableMessage = throwable.message
        check(!throwableMessage.isNullOrBlank()) {
            "Expected throwable to have a non null, non blank message"
        }
        fakeEvent = RumRawEvent.AddError(
            throwableMessage,
            source,
            throwable,
            stacktrace,
            false,
            attributes
        )

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(capture())

            assertThat(firstValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(throwableMessage)
                    hasSource(source)
                    hasStackTrace(stacktrace)
                    isCrash(false)
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(testedScope.viewId, testedScope.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(attributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event 𝕎 AddError {throwable=null}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @StringForgery stacktrace: String,
        forge: Forge
    ) {
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            null,
            stacktrace,
            false,
            attributes
        )

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(capture())

            assertThat(firstValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(message)
                    hasSource(source)
                    hasStackTrace(stacktrace)
                    isCrash(false)
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(testedScope.viewId, testedScope.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(attributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event 𝕎 AddError {stacktrace=null}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @Forgery sourceType: RumErrorSourceType,
        forge: Forge
    ) {
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            null,
            false,
            attributes,
            sourceType = sourceType
        )

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        val expectedMessage = "$message: ${throwable.message}"
        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(capture())

            assertThat(firstValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(expectedMessage)
                    hasSource(source)
                    hasStackTrace(throwable.loggableStackTrace())
                    isCrash(false)
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(testedScope.viewId, testedScope.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasErrorType(throwable.javaClass.canonicalName)
                    hasErrorSourceType(sourceType.toSchemaSourceType())
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(attributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(AddError) {throwable=null, stacktrace=null, fatal=false}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery sourceType: RumErrorSourceType,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(
            message, source, null, null, false, attributes, sourceType = sourceType
        )

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(capture())

            assertThat(firstValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(message)
                    hasSource(source)
                    hasStackTrace(null)
                    isCrash(false)
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(testedScope.viewId, testedScope.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasErrorType(null)
                    hasErrorSourceType(sourceType.toSchemaSourceType())
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(attributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(AddError) {throwable=null, stacktrace=null, fatal=true}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery sourceType: RumErrorSourceType,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(
            message, source, null, null, true, attributes,
            sourceType = sourceType
        )

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<Any> {
            verify(mockWriter, times(2)).write(capture())
            assertThat(firstValue as ErrorEvent)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(message)
                    hasSource(source)
                    hasStackTrace(null)
                    isCrash(true)
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(testedScope.viewId, testedScope.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasErrorType(null)
                    hasErrorSourceType(sourceType.toSchemaSourceType())
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(attributes)
                }
            assertThat(lastValue as ViewEvent)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasLoadingTime(null)
                    hasLoadingType(null)
                    hasVersion(2)
                    hasErrorCount(1)
                    hasCrashCount(1)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(null)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event with global attributes 𝕎 handleEvent(AddError)`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @Forgery sourceType: RumErrorSourceType,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            null,
            false,
            emptyMap(),
            sourceType = sourceType
        )
        val attributes = forgeGlobalAttributes(forge, fakeAttributes)
        GlobalRum.globalAttributes.putAll(attributes)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        val expectedMessage = "$message: ${throwable.message}"
        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(capture())

            assertThat(firstValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(expectedMessage)
                    hasSource(source)
                    hasStackTrace(throwable.loggableStackTrace())
                    isCrash(false)
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(testedScope.viewId, testedScope.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasErrorType(throwable.javaClass.canonicalName)
                    hasErrorSourceType(sourceType.toSchemaSourceType())
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(attributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(AddError) {isFatal=true}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @Forgery sourceType: RumErrorSourceType,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            null,
            true,
            attributes,
            sourceType = sourceType
        )

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        val expectedMessage = "$message: ${throwable.message}"
        argumentCaptor<Any> {
            verify(mockWriter, times(2)).write(capture())
            assertThat(firstValue as ErrorEvent)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(expectedMessage)
                    hasSource(source)
                    hasStackTrace(throwable.loggableStackTrace())
                    isCrash(true)
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(testedScope.viewId, testedScope.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasErrorType(throwable.javaClass.canonicalName)
                    hasErrorSourceType(sourceType.toSchemaSourceType())
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(attributes)
                }
            assertThat(lastValue as ViewEvent)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasLoadingTime(null)
                    hasLoadingType(null)
                    hasVersion(2)
                    hasErrorCount(1)
                    hasCrashCount(1)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(null)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(AddError) {internal is_crash=true}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @Forgery sourceType: RumErrorSourceType,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val attributesWithCrash = attributes.toMutableMap()
        attributesWithCrash["_dd.error.is_crash"] = true
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            null,
            false,
            attributesWithCrash,
            sourceType = sourceType
        )

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        val expectedMessage = "$message: ${throwable.message}"
        argumentCaptor<Any> {
            verify(mockWriter).write(capture())
            assertThat(firstValue as ErrorEvent)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(expectedMessage)
                    hasSource(source)
                    hasStackTrace(throwable.loggableStackTrace())
                    isCrash(true)
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(testedScope.viewId, testedScope.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasErrorType(throwable.javaClass.canonicalName)
                    hasErrorSourceType(sourceType.toSchemaSourceType())
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(attributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(AddError) {internal is_crash=false}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @Forgery sourceType: RumErrorSourceType,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val attributesWithCrash = attributes.toMutableMap()
        attributesWithCrash["_dd.error.is_crash"] = false
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            null,
            false,
            attributesWithCrash,
            sourceType = sourceType
        )

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        val expectedMessage = "$message: ${throwable.message}"
        argumentCaptor<Any> {
            verify(mockWriter).write(capture())
            assertThat(firstValue as ErrorEvent)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(expectedMessage)
                    hasSource(source)
                    hasStackTrace(throwable.loggableStackTrace())
                    isCrash(false)
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(testedScope.viewId, testedScope.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasErrorType(throwable.javaClass.canonicalName)
                    hasErrorSourceType(sourceType.toSchemaSourceType())
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(attributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(AddError) {custom error type}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @StringForgery errorType: String,
        @Forgery sourceType: RumErrorSourceType,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            null,
            false,
            attributes,
            type = errorType,
            sourceType = sourceType
        )

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        val expectedMessage = "$message: ${throwable.message}"
        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(capture())

            assertThat(firstValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(expectedMessage)
                    hasSource(source)
                    hasStackTrace(throwable.loggableStackTrace())
                    isCrash(false)
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(testedScope.viewId, testedScope.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasErrorType(errorType)
                    hasErrorSourceType(sourceType.toSchemaSourceType())
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(attributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event with global attributes 𝕎 handleEvent(AddError) {isFatal=true}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @Forgery sourceType: RumErrorSourceType,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            null,
            true,
            emptyMap(),
            sourceType = sourceType
        )
        val attributes = forgeGlobalAttributes(forge, fakeAttributes)
        GlobalRum.globalAttributes.putAll(attributes)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        val expectedMessage = "$message: ${throwable.message}"
        argumentCaptor<Any> {
            verify(mockWriter, times(2)).write(capture())
            assertThat(firstValue as ErrorEvent)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(expectedMessage)
                    hasSource(source)
                    hasStackTrace(throwable.loggableStackTrace())
                    isCrash(true)
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(testedScope.viewId, testedScope.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasErrorType(throwable.javaClass.canonicalName)
                    hasErrorSourceType(sourceType.toSchemaSourceType())
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(attributes)
                }
            assertThat(lastValue as ViewEvent)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasLoadingTime(null)
                    hasLoadingType(null)
                    hasVersion(2)
                    hasErrorCount(1)
                    hasCrashCount(1)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(null)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes + attributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(AddError) on stopped view {throwable}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @BoolForgery fatal: Boolean,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(message, source, throwable, null, fatal, attributes)
        testedScope.stopped = true

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyZeroInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(AddError) on stopped view {stacktrace}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @StringForgery stacktrace: String,
        @BoolForgery fatal: Boolean,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(message, source, null, stacktrace, fatal, attributes)
        testedScope.stopped = true

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyZeroInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 wait for pending Error 𝕎 handleEvent(AddError) on active view {fatal=false}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @StringForgery stacktrace: String
    ) {
        // Given
        testedScope.pendingErrorCount = 0
        fakeEvent = RumRawEvent.AddError(message, source, null, stacktrace, false, emptyMap())

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingErrorCount).isEqualTo(1)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 not wait for pending Error 𝕎 handleEvent(AddError) on active view {fatal=true}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @StringForgery stacktrace: String
    ) {
        // Given
        testedScope.pendingErrorCount = 0
        fakeEvent = RumRawEvent.AddError(message, source, null, stacktrace, true, emptyMap())

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingErrorCount).isEqualTo(0)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 decrease pending Error 𝕎 handleEvent(ErrorDropped) on active view`(
        @LongForgery(1) pending: Long
    ) {
        // Given
        testedScope.pendingErrorCount = pending
        fakeEvent = RumRawEvent.ErrorDropped(testedScope.viewId)

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingErrorCount).isEqualTo(pending - 1)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 decrease pending Error 𝕎 handleEvent(ErrorDropped) on stopped view`() {
        // Given
        testedScope.pendingErrorCount = 1
        fakeEvent = RumRawEvent.ErrorDropped(testedScope.viewId)
        testedScope.stopped = true

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingErrorCount).isEqualTo(0)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(ErrorDropped) on active view {unknown viewId}`(
        @LongForgery(1) pending: Long,
        @Forgery viewUuid: UUID
    ) {
        // Given
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        testedScope.pendingErrorCount = pending
        fakeEvent = RumRawEvent.ErrorDropped(viewId)

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingErrorCount).isEqualTo(pending)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(ErrorDropped) on stopped view {unknown viewId}`(
        @LongForgery(1) pending: Long,
        @Forgery viewUuid: UUID
    ) {
        // Given
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        testedScope.pendingErrorCount = pending
        fakeEvent = RumRawEvent.ErrorDropped(viewId)
        testedScope.stopped = true

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingErrorCount).isEqualTo(pending)
        assertThat(result).isSameAs(testedScope)
    }

    // endregion

    // region Long Task

    @Test
    fun `𝕄 send event 𝕎 handleEvent(AddLongTask) on active view {not frozen}`(
        @LongForgery(0L, 700_000_000L) durationNs: Long,
        @StringForgery target: String
    ) {
        // Given
        testedScope.activeActionScope = null
        fakeEvent = RumRawEvent.AddLongTask(durationNs, target)
        val durationMs = TimeUnit.NANOSECONDS.toMillis(durationNs)

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        argumentCaptor<LongTaskEvent> {
            verify(mockWriter).write(capture())

            assertThat(firstValue)
                .apply {
                    hasTimestamp(
                        resolveExpectedTimestamp(fakeEvent.eventTime.timestamp) - durationMs
                    )
                    hasDuration(durationNs)
                    isFrozenFrame(false)
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(testedScope.viewId, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(AddLongTask) on active view {frozen}`(
        @LongForgery(700_000_000L, 10_000_000_000L) durationNs: Long,
        @StringForgery target: String
    ) {
        // Given
        testedScope.activeActionScope = null
        fakeEvent = RumRawEvent.AddLongTask(durationNs, target)
        val durationMs = TimeUnit.NANOSECONDS.toMillis(durationNs)

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        argumentCaptor<LongTaskEvent> {
            verify(mockWriter).write(capture())

            assertThat(firstValue)
                .apply {
                    hasTimestamp(
                        resolveExpectedTimestamp(fakeEvent.eventTime.timestamp) - durationMs
                    )
                    hasDuration(durationNs)
                    isFrozenFrame(true)
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(testedScope.viewId, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event with global attributes 𝕎 handleEvent(AddLongTask) {not frozen}`(
        @LongForgery(0L, 700_000_000L) durationNs: Long,
        @StringForgery target: String,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val fakeLongTaskEvent = RumRawEvent.AddLongTask(durationNs, target)
        val attributes = forgeGlobalAttributes(forge, fakeAttributes)
        val durationMs = TimeUnit.NANOSECONDS.toMillis(durationNs)
        GlobalRum.globalAttributes.putAll(attributes)
        val expectedAttributes = attributes + mapOf(
            RumAttributes.LONG_TASK_TARGET to fakeLongTaskEvent.target
        )

        // When
        val result = testedScope.handleEvent(fakeLongTaskEvent, mockWriter)

        // Then
        val expectedTimestamp =
            resolveExpectedTimestamp(fakeLongTaskEvent.eventTime.timestamp) - durationMs
        argumentCaptor<LongTaskEvent> {
            verify(mockWriter).write(capture())

            assertThat(firstValue)
                .apply {
                    hasTimestamp(expectedTimestamp)
                    hasDuration(durationNs)
                    isFrozenFrame(false)
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(testedScope.viewId, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(expectedAttributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event with global attributes 𝕎 handleEvent(AddLongTask) {frozen}`(
        @LongForgery(700_000_000L, 10_000_000_000L) durationNs: Long,
        @StringForgery target: String,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val fakeLongTaskEvent = RumRawEvent.AddLongTask(durationNs, target)
        val attributes = forgeGlobalAttributes(forge, fakeAttributes)
        val durationMs = TimeUnit.NANOSECONDS.toMillis(durationNs)
        GlobalRum.globalAttributes.putAll(attributes)
        val expectedAttributes = attributes + mapOf(
            RumAttributes.LONG_TASK_TARGET to fakeLongTaskEvent.target
        )

        // When
        val result = testedScope.handleEvent(fakeLongTaskEvent, mockWriter)

        // Then
        argumentCaptor<LongTaskEvent> {
            verify(mockWriter).write(capture())

            assertThat(firstValue)
                .apply {
                    hasTimestamp(
                        resolveExpectedTimestamp(fakeLongTaskEvent.eventTime.timestamp) - durationMs
                    )
                    hasDuration(durationNs)
                    isFrozenFrame(true)
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(testedScope.viewId, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(expectedAttributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(AddLongTask) on stopped view`(
        @LongForgery(0) durationNs: Long,
        @StringForgery target: String,
        @LongForgery(1) pending: Long
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        testedScope.pendingLongTaskCount = pending
        fakeEvent = RumRawEvent.AddLongTask(durationNs, target)
        testedScope.stopped = true

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyZeroInteractions(mockWriter)
        assertThat(testedScope.pendingLongTaskCount).isEqualTo(pending)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 wait for pending Long Task 𝕎 handleEvent(AddLongTask) on active view {not frozen}`(
        @LongForgery(0L, 700_000_000L) durationNs: Long,
        @StringForgery target: String
    ) {
        // Given
        testedScope.pendingLongTaskCount = 0
        fakeEvent = RumRawEvent.AddLongTask(durationNs, target)

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingLongTaskCount).isEqualTo(1)
        assertThat(testedScope.pendingFrozenFrameCount).isEqualTo(0)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 wait for pending LT and FF 𝕎 handleEvent(AddLongTask) on active view {frozen}`(
        @LongForgery(700_000_000L, 10_000_000_000L) durationNs: Long,
        @StringForgery target: String
    ) {
        // Given
        testedScope.pendingLongTaskCount = 0
        fakeEvent = RumRawEvent.AddLongTask(durationNs, target)

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingLongTaskCount).isEqualTo(1)
        assertThat(testedScope.pendingFrozenFrameCount).isEqualTo(1)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 decrease pending Long Task 𝕎 handleEvent(LongTaskDropped) on active view {not frozen}`(
        @LongForgery(1) pendingLongTask: Long,
        @LongForgery(1) pendingFrozenFrame: Long
    ) {
        // Given
        testedScope.pendingLongTaskCount = pendingLongTask
        testedScope.pendingFrozenFrameCount = pendingFrozenFrame
        fakeEvent = RumRawEvent.LongTaskDropped(testedScope.viewId, false)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.pendingLongTaskCount).isEqualTo(pendingLongTask - 1)
        assertThat(testedScope.pendingFrozenFrameCount).isEqualTo(pendingFrozenFrame)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 decrease pending LT and FF 𝕎 handleEvent(LongTaskDropped) on active view {frozen}`(
        @LongForgery(1) pendingLongTask: Long,
        @LongForgery(1) pendingFrozenFrame: Long
    ) {
        // Given
        testedScope.pendingLongTaskCount = pendingLongTask
        testedScope.pendingFrozenFrameCount = pendingFrozenFrame
        fakeEvent = RumRawEvent.LongTaskDropped(testedScope.viewId, true)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.pendingLongTaskCount).isEqualTo(pendingLongTask - 1)
        assertThat(testedScope.pendingFrozenFrameCount).isEqualTo(pendingFrozenFrame - 1)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 decrease pending LT 𝕎 handleEvent(LongTaskDropped) on stopped view {not frozen}`() {
        // Given
        testedScope.pendingLongTaskCount = 1
        testedScope.pendingFrozenFrameCount = 0
        fakeEvent = RumRawEvent.LongTaskDropped(testedScope.viewId, false)
        testedScope.stopped = true

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.pendingLongTaskCount).isEqualTo(0)
        assertThat(testedScope.pendingFrozenFrameCount).isEqualTo(0)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 decrease pending LT and FF 𝕎 handleEvent(LongTaskDropped) on stopped view {frozen}`() {
        // Given
        testedScope.pendingLongTaskCount = 1
        testedScope.pendingFrozenFrameCount = 1
        fakeEvent = RumRawEvent.LongTaskDropped(testedScope.viewId, true)
        testedScope.stopped = true

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.pendingLongTaskCount).isEqualTo(0)
        assertThat(testedScope.pendingLongTaskCount).isEqualTo(0)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(LongTaskDropped) on active view {unknown viewId}`(
        @LongForgery(1) pending: Long,
        @BoolForgery isFrozenFrame: Boolean,
        @Forgery viewUuid: UUID
    ) {
        // Given
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        testedScope.pendingLongTaskCount = pending
        fakeEvent = RumRawEvent.LongTaskDropped(viewId, isFrozenFrame)

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingLongTaskCount).isEqualTo(pending)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(LongTaskDropped) on stopped view {unknown viewId}`(
        @LongForgery(1) pending: Long,
        @BoolForgery isFrozenFrame: Boolean,
        @Forgery viewUuid: UUID
    ) {
        // Given
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        testedScope.pendingLongTaskCount = pending
        fakeEvent = RumRawEvent.LongTaskDropped(viewId, isFrozenFrame)
        testedScope.stopped = true

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingLongTaskCount).isEqualTo(pending)
        assertThat(result).isSameAs(testedScope)
    }

    // endregion

    // region Loading Time

    @ParameterizedTest
    @EnumSource(ViewEvent.LoadingType::class)
    fun `𝕄 send event 𝕎 handleEvent(UpdateViewLoadingTime) on active view`(
        loadingType: ViewEvent.LoadingType,
        forge: Forge
    ) {
        // Given
        val loadingTime = forge.aLong(min = 1)

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.UpdateViewLoadingTime(fakeKey, loadingTime, loadingType),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasLoadingTime(loadingTime)
                    hasLoadingType(loadingType)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(null)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @ParameterizedTest
    @EnumSource(ViewEvent.LoadingType::class)
    fun `𝕄 send event 𝕎 handleEvent(UpdateViewLoadingTime) on stopped view`(
        loadingType: ViewEvent.LoadingType,
        forge: Forge
    ) {
        // Given
        testedScope.stopped = true
        val loadingTime = forge.aLong(min = 1)

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.UpdateViewLoadingTime(fakeKey, loadingTime, loadingType),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasLoadingTime(loadingTime)
                    hasLoadingType(loadingType)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(false)
                    isSlowRendered(null)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @ParameterizedTest
    @EnumSource(ViewEvent.LoadingType::class)
    fun `𝕄 do nothing 𝕎 handleEvent(UpdateViewLoadingTime) with different key`(
        loadingType: ViewEvent.LoadingType,
        forge: Forge
    ) {
        // Given
        val differentKey = fakeKey + "different".toByteArray()
        val loadingTime = forge.aLong(min = 1)

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.UpdateViewLoadingTime(differentKey, loadingTime, loadingType),
            mockWriter
        )

        // Then
        verifyZeroInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event with custom timing 𝕎 handleEvent(AddCustomTiming) on active view`(
        forge: Forge
    ) {
        // Given
        val fakeTimingKey = forge.anAlphabeticalString()

        // When
        testedScope.handleEvent(
            RumRawEvent.AddCustomTiming(fakeTimingKey),
            mockWriter
        )
        val customTimingEstimatedDuration = System.nanoTime() - fakeEventTime.nanoTime

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasLoadingTime(null)
                    hasLoadingType(null)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(null)
                    hasCustomTimings(mapOf(fakeTimingKey to customTimingEstimatedDuration))
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                }
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `𝕄 send event with custom timings 𝕎 handleEvent(AddCustomTiming) called multiple times`(
        forge: Forge
    ) {
        // Given
        val fakeTimingKey1 = forge.anAlphabeticalString()
        val fakeTimingKey2 = forge.anAlphabeticalString()

        // When
        testedScope.handleEvent(
            RumRawEvent.AddCustomTiming(fakeTimingKey1),
            mockWriter
        )
        val customTiming1EstimatedDuration = System.nanoTime() - fakeEventTime.nanoTime
        testedScope.handleEvent(
            RumRawEvent.AddCustomTiming(fakeTimingKey2),
            mockWriter
        )
        val customTiming2EstimatedDuration = System.nanoTime() - fakeEventTime.nanoTime

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter, times(2)).write(capture())
            assertThat(firstValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasLoadingTime(null)
                    hasLoadingType(null)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(null)
                    hasCustomTimings(mapOf(fakeTimingKey1 to customTiming1EstimatedDuration))
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                }
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasLoadingTime(null)
                    hasLoadingType(null)
                    hasVersion(3)
                    hasErrorCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(null)
                    hasCustomTimings(
                        mapOf(
                            fakeTimingKey1 to customTiming1EstimatedDuration,
                            fakeTimingKey2 to customTiming2EstimatedDuration
                        )
                    )
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
    }

    // endregion

    // region Vitals

    @Test
    fun `𝕄 send View update 𝕎 onVitalUpdate()+handleEvent(KeepAlive) {CPU}`(
        forge: Forge
    ) {
        // Given
        // cpu ticks should be received in ascending order
        val cpuTicks = forge.aList { aLong(1L, 65536L).toDouble() }.sorted()
        val listenerCaptor = argumentCaptor<VitalListener> {
            verify(mockCpuVitalMonitor).register(capture())
        }
        val listener = listenerCaptor.firstValue

        // When
        cpuTicks.forEachIndexed { index, value ->
            listener.onVitalUpdate(VitalInfo(index + 1, 0.0, value, value / 2.0))
        }
        val result = testedScope.handleEvent(
            RumRawEvent.KeepAlive(),
            mockWriter
        )

        // Then
        val expectedTotal = if (cpuTicks.size > 1) {
            cpuTicks.last() - cpuTicks.first()
        } else {
            // we need to have at least 2 ticks to submit "ticks on the view" metric
            null
        }
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(expectedTotal)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(null)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send View update 𝕎 onVitalUpdate()+handleEvent(KeepAlive) {Memory}`(
        forge: Forge
    ) {
        // Given
        val vitals = forge.aList { getForgery<VitalInfo>() }
        val listenerCaptor = argumentCaptor<VitalListener> {
            verify(mockMemoryVitalMonitor).register(capture())
        }
        val listener = listenerCaptor.firstValue

        // When
        vitals.forEach { listener.onVitalUpdate(it) }
        val result = testedScope.handleEvent(
            RumRawEvent.KeepAlive(),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(vitals.last().meanValue, vitals.last().maxValue)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(null)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send View update 𝕎 onVitalUpdate()+handleEvent(KeepAlive) {high frameRate}`(
        forge: Forge
    ) {
        // Given
        val frameRates = forge.aList { aDouble(55.0, 60.0) }.sorted()
        val listenerCaptor = argumentCaptor<VitalListener> {
            verify(mockFrameRateVitalMonitor).register(capture())
        }
        val listener = listenerCaptor.firstValue

        // When
        var sum = 0.0
        var min = 60.0
        var max = 0.0
        var count = 0
        frameRates.forEach { value ->
            count++
            sum += value
            min = min(min, value)
            max = max(max, value)
            listener.onVitalUpdate(VitalInfo(count, min, max, sum / count))
        }
        val result = testedScope.handleEvent(
            RumRawEvent.KeepAlive(),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(sum / frameRates.size, min)
                    isActive(true)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send View update 𝕎 onVitalUpdate()+handleEvent(KeepAlive) {low frameRate}`(
        forge: Forge
    ) {
        // Given
        val frameRates = forge.aList { aDouble(10.0, 55.0) }.sorted()
        val listenerCaptor = argumentCaptor<VitalListener> {
            verify(mockFrameRateVitalMonitor).register(capture())
        }
        val listener = listenerCaptor.firstValue

        // When
        var sum = 0.0
        var min = 60.0
        var max = 0.0
        var count = 0
        frameRates.forEach { value ->
            count++
            sum += value
            min = min(min, value)
            max = max(max, value)
            listener.onVitalUpdate(VitalInfo(count, min, max, sum / count))
        }
        val result = testedScope.handleEvent(
            RumRawEvent.KeepAlive(),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(sum / frameRates.size, min)
                    isActive(true)
                    isSlowRendered(true)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @TestTargetApi(Build.VERSION_CODES.R)
    @Test
    fun `𝕄 detect slow refresh rate 𝕎 init()+onVitalUpdate()+handleEvent(KeepAlive) {Activity}`(
        @FloatForgery(120.0f, 240.0f) deviceRefreshRate: Float,
        @DoubleForgery(30.0, 55.0) meanRefreshRate: Double,
        @DoubleForgery(0.0, 30.0) minRefreshRate: Double
    ) {
        // Given
        val mockActivity = mock<Activity>()
        val mockDisplay = mock<Display>()
        whenever(mockActivity.display) doReturn mockDisplay
        whenever(mockDisplay.refreshRate) doReturn deviceRefreshRate
        reset(mockFrameRateVitalMonitor)
        reset(mockTimeProvider)
        whenever(mockTimeProvider.getServerOffsetMillis())
            .thenReturn(fakeServerOffset)
            .thenReturn(fakeServerOffsetSecond)
        val testedScope = RumViewScope(
            mockParentScope,
            mockActivity,
            fakeName,
            fakeEventTime,
            fakeAttributes,
            mockDetector,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            mockTimeProvider
        )
        val listenerCaptor = argumentCaptor<VitalListener> {
            verify(mockFrameRateVitalMonitor).register(capture())
        }
        val listener = listenerCaptor.firstValue

        // When
        listener.onVitalUpdate(VitalInfo(1, minRefreshRate, meanRefreshRate * 2, meanRefreshRate))
        val result = testedScope.handleEvent(RumRawEvent.KeepAlive(), mockWriter)

        // Then
        val expectedAverage = (meanRefreshRate * 60.0) / deviceRefreshRate
        val expectedMinimum = (minRefreshRate * 60.0) / deviceRefreshRate
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeName)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(expectedAverage, expectedMinimum)
                    isActive(true)
                    isSlowRendered(true)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @TestTargetApi(Build.VERSION_CODES.R)
    @Test
    fun `𝕄 detect high refresh rate 𝕎 init()+onVitalUpdate()+handleEvent(KeepAlive) {Activity}`(
        @FloatForgery(120.0f, 240.0f) deviceRefreshRate: Float,
        @DoubleForgery(55.0, 60.0) meanRefreshRate: Double,
        @DoubleForgery(50.0, 55.0) minRefreshRate: Double
    ) {
        // Given
        val mockActivity = mock<Activity>()
        val mockDisplay = mock<Display>()
        whenever(mockActivity.display) doReturn mockDisplay
        whenever(mockDisplay.refreshRate) doReturn deviceRefreshRate
        reset(mockFrameRateVitalMonitor)
        reset(mockTimeProvider)
        whenever(mockTimeProvider.getServerOffsetMillis())
            .thenReturn(fakeServerOffset)
            .thenReturn(fakeServerOffsetSecond)
        val testedScope = RumViewScope(
            mockParentScope,
            mockActivity,
            fakeName,
            fakeEventTime,
            fakeAttributes,
            mockDetector,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            mockTimeProvider
        )
        val listenerCaptor = argumentCaptor<VitalListener> {
            verify(mockFrameRateVitalMonitor).register(capture())
        }
        val listener = listenerCaptor.firstValue

        // When
        listener.onVitalUpdate(VitalInfo(1, minRefreshRate, meanRefreshRate * 2, meanRefreshRate))
        val result = testedScope.handleEvent(RumRawEvent.KeepAlive(), mockWriter)

        // Then
        val expectedAverage = (meanRefreshRate * 60.0) / deviceRefreshRate
        val expectedMinimum = (minRefreshRate * 60.0) / deviceRefreshRate
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeName)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(expectedAverage, expectedMinimum)
                    isActive(true)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @TestTargetApi(Build.VERSION_CODES.R)
    @Test
    fun `𝕄 detect low refresh rate 𝕎 init()+onVitalUpdate()+handleEvent(KeepAlive) {Frag X}`(
        @FloatForgery(120.0f, 240.0f) deviceRefreshRate: Float,
        @DoubleForgery(30.0, 55.0) meanRefreshRate: Double,
        @DoubleForgery(0.0, 30.0) minRefreshRate: Double
    ) {
        // Given
        val mockFragment = mock<Fragment>()
        val mockActivity = mock<FragmentActivity>()
        val mockDisplay = mock<Display>()
        whenever(mockFragment.activity) doReturn mockActivity
        whenever(mockActivity.display) doReturn mockDisplay
        whenever(mockDisplay.refreshRate) doReturn deviceRefreshRate
        reset(mockFrameRateVitalMonitor)
        reset(mockTimeProvider)
        whenever(mockTimeProvider.getServerOffsetMillis())
            .thenReturn(fakeServerOffset)
            .thenReturn(fakeServerOffsetSecond)
        val testedScope = RumViewScope(
            mockParentScope,
            mockFragment,
            fakeName,
            fakeEventTime,
            fakeAttributes,
            mockDetector,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            mockTimeProvider
        )
        val listenerCaptor = argumentCaptor<VitalListener> {
            verify(mockFrameRateVitalMonitor).register(capture())
        }
        val listener = listenerCaptor.firstValue

        // When
        listener.onVitalUpdate(VitalInfo(1, minRefreshRate, meanRefreshRate * 2, meanRefreshRate))
        val result = testedScope.handleEvent(RumRawEvent.KeepAlive(), mockWriter)

        // Then
        val expectedAverage = (meanRefreshRate * 60.0) / deviceRefreshRate
        val expectedMinimum = (minRefreshRate * 60.0) / deviceRefreshRate
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeName)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(expectedAverage, expectedMinimum)
                    isActive(true)
                    isSlowRendered(true)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @TestTargetApi(Build.VERSION_CODES.R)
    @Test
    fun `𝕄 detect high refresh rate 𝕎 init()+onVitalUpdate()+handleEvent(KeepAlive) {Frag X}`(
        @FloatForgery(120.0f, 240.0f) deviceRefreshRate: Float,
        @DoubleForgery(55.0, 60.0) meanRefreshRate: Double,
        @DoubleForgery(50.0, 55.0) minRefreshRate: Double
    ) {
        // Given
        val mockFragment = mock<Fragment>()
        val mockActivity = mock<FragmentActivity>()
        val mockDisplay = mock<Display>()
        whenever(mockFragment.activity) doReturn mockActivity
        whenever(mockActivity.display) doReturn mockDisplay
        whenever(mockDisplay.refreshRate) doReturn deviceRefreshRate
        reset(mockFrameRateVitalMonitor)
        reset(mockTimeProvider)
        whenever(mockTimeProvider.getServerOffsetMillis())
            .thenReturn(fakeServerOffset)
            .thenReturn(fakeServerOffsetSecond)
        val testedScope = RumViewScope(
            mockParentScope,
            mockFragment,
            fakeName,
            fakeEventTime,
            fakeAttributes,
            mockDetector,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            mockTimeProvider
        )
        val listenerCaptor = argumentCaptor<VitalListener> {
            verify(mockFrameRateVitalMonitor).register(capture())
        }
        val listener = listenerCaptor.firstValue

        // When
        listener.onVitalUpdate(VitalInfo(1, minRefreshRate, meanRefreshRate * 2, meanRefreshRate))
        val result = testedScope.handleEvent(RumRawEvent.KeepAlive(), mockWriter)

        // Then
        val expectedAverage = (meanRefreshRate * 60.0) / deviceRefreshRate
        val expectedMinimum = (minRefreshRate * 60.0) / deviceRefreshRate
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeName)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(expectedAverage, expectedMinimum)
                    isActive(true)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Suppress("DEPRECATION")
    @TestTargetApi(Build.VERSION_CODES.R)
    @Test
    fun `𝕄 detect low refresh rate 𝕎 init()+onVitalUpdate()+handleEvent(KeepAlive) {Fragment}`(
        @FloatForgery(120.0f, 240.0f) deviceRefreshRate: Float,
        @DoubleForgery(30.0, 55.0) meanRefreshRate: Double,
        @DoubleForgery(0.0, 30.0) minRefreshRate: Double
    ) {
        // Given
        val mockFragment = mock<android.app.Fragment>()
        val mockActivity = mock<Activity>()
        val mockDisplay = mock<Display>()
        whenever(mockFragment.activity) doReturn mockActivity
        whenever(mockActivity.display) doReturn mockDisplay
        whenever(mockDisplay.refreshRate) doReturn deviceRefreshRate
        reset(mockFrameRateVitalMonitor)
        reset(mockTimeProvider)
        whenever(mockTimeProvider.getServerOffsetMillis())
            .thenReturn(fakeServerOffset)
            .thenReturn(fakeServerOffsetSecond)
        val testedScope = RumViewScope(
            mockParentScope,
            mockFragment,
            fakeName,
            fakeEventTime,
            fakeAttributes,
            mockDetector,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            mockTimeProvider
        )
        val listenerCaptor = argumentCaptor<VitalListener> {
            verify(mockFrameRateVitalMonitor).register(capture())
        }
        val listener = listenerCaptor.firstValue

        // When
        listener.onVitalUpdate(VitalInfo(1, minRefreshRate, meanRefreshRate * 2, meanRefreshRate))
        val result = testedScope.handleEvent(RumRawEvent.KeepAlive(), mockWriter)

        // Then
        val expectedAverage = (meanRefreshRate * 60.0) / deviceRefreshRate
        val expectedMinimum = (minRefreshRate * 60.0) / deviceRefreshRate
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeName)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(expectedAverage, expectedMinimum)
                    isActive(true)
                    isSlowRendered(true)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Suppress("DEPRECATION")
    @TestTargetApi(Build.VERSION_CODES.R)
    @Test
    fun `𝕄 detect high refresh rate 𝕎 init()+onVitalUpdate()+handleEvent(KeepAlive) {Fragment}`(
        @FloatForgery(120.0f, 240.0f) deviceRefreshRate: Float,
        @DoubleForgery(55.0, 60.0) meanRefreshRate: Double,
        @DoubleForgery(50.0, 55.0) minRefreshRate: Double
    ) {
        // Given
        val mockFragment = mock<android.app.Fragment>()
        val mockActivity = mock<Activity>()
        val mockDisplay = mock<Display>()
        whenever(mockFragment.activity) doReturn mockActivity
        whenever(mockActivity.display) doReturn mockDisplay
        whenever(mockDisplay.refreshRate) doReturn deviceRefreshRate
        reset(mockFrameRateVitalMonitor)
        reset(mockTimeProvider)
        whenever(mockTimeProvider.getServerOffsetMillis())
            .thenReturn(fakeServerOffset)
            .thenReturn(fakeServerOffsetSecond)
        val testedScope = RumViewScope(
            mockParentScope,
            mockFragment,
            fakeName,
            fakeEventTime,
            fakeAttributes,
            mockDetector,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            mockTimeProvider
        )
        val listenerCaptor = argumentCaptor<VitalListener> {
            verify(mockFrameRateVitalMonitor).register(capture())
        }
        val listener = listenerCaptor.firstValue

        // When
        listener.onVitalUpdate(VitalInfo(1, minRefreshRate, meanRefreshRate * 2, meanRefreshRate))
        val result = testedScope.handleEvent(RumRawEvent.KeepAlive(), mockWriter)

        // Then
        val expectedAverage = (meanRefreshRate * 60.0) / deviceRefreshRate
        val expectedMinimum = (minRefreshRate * 60.0) / deviceRefreshRate
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeName)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(expectedAverage, expectedMinimum)
                    isActive(true)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    containsExactlyContextAttributes(fakeAttributes)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    // endregion

    // region Internal

    private fun mockEvent(): RumRawEvent {
        val event: RumRawEvent = mock()
        whenever(event.eventTime) doReturn Time()
        return event
    }

    private fun forgeGlobalAttributes(
        forge: Forge,
        existingAttributes: Map<String, Any?>
    ): Map<String, Any?> {
        val existingKeys = existingAttributes.keys
        return forge.aFilteredMap(excludedKeys = existingKeys) {
            anHexadecimalString() to anAsciiString()
        }
    }

    private fun resolveExpectedTimestamp(timestamp: Long): Long {
        return timestamp + fakeServerOffset
    }

    // endregion

    companion object {
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
