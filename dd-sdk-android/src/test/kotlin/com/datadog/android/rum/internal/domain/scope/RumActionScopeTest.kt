/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import com.datadog.android.core.internal.data.Writer
import com.datadog.android.core.internal.domain.Time
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.log.internal.user.NoOpMutableUserInfoProvider
import com.datadog.android.log.internal.user.UserInfo
import com.datadog.android.log.internal.user.UserInfoProvider
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.assertj.RumEventAssert.Companion.assertThat
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
    lateinit var mockTimeProvider: TimeProvider

    @Mock
    lateinit var mockUserInfoProvider: UserInfoProvider

    @Mock
    lateinit var mockWriter: Writer<RumEvent>

    @Forgery
    lateinit var fakeType: RumActionType

    @StringForgery(StringForgeryType.ALPHABETICAL)
    lateinit var fakeName: String

    lateinit var fakeKey: ByteArray
    lateinit var fakeAttributes: Map<String, Any?>

    @Forgery
    lateinit var fakeParentContext: RumContext

    @Forgery
    lateinit var fakeUserInfo: UserInfo

    lateinit var fakeEventTime: Time

    lateinit var fakeEvent: RumRawEvent

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeEventTime = Time()

        RumFeature::class.java.setStaticValue("userInfoProvider", mockUserInfoProvider)

        fakeAttributes = forge.exhaustiveAttributes()
        fakeKey = forge.anAsciiString().toByteArray()

        whenever(mockUserInfoProvider.getUserInfo()) doReturn fakeUserInfo
        whenever(mockParentScope.getRumContext()) doReturn fakeParentContext

        testedScope = RumActionScope(
            mockParentScope,
            false,
            fakeEventTime,
            fakeType,
            fakeName,
            fakeAttributes
        )
    }

    @AfterEach
    fun `tear down`() {
        RumFeature::class.java.setStaticValue("userInfoProvider", NoOpMutableUserInfoProvider())
        GlobalRum.globalAttributes.clear()
    }

    @Test
    fun `start+stop resource extends action threshold`(
        @StringForgery(StringForgeryType.ALPHABETICAL) key: String,
        @StringForgery(StringForgeryType.ALPHABETICAL) method: String,
        @RegexForgery("http(s?)://[a-z]+.com/[a-z]+") url: String,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        @Forgery kind: RumResourceKind
    ) {
        fakeEvent = RumRawEvent.StartResource(key, url, method, emptyMap())
        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(500)
        fakeEvent = RumRawEvent.StopResource(key, statusCode, size, kind, emptyMap())
        val result2 = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(500)
        val result3 = testedScope.handleEvent(mockEvent(), mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasActionData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(500)
                    hasResourceCount(1)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasView(fakeParentContext.viewId, fakeParentContext.viewUrl)
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
    fun `start+stop resource (with error) extends action threshold`(
        @StringForgery(StringForgeryType.ALPHABETICAL) key: String,
        @StringForgery(StringForgeryType.ALPHABETICAL) method: String,
        @RegexForgery("http(s?)://[a-z]+.com/[a-z]+") url: String,
        @LongForgery(200, 600) statusCode: Long,
        @StringForgery(StringForgeryType.ALPHABETICAL) message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable
    ) {
        fakeEvent = RumRawEvent.StartResource(key, url, method, emptyMap())
        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(500)
        fakeEvent = RumRawEvent.StopResourceWithError(key, statusCode, message, source, throwable)
        val result2 = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(500)
        val result3 = testedScope.handleEvent(mockEvent(), mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasActionData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(500)
                    hasResourceCount(0)
                    hasErrorCount(1)
                    hasCrashCount(0)
                    hasView(fakeParentContext.viewId, fakeParentContext.viewUrl)
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
        fakeEvent = RumRawEvent.StartResource(key.toString(), url, method, emptyMap())
        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(500)

        key = null
        fakeEvent = mockEvent()
        System.gc()
        val result2 = testedScope.handleEvent(mockEvent(), mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasActionData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationLowerThan(TimeUnit.MILLISECONDS.toNanos(500))
                    hasResourceCount(1)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasView(fakeParentContext.viewId, fakeParentContext.viewUrl)
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
    fun `send action if an error occured`(
        @StringForgery(StringForgeryType.ALPHABETICAL) message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable
    ) {
        fakeEvent = RumRawEvent.AddError(message, source, throwable, false, emptyMap())
        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(500)

        val result2 = testedScope.handleEvent(mockEvent(), mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasActionData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationLowerThan(TimeUnit.MILLISECONDS.toNanos(500))
                    hasResourceCount(0)
                    hasErrorCount(1)
                    hasCrashCount(0)
                    hasView(fakeParentContext.viewId, fakeParentContext.viewUrl)
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
    fun `send action if a fatal error occured`(
        @StringForgery(StringForgeryType.ALPHABETICAL) message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable
    ) {
        fakeEvent = RumRawEvent.AddError(message, source, throwable, true, emptyMap())
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasActionData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationLowerThan(TimeUnit.MILLISECONDS.toNanos(500))
                    hasResourceCount(0)
                    hasErrorCount(1)
                    hasCrashCount(1)
                    hasView(fakeParentContext.viewId, fakeParentContext.viewUrl)
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
    fun `send action if a non fatal and fatal error occured`(
        @StringForgery(StringForgeryType.ALPHABETICAL) message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable
    ) {
        fakeEvent = RumRawEvent.AddError(message, source, throwable, false, emptyMap())
        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        fakeEvent = RumRawEvent.AddError(message, source, throwable, true, emptyMap())
        val result2 = testedScope.handleEvent(fakeEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasActionData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationLowerThan(TimeUnit.MILLISECONDS.toNanos(500))
                    hasResourceCount(0)
                    hasErrorCount(2)
                    hasCrashCount(1)
                    hasView(fakeParentContext.viewId, fakeParentContext.viewUrl)
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
        fakeEvent = RumRawEvent.StopView(Object(), emptyMap())
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasActionData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasView(fakeParentContext.viewId, fakeParentContext.viewUrl)
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
        @LongForgery(1, 1024) count: Long
    ) {
        testedScope.resourceCount = count
        fakeEvent = RumRawEvent.StopView(Object(), emptyMap())
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasActionData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(count)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasView(fakeParentContext.viewId, fakeParentContext.viewUrl)
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
    fun `send action on stop view with some errors`(
        @LongForgery(1, 1024) count: Long
    ) {
        testedScope.errorCount = count
        fakeEvent = RumRawEvent.StopView(Object(), emptyMap())
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasActionData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(0)
                    hasErrorCount(count)
                    hasCrashCount(0)
                    hasView(fakeParentContext.viewId, fakeParentContext.viewUrl)
                    hasUserInfo(fakeUserInfo)
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
    fun `send action on stop view with some fatal errors`(
        @LongForgery(1, 1024) nonFatalcount: Long,
        @LongForgery(1, 1024) fatalcount: Long
    ) {
        testedScope.errorCount = nonFatalcount + fatalcount
        testedScope.crashCount = fatalcount
        fakeEvent = RumRawEvent.StopView(Object(), emptyMap())
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasActionData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(0)
                    hasErrorCount(nonFatalcount + fatalcount)
                    hasCrashCount(fatalcount)
                    hasView(fakeParentContext.viewId, fakeParentContext.viewUrl)
                    hasUserInfo(fakeUserInfo)
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
        testedScope.resourceCount = 0
        testedScope.viewTreeChangeCount = 0
        fakeEvent = RumRawEvent.StopView(Object(), emptyMap())
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        verifyZeroInteractions(mockWriter, mockParentScope)
        assertThat(result).isNull()
    }

    @Test
    fun `send action on random event with some viewTreeChanges`(
        @IntForgery(1) count: Int
    ) {
        testedScope.viewTreeChangeCount = count
        Thread.sleep(RumActionScope.ACTION_INACTIVITY_MS)
        val result = testedScope.handleEvent(mockEvent(), mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasActionData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasView(fakeParentContext.viewId, fakeParentContext.viewUrl)
                    hasUserInfo(fakeUserInfo)
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

        val result = testedScope.handleEvent(mockEvent(), mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(expectedAttributes)
                .hasActionData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasView(fakeParentContext.viewId, fakeParentContext.viewUrl)
                    hasUserInfo(fakeUserInfo)
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
        @LongForgery(1, 1024) count: Long
    ) {
        testedScope.resourceCount = count
        Thread.sleep(RumActionScope.ACTION_INACTIVITY_MS)
        val result = testedScope.handleEvent(mockEvent(), mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasActionData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(count)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasView(fakeParentContext.viewId, fakeParentContext.viewUrl)
                    hasUserInfo(fakeUserInfo)
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
        @LongForgery(1, 1024) count: Long
    ) {
        testedScope.resourceCount = count
        Thread.sleep(RumActionScope.ACTION_INACTIVITY_MS)
        val result = testedScope.handleEvent(mockEvent(), mockWriter)
        val result2 = testedScope.handleEvent(mockEvent(), mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasActionData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(count)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasView(fakeParentContext.viewId, fakeParentContext.viewUrl)
                    hasUserInfo(fakeUserInfo)
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
        testedScope = RumActionScope(
            mockParentScope,
            false,
            fakeEventTime,
            fakeType,
            fakeName,
            fakeAttributes
        )
        Thread.sleep(RumActionScope.ACTION_INACTIVITY_MS)
        val result = testedScope.handleEvent(mockEvent(), mockWriter)

        verifyZeroInteractions(mockWriter, mockParentScope)
        assertThat(result).isNull()
    }

    @Test
    fun `don't send action if a resource is ongoing`(
        @StringForgery(StringForgeryType.ALPHABETICAL) key: String,
        @StringForgery(StringForgeryType.ALPHABETICAL) method: String,
        @RegexForgery("http(s?)://[a-z]+.com/[a-z]+") url: String
    ) {
        fakeEvent = RumRawEvent.StartResource(key, url, method, emptyMap())
        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(1000)
        val result2 = testedScope.handleEvent(mockEvent(), mockWriter)

        verifyZeroInteractions(mockWriter, mockParentScope)
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isSameAs(testedScope)
    }

    @Test
    fun `don't send action if a waitForStop is true`() {
        testedScope = RumActionScope(
            mockParentScope,
            true,
            fakeEventTime,
            fakeType,
            fakeName,
            fakeAttributes
        )
        Thread.sleep(1000)
        val result = testedScope.handleEvent(mockEvent(), mockWriter)

        verifyZeroInteractions(mockWriter, mockParentScope)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `send action if a resource is ongoing for too long`(
        @StringForgery(StringForgeryType.ALPHABETICAL) key: String,
        @StringForgery(StringForgeryType.ALPHABETICAL) method: String,
        @RegexForgery("http(s?)://[a-z]+.com/[a-z]+") url: String
    ) {
        fakeEvent = RumRawEvent.StartResource(key, url, method, emptyMap())
        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(RumActionScope.ACTION_MAX_DURATION_MS)
        val result2 = testedScope.handleEvent(mockEvent(), mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasActionData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(RumActionScope.ACTION_MAX_DURATION_NS)
                    hasResourceCount(1)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasView(fakeParentContext.viewId, fakeParentContext.viewUrl)
                    hasUserInfo(fakeUserInfo)
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
        testedScope = RumActionScope(
            mockParentScope,
            true,
            fakeEventTime,
            fakeType,
            fakeName,
            fakeAttributes
        )
        testedScope.viewTreeChangeCount = 1
        Thread.sleep(RumActionScope.ACTION_MAX_DURATION_MS)
        val result = testedScope.handleEvent(mockEvent(), mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasActionData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(RumActionScope.ACTION_MAX_DURATION_NS)
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasView(fakeParentContext.viewId, fakeParentContext.viewUrl)
                    hasUserInfo(fakeUserInfo)
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
        testedScope = RumActionScope(
            mockParentScope,
            true,
            fakeEventTime,
            fakeType,
            fakeName,
            fakeAttributes
        )
        testedScope.viewTreeChangeCount = 1
        Thread.sleep(500)
        fakeEvent = RumRawEvent.StopAction(fakeType, fakeName, attributes)
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(expectedAttributes)
                .hasActionData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(500))
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasView(fakeParentContext.viewId, fakeParentContext.viewUrl)
                    hasUserInfo(fakeUserInfo)
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
    fun `send action with updated type on stopAction when waitForStop`(
        @Forgery type: RumActionType,
        @StringForgery(StringForgeryType.ALPHABETICAL) name: String,
        forge: Forge
    ) {
        val attributes = forge.exhaustiveAttributes()
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)
        testedScope = RumActionScope(
            mockParentScope,
            true,
            fakeEventTime,
            fakeType,
            fakeName,
            fakeAttributes
        )
        testedScope.viewTreeChangeCount = 1
        Thread.sleep(500)
        fakeEvent = RumRawEvent.StopAction(type, name, attributes)
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(expectedAttributes)
                .hasActionData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasType(type)
                    hasTargetName(name)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(500))
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasView(fakeParentContext.viewId, fakeParentContext.viewUrl)
                    hasUserInfo(fakeUserInfo)
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
        @Forgery type: RumActionType,
        @StringForgery(StringForgeryType.ALPHABETICAL) name: String,
        forge: Forge
    ) {
        val attributes = forge.exhaustiveAttributes()
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)
        testedScope = RumActionScope(
            mockParentScope,
            true,
            fakeEventTime,
            type,
            fakeName,
            fakeAttributes
        )
        testedScope.viewTreeChangeCount = 1
        Thread.sleep(500)
        fakeEvent = RumRawEvent.StopAction(type, name, attributes)
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(expectedAttributes)
                .hasActionData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasTargetName(name)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(500))
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasView(fakeParentContext.viewId, fakeParentContext.viewUrl)
                    hasUserInfo(fakeUserInfo)
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
        val result = testedScope.handleEvent(mockEvent(), mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasActionData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasView(fakeParentContext.viewId, fakeParentContext.viewUrl)
                    hasUserInfo(fakeUserInfo)
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
        val result = testedScope.handleEvent(mockEvent(), mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasActionData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(duration)
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasView(fakeParentContext.viewId, fakeParentContext.viewUrl)
                    hasUserInfo(fakeUserInfo)
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

    private fun mockEvent(): RumRawEvent {
        val event: RumRawEvent = mock()
        whenever(event.eventTime) doReturn Time()
        return event
    }
}
