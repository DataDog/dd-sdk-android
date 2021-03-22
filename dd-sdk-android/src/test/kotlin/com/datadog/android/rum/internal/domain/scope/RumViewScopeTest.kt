/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import android.util.Log
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.net.FirstPartyHostDetector
import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.utils.loggableStackTrace
import com.datadog.android.core.internal.utils.resolveViewUrl
import com.datadog.android.core.model.NetworkInfo
import com.datadog.android.core.model.UserInfo
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.assertj.RumEventAssert.Companion.assertThat
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.domain.event.RumEvent
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.forge.exhaustiveAttributes
import com.datadog.android.utils.mockCoreFeature
import com.datadog.android.utils.mockDevLogHandler
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
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
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
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
internal class RumViewScopeTest {

    lateinit var testedScope: RumViewScope

    @Mock
    lateinit var mockParentScope: RumScope

    @Mock
    lateinit var mockChildScope: RumScope

    @Mock
    lateinit var mockActionScope: RumActionScope

    @Mock
    lateinit var mockWriter: DataWriter<RumEvent>

    @Mock
    lateinit var mockDetector: FirstPartyHostDetector

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

    lateinit var fakeEvent: RumRawEvent

    @BeforeEach
    fun `set up`(forge: Forge) {

        val fakeOffset = -forge.aLong(1000, 50000)
        val fakeTimestamp = System.currentTimeMillis() + fakeOffset
        val fakeNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(fakeOffset)
        fakeEventTime = Time(fakeTimestamp, fakeNanos)
        fakeAttributes = forge.exhaustiveAttributes()
        fakeKey = forge.anAsciiString().toByteArray()
        fakeEvent = mockEvent()
        fakeUrl = fakeKey.resolveViewUrl().replace('.', '/')

        mockCoreFeature()
        whenever(CoreFeature.userInfoProvider.getUserInfo()) doReturn fakeUserInfo
        whenever(CoreFeature.networkInfoProvider.getLatestNetworkInfo()) doReturn fakeNetworkInfo
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
            mockDetector
        )

