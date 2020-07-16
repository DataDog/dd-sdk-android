/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import com.datadog.android.core.internal.data.Writer
import com.datadog.android.core.internal.domain.Time
import com.datadog.android.core.internal.net.info.NetworkInfo
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.core.internal.utils.loggableStackTrace
import com.datadog.android.log.internal.user.NoOpMutableUserInfoProvider
import com.datadog.android.log.internal.user.UserInfo
import com.datadog.android.log.internal.user.UserInfoProvider
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.assertj.RumEventAssert.Companion.assertThat
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.event.RumEvent
import com.datadog.android.rum.internal.domain.model.ViewEvent
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.forge.exhaustiveAttributes
import com.datadog.tools.unit.setStaticValue
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
import fr.xgouchet.elmyr.annotation.RegexForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.UUID
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
internal class RumViewScopeTest {

    lateinit var testedScope: RumViewScope

    @Mock
    lateinit var mockParentScope: RumScope

    @Mock
    lateinit var mockChildScope: RumScope

    @Mock
    lateinit var mockActionScope: RumActionScope

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @Mock
    lateinit var mockUserInfoProvider: UserInfoProvider

    @Mock
    lateinit var mockNetworkInfoProvider: NetworkInfoProvider

    @Mock
    lateinit var mockWriter: Writer<RumEvent>

    @RegexForgery("([a-z]+\\.)+[A-Z][a-z]+")
    lateinit var fakeName: String

    @RegexForgery("[a-f0-9]{8}-([a-f0-9]{4}-){3}[a-f0-9]{12}")
    lateinit var fakeActionId: String
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
        fakeEventTime = Time()

        RumFeature::class.java.setStaticValue("userInfoProvider", mockUserInfoProvider)
        RumFeature::class.java.setStaticValue("networkInfoProvider", mockNetworkInfoProvider)

        fakeAttributes = forge.exhaustiveAttributes()
        fakeKey = forge.anAsciiString().toByteArray()
        fakeEvent = mockEvent()

        whenever(mockUserInfoProvider.getUserInfo()) doReturn fakeUserInfo
        whenever(mockNetworkInfoProvider.getLatestNetworkInfo()) doReturn fakeNetworkInfo
        whenever(mockParentScope.getRumContext()) doReturn fakeParentContext
        whenever(mockChildScope.handleEvent(any(), any())) doReturn mockChildScope
        whenever(mockActionScope.handleEvent(any(), any())) doReturn mockActionScope
        whenever(mockActionScope.actionId) doReturn fakeActionId

        testedScope = RumViewScope(
            mockParentScope,
            fakeKey,
            fakeName,
            fakeEventTime,
            fakeAttributes
        )

