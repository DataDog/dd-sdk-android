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
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumResourceKind
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
    lateinit var mockWriter: Writer<RumEvent>

    @RegexForgery("http(s?)://[a-z]+.com/[a-z]+")
    lateinit var fakeUrl: String
    lateinit var fakeKey: Any
    lateinit var fakeMethod: String
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
        fakeMethod = forge.anElementFrom("PUT", "POST", "GET", "DELETE")

        whenever(mockTimeProvider.getDeviceTimestamp()) doReturn fakeTimeStamp
        whenever(mockUserInfoProvider.getUserInfo()) doReturn fakeUserInfo
        whenever(mockParentScope.getRumContext()) doReturn fakeParentContext

        testedScope = RumResourceScope(
            mockParentScope,
            fakeUrl,
            fakeMethod,
            fakeKey,
            fakeAttributes
        )
    }

    @AfterEach
    fun `tear down`() {
        RumFeature::class.java.setStaticValue("timeProvider", SystemTimeProvider())
        RumFeature::class.java.setStaticValue("userInfoProvider", NoOpMutableUserInfoProvider())
    }

    @Test
    fun `send Resource on StopResource and notify parent`(
        @Forgery kind: RumResourceKind,
        forge: Forge
    ) {
        val attributes = forge.exhaustiveAttributes()
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)
        mockEvent = RumRawEvent.StopResource(fakeKey, kind, attributes)

        Thread.sleep(500)
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            RumEventAssert.assertThat(lastValue)
                .hasTimestamp(fakeTimeStamp)
                .hasUserInfo(fakeUserInfo)
                .hasAttributes(expectedAttributes)
                .hasResourceData {
                    hasUrl(fakeUrl)
                    hasMethod(fakeMethod)
                    hasKind(kind)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(500))
                }
                .hasContext {
                    hasViewId(fakeParentContext.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
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
    fun `send Error on StopResourceWithError and notify parent`(
        @StringForgery(StringForgeryType.ALPHABETICAL) message: String,
        @StringForgery(StringForgeryType.ALPHABETICAL) origin: String,
        @Forgery throwable: Throwable,
        forge: Forge
    ) {
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.put(RumAttributes.HTTP_URL, fakeUrl)
        mockEvent = RumRawEvent.StopResourceWithError(fakeKey, message, origin, throwable)

        Thread.sleep(500)
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            RumEventAssert.assertThat(lastValue)
                .hasTimestamp(fakeTimeStamp)
                .hasUserInfo(fakeUserInfo)
                .hasAttributes(expectedAttributes)
                .hasErrorData {
                    hasMessage(message)
                    hasOrigin(origin)
                    hasThrowable(throwable)
                }
                .hasContext {
                    hasViewId(fakeParentContext.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
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
        forge: Forge
    ) {
        val attributes = forge.exhaustiveAttributes()
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)
        mockEvent = RumRawEvent.StopResource(Object(), kind, attributes)

        Thread.sleep(500)
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        verifyZeroInteractions(mockWriter, mockParentScope)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `ignores StopResourceWithError with different key`(
        @StringForgery(StringForgeryType.ALPHABETICAL) message: String,
        @StringForgery(StringForgeryType.ALPHABETICAL) origin: String,
        @Forgery throwable: Throwable,
        forge: Forge
    ) {
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.put(RumAttributes.HTTP_URL, fakeUrl)
        mockEvent = RumRawEvent.StopResourceWithError(Object(), message, origin, throwable)

        Thread.sleep(500)
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        verifyZeroInteractions(mockWriter, mockParentScope)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `send Resource on any event when key missing and notify parent`() {
        fakeKey = Object()
        System.gc()

        Thread.sleep(500)
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            RumEventAssert.assertThat(lastValue)
                .hasTimestamp(fakeTimeStamp)
                .hasUserInfo(fakeUserInfo)
                .hasAttributes(fakeAttributes)
                .hasResourceData {
                    hasUrl(fakeUrl)
                    hasMethod(fakeMethod)
                    hasKind(RumResourceKind.UNKNOWN)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(500))
                }
                .hasContext {
                    hasViewId(fakeParentContext.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                }
        }
        verify(mockParentScope).handleEvent(
            isA<RumRawEvent.SentResource>(),
            same(mockWriter)
        )
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isEqualTo(null)
    }
}