        assertThat(GlobalRum.getRumContext()).isEqualTo(testedScope.getRumContext())
    }

    @AfterEach
    fun `tear down`() {
        CoreFeature.stop()
        GlobalRum.globalAttributes.clear()
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
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasViewData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    isActive(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
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
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasViewData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    isActive(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
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
        val attributes = forge.exhaustiveAttributes()
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, attributes),
            mockWriter
        )

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(expectedAttributes)
                .hasViewData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    isActive(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
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
        val attributes = forge.exhaustiveAttributes()
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, attributes),
            mockWriter
        )

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(expectedAttributes)
                .hasViewData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    isActive(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 send event with user extra attributes 𝕎 handleEvent(StopView) on active view`() {
        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, emptyMap()),
            mockWriter
        )

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasViewData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    isActive(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
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
        val attributes = forge.aMap { anHexadecimalString() to anAsciiString() }
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        // When
        GlobalRum.globalAttributes.putAll(attributes)
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, emptyMap()),
            mockWriter
        )

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(expectedAttributes)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasViewData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    isActive(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
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
        testedScope = RumViewScope(
            mockParentScope,
            fakeKey,
            fakeName,
            fakeEventTime,
            fakeAttributes,
            mockDetector
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
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(expectedAttributes)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasViewData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    isActive(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
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
        testedScope = RumViewScope(
            mockParentScope,
            fakeKey,
            fakeName,
            fakeEventTime,
            fakeAttributes,
            mockDetector
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
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(expectedAttributes)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasViewData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    isActive(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
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
        val attributes = forge.exhaustiveAttributes()
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
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(expectedAttributes)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasViewData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    isActive(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
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
        val attributes = forge.exhaustiveAttributes()
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, attributes),
            mockWriter
        )

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(expectedAttributes)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasViewData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    isActive(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
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
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasViewData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    isActive(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
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
        val attributes = forge.exhaustiveAttributes()
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
        val attributes = forge.exhaustiveAttributes()
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
        fakeEvent = RumRawEvent.ErrorSent(testedScope.viewId, isCrash = false)
        testedScope.pendingErrorCount = pending

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasViewData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(1)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    isActive(true)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.pendingErrorCount).isEqualTo(pending - 1)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(ErrorSent) on active view {isCrash = true}`(
        @LongForgery(1) pending: Long
    ) {
        // Given
        fakeEvent = RumRawEvent.ErrorSent(testedScope.viewId, isCrash = true)
        testedScope.pendingErrorCount = pending

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasViewData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(1)
                    hasCrashCount(1)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    isActive(true)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.pendingErrorCount).isEqualTo(pending - 1)
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(ErrorSent) on active view {unknown viewId}`(
        @Forgery viewUuid: UUID,
        @BoolForgery isCrash: Boolean,
        @LongForgery(1) pending: Long
    ) {
        // Given
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        fakeEvent = RumRawEvent.ErrorSent(viewId, isCrash)
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
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasViewData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(1)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    isActive(true)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
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
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasViewData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(1)
                    hasLongTaskCount(0)
                    isActive(true)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
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
    fun `𝕄 send event 𝕎 handleEvent(LongTaskSent) on active view`(
        @LongForgery(1) pending: Long
    ) {
        // Given
        fakeEvent = RumRawEvent.LongTaskSent(testedScope.viewId)
        testedScope.pendingLongTaskCount = pending

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasViewData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(1)
                    isActive(true)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.pendingLongTaskCount).isEqualTo(pending - 1)
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
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(firstValue)
                .hasAttributes(attributes)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasActionData {
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
        fakeEvent = RumRawEvent.ErrorSent(testedScope.viewId, isCrash = false)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasViewData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(1)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    isActive(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
        assertThat(testedScope.pendingErrorCount).isEqualTo(0)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(ErrorSent) on stopped view {isCrash = true}`() {
        // Given
        testedScope.stopped = true
        testedScope.pendingErrorCount = 1
        fakeEvent = RumRawEvent.ErrorSent(testedScope.viewId, isCrash = true)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasViewData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(1)
                    hasCrashCount(1)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    isActive(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
        assertThat(testedScope.pendingErrorCount).isEqualTo(0)
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(ErrorSent) on stopped view {unknown viewId}`(
        @Forgery viewUuid: UUID,
        @BoolForgery isCrash: Boolean,
        @LongForgery(1) pending: Long
    ) {
        // Given
        testedScope.stopped = true
        testedScope.pendingErrorCount = pending
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        fakeEvent = RumRawEvent.ErrorSent(viewId, isCrash)

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
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasViewData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(1)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    isActive(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
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
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasViewData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(1)
                    hasLongTaskCount(0)
                    isActive(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
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
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasViewData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(1)
                    isActive(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
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
        argumentCaptor<RumEvent> {
            verify(mockWriter, times(2)).write(capture())
            assertThat(firstValue)
                .hasActionData {
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
        argumentCaptor<RumEvent> {
            verify(mockWriter, times(1)).write(capture())
            assertThat(firstValue)
                .hasActionData {
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
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasViewData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasName(fakeName)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasLongTaskCount(0)
                    isActive(true)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
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
        val attributes = forge.exhaustiveAttributes()

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StartAction(type, name, waitForStop, attributes),
            mockWriter
        )

        // Then
        verifyZeroInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.activeActionScope).isInstanceOf(RumActionScope::class.java)
        val actionScope = testedScope.activeActionScope as RumActionScope
        assertThat(actionScope.name).isEqualTo(name)
        assertThat(actionScope.waitForStop).isEqualTo(waitForStop)
        assertThat(actionScope.attributes).containsAllEntriesOf(attributes)
        assertThat(actionScope.parentScope).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 do nothing and log warning 𝕎 handleEvent(StartAction) with active child ActionScope`(
        @Forgery type: RumActionType,
        @StringForgery name: String,
        @BoolForgery waitForStop: Boolean,
        forge: Forge
    ) {
        // Given
        val mockDevLogHandler = mockDevLogHandler()
        val attributes = forge.exhaustiveAttributes()
        testedScope.activeActionScope = mockChildScope
        fakeEvent = RumRawEvent.StartAction(type, name, waitForStop, attributes)
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
    fun `𝕄 do nothing 𝕎 handleEvent(StartAction) on stopped view`(
        @Forgery type: RumActionType,
        @StringForgery name: String,
        @BoolForgery waitForStop: Boolean,
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes()
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
        @StringForgery(regex = "http(s?)://[a-z]+.com/[a-z]+") url: String,
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes()

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StartResource(key, url, method, attributes),
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
        assertThat(resourceScope.firstPartyHostDetector).isSameAs(mockDetector)
    }

    @Test
    fun `𝕄 create ResourceScope with active actionId 𝕎 handleEvent(StartResource)`(
        @StringForgery key: String,
        @StringForgery method: String,
        @StringForgery(regex = "http(s?)://[a-z]+.com/[a-z]+") url: String,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes()
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
        @StringForgery(regex = "http(s?)://[a-z]+.com/[a-z]+") url: String
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
        val attributes = forge.exhaustiveAttributes()
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace,
            false,
            attributes
        )

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())

            assertThat(firstValue)
                .hasAttributes(attributes)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasErrorData {
                    hasTimestamp(fakeEvent.eventTime.timestamp)
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
        val attributes = forge.exhaustiveAttributes()
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            null,
            stacktrace,
            false,
            attributes
        )

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())

            assertThat(firstValue)
                .hasAttributes(attributes)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasErrorData {
                    hasTimestamp(fakeEvent.eventTime.timestamp)
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
        forge: Forge
    ) {
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes()
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            null,
            false,
            attributes
        )

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())

            assertThat(firstValue)
                .hasAttributes(attributes)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasErrorData {
                    hasTimestamp(fakeEvent.eventTime.timestamp)
                    hasMessage(message)
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
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(AddError) {throwable=null, stacktrace=null}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @BoolForgery fatal: Boolean,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes()
        fakeEvent = RumRawEvent.AddError(message, source, null, null, fatal, attributes)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())

            assertThat(firstValue)
                .hasAttributes(attributes)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasErrorData {
                    hasTimestamp(fakeEvent.eventTime.timestamp)
                    hasMessage(message)
                    hasSource(source)
                    hasStackTrace(null)
                    isCrash(fatal)
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(testedScope.viewId, testedScope.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasErrorType(null)
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
            emptyMap()
        )
        val attributes = forgeGlobalAttributes(forge, fakeAttributes)
        GlobalRum.globalAttributes.putAll(attributes)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())

            assertThat(firstValue)
                .hasAttributes(attributes)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasErrorData {
                    hasTimestamp(fakeEvent.eventTime.timestamp)
                    hasMessage(message)
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
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes()
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            null,
            true,
            attributes
        )

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())

            assertThat(firstValue)
                .hasAttributes(attributes)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasErrorData {
                    hasTimestamp(fakeEvent.eventTime.timestamp)
                    hasMessage(message)
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
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes()
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            null,
            true,
            attributes,
            type = errorType
        )

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())

            assertThat(firstValue)
                .hasAttributes(attributes)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasErrorData {
                    hasTimestamp(fakeEvent.eventTime.timestamp)
                    hasMessage(message)
                    hasSource(source)
                    hasStackTrace(throwable.loggableStackTrace())
                    isCrash(true)
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(testedScope.viewId, testedScope.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasErrorType(errorType)
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
            emptyMap()
        )
        val attributes = forgeGlobalAttributes(forge, fakeAttributes)
        GlobalRum.globalAttributes.putAll(attributes)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())

            assertThat(firstValue)
                .hasAttributes(attributes)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasErrorData {
                    hasTimestamp(fakeEvent.eventTime.timestamp)
                    hasMessage(message)
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
        val attributes = forge.exhaustiveAttributes()
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
        val attributes = forge.exhaustiveAttributes()
        fakeEvent = RumRawEvent.AddError(message, source, null, stacktrace, fatal, attributes)
        testedScope.stopped = true

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyZeroInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 wait for pending Error 𝕎 handleEvent(AddError) on active view`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @StringForgery stacktrace: String,
        @BoolForgery fatal: Boolean
    ) {
        // Given
        testedScope.pendingErrorCount = 0
        fakeEvent = RumRawEvent.AddError(message, source, null, stacktrace, fatal, emptyMap())

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingErrorCount).isEqualTo(1)
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
    fun `𝕄 send event 𝕎 handleEvent(AddLongTask) on active view`(
        @LongForgery(0) durationNs: Long,
        @StringForgery target: String
    ) {
        // Given
        testedScope.activeActionScope = null
        fakeEvent = RumRawEvent.AddLongTask(durationNs, target)
        val durationMs = TimeUnit.NANOSECONDS.toMillis(durationNs)

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())

            assertThat(firstValue)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasLongTaskData {
                    hasTimestamp(fakeEvent.eventTime.timestamp - durationMs)
                    hasDuration(durationNs)
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(testedScope.viewId, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event with global attributes 𝕎 handleEvent(AddLongTask)`(
        @LongForgery(0) durationNs: Long,
        @StringForgery target: String,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        fakeEvent = RumRawEvent.AddLongTask(durationNs, target)
        val attributes = forgeGlobalAttributes(forge, fakeAttributes)
        val durationMs = TimeUnit.NANOSECONDS.toMillis(durationNs)
        GlobalRum.globalAttributes.putAll(attributes)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())

            assertThat(firstValue)
                .hasAttributes(attributes)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasLongTaskData {
                    hasTimestamp(fakeEvent.eventTime.timestamp - durationMs)
                    hasDuration(durationNs)
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(testedScope.viewId, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
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
    fun `𝕄 wait for pending Long Task 𝕎 handleEvent(AddLongTask) on active view`(
        @LongForgery(0) duration: Long,
        @StringForgery target: String
    ) {
        // Given
        testedScope.pendingLongTaskCount = 0
        fakeEvent = RumRawEvent.AddLongTask(duration, target)

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingLongTaskCount).isEqualTo(1)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 decrease pending Long Task 𝕎 handleEvent(LongTaskDropped) on active view`(
        @LongForgery(1) pending: Long
    ) {
        // Given
        testedScope.pendingLongTaskCount = pending
        fakeEvent = RumRawEvent.LongTaskDropped(testedScope.viewId)

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingLongTaskCount).isEqualTo(pending - 1)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 decrease pending Long Task 𝕎 handleEvent(LongTaskDropped) on stopped view`() {
        // Given
        testedScope.pendingLongTaskCount = 1
        fakeEvent = RumRawEvent.LongTaskDropped(testedScope.viewId)
        testedScope.stopped = true

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingLongTaskCount).isEqualTo(0)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(LongTaskDropped) on active view {unknown viewId}`(
        @LongForgery(1) pending: Long,
        @Forgery viewUuid: UUID
    ) {
        // Given
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        testedScope.pendingLongTaskCount = pending
        fakeEvent = RumRawEvent.LongTaskDropped(viewId)

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingLongTaskCount).isEqualTo(pending)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(LongTaskDropped) on stopped view {unknown viewId}`(
        @LongForgery(1) pending: Long,
        @Forgery viewUuid: UUID
    ) {
        // Given
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        testedScope.pendingLongTaskCount = pending
        fakeEvent = RumRawEvent.LongTaskDropped(viewId)
        testedScope.stopped = true

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingLongTaskCount).isEqualTo(pending)
        assertThat(result).isSameAs(testedScope)
    }

    // endregion

    // region Loading Time

    @Test
    fun `𝕄 send event 𝕎 handleEvent(UpdateViewLoadingTime) on active view`(forge: Forge) {
        // Given
        val loadingTime = forge.aLong(min = 1)
        val loadingType = forge.aValueFrom(ViewEvent.LoadingType::class.java)

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.UpdateViewLoadingTime(fakeKey, loadingTime, loadingType),
            mockWriter
        )

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasViewData {
                    hasTimestamp(fakeEventTime.timestamp)
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
                    isActive(true)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(UpdateViewLoadingTime) on stopped view`(forge: Forge) {
        // Given
        testedScope.stopped = true
        val loadingTime = forge.aLong(min = 1)
        val loadingType = forge.aValueFrom(ViewEvent.LoadingType::class.java)

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.UpdateViewLoadingTime(fakeKey, loadingTime, loadingType),
            mockWriter
        )

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasViewData {
                    hasTimestamp(fakeEventTime.timestamp)
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
                    isActive(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(UpdateViewLoadingTime) with different key`(forge: Forge) {
        // Given
        val differentKey = fakeKey + "different".toByteArray()
        val loadingTime = forge.aLong(min = 1)
        val loadingType = forge.aValueFrom(ViewEvent.LoadingType::class.java)

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
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasViewData {
                    hasTimestamp(fakeEventTime.timestamp)
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
                    isActive(true)
                    hasCustomTimings(mapOf(fakeTimingKey to customTimingEstimatedDuration))
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
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
        argumentCaptor<RumEvent> {
            verify(mockWriter, times(2)).write(capture())
            assertThat(firstValue)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasViewData {
                    hasTimestamp(fakeEventTime.timestamp)
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
                    isActive(true)
                    hasCustomTimings(mapOf(fakeTimingKey1 to customTiming1EstimatedDuration))
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                }
            assertThat(lastValue)
                .hasUserExtraAttributes(fakeUserInfo.additionalProperties)
                .hasViewData {
                    hasTimestamp(fakeEventTime.timestamp)
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
                    isActive(true)
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
                }
        }
        verifyNoMoreInteractions(mockWriter)
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
        return forge.aMap<String, Any?> { anHexadecimalString() to anAsciiString() }
            .filter { it.key !in existingKeys }
    }

    // endregion
}