        assertThat(GlobalRum.getRumContext()).isEqualTo(testedScope.getRumContext())
    }

    @AfterEach
    fun `tear down`() {
        RumFeature::class.java.setStaticValue("userInfoProvider", NoOpMutableUserInfoProvider())
        GlobalRum.globalAttributes.clear()
    }

    // region Context

    @Test
    fun `always returns the same viewId and viewUrl`() {
        val context = testedScope.getRumContext()

        assertThat(context.actionId).isNull()
        assertThat(context.viewId).isEqualTo(testedScope.viewId)
        assertThat(context.viewUrl).isEqualTo(testedScope.urlName)
        assertThat(context.applicationId).isEqualTo(fakeParentContext.applicationId)
        assertThat(context.sessionId).isEqualTo(fakeParentContext.sessionId)
    }

    @Test
    fun `always returns the active action id`() {
        testedScope.activeActionScope = mockActionScope

        val context = testedScope.getRumContext()

        assertThat(context.actionId).isEqualTo(fakeActionId)
        assertThat(context.viewId).isEqualTo(testedScope.viewId)
        assertThat(context.viewUrl).isEqualTo(testedScope.urlName)
        assertThat(context.applicationId).isEqualTo(fakeParentContext.applicationId)
        assertThat(context.sessionId).isEqualTo(fakeParentContext.sessionId)
    }

    @Test
    fun `updates the viewId when the parent sessionId changes`(
        @Forgery newSessionId: UUID
    ) {
        val initialViewId = testedScope.viewId
        val context = testedScope.getRumContext()
        whenever(mockParentScope.getRumContext())
            .doReturn(fakeParentContext.copy(sessionId = newSessionId.toString()))

        val updatedContext = testedScope.getRumContext()

        assertThat(context.actionId).isNull()
        assertThat(context.viewId).isEqualTo(initialViewId)
        assertThat(context.viewUrl).isEqualTo(testedScope.urlName)
        assertThat(context.sessionId).isEqualTo(fakeParentContext.sessionId)
        assertThat(context.applicationId).isEqualTo(fakeParentContext.applicationId)

        assertThat(updatedContext.actionId).isNull()
        assertThat(updatedContext.viewId).isNotEqualTo(initialViewId)
        assertThat(updatedContext.viewUrl).isEqualTo(testedScope.urlName)
        assertThat(updatedContext.sessionId).isEqualTo(newSessionId.toString())
        assertThat(updatedContext.applicationId).isEqualTo(fakeParentContext.applicationId)
    }

    // endregion

    // region View

    @Test
    fun `startView doesn't send anything for stopped view Rum Event`(
        @StringForgery(StringForgeryType.ALPHABETICAL) key: String,
        @StringForgery(StringForgeryType.ALPHABETICAL) name: String
    ) {
        testedScope.stopped = true

        val result = testedScope.handleEvent(
            RumRawEvent.StartView(key, name, emptyMap()),
            mockWriter
        )

        verifyZeroInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `startView sends current unstopped view Rum Event`(
        @StringForgery(StringForgeryType.ALPHABETICAL) key: String,
        @StringForgery(StringForgeryType.ALPHABETICAL) name: String
    ) {

        val result = testedScope.handleEvent(
            RumRawEvent.StartView(key, name, emptyMap()),
            mockWriter
        )

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasViewData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasName(fakeName.replace('.', '/'))
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
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
    fun `startView sends current unstopped view Rum Event only once`(
        @StringForgery(StringForgeryType.ALPHABETICAL) key: String,
        @StringForgery(StringForgeryType.ALPHABETICAL) name: String,
        @StringForgery(StringForgeryType.ALPHABETICAL) key2: String,
        @StringForgery(StringForgeryType.ALPHABETICAL) name2: String
    ) {

        val result = testedScope.handleEvent(
            RumRawEvent.StartView(key, name, emptyMap()),
            mockWriter
        )
        val result2 = testedScope.handleEvent(
            RumRawEvent.StartView(key2, name2, emptyMap()),
            mockWriter
        )

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasViewData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasName(fakeName.replace('.', '/'))
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
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
    fun `stopView sends current stopped view Rum Event`(
        forge: Forge
    ) {
        val attributes = forge.exhaustiveAttributes()
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, attributes),
            mockWriter
        )

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(expectedAttributes)
                .hasViewData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasName(fakeName.replace('.', '/'))
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
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
    fun `stopView sends current stopped view Rum Event with global attributes`(
        forge: Forge
    ) {
        val attributes = forge.aMap { anHexadecimalString() to anAsciiString() }
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        GlobalRum.globalAttributes.putAll(attributes)
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, emptyMap()),
            mockWriter
        )

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(expectedAttributes)
                .hasViewData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasName(fakeName.replace('.', '/'))
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
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
    fun `stopView sends current stopped view Rum Event only once`(
        forge: Forge
    ) {
        val attributes = forge.exhaustiveAttributes()
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, attributes),
            mockWriter
        )
        val result2 = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, attributes),
            mockWriter
        )

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(expectedAttributes)
                .hasViewData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasName(fakeName.replace('.', '/'))
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
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
    fun `stopView returns notNull if a resource is still active`(
        @StringForgery(StringForgeryType.ALPHABETICAL) key: String,
        forge: Forge
    ) {
        testedScope.activeResourceScopes.put(key, mockChildScope)

        val attributes = forge.exhaustiveAttributes()
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, attributes),
            mockWriter
        )

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(expectedAttributes)
                .hasViewData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasName(fakeName.replace('.', '/'))
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
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
    fun `stopView sends current stopped view Rum Event with missing key`() {
        fakeKey = ByteArray(0)
        System.gc()
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, emptyMap()),
            mockWriter
        )

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasViewData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasName(fakeName.replace('.', '/'))
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
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
    fun `stopView doesn't send anything without matching key`(
        @StringForgery(StringForgeryType.ALPHABETICAL) key: String,
        @StringForgery(StringForgeryType.ALPHABETICAL) name: String,
        forge: Forge
    ) {
        val attributes = forge.exhaustiveAttributes()
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        val result = testedScope.handleEvent(
            RumRawEvent.StopView(key, attributes),
            mockWriter
        )

        verifyZeroInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `sends View event on SentError`() {
        fakeEvent = RumRawEvent.SentError()

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasViewData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasName(fakeName.replace('.', '/'))
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(1)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
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
    fun `sends View event on SentResource`() {
        fakeEvent = RumRawEvent.SentResource()

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasViewData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasName(fakeName.replace('.', '/'))
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(1)
                    hasActionCount(0)
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
    fun `sends View event on SentAction`() {
        fakeEvent = RumRawEvent.SentAction()

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasViewData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasName(fakeName.replace('.', '/'))
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(1)
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
    fun `keepAlive doesn't send anything for stopped view Rum Event`(
        @StringForgery(StringForgeryType.ALPHABETICAL) key: String,
        @StringForgery(StringForgeryType.ALPHABETICAL) name: String
    ) {
        testedScope.stopped = true

        val result = testedScope.handleEvent(
            RumRawEvent.KeepAlive(),
            mockWriter
        )

        verifyZeroInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `keepAlive sends current unstopped view Rum Event`(
        @StringForgery(StringForgeryType.ALPHABETICAL) key: String,
        @StringForgery(StringForgeryType.ALPHABETICAL) name: String
    ) {

        val result = testedScope.handleEvent(
            RumRawEvent.KeepAlive(),
            mockWriter
        )

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasViewData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasName(fakeName.replace('.', '/'))
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasUserInfo(fakeUserInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    // endregion

    // region Action

    @Test
    fun `startAction adds a child Action Scope`(
        @Forgery type: RumActionType,
        @StringForgery(StringForgeryType.ALPHABETICAL) name: String,
        @BoolForgery waitForStop: Boolean,
        forge: Forge
    ) {
        val attributes = forge.exhaustiveAttributes()

        val result = testedScope.handleEvent(
            RumRawEvent.StartAction(type, name, waitForStop, attributes),
            mockWriter
        )

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
    fun `startAction ignored if previous action active`(
        @Forgery type: RumActionType,
        @StringForgery(StringForgeryType.ALPHABETICAL) name: String,
        @BoolForgery waitForStop: Boolean,
        forge: Forge
    ) {
        val attributes = forge.exhaustiveAttributes()
        testedScope.activeActionScope = mockChildScope
        fakeEvent = RumRawEvent.StartAction(type, name, waitForStop, attributes)
        whenever(mockChildScope.handleEvent(fakeEvent, mockWriter)) doReturn mockChildScope

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        verify(mockChildScope).handleEvent(fakeEvent, mockWriter)
        verifyZeroInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.activeActionScope).isSameAs(mockChildScope)
    }

    @Test
    fun `startAction ignored if view is stopped`(
        @Forgery type: RumActionType,
        @StringForgery(StringForgeryType.ALPHABETICAL) name: String,
        @BoolForgery waitForStop: Boolean,
        forge: Forge
    ) {
        val attributes = forge.exhaustiveAttributes()
        testedScope.stopped = true
        fakeEvent = RumRawEvent.StartAction(type, name, waitForStop, attributes)

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        verifyZeroInteractions(mockWriter)
        assertThat(result).isNull()
        assertThat(testedScope.activeActionScope).isNull()
    }

    @Test
    fun `unknown events are sent to children ActionScope`() {
        testedScope.activeActionScope = mockChildScope

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        verify(mockChildScope).handleEvent(fakeEvent, mockWriter)
        verifyZeroInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `remove child ActionScope when it updates to null`() {
        testedScope.activeActionScope = mockChildScope
        whenever(mockChildScope.handleEvent(fakeEvent, mockWriter)) doReturn null

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        verify(mockChildScope).handleEvent(fakeEvent, mockWriter)
        verifyZeroInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.activeActionScope).isNull()
    }

    // endregion

    // region Resource

    @Test
    fun `startResource adds a child ResourceScope`(
        @StringForgery(StringForgeryType.ALPHABETICAL) key: String,
        @StringForgery(StringForgeryType.ALPHABETICAL) method: String,
        @RegexForgery("http(s?)://[a-z]+.com/[a-z]+") url: String,
        forge: Forge
    ) {
        val attributes = forge.exhaustiveAttributes()

        val result = testedScope.handleEvent(
            RumRawEvent.StartResource(key, url, method, attributes),
            mockWriter
        )

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
    }

    @Test
    fun `startResource adds a child ResourceScope with active Action`(
        @StringForgery(StringForgeryType.ALPHABETICAL) key: String,
        @StringForgery(StringForgeryType.ALPHABETICAL) method: String,
        @RegexForgery("http(s?)://[a-z]+.com/[a-z]+") url: String,
        forge: Forge
    ) {
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes()
        fakeEvent = RumRawEvent.StartResource(key, url, method, attributes)

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

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
    }

    @Test
    fun `unknown events are sent to children ResourceScopes`(
        @StringForgery(StringForgeryType.ALPHABETICAL) key: String
    ) {
        testedScope.activeResourceScopes[key] = mockChildScope
        whenever(mockChildScope.handleEvent(fakeEvent, mockWriter)) doReturn mockChildScope

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        verify(mockChildScope).handleEvent(fakeEvent, mockWriter)
        verifyZeroInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `removes ResourceScope when it updates to null`(
        @StringForgery(StringForgeryType.ALPHABETICAL) key: String
    ) {
        testedScope.activeResourceScopes[key] = mockChildScope
        whenever(mockChildScope.handleEvent(fakeEvent, mockWriter)) doReturn null

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        verify(mockChildScope).handleEvent(fakeEvent, mockWriter)
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.activeResourceScopes).isEmpty()
    }

    // endregion

    // region Error

    @Test
    fun `sends Error and View event on AddError`(
        @StringForgery(StringForgeryType.ALPHABETICAL) message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        forge: Forge
    ) {
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes()
        fakeEvent = RumRawEvent.AddError(message, source, throwable, false, attributes)

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter, times(2)).write(capture())

            assertThat(firstValue)
                .hasAttributes(attributes)
                .hasErrorData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasMessage(message)
                    hasSource(source)
                    hasStackTrace(throwable.loggableStackTrace())
                    isCrash(false)
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(testedScope.viewId, testedScope.urlName)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                }

            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasViewData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasErrorCount(1)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
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
    fun `sends Error and View event on AddError with global attributes`(
        @StringForgery(StringForgeryType.ALPHABETICAL) message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        forge: Forge
    ) {
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.aMap<String, Any?> { anHexadecimalString() to anAsciiString() }
        fakeEvent = RumRawEvent.AddError(message, source, throwable, false, emptyMap())
        GlobalRum.globalAttributes.putAll(attributes)

        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        val expectedViewAttributes = attributes.toMutableMap().apply {
            putAll(fakeAttributes)
        }
        argumentCaptor<RumEvent> {
            verify(mockWriter, times(2)).write(capture())

            assertThat(firstValue)
                .hasAttributes(attributes)
                .hasErrorData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasMessage(message)
                    hasSource(source)
                    hasStackTrace(throwable.loggableStackTrace())
                    isCrash(false)
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(testedScope.viewId, testedScope.urlName)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                }

            assertThat(lastValue)
                .hasAttributes(expectedViewAttributes)
                .hasViewData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasErrorCount(1)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
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
    fun `sends Error and View event on fatal AddError`(
        @StringForgery(StringForgeryType.ALPHABETICAL) message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        forge: Forge
    ) {
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes()
        fakeEvent = RumRawEvent.AddError(message, source, throwable, true, attributes)

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter, times(2)).write(capture())

            assertThat(firstValue)
                .hasAttributes(attributes)
                .hasErrorData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasMessage(message)
                    hasSource(source)
                    hasStackTrace(throwable.loggableStackTrace())
                    isCrash(true)
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(testedScope.viewId, testedScope.urlName)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                }

            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasViewData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasErrorCount(1)
                    hasCrashCount(1)
                    hasResourceCount(0)
                    hasActionCount(0)
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
    fun `sends Error and View event on fatal AddError with global attributes`(
        @StringForgery(StringForgeryType.ALPHABETICAL) message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        forge: Forge
    ) {
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.aMap<String, Any?> { anHexadecimalString() to anAsciiString() }
        fakeEvent = RumRawEvent.AddError(message, source, throwable, true, emptyMap())
        GlobalRum.globalAttributes.putAll(attributes)

        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        val expectedViewAttributes = attributes.toMutableMap().apply {
            putAll(fakeAttributes)
        }
        argumentCaptor<RumEvent> {
            verify(mockWriter, times(2)).write(capture())

            assertThat(firstValue)
                .hasAttributes(attributes)
                .hasErrorData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasMessage(message)
                    hasSource(source)
                    hasStackTrace(throwable.loggableStackTrace())
                    isCrash(true)
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(testedScope.viewId, testedScope.urlName)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                }

            assertThat(lastValue)
                .hasAttributes(expectedViewAttributes)
                .hasViewData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasErrorCount(1)
                    hasCrashCount(1)
                    hasResourceCount(0)
                    hasActionCount(0)
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
    fun `updates view loading time and type when required`(forge: Forge) {
        val loadingTime = forge.aLong(min = 1)
        val loadingType = forge.aValueFrom(ViewEvent.LoadingType::class.java)
        val result = testedScope.handleEvent(
            RumRawEvent.UpdateViewLoadingTime(fakeKey, loadingTime, loadingType),
            mockWriter
        )

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasViewData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasName(fakeName.replace('.', '/'))
                    hasDurationGreaterThan(1)
                    hasLoadingTime(loadingTime)
                    hasLoadingType(loadingType)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
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
    fun `trying to update the loading time using a different key does nothing`(forge: Forge) {
        val differentKey = fakeKey + "different".toByteArray()
        val loadingTime = forge.aLong(min = 1)
        val loadingType = forge.aValueFrom(ViewEvent.LoadingType::class.java)
        val result = testedScope.handleEvent(
            RumRawEvent.UpdateViewLoadingTime(differentKey, loadingTime, loadingType),
            mockWriter
        )

        verifyZeroInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    // endregion

    private fun mockEvent(): RumRawEvent {
        val event: RumRawEvent = mock()
        whenever(event.eventTime) doReturn Time()
        return event
    }
}
