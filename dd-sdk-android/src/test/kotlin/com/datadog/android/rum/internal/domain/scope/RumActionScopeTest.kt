/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import com.datadog.android.core.internal.data.Writer
import com.datadog.android.core.internal.time.SystemTimeProvider
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.log.internal.user.NoOpMutableUserInfoProvider
import com.datadog.android.log.internal.user.UserInfo
import com.datadog.android.log.internal.user.UserInfoProvider
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumResourceType
import com.datadog.android.rum.assertj.RumEventAssert
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.event.RumEvent
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.forge.exhaustiveAttributes
import com.datadog.tools.unit.setStaticValue
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.isA
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.same
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.RegexForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.concurrent.TimeUnit
import kotlin.system.measureNanoTime
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
internal class RumActionScopeTest {

    lateinit var testedScope: RumActionScope

    @Mock
    lateinit var mockParentScope: RumScope

    @Mock
    lateinit var mockEvent: RumRawEvent

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @Mock
    lateinit var mockUserInfoProvider: UserInfoProvider

    @Mock
    lateinit var mockWriter: Writer<RumEvent>

    @StringForgery(StringForgeryType.ALPHABETICAL)
    lateinit var fakeName: String

    lateinit var fakeKey: ByteArray
    lateinit var fakeAttributes: Map<String, Any?>

    @Forgery
    lateinit var fakeParentContext: RumContext

    @Forgery
    lateinit var fakeUserInfo: UserInfo

    @LongForgery
    var fakeTimeStamp: Long = 0L

    @BeforeEach
    fun `set up`(forge: Forge) {
        RumFeature::class.java.setStaticValue("timeProvider", mockTimeProvider)
        RumFeature::class.java.setStaticValue("userInfoProvider", mockUserInfoProvider)

        fakeAttributes = forge.exhaustiveAttributes()
        fakeKey = forge.anAsciiString().toByteArray()

        whenever(mockTimeProvider.getDeviceTimestamp()) doReturn fakeTimeStamp
        whenever(mockUserInfoProvider.getUserInfo()) doReturn fakeUserInfo
        whenever(mockParentScope.getRumContext()) doReturn fakeParentContext

        testedScope = RumActionScope(mockParentScope, false, fakeName, fakeAttributes)
    }

    @AfterEach
    fun `tear down`() {
        RumFeature::class.java.setStaticValue("timeProvider", SystemTimeProvider())
        RumFeature::class.java.setStaticValue("userInfoProvider", NoOpMutableUserInfoProvider())
        GlobalRum.globalAttributes.clear()
    }

