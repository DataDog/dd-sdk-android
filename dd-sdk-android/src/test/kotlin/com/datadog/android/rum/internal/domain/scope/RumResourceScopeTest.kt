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
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.assertj.RumEventAssert.Companion.assertThat
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.event.ResourceTiming
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
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.RegexForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.concurrent.TimeUnit
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
internal class RumResourceScopeTest {

    lateinit var testedScope: RumResourceScope

    @Mock
    lateinit var mockParentScope: RumScope

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

    @RegexForgery("http(s?)://[a-z]+.com/[a-z]+")
    lateinit var fakeUrl: String
    lateinit var fakeKey: String
    lateinit var fakeMethod: String
    lateinit var fakeAttributes: Map<String, Any?>

    @Forgery
    lateinit var fakeParentContext: RumContext

    @Forgery
    lateinit var fakeUserInfo: UserInfo

    @Forgery
    lateinit var fakeNetworkInfo: NetworkInfo

    lateinit var fakeEventTime: Time

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeEventTime = Time()

        RumFeature::class.java.setStaticValue("userInfoProvider", mockUserInfoProvider)
        RumFeature::class.java.setStaticValue("networkInfoProvider", mockNetworkInfoProvider)

        fakeAttributes = forge.exhaustiveAttributes()
        fakeKey = forge.anAsciiString()
        fakeMethod = forge.anElementFrom("PUT", "POST", "GET", "DELETE")
        mockEvent = mockEvent()

        whenever(mockUserInfoProvider.getUserInfo()) doReturn fakeUserInfo
        whenever(mockNetworkInfoProvider.getLatestNetworkInfo()) doReturn fakeNetworkInfo
        whenever(mockParentScope.getRumContext()) doReturn fakeParentContext

