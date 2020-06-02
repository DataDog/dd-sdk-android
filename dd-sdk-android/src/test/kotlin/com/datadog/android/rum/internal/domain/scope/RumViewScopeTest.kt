/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import com.datadog.android.core.internal.data.Writer
import com.datadog.android.core.internal.net.info.NetworkInfo
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.time.SystemTimeProvider
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.log.internal.user.NoOpMutableUserInfoProvider
import com.datadog.android.log.internal.user.UserInfo
import com.datadog.android.log.internal.user.UserInfoProvider
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.assertj.RumEventAssert.Companion.assertThat
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.event.RumEvent
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.forge.exhaustiveAttributes
import com.datadog.tools.unit.setStaticValue
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
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
    lateinit var mockEvent: RumRawEvent

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
    lateinit var fakeKey: ByteArray
    lateinit var fakeAttributes: Map<String, Any?>

    @Forgery
    lateinit var fakeParentContext: RumContext

    @Forgery
    lateinit var fakeUserInfo: UserInfo

    @Forgery
    lateinit var fakeNetworkInfo: NetworkInfo

    @LongForgery
    var fakeTimeStamp: Long = 0L

    @BeforeEach
    fun `set up`(forge: Forge) {
        RumFeature::class.java.setStaticValue("timeProvider", mockTimeProvider)
        RumFeature::class.java.setStaticValue("userInfoProvider", mockUserInfoProvider)
        RumFeature::class.java.setStaticValue("networkInfoProvider", mockNetworkInfoProvider)

        fakeAttributes = forge.exhaustiveAttributes()
        fakeKey = forge.anAsciiString().toByteArray()

        whenever(mockTimeProvider.getDeviceTimestamp()) doReturn fakeTimeStamp
        whenever(mockUserInfoProvider.getUserInfo()) doReturn fakeUserInfo
        whenever(mockNetworkInfoProvider.getLatestNetworkInfo()) doReturn fakeNetworkInfo
        whenever(mockParentScope.getRumContext()) doReturn fakeParentContext
        whenever(mockChildScope.handleEvent(any(), any())) doReturn mockChildScope
        whenever(mockActionScope.handleEvent(any(), any())) doReturn mockActionScope

        testedScope = RumViewScope(mockParentScope, fakeKey, fakeName, fakeAttributes)

        assertThat(GlobalRum.getRumContext()).isEqualTo(testedScope.getRumContext())
    }

    @AfterEach
    fun `tear down`() {
        RumFeature::class.java.setStaticValue("timeProvider", SystemTimeProvider())
        RumFeature::class.java.setStaticValue("userInfoProvider", NoOpMutableUserInfoProvider())
        GlobalRum.globalAttributes.clear()
    }

    @Test
    fun `always returns the same viewId`() {
        val context = testedScope.getRumContext()

        assertThat(context.viewId).isEqualTo(testedScope.viewId)
        assertThat(context.applicationId).isEqualTo(fakeParentContext.applicationId)
        assertThat(context.sessionId).isEqualTo(fakeParentContext.sessionId)
    }

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
                .hasTimestamp(fakeTimeStamp)
                .hasUserInfo(fakeUserInfo)
                .hasNetworkInfo(null)
                .hasAttributes(fakeAttributes)
                .hasViewData {
                    hasName(fakeName.replace('.', '/'))
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasMeasures {
                        hasErrorCount(0)
                        hasResourceCount(0)
                        hasUserActionCount(0)
                    }
                }
                .hasContext {
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
                .hasTimestamp(fakeTimeStamp)
                .hasUserInfo(fakeUserInfo)
                .hasNetworkInfo(null)
                .hasAttributes(fakeAttributes)
                .hasViewData {
                    hasName(fakeName.replace('.', '/'))
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasMeasures {
                        hasErrorCount(0)
                        hasResourceCount(0)
                        hasUserActionCount(0)
                    }
                }
                .hasContext {
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
                .hasTimestamp(fakeTimeStamp)
                .hasUserInfo(fakeUserInfo)
                .hasNetworkInfo(null)
                .hasAttributes(expectedAttributes)
                .hasViewData {
                    hasName(fakeName.replace('.', '/'))
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasMeasures {
                        hasErrorCount(0)
                        hasResourceCount(0)
                        hasUserActionCount(0)
                    }
                }
                .hasContext {
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
                .hasTimestamp(fakeTimeStamp)
                .hasUserInfo(fakeUserInfo)
                .hasNetworkInfo(null)
                .hasAttributes(expectedAttributes)
                .hasViewData {
                    hasName(fakeName.replace('.', '/'))
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasMeasures {
                        hasErrorCount(0)
                        hasResourceCount(0)
                        hasUserActionCount(0)
                    }
                }
                .hasContext {
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
                .hasTimestamp(fakeTimeStamp)
                .hasUserInfo(fakeUserInfo)
                .hasNetworkInfo(null)
                .hasAttributes(expectedAttributes)
                .hasViewData {
                    hasName(fakeName.replace('.', '/'))
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasMeasures {
                        hasErrorCount(0)
                        hasResourceCount(0)
                        hasUserActionCount(0)
                    }
                }
                .hasContext {
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
                .hasTimestamp(fakeTimeStamp)
                .hasUserInfo(fakeUserInfo)
                .hasNetworkInfo(null)
                .hasAttributes(expectedAttributes)
                .hasViewData {
                    hasName(fakeName.replace('.', '/'))
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasMeasures {
                        hasErrorCount(0)
                        hasResourceCount(0)
                        hasUserActionCount(0)
                    }
                }
                .hasContext {
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
                .hasTimestamp(fakeTimeStamp)
                .hasUserInfo(fakeUserInfo)
                .hasNetworkInfo(null)
                .hasAttributes(fakeAttributes)
                .hasViewData {
                    hasName(fakeName.replace('.', '/'))
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasMeasures {
                        hasErrorCount(0)
                        hasResourceCount(0)
                        hasUserActionCount(0)
                    }
                }
                .hasContext {
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
        mockEvent = RumRawEvent.SentError()

        val result = testedScope.handleEvent(mockEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasTimestamp(fakeTimeStamp)
                .hasUserInfo(fakeUserInfo)
                .hasNetworkInfo(null)
                .hasAttributes(fakeAttributes)
                .hasViewData {
                    hasName(fakeName.replace('.', '/'))
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasMeasures {
                        hasErrorCount(1)
                        hasResourceCount(0)
                        hasUserActionCount(0)
                    }
                }
                .hasContext {
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
        mockEvent = RumRawEvent.SentResource()

        val result = testedScope.handleEvent(mockEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasTimestamp(fakeTimeStamp)
                .hasUserInfo(fakeUserInfo)
                .hasNetworkInfo(null)
                .hasAttributes(fakeAttributes)
                .hasViewData {
                    hasName(fakeName.replace('.', '/'))
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasMeasures {
                        hasErrorCount(0)
                        hasResourceCount(1)
                        hasUserActionCount(0)
                    }
                }
                .hasContext {
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
        mockEvent = RumRawEvent.SentAction()

        val result = testedScope.handleEvent(mockEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasTimestamp(fakeTimeStamp)
                .hasUserInfo(fakeUserInfo)
                .hasNetworkInfo(null)
                .hasAttributes(fakeAttributes)
                .hasViewData {
                    hasName(fakeName.replace('.', '/'))
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasMeasures {
                        hasErrorCount(0)
                        hasResourceCount(0)
                        hasUserActionCount(1)
                    }
                }
                .hasContext {
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
                .hasTimestamp(fakeTimeStamp)
                .hasUserInfo(fakeUserInfo)
                .hasNetworkInfo(null)
                .hasAttributes(fakeAttributes)
                .hasViewData {
                    hasName(fakeName.replace('.', '/'))
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasMeasures {
                        hasErrorCount(0)
                        hasResourceCount(0)
                        hasUserActionCount(0)
                    }
                }
                .hasContext {
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
        @StringForgery(StringForgeryType.ALPHABETICAL) name: String,
        @BoolForgery waitForStop: Boolean,
        forge: Forge
    ) {
        val attributes = forge.exhaustiveAttributes()

        val result = testedScope.handleEvent(
            RumRawEvent.StartAction(name, waitForStop, attributes),
            mockWriter
        )
        val expectedAttributes = attributes.toMutableMap().apply {
            put(RumAttributes.VIEW_URL, testedScope.urlName)
        }

        verifyZeroInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.activeActionScope).isInstanceOf(RumActionScope::class.java)
        val actionScope = testedScope.activeActionScope as RumActionScope
        assertThat(actionScope.name).isEqualTo(name)
        assertThat(actionScope.waitForStop).isEqualTo(waitForStop)
        assertThat(actionScope.attributes).containsAllEntriesOf(expectedAttributes)
        assertThat(actionScope.parentScope).isSameAs(testedScope)
    }

    @Test
    fun `startAction ignored if previous action active`(
        @StringForgery(StringForgeryType.ALPHABETICAL) name: String,
        @BoolForgery waitForStop: Boolean,
        forge: Forge
    ) {
        val attributes = forge.exhaustiveAttributes()
        testedScope.activeActionScope = mockChildScope
        mockEvent = RumRawEvent.StartAction(name, waitForStop, attributes)
        whenever(mockChildScope.handleEvent(mockEvent, mockWriter)) doReturn mockChildScope

        val result = testedScope.handleEvent(mockEvent, mockWriter)

        verify(mockChildScope).handleEvent(mockEvent, mockWriter)
        verifyZeroInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.activeActionScope).isSameAs(mockChildScope)
    }

    @Test
    fun `startAction ignored if view is stopped`(
        @StringForgery(StringForgeryType.ALPHABETICAL) name: String,
        @BoolForgery waitForStop: Boolean,
        forge: Forge
    ) {
        val attributes = forge.exhaustiveAttributes()
        testedScope.stopped = true
        mockEvent = RumRawEvent.StartAction(name, waitForStop, attributes)

        val result = testedScope.handleEvent(mockEvent, mockWriter)

        verifyZeroInteractions(mockWriter)
        assertThat(result).isNull()
        assertThat(testedScope.activeActionScope).isNull()
    }

    @Test
    fun `unknown events are sent to children ActionScope`() {
        testedScope.activeActionScope = mockChildScope

        val result = testedScope.handleEvent(mockEvent, mockWriter)

        verify(mockChildScope).handleEvent(mockEvent, mockWriter)
        verifyZeroInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `remove child ActionScope when it updates to null`() {
        testedScope.activeActionScope = mockChildScope
        whenever(mockChildScope.handleEvent(mockEvent, mockWriter)) doReturn null

        val result = testedScope.handleEvent(mockEvent, mockWriter)

        verify(mockChildScope).handleEvent(mockEvent, mockWriter)
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
        val expectedAttributes = attributes.toMutableMap().apply {
            put(RumAttributes.VIEW_URL, testedScope.urlName)
        }

        verifyZeroInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.activeResourceScopes).isNotEmpty()
        val entry = testedScope.activeResourceScopes.entries.first()
        assertThat(entry.key).isEqualTo(key)
        assertThat(entry.value).isInstanceOf(RumResourceScope::class.java)
        val resourceScope = entry.value as RumResourceScope
        assertThat(resourceScope.parentScope).isSameAs(testedScope)
        assertThat(resourceScope.attributes).containsAllEntriesOf(expectedAttributes)
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
        val randomActionId = UUID.randomUUID()
        whenever(mockActionScope.actionId).thenReturn(randomActionId)
        val attributes = forge.exhaustiveAttributes()
        mockEvent = RumRawEvent.StartResource(key, url, method, attributes)

        val result = testedScope.handleEvent(mockEvent, mockWriter)
        val expectedAttributes = attributes.toMutableMap().apply {
            put(RumAttributes.VIEW_URL, testedScope.urlName)
            put(RumAttributes.ACTION_ID, randomActionId.toString())
        }

        verify(mockActionScope).handleEvent(mockEvent, mockWriter)
        verifyZeroInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.activeResourceScopes).isNotEmpty()
        val entry = testedScope.activeResourceScopes.entries.first()
        assertThat(entry.key).isEqualTo(key)
        val resourceScope = entry.value as RumResourceScope
        assertThat(resourceScope.parentScope).isSameAs(testedScope)
        assertThat(resourceScope.attributes).containsAllEntriesOf(expectedAttributes)
        assertThat(resourceScope.key).isSameAs(key)
        assertThat(resourceScope.url).isEqualTo(url)
        assertThat(resourceScope.method).isSameAs(method)
    }

    @Test
    fun `unknown events are sent to children ResourceScopes`(
        @StringForgery(StringForgeryType.ALPHABETICAL) key: String
    ) {
        testedScope.activeResourceScopes[key] = mockChildScope
        whenever(mockChildScope.handleEvent(mockEvent, mockWriter)) doReturn mockChildScope

        val result = testedScope.handleEvent(mockEvent, mockWriter)

        verify(mockChildScope).handleEvent(mockEvent, mockWriter)
        verifyZeroInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `removes ResourceScope when it updates to null`(
        @StringForgery(StringForgeryType.ALPHABETICAL) key: String
    ) {
        testedScope.activeResourceScopes[key] = mockChildScope
        whenever(mockChildScope.handleEvent(mockEvent, mockWriter)) doReturn null

        val result = testedScope.handleEvent(mockEvent, mockWriter)

        verify(mockChildScope).handleEvent(mockEvent, mockWriter)
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.activeResourceScopes).isEmpty()
    }

    // endregion

    // region Error

    @Test
    fun `sends Error and View event on AddError`(
        @StringForgery(StringForgeryType.ALPHABETICAL) message: String,
        @StringForgery(StringForgeryType.ALPHABETICAL) origin: String,
        @Forgery throwable: Throwable,
        forge: Forge
    ) {
        testedScope.activeActionScope = mockActionScope
        val randomActionId = UUID.randomUUID()
        whenever(mockActionScope.actionId).thenReturn(randomActionId)
        val attributes = forge.exhaustiveAttributes()
        mockEvent = RumRawEvent.AddError(message, origin, throwable, attributes)

        val result = testedScope.handleEvent(mockEvent, mockWriter)
        val expectedAttributes = attributes.toMutableMap().apply {
            put(RumAttributes.VIEW_URL, testedScope.urlName)
            put(RumAttributes.ACTION_ID, randomActionId.toString())
        }
        argumentCaptor<RumEvent> {
            verify(mockWriter, times(2)).write(capture())

            assertThat(firstValue)
                .hasTimestamp(fakeTimeStamp)
                .hasUserInfo(fakeUserInfo)
                .hasNetworkInfo(fakeNetworkInfo)
                .hasAttributes(expectedAttributes)
                .hasErrorData {
                    hasMessage(message)
                    hasOrigin(origin)
                    hasThrowable(throwable)
                }
                .hasContext {
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                }

            assertThat(lastValue)
                .hasTimestamp(fakeTimeStamp)
                .hasUserInfo(fakeUserInfo)
                .hasNetworkInfo(null)
                .hasAttributes(fakeAttributes)
                .hasViewData {
                    hasMeasures {
                        hasErrorCount(1)
                        hasResourceCount(0)
                        hasUserActionCount(0)
                    }
                }
                .hasContext {
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
        @StringForgery(StringForgeryType.ALPHABETICAL) origin: String,
        @Forgery throwable: Throwable,
        forge: Forge
    ) {
        testedScope.activeActionScope = mockActionScope
        val randomActionId = UUID.randomUUID()
        whenever(mockActionScope.actionId).thenReturn(randomActionId)
        val attributes = forge.aMap<String, Any?> { anHexadecimalString() to anAsciiString() }
        mockEvent = RumRawEvent.AddError(message, origin, throwable, emptyMap())
        GlobalRum.globalAttributes.putAll(attributes)

        val result = testedScope.handleEvent(mockEvent, mockWriter)
        val expectedErrorAttributes = attributes.toMutableMap().apply {
            put(RumAttributes.VIEW_URL, testedScope.urlName)
            put(RumAttributes.ACTION_ID, randomActionId.toString())
        }
        val expectedViewAttributes = attributes.toMutableMap().apply {
            putAll(fakeAttributes)
        }
        argumentCaptor<RumEvent> {
            verify(mockWriter, times(2)).write(capture())

            assertThat(firstValue)
                .hasTimestamp(fakeTimeStamp)
                .hasUserInfo(fakeUserInfo)
                .hasNetworkInfo(fakeNetworkInfo)
                .hasAttributes(expectedErrorAttributes)
                .hasErrorData {
                    hasMessage(message)
                    hasOrigin(origin)
                    hasThrowable(throwable)
                }
                .hasContext {
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                }

            assertThat(lastValue)
                .hasTimestamp(fakeTimeStamp)
                .hasUserInfo(fakeUserInfo)
                .hasNetworkInfo(null)
                .hasAttributes(expectedViewAttributes)
                .hasViewData {
                    hasMeasures {
                        hasErrorCount(1)
                        hasResourceCount(0)
                        hasUserActionCount(0)
                    }
                }
                .hasContext {
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    // endregion
}