    @Test
    fun `start+stop resource extends action threshold`(
        @StringForgery(StringForgeryType.ALPHABETICAL) key: String,
        @StringForgery(StringForgeryType.ALPHABETICAL) method: String,
        @RegexForgery("http(s?)://[a-z]+.com/[a-z]+") url: String,
        @Forgery type: RumResourceType
    ) {
        mockEvent = RumRawEvent.StartResource(key, url, method, emptyMap())
        val result = testedScope.handleEvent(mockEvent, mockWriter)
        Thread.sleep(500)
        mockEvent = RumRawEvent.StopResource(key, type, emptyMap())
        val result2 = testedScope.handleEvent(mockEvent, mockWriter)
        Thread.sleep(500)
        mockEvent = mock()
        val result3 = testedScope.handleEvent(mockEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            RumEventAssert.assertThat(lastValue)
                .hasTimestamp(fakeTimeStamp)
                .hasUserInfo(fakeUserInfo)
                .hasNetworkInfo(null)
                .hasAttributes(fakeAttributes)
                .hasUserActionData {
                    hasName(fakeName)
                    hasDurationGreaterThan(500)
                }
                .hasContext {
                    hasViewId(fakeParentContext.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                }
        }
        verify(mockParentScope).handleEvent(
            isA<RumRawEvent.SentAction>(),
            same(mockWriter)
        )
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isSameAs(testedScope)
        assertThat(result3).isNull()
    }

    @Test
    fun `send action if started Resource key is missing`(
        @StringForgery(StringForgeryType.ALPHABETICAL) method: String,
        @RegexForgery("http(s?)://[a-z]+.com/[a-z]+") url: String
    ) {
        var key: Any? = Object()
        mockEvent = RumRawEvent.StartResource(key.toString(), url, method, emptyMap())
        val result = testedScope.handleEvent(mockEvent, mockWriter)
        Thread.sleep(500)

        mockEvent = mock()
        key = null
        System.gc()
        val result2 = testedScope.handleEvent(mockEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            RumEventAssert.assertThat(lastValue)
                .hasTimestamp(fakeTimeStamp)
                .hasUserInfo(fakeUserInfo)
                .hasNetworkInfo(null)
                .hasAttributes(fakeAttributes)
                .hasUserActionData {
                    hasName(fakeName)
                    hasDurationLowerThan(TimeUnit.MILLISECONDS.toNanos(500))
                }
                .hasContext {
                    hasViewId(fakeParentContext.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                }
        }
        verify(mockParentScope).handleEvent(
            isA<RumRawEvent.SentAction>(),
            same(mockWriter)
        )
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isNull()
    }

    @Test
    fun `send action on stop view with some viewTreeChanges`(
        @IntForgery(1) count: Int
    ) {
        testedScope.viewTreeChangeCount = count
        mockEvent = RumRawEvent.StopView(Object(), emptyMap())
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            RumEventAssert.assertThat(lastValue)
                .hasTimestamp(fakeTimeStamp)
                .hasUserInfo(fakeUserInfo)
                .hasNetworkInfo(null)
                .hasAttributes(fakeAttributes)
                .hasUserActionData {
                    hasName(fakeName)
                    hasDurationGreaterThan(1)
                }
                .hasContext {
                    hasViewId(fakeParentContext.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                }
        }
        verify(mockParentScope).handleEvent(
            isA<RumRawEvent.SentAction>(),
            same(mockWriter)
        )
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `send action on stop view with some resources`(
        @IntForgery(1) count: Int
    ) {
        testedScope.resourcesCount = count
        mockEvent = RumRawEvent.StopView(Object(), emptyMap())
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            RumEventAssert.assertThat(lastValue)
                .hasTimestamp(fakeTimeStamp)
                .hasUserInfo(fakeUserInfo)
                .hasNetworkInfo(null)
                .hasAttributes(fakeAttributes)
                .hasUserActionData {
                    hasName(fakeName)
                    hasDurationGreaterThan(1)
                }
                .hasContext {
                    hasViewId(fakeParentContext.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                }
        }
        verify(mockParentScope).handleEvent(
            isA<RumRawEvent.SentAction>(),
            same(mockWriter)
        )
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `don't send action on stopView with no intermediate event`() {
        testedScope.resourcesCount = 0
        testedScope.viewTreeChangeCount = 0
        mockEvent = RumRawEvent.StopView(Object(), emptyMap())
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        verifyZeroInteractions(mockWriter, mockParentScope)
        assertThat(result).isNull()
    }

    @Test
    fun `send action on random event with some viewTreeChanges`(
        @IntForgery(1) count: Int
    ) {
        testedScope.viewTreeChangeCount = count
        Thread.sleep(RumActionScope.ACTION_INACTIVITY_MS)
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            RumEventAssert.assertThat(lastValue)
                .hasTimestamp(fakeTimeStamp)
                .hasUserInfo(fakeUserInfo)
                .hasNetworkInfo(null)
                .hasAttributes(fakeAttributes)
                .hasUserActionData {
                    hasName(fakeName)
                    hasDurationGreaterThan(1)
                }
                .hasContext {
                    hasViewId(fakeParentContext.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                }
        }
        verify(mockParentScope).handleEvent(
            isA<RumRawEvent.SentAction>(),
            same(mockWriter)
        )
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `send action on random event with some viewTreeChanges and global attributes`(
        @IntForgery(1) count: Int,
        forge: Forge
    ) {
        val attributes = forge.aMap { anHexadecimalString() to anAsciiString() }
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)
        testedScope.viewTreeChangeCount = count
        Thread.sleep(RumActionScope.ACTION_INACTIVITY_MS)
        GlobalRum.globalAttributes.putAll(attributes)

        val result = testedScope.handleEvent(mockEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            RumEventAssert.assertThat(lastValue)
                .hasTimestamp(fakeTimeStamp)
                .hasUserInfo(fakeUserInfo)
                .hasNetworkInfo(null)
                .hasAttributes(expectedAttributes)
                .hasUserActionData {
                    hasName(fakeName)
                    hasDurationGreaterThan(1)
                }
                .hasContext {
                    hasViewId(fakeParentContext.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                }
        }
        verify(mockParentScope).handleEvent(
            isA<RumRawEvent.SentAction>(),
            same(mockWriter)
        )
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `send action on random event with some resources`(
        @IntForgery(1) count: Int
    ) {
        testedScope.resourcesCount = count
        Thread.sleep(RumActionScope.ACTION_INACTIVITY_MS)
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            RumEventAssert.assertThat(lastValue)
                .hasTimestamp(fakeTimeStamp)
                .hasUserInfo(fakeUserInfo)
                .hasNetworkInfo(null)
                .hasAttributes(fakeAttributes)
                .hasUserActionData {
                    hasName(fakeName)
                    hasDurationGreaterThan(1)
                }
                .hasContext {
                    hasViewId(fakeParentContext.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                }
        }
        verify(mockParentScope).handleEvent(
            isA<RumRawEvent.SentAction>(),
            same(mockWriter)
        )
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `send action only once`(
        @IntForgery(1) count: Int
    ) {
        testedScope.resourcesCount = count
        Thread.sleep(RumActionScope.ACTION_INACTIVITY_MS)
        val result = testedScope.handleEvent(mockEvent, mockWriter)
        val result2 = testedScope.handleEvent(mockEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            RumEventAssert.assertThat(lastValue)
                .hasTimestamp(fakeTimeStamp)
                .hasUserInfo(fakeUserInfo)
                .hasNetworkInfo(null)
                .hasAttributes(fakeAttributes)
                .hasUserActionData {
                    hasName(fakeName)
                    hasDurationGreaterThan(1)
                }
                .hasContext {
                    hasViewId(fakeParentContext.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                }
        }
        verify(mockParentScope).handleEvent(
            isA<RumRawEvent.SentAction>(),
            same(mockWriter)
        )
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
        assertThat(result2).isNull()
    }

    @Test
    fun `don't send action on random event if no event occured`() {
        testedScope = RumActionScope(mockParentScope, false, fakeName, fakeAttributes)
        Thread.sleep(RumActionScope.ACTION_INACTIVITY_MS)
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        verifyZeroInteractions(mockWriter, mockParentScope)
        assertThat(result).isNull()
    }

    @Test
    fun `don't send action if a resource is ongoing`(
        @StringForgery(StringForgeryType.ALPHABETICAL) key: String,
        @StringForgery(StringForgeryType.ALPHABETICAL) method: String,
        @RegexForgery("http(s?)://[a-z]+.com/[a-z]+") url: String
    ) {
        mockEvent = RumRawEvent.StartResource(key, url, method, emptyMap())
        val result = testedScope.handleEvent(mockEvent, mockWriter)
        Thread.sleep(1000)
        mockEvent = mock()
        val result2 = testedScope.handleEvent(mockEvent, mockWriter)

        verifyZeroInteractions(mockWriter, mockParentScope)
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isSameAs(testedScope)
    }

    @Test
    fun `don't send action if a waitForStop is true`() {
        testedScope = RumActionScope(mockParentScope, true, fakeName, fakeAttributes)
        Thread.sleep(1000)
        mockEvent = mock()
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        verifyZeroInteractions(mockWriter, mockParentScope)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `send action if a resource is ongoing for too long`(
        @StringForgery(StringForgeryType.ALPHABETICAL) key: String,
        @StringForgery(StringForgeryType.ALPHABETICAL) method: String,
        @RegexForgery("http(s?)://[a-z]+.com/[a-z]+") url: String
    ) {
        mockEvent = RumRawEvent.StartResource(key, url, method, emptyMap())
        val result = testedScope.handleEvent(mockEvent, mockWriter)
        Thread.sleep(RumActionScope.ACTION_MAX_DURATION_MS)
        mockEvent = mock()
        val result2 = testedScope.handleEvent(mockEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            RumEventAssert.assertThat(lastValue)
                .hasTimestamp(fakeTimeStamp)
                .hasUserInfo(fakeUserInfo)
                .hasNetworkInfo(null)
                .hasAttributes(fakeAttributes)
                .hasUserActionData {
                    hasName(fakeName)
                    hasDurationGreaterThan(RumActionScope.ACTION_MAX_DURATION_NS)
                }
                .hasContext {
                    hasViewId(fakeParentContext.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                }
        }
        verify(mockParentScope).handleEvent(
            isA<RumRawEvent.SentAction>(),
            same(mockWriter)
        )
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isNull()
    }

    @Test
    fun `send action if a waiting for stop for too long`() {
        testedScope = RumActionScope(mockParentScope, true, fakeName, fakeAttributes)
        testedScope.viewTreeChangeCount = 1
        Thread.sleep(RumActionScope.ACTION_MAX_DURATION_MS)
        mockEvent = mock()
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            RumEventAssert.assertThat(lastValue)
                .hasTimestamp(fakeTimeStamp)
                .hasUserInfo(fakeUserInfo)
                .hasNetworkInfo(null)
                .hasAttributes(fakeAttributes)
                .hasUserActionData {
                    hasName(fakeName)
                    hasDurationGreaterThan(RumActionScope.ACTION_MAX_DURATION_NS)
                }
                .hasContext {
                    hasViewId(fakeParentContext.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                }
        }
        verify(mockParentScope).handleEvent(
            isA<RumRawEvent.SentAction>(),
            same(mockWriter)
        )
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `send action on stopAction when waitForStop`(
        forge: Forge
    ) {
        val attributes = forge.exhaustiveAttributes()
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)
        testedScope = RumActionScope(mockParentScope, true, fakeName, fakeAttributes)
        testedScope.viewTreeChangeCount = 1
        Thread.sleep(500)
        mockEvent = RumRawEvent.StopAction(fakeName, attributes)
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            RumEventAssert.assertThat(lastValue)
                .hasTimestamp(fakeTimeStamp)
                .hasUserInfo(fakeUserInfo)
                .hasNetworkInfo(null)
                .hasAttributes(expectedAttributes)
                .hasUserActionData {
                    hasName(fakeName)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(500))
                }
                .hasContext {
                    hasViewId(fakeParentContext.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                }
        }
        verify(mockParentScope).handleEvent(
            isA<RumRawEvent.SentAction>(),
            same(mockWriter)
        )
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `send action with updated name on stopAction when waitForStop`(
        @StringForgery(StringForgeryType.ALPHABETICAL) name: String,
        forge: Forge
    ) {
        val attributes = forge.exhaustiveAttributes()
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)
        testedScope = RumActionScope(mockParentScope, true, fakeName, fakeAttributes)
        testedScope.viewTreeChangeCount = 1
        Thread.sleep(500)
        mockEvent = RumRawEvent.StopAction(name, attributes)
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            RumEventAssert.assertThat(lastValue)
                .hasTimestamp(fakeTimeStamp)
                .hasUserInfo(fakeUserInfo)
                .hasNetworkInfo(null)
                .hasAttributes(expectedAttributes)
                .hasUserActionData {
                    hasName(name)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(500))
                }
                .hasContext {
                    hasViewId(fakeParentContext.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                }
        }
        verify(mockParentScope).handleEvent(
            isA<RumRawEvent.SentAction>(),
            same(mockWriter)
        )
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `send action on event after threshold and notify parent`() {
        testedScope.viewTreeChangeCount = 1
        Thread.sleep(RumActionScope.ACTION_INACTIVITY_MS)
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            RumEventAssert.assertThat(lastValue)
                .hasTimestamp(fakeTimeStamp)
                .hasUserInfo(fakeUserInfo)
                .hasNetworkInfo(null)
                .hasAttributes(fakeAttributes)
                .hasUserActionData {
                    hasName(fakeName)
                    hasDurationGreaterThan(1)
                }
                .hasContext {
                    hasViewId(fakeParentContext.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                }
        }
        verify(mockParentScope).handleEvent(
            isA<RumRawEvent.SentAction>(),
            same(mockWriter)
        )
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `ViewTree event extends the action scope`() {

        val duration = measureNanoTime {
            repeat(10) {
                Thread.sleep(RumActionScope.ACTION_INACTIVITY_MS / 3)
                testedScope.handleEvent(RumRawEvent.ViewTreeChanged(), mockWriter)
            }
        }
        testedScope.handleEvent(RumRawEvent.ViewTreeChanged(), mockWriter)
        Thread.sleep(RumActionScope.ACTION_INACTIVITY_MS)
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            RumEventAssert.assertThat(lastValue)
                .hasTimestamp(fakeTimeStamp)
                .hasUserInfo(fakeUserInfo)
                .hasNetworkInfo(null)
                .hasAttributes(fakeAttributes)
                .hasUserActionData {
                    hasName(fakeName)
                    hasDurationGreaterThan(duration)
                }
                .hasContext {
                    hasViewId(fakeParentContext.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                }
        }
        verify(mockParentScope).handleEvent(
            isA<RumRawEvent.SentAction>(),
            same(mockWriter)
        )
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }
}