        testedScope = RumResourceScope(
            mockParentScope,
            fakeUrl,
            fakeMethod,
            fakeKey,
            fakeEventTime,
            fakeAttributes
        )
    }

    @AfterEach
    fun `tear down`() {
        RumFeature::class.java.setStaticValue("userInfoProvider", NoOpMutableUserInfoProvider())
        GlobalRum.globalAttributes.clear()
    }

    @Test
    fun `send Resource on StopResource and notify parent`(
        @Forgery kind: RumResourceKind,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        forge: Forge
    ) {
        val attributes = forge.exhaustiveAttributes()
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        Thread.sleep(500)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(expectedAttributes)
                .hasResourceData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasUrl(fakeUrl)
                    hasMethod(fakeMethod)
                    hasKind(kind)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(500))
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(fakeParentContext.viewId, fakeParentContext.viewUrl)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                }
        }
        verify(mockParentScope).handleEvent(
            isA<RumRawEvent.SentResource>(),
            same(mockWriter)
        )
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `send Resource on StopResource and notify parent with global attributes`(
        @Forgery kind: RumResourceKind,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        forge: Forge
    ) {
        val attributes = forge.aMap { anHexadecimalString() to anAsciiString() }
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)
        GlobalRum.globalAttributes.putAll(attributes)

        Thread.sleep(500)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, emptyMap())
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(expectedAttributes)
                .hasResourceData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasUrl(fakeUrl)
                    hasMethod(fakeMethod)
                    hasKind(kind)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(500))
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(fakeParentContext.viewId, fakeParentContext.viewUrl)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                }
        }
        verify(mockParentScope).handleEvent(
            isA<RumRawEvent.SentResource>(),
            same(mockWriter)
        )
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `send Resource on StopResource with timing and notify parent`(
        @Forgery kind: RumResourceKind,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        @Forgery timing: ResourceTiming,
        forge: Forge
    ) {
        val attributes = forge.exhaustiveAttributes()
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        mockEvent = RumRawEvent.AddResourceTiming(fakeKey, timing)
        val resultTiming = testedScope.handleEvent(mockEvent, mockWriter)
        Thread.sleep(500)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(expectedAttributes)
                .hasResourceData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasUrl(fakeUrl)
                    hasMethod(fakeMethod)
                    hasKind(kind)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(500))
                    hasTiming(timing)
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(fakeParentContext.viewId, fakeParentContext.viewUrl)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                }
        }
        verify(mockParentScope).handleEvent(
            isA<RumRawEvent.SentResource>(),
            same(mockWriter)
        )
        verifyNoMoreInteractions(mockWriter)
        assertThat(resultTiming).isEqualTo(testedScope)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `send Resource on StopResource with unrelated timing event and notify parent`(
        @Forgery kind: RumResourceKind,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        @Forgery timing: ResourceTiming,
        forge: Forge
    ) {
        val attributes = forge.exhaustiveAttributes()
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        mockEvent = RumRawEvent.AddResourceTiming("not_the_$fakeKey", timing)
        val resultTiming = testedScope.handleEvent(mockEvent, mockWriter)
        Thread.sleep(500)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(expectedAttributes)
                .hasResourceData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasUrl(fakeUrl)
                    hasMethod(fakeMethod)
                    hasKind(kind)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(500))
                    hasNoTiming()
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(fakeParentContext.viewId, fakeParentContext.viewUrl)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                }
        }
        verify(mockParentScope).handleEvent(
            isA<RumRawEvent.SentResource>(),
            same(mockWriter)
        )
        verifyNoMoreInteractions(mockWriter)
        assertThat(resultTiming).isEqualTo(testedScope)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `send Error on StopResourceWithError and notify parent`(
        @StringForgery(StringForgeryType.ALPHABETICAL) message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        forge: Forge
    ) {
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        mockEvent = RumRawEvent.StopResourceWithError(fakeKey, null, message, source, throwable)

        Thread.sleep(500)
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(expectedAttributes)
                .hasErrorData {
                    hasMessage(message)
                    hasSource(source)
                    hasStackTrace(throwable.loggableStackTrace())
                    isCrash(false)
                    hasResource(fakeUrl, fakeMethod, 0L)
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(fakeParentContext.viewId, fakeParentContext.viewUrl)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                }
        }
        verify(mockParentScope).handleEvent(
            isA<RumRawEvent.SentError>(),
            same(mockWriter)
        )
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `send Error on StopResourceWithError and notify parent with global attributes`(
        @StringForgery(StringForgeryType.ALPHABETICAL) message: String,
        @Forgery source: RumErrorSource,
        @LongForgery(200, 600) statusCode: Long,
        @Forgery throwable: Throwable,
        forge: Forge
    ) {
        val attributes = forge.aMap { anHexadecimalString() to anAsciiString() }
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)
        GlobalRum.globalAttributes.putAll(attributes)
        mockEvent =
            RumRawEvent.StopResourceWithError(fakeKey, statusCode, message, source, throwable)

        Thread.sleep(500)
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(expectedAttributes)
                .hasErrorData {
                    hasMessage(message)
                    hasSource(source)
                    hasStackTrace(throwable.loggableStackTrace())
                    isCrash(false)
                    hasResource(fakeUrl, fakeMethod, statusCode)
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(fakeParentContext.viewId, fakeParentContext.viewUrl)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                }
        }
        verify(mockParentScope).handleEvent(
            isA<RumRawEvent.SentError>(),
            same(mockWriter)
        )
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `ignores StopResource with different key`(
        @Forgery kind: RumResourceKind,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        forge: Forge
    ) {
        val attributes = forge.exhaustiveAttributes()
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)
        mockEvent = RumRawEvent.StopResource("not_the_$fakeKey", statusCode, size, kind, attributes)

        Thread.sleep(500)
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        verifyZeroInteractions(mockWriter, mockParentScope)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `ignores StopResourceWithError with different key`(
        @StringForgery(StringForgeryType.ALPHABETICAL) message: String,
        @Forgery source: RumErrorSource,
        @LongForgery(200, 600) statusCode: Long,
        @Forgery throwable: Throwable,
        forge: Forge
    ) {
        mockEvent = RumRawEvent.StopResourceWithError(
            "not_the_$fakeKey",
            statusCode,
            message,
            source,
            throwable
        )

        Thread.sleep(500)
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        verifyZeroInteractions(mockWriter, mockParentScope)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `ignores StopResource if waiting for timing`(
        @Forgery kind: RumResourceKind,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        forge: Forge
    ) {
        val attributes = forge.exhaustiveAttributes()
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        mockEvent = RumRawEvent.WaitForResourceTiming(fakeKey)
        val resultWaitForTiming = testedScope.handleEvent(mockEvent, mockWriter)
        Thread.sleep(500)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)
        val resultStop = testedScope.handleEvent(mockEvent, mockWriter)

        verifyZeroInteractions(mockWriter, mockParentScope)
        assertThat(resultWaitForTiming).isEqualTo(testedScope)
        assertThat(resultStop).isSameAs(testedScope)
    }

    @Test
    fun `send Resource on StopResource and notify parent if waiting for timing with different key`(
        @Forgery kind: RumResourceKind,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        forge: Forge
    ) {
        val attributes = forge.exhaustiveAttributes()
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        mockEvent = RumRawEvent.WaitForResourceTiming("not_the_$fakeKey")
        val resultWaitForTiming = testedScope.handleEvent(mockEvent, mockWriter)
        Thread.sleep(500)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)
        val resultStop = testedScope.handleEvent(mockEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(expectedAttributes)
                .hasResourceData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasUrl(fakeUrl)
                    hasMethod(fakeMethod)
                    hasKind(kind)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(500))
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(fakeParentContext.viewId, fakeParentContext.viewUrl)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                }
        }
        verify(mockParentScope).handleEvent(
            isA<RumRawEvent.SentResource>(),
            same(mockWriter)
        )
        verifyNoMoreInteractions(mockWriter)
        assertThat(resultWaitForTiming).isSameAs(testedScope)
        assertThat(resultStop).isEqualTo(null)
    }

    @Test
    fun `send Resource on StopResource after waiting for timing and notify parent`(
        @Forgery kind: RumResourceKind,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        @Forgery timing: ResourceTiming,
        forge: Forge
    ) {
        val attributes = forge.exhaustiveAttributes()
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        mockEvent = RumRawEvent.WaitForResourceTiming(fakeKey)
        val resultWaitForTiming = testedScope.handleEvent(mockEvent, mockWriter)
        mockEvent = RumRawEvent.AddResourceTiming(fakeKey, timing)
        val resultTiming = testedScope.handleEvent(mockEvent, mockWriter)
        Thread.sleep(500)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)
        val resultStop = testedScope.handleEvent(mockEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(expectedAttributes)
                .hasResourceData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasUrl(fakeUrl)
                    hasMethod(fakeMethod)
                    hasKind(kind)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(500))
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(fakeParentContext.viewId, fakeParentContext.viewUrl)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                }
        }
        verify(mockParentScope).handleEvent(
            isA<RumRawEvent.SentResource>(),
            same(mockWriter)
        )
        verifyNoMoreInteractions(mockWriter)
        assertThat(resultWaitForTiming).isEqualTo(testedScope)
        assertThat(resultTiming).isEqualTo(testedScope)
        assertThat(resultStop).isEqualTo(null)
    }

    @Test
    fun `send Resource on Timing event after waitForTiming and stopResource and notify parent`(
        @Forgery kind: RumResourceKind,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        @Forgery timing: ResourceTiming,
        forge: Forge
    ) {
        val attributes = forge.exhaustiveAttributes()
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        mockEvent = RumRawEvent.WaitForResourceTiming(fakeKey)
        val resultWaitForTiming = testedScope.handleEvent(mockEvent, mockWriter)
        mockEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, attributes)
        val resultStop = testedScope.handleEvent(mockEvent, mockWriter)
        Thread.sleep(500)
        mockEvent = RumRawEvent.AddResourceTiming(fakeKey, timing)
        val resultTiming = testedScope.handleEvent(mockEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(expectedAttributes)
                .hasResourceData {
                    hasTimestamp(fakeEventTime.timestamp)
                    hasUrl(fakeUrl)
                    hasMethod(fakeMethod)
                    hasKind(kind)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(500))
                    hasTiming(timing)
                    hasUserInfo(fakeUserInfo)
                    hasConnectivityInfo(fakeNetworkInfo)
                    hasView(fakeParentContext.viewId, fakeParentContext.viewUrl)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeParentContext.actionId)
                }
        }
        verify(mockParentScope).handleEvent(
            isA<RumRawEvent.SentResource>(),
            same(mockWriter)
        )
        verifyNoMoreInteractions(mockWriter)
        assertThat(resultWaitForTiming).isEqualTo(testedScope)
        assertThat(resultStop).isEqualTo(testedScope)
        assertThat(resultTiming).isEqualTo(null)
    }

    private fun mockEvent(): RumRawEvent {
        val event: RumRawEvent = mock()
        whenever(event.eventTime) doReturn Time()
        return event
    }
}
