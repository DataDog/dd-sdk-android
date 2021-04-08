/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.model.UserInfo
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.assertj.RumEventAssert.Companion.assertThat
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.domain.event.RumEvent
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.forge.aFilteredMap
import com.datadog.android.utils.forge.exhaustiveAttributes
import com.datadog.android.utils.mockCoreFeature
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
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.concurrent.TimeUnit
import kotlin.system.measureNanoTime
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
internal class RumContinuousActionScopeTest {

    lateinit var testedScope: RumActionScope

    @Mock
    lateinit var mockParentScope: RumScope

    @Mock
    lateinit var mockWriter: DataWriter<RumEvent>

    @Forgery
    lateinit var fakeType: RumActionType

    @StringForgery
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

        fakeAttributes = forge.exhaustiveAttributes()
        fakeKey = forge.anAsciiString().toByteArray()

        mockCoreFeature()
        whenever(CoreFeature.userInfoProvider.getUserInfo()) doReturn fakeUserInfo
        whenever(mockParentScope.getRumContext()) doReturn fakeParentContext

        testedScope = RumActionScope(
            mockParentScope,
            true,
            fakeEventTime,
            fakeType,
            fakeName,
            fakeAttributes,
            TEST_INACTIVITY_MS,
            TEST_MAX_DURATION_MS
        )
    }

    @AfterEach
    fun `tear down`() {
        CoreFeature.stop()
        GlobalRum.globalAttributes.clear()
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(any) {viewTreeChangeCount != 0}`(
        @IntForgery(1) count: Int
    ) {
        // Given
        Thread.sleep(RumActionScope.ACTION_INACTIVITY_MS)
        testedScope.viewTreeChangeCount = count

        // When
        val result = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        verifyZeroInteractions(mockWriter, mockParentScope)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(any) {resourceCount != 0}`(
        @LongForgery(1) count: Long
    ) {
        // Given
        Thread.sleep(RumActionScope.ACTION_INACTIVITY_MS)
        testedScope.resourceCount = count

        // When
        val result = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        verifyZeroInteractions(mockWriter, mockParentScope)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(any) {errorCount != 0}`(
        @LongForgery(1) count: Long
    ) {
        // Given
        Thread.sleep(RumActionScope.ACTION_INACTIVITY_MS)
        testedScope.errorCount = count

        // When
        val result = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        verifyZeroInteractions(mockWriter, mockParentScope)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(any) {crashCount != 0}`(
        @LongForgery(1) nonFatalcount: Long,
        @LongForgery(1) fatalcount: Long
    ) {
        // Given
        Thread.sleep(RumActionScope.ACTION_INACTIVITY_MS)
        testedScope.errorCount = nonFatalcount + fatalcount
        testedScope.crashCount = fatalcount

        // When
        val result = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        verifyZeroInteractions(mockWriter, mockParentScope)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send Action after timeout 𝕎 handleEvent(any)`() {
        // Given
        testedScope.viewTreeChangeCount = 1
        Thread.sleep(TEST_MAX_DURATION_MS)

        // When
        val result = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasActionData {
                    hasId(testedScope.actionId)
                    hasTimestamp(fakeEventTime.timestamp)
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(TEST_MAX_DURATION_NS)
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasView(fakeParentContext)
                    hasUserInfo(fakeUserInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 send Action with updated data 𝕎 handleEvent(StopAction+any) {viewTreeChangeCount!=0}`(
        @Forgery type: RumActionType,
        @StringForgery name: String,
        forge: Forge
    ) {
        // Given
        assumeTrue { type != fakeType }
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)
        testedScope.viewTreeChangeCount = 1

        // When
        Thread.sleep(TEST_INACTIVITY_MS * 2)
        fakeEvent = RumRawEvent.StopAction(type, name, attributes)
        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_INACTIVITY_MS * 2)
        val result2 = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(expectedAttributes)
                .hasActionData {
                    hasId(testedScope.actionId)
                    hasTimestamp(fakeEventTime.timestamp)
                    hasTargetName(name)
                    hasType(type)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(TEST_INACTIVITY_MS * 2))
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasView(fakeParentContext)
                    hasUserInfo(fakeUserInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isNull()
    }

    @Test
    fun `𝕄 send Action with original data 𝕎 handleEvent(StopAction) {viewTreeChangeCount!=0}`(
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)
        testedScope.viewTreeChangeCount = 1

        // When
        Thread.sleep(TEST_INACTIVITY_MS * 2)
        fakeEvent = RumRawEvent.StopAction(null, null, attributes)
        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_INACTIVITY_MS * 2)
        val result2 = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(expectedAttributes)
                .hasActionData {
                    hasId(testedScope.actionId)
                    hasTimestamp(fakeEventTime.timestamp)
                    hasTargetName(fakeName)
                    hasType(fakeType)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(TEST_INACTIVITY_MS * 2))
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasView(fakeParentContext)
                    hasUserInfo(fakeUserInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isNull()
    }

    @Test
    fun `𝕄 send Action after threshold 𝕎 handleEvent(StartResource+StopAction+StopResource+any)`(
        @StringForgery key: String,
        @StringForgery method: String,
        @StringForgery(regex = "http(s?)://[a-z]+.com/[a-z]+") url: String,
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
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasActionData {
                    hasId(testedScope.actionId)
                    hasTimestamp(fakeEventTime.timestamp)
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(TEST_INACTIVITY_MS * 2))
                    hasDurationLowerThan(TimeUnit.MILLISECONDS.toNanos(TEST_INACTIVITY_MS * 4))
                    hasResourceCount(1)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
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
    fun `𝕄 send Action 𝕎 handleEvent(StartResource+StopAction+StopResourceWithError+any)`(
        @StringForgery key: String,
        @StringForgery method: String,
        @StringForgery(regex = "http(s?)://[a-z]+.com/[a-z]+") url: String,
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
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasActionData {
                    hasId(testedScope.actionId)
                    hasTimestamp(fakeEventTime.timestamp)
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(TEST_INACTIVITY_MS * 2))
                    hasDurationLowerThan(TimeUnit.MILLISECONDS.toNanos(TEST_INACTIVITY_MS * 4))
                    hasResourceCount(0)
                    hasErrorCount(1)
                    hasCrashCount(0)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
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
    fun `𝕄 send Action 𝕎 handleEvent(StartResource+StopAction+any) missing resource key`(
        @StringForgery method: String,
        @StringForgery(regex = "http(s?)://[a-z]+.com/[a-z]+") url: String
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
        key = null
        System.gc()
        val result3 = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasActionData {
                    hasId(testedScope.actionId)
                    hasTimestamp(fakeEventTime.timestamp)
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationLowerThan(TimeUnit.MILLISECONDS.toNanos(TEST_INACTIVITY_MS * 2))
                    hasResourceCount(1)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isSameAs(testedScope)
        assertThat(result3).isNull()
        assertThat(key as Any?).isNull()
    }

    @Test
    fun `𝕄 send Action after threshold 𝕎 handleEvent(AddError+StopAction+any)`(
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
        fakeEvent = RumRawEvent.StopAction(fakeType, fakeName, emptyMap())
        val result2 = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_INACTIVITY_MS * 2)
        val result3 = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasActionData {
                    hasId(testedScope.actionId)
                    hasTimestamp(fakeEventTime.timestamp)
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(TimeUnit.MILLISECONDS.toNanos(TEST_INACTIVITY_MS * 2))
                    hasDurationLowerThan(TimeUnit.MILLISECONDS.toNanos(TEST_INACTIVITY_MS * 4))
                    hasResourceCount(0)
                    hasErrorCount(1)
                    hasCrashCount(0)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isSameAs(testedScope)
        assertThat(result3).isNull()
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
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasActionData {
                    hasId(testedScope.actionId)
                    hasTimestamp(fakeEventTime.timestamp)
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationLowerThan(TimeUnit.MILLISECONDS.toNanos(100))
                    hasResourceCount(0)
                    hasErrorCount(1)
                    hasCrashCount(1)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
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
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            null,
            false,
            emptyMap()
        )
        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            null,
            true,
            emptyMap()
        )
        val result2 = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasActionData {
                    hasId(testedScope.actionId)
                    hasTimestamp(fakeEventTime.timestamp)
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationLowerThan(TimeUnit.MILLISECONDS.toNanos(100))
                    hasResourceCount(0)
                    hasErrorCount(2)
                    hasCrashCount(1)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isNull()
    }

    @Test
    fun `𝕄 send Action immediately 𝕎 handleEvent(StopView) {viewTreeChangeCount != 0}`(
        @IntForgery(1) count: Int
    ) {
        // Given
        testedScope.viewTreeChangeCount = count

        // When
        fakeEvent = RumRawEvent.StopView(Object(), emptyMap())
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasActionData {
                    hasId(testedScope.actionId)
                    hasTimestamp(fakeEventTime.timestamp)
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
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
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasActionData {
                    hasId(testedScope.actionId)
                    hasTimestamp(fakeEventTime.timestamp)
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(count)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
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
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasActionData {
                    hasId(testedScope.actionId)
                    hasTimestamp(fakeEventTime.timestamp)
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(0)
                    hasErrorCount(count)
                    hasCrashCount(0)
                    hasView(fakeParentContext)
                    hasUserInfo(fakeUserInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
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
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasActionData {
                    hasId(testedScope.actionId)
                    hasTimestamp(fakeEventTime.timestamp)
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(0)
                    hasErrorCount(nonFatalCount + fatalCount)
                    hasCrashCount(fatalCount)
                    hasView(fakeParentContext)
                    hasUserInfo(fakeUserInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 send Action after threshold 𝕎 handleEvent(StopAction+any) {viewTreeChangeCount != 0}`(
        @IntForgery(1) count: Int
    ) {
        // Given
        testedScope.viewTreeChangeCount = count

        // When
        fakeEvent = RumRawEvent.StopAction(fakeType, fakeName, emptyMap())
        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_INACTIVITY_MS)
        val result2 = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasActionData {
                    hasId(testedScope.actionId)
                    hasTimestamp(fakeEventTime.timestamp)
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasView(fakeParentContext)
                    hasUserInfo(fakeUserInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isNull()
    }

    @Test
    fun `𝕄 send Action with global attributes after threshold 𝕎 handleEvent(StopAction+any)`(
        @IntForgery(1) count: Int,
        forge: Forge
    ) {
        // Given
        val attributes = forge.aFilteredMap(excludedKeys = fakeAttributes.keys) {
            anHexadecimalString() to anAsciiString()
        }
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)
        testedScope.viewTreeChangeCount = count
        GlobalRum.globalAttributes.putAll(attributes)
        fakeEvent = RumRawEvent.StopAction(fakeType, fakeName, emptyMap())

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_INACTIVITY_MS)
        val result2 = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(expectedAttributes)
                .hasActionData {
                    hasId(testedScope.actionId)
                    hasTimestamp(fakeEventTime.timestamp)
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasView(fakeParentContext)
                    hasUserInfo(fakeUserInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isNull()
    }

    @Test
    fun `𝕄 send Action after threshold 𝕎 handleEvent(StopAction+any) {resourceCount != 0}`(
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
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasActionData {
                    hasId(testedScope.actionId)
                    hasTimestamp(fakeEventTime.timestamp)
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(count)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasView(fakeParentContext)
                    hasUserInfo(fakeUserInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isNull()
    }

    @Test
    fun `𝕄 send Action after threshold 𝕎 handleEvent(StopAction+any) {errorCount != 0}`(
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
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasActionData {
                    hasId(testedScope.actionId)
                    hasTimestamp(fakeEventTime.timestamp)
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(0)
                    hasErrorCount(count)
                    hasCrashCount(0)
                    hasView(fakeParentContext)
                    hasUserInfo(fakeUserInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isNull()
    }

    @Test
    fun `𝕄 send Action after threshold 𝕎 handleEvent(StopAction+any) {crashCount != 0}`(
        @LongForgery(1, 1024) nonFatalcount: Long,
        @LongForgery(1, 1024) fatalcount: Long
    ) {
        // Given
        testedScope.errorCount = nonFatalcount + fatalcount
        testedScope.crashCount = fatalcount
        fakeEvent = RumRawEvent.StopAction(fakeType, fakeName, emptyMap())

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_INACTIVITY_MS)
        val result2 = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasActionData {
                    hasId(testedScope.actionId)
                    hasTimestamp(fakeEventTime.timestamp)
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasErrorCount(nonFatalcount + fatalcount)
                    hasCrashCount(fatalcount)
                    hasView(fakeParentContext)
                    hasUserInfo(fakeUserInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isNull()
    }

    @Test
    fun `𝕄 send Action only once 𝕎 handleEvent(StopAction) + handleEvent(any) twice`(
        @IntForgery(1, 1024) count: Int
    ) {
        // Given
        testedScope.viewTreeChangeCount = count
        fakeEvent = RumRawEvent.StopAction(fakeType, fakeName, emptyMap())

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_INACTIVITY_MS)
        val result2 = testedScope.handleEvent(mockEvent(), mockWriter)
        val result3 = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasActionData {
                    hasId(testedScope.actionId)
                    hasTimestamp(fakeEventTime.timestamp)
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasView(fakeParentContext)
                    hasUserInfo(fakeUserInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isNull()
        assertThat(result3).isNull()
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(StopView) {no side effect}`() {
        assumeTrue(testedScope.type != RumActionType.CUSTOM)

        // Given
        testedScope.resourceCount = 0
        testedScope.viewTreeChangeCount = 0
        testedScope.errorCount = 0
        testedScope.crashCount = 0
        fakeEvent = RumRawEvent.StopView(Object(), emptyMap())

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyZeroInteractions(mockWriter, mockParentScope)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 send custom Action immediately 𝕎 handleEvent(StopView) {no side effect}`() {
        // Given
        testedScope.type = RumActionType.CUSTOM
        testedScope.resourceCount = 0
        testedScope.viewTreeChangeCount = 0
        testedScope.errorCount = 0
        testedScope.crashCount = 0
        fakeEvent = RumRawEvent.StopView(Object(), emptyMap())

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasActionData {
                    hasId(testedScope.actionId)
                    hasTimestamp(fakeEventTime.timestamp)
                    hasType(RumActionType.CUSTOM)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(1)
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasView(fakeParentContext)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 do nothing after threshold 𝕎 handleEvent(any) {no side effect}`() {
        // Given
        testedScope.resourceCount = 0
        testedScope.viewTreeChangeCount = 0
        testedScope.errorCount = 0
        testedScope.crashCount = 0
        Thread.sleep(TEST_INACTIVITY_MS)

        // When
        val result = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        verifyZeroInteractions(mockWriter, mockParentScope)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(any) before threshold`() {
        // Given
        testedScope.viewTreeChangeCount = 1

        // When
        val result = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        verifyZeroInteractions(mockWriter, mockParentScope)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(StartResource+any)`(
        @StringForgery key: String,
        @StringForgery method: String,
        @StringForgery(regex = "http(s?)://[a-z]+.com/[a-z]+") url: String
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
    fun `𝕄 do nothing 𝕎 handleEvent(StartResource+StopAction+any)`(
        @StringForgery key: String,
        @StringForgery method: String,
        @StringForgery(regex = "http(s?)://[a-z]+.com/[a-z]+") url: String
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
        verifyZeroInteractions(mockWriter, mockParentScope)
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isSameAs(testedScope)
        assertThat(result3).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send Action after timeout 𝕎 handleEvent(StartResource+any)`(
        @StringForgery key: String,
        @StringForgery method: String,
        @StringForgery(regex = "http(s?)://[a-z]+.com/[a-z]+") url: String
    ) {
        // When
        fakeEvent = RumRawEvent.StartResource(key, url, method, emptyMap())
        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        Thread.sleep(TEST_MAX_DURATION_MS)
        val result2 = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasActionData {
                    hasId(testedScope.actionId)
                    hasTimestamp(fakeEventTime.timestamp)
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(TEST_MAX_DURATION_NS)
                    hasResourceCount(1)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasView(fakeParentContext)
                    hasUserInfo(fakeUserInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isNull()
    }

    @Test
    fun `𝕄 send Action after timeout 𝕎 handleEvent(StartResource+StopAction+any)`(
        @StringForgery key: String,
        @StringForgery method: String,
        @StringForgery(regex = "http(s?)://[a-z]+.com/[a-z]+") url: String
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
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasActionData {
                    hasId(testedScope.actionId)
                    hasTimestamp(fakeEventTime.timestamp)
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(TEST_MAX_DURATION_NS)
                    hasResourceCount(1)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasView(fakeParentContext)
                    hasUserInfo(fakeUserInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                }
        }
        verify(mockParentScope, never()).handleEvent(any(), any())
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(result2).isSameAs(testedScope)
        assertThat(result3).isNull()
    }

    @Test
    fun `𝕄 send Action after threshold 𝕎 handleEvent(StopAction+ViewTreeChanged+any)`() {
        // When
        fakeEvent = RumRawEvent.StopAction(fakeType, fakeName, emptyMap())
        val result = testedScope.handleEvent(fakeEvent, mockWriter)
        val duration = measureNanoTime {
            repeat(4) {
                Thread.sleep(TEST_INACTIVITY_MS / 3)
                testedScope.handleEvent(RumRawEvent.ViewTreeChanged(Time()), mockWriter)
            }
        }
        testedScope.handleEvent(RumRawEvent.ViewTreeChanged(Time()), mockWriter)
        Thread.sleep(TEST_INACTIVITY_MS)
        val result2 = testedScope.handleEvent(mockEvent(), mockWriter)

        // Then
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())
            assertThat(lastValue)
                .hasAttributes(fakeAttributes)
                .hasActionData {
                    hasId(testedScope.actionId)
                    hasTimestamp(fakeEventTime.timestamp)
                    hasType(fakeType)
                    hasTargetName(fakeName)
                    hasDurationGreaterThan(duration)
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasView(fakeParentContext)
                    hasUserInfo(fakeUserInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
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

    // endregion

    companion object {
        internal const val TEST_INACTIVITY_MS = 30L
        internal const val TEST_MAX_DURATION_MS = 500L
        internal val TEST_MAX_DURATION_NS = TimeUnit.MILLISECONDS.toNanos(TEST_MAX_DURATION_MS)
    }
}
