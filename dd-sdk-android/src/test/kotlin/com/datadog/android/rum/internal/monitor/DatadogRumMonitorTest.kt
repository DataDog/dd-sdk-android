/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.monitor

import android.os.Handler
import com.datadog.android.core.internal.data.Writer
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.internal.domain.event.ResourceTiming
import com.datadog.android.rum.internal.domain.event.RumEvent
import com.datadog.android.rum.internal.domain.model.ViewEvent
import com.datadog.android.rum.internal.domain.scope.RumRawEvent
import com.datadog.android.rum.internal.domain.scope.RumScope
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.forge.exhaustiveAttributes
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.setFieldValue
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.same
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
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
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogRumMonitorTest {

    lateinit var testedMonitor: DatadogRumMonitor

    @Mock
    lateinit var mockScope: RumScope

    @Mock
    lateinit var mockWriter: Writer<RumEvent>

    @Mock
    lateinit var mockHandler: Handler

    @Forgery
    lateinit var fakeApplicationId: UUID

    lateinit var fakeAttributes: Map<String, Any?>

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeAttributes = forge.exhaustiveAttributes()
        testedMonitor = DatadogRumMonitor(fakeApplicationId, mockWriter, mockHandler)
        testedMonitor.setFieldValue("rootScope", mockScope)
    }

    @AfterEach
    fun `tear down`() {
    }

    @Test
    fun `delegates startView to rootScope`(
        @StringForgery(StringForgeryType.ASCII) key: String,
        @StringForgery(StringForgeryType.ALPHABETICAL) name: String
    ) {
        testedMonitor.startView(key, name, fakeAttributes)
        Thread.sleep(200)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            val event = firstValue as RumRawEvent.StartView
            assertThat(event.key).isEqualTo(key)
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
            assertThat(event.name).isEqualTo(name)
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `delegates stopView to rootScope`(
        @StringForgery(StringForgeryType.ASCII) key: String
    ) {
        testedMonitor.stopView(key, fakeAttributes)
        Thread.sleep(200)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            val event = firstValue as RumRawEvent.StopView
            assertThat(event.key).isEqualTo(key)
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `delegates addUserAction to rootScope`(
        @Forgery type: RumActionType,
        @StringForgery(StringForgeryType.ALPHABETICAL) name: String
    ) {
        testedMonitor.addUserAction(type, name, fakeAttributes)
        Thread.sleep(200)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            val event = firstValue as RumRawEvent.StartAction
            assertThat(event.type).isEqualTo(type)
            assertThat(event.name).isEqualTo(name)
            assertThat(event.waitForStop).isFalse()
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `delegates startUserAction to rootScope`(
        @Forgery type: RumActionType,
        @StringForgery(StringForgeryType.ALPHABETICAL) name: String
    ) {
        testedMonitor.startUserAction(type, name, fakeAttributes)
        Thread.sleep(200)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            val event = firstValue as RumRawEvent.StartAction
            assertThat(event.type).isEqualTo(type)
            assertThat(event.name).isEqualTo(name)
            assertThat(event.waitForStop).isTrue()
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `delegates stopUserAction to rootScope`(
        @Forgery type: RumActionType,
        @StringForgery(StringForgeryType.ALPHABETICAL) name: String
    ) {
        testedMonitor.stopUserAction(type, name, fakeAttributes)
        Thread.sleep(200)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            val event = firstValue as RumRawEvent.StopAction
            assertThat(event.type).isEqualTo(type)
            assertThat(event.name).isEqualTo(name)
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `delegates startResource to rootScope`(
        @StringForgery(StringForgeryType.ALPHABETICAL) key: String,
        @StringForgery(StringForgeryType.ALPHABETICAL) method: String,
        @RegexForgery("http(s?)://[a-z]+.com/[a-z]+") url: String
    ) {
        testedMonitor.startResource(key, method, url, fakeAttributes)
        Thread.sleep(200)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            val event = firstValue as RumRawEvent.StartResource
            assertThat(event.key).isEqualTo(key)
            assertThat(event.method).isEqualTo(method)
            assertThat(event.url).isEqualTo(url)
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `delegates stopResource to rootScope`(
        @StringForgery(StringForgeryType.ALPHABETICAL) key: String,
        @IntForgery(200, 600) statusCode: Int,
        @LongForgery(0, 1024) size: Long,
        @Forgery kind: RumResourceKind
    ) {
        testedMonitor.stopResource(key, statusCode, size, kind, fakeAttributes)
        Thread.sleep(200)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            val event = firstValue as RumRawEvent.StopResource
            assertThat(event.key).isEqualTo(key)
            assertThat(event.statusCode).isEqualTo(statusCode.toLong())
            assertThat(event.kind).isEqualTo(kind)
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `delegates stopResourceWithError to rootScope`(
        @StringForgery(StringForgeryType.ALPHABETICAL) key: String,
        @StringForgery(StringForgeryType.ALPHABETICAL) message: String,
        @Forgery source: RumErrorSource,
        @IntForgery(200, 600) statusCode: Int,
        @Forgery throwable: Throwable
    ) {
        testedMonitor.stopResourceWithError(key, statusCode, message, source, throwable)
        Thread.sleep(200)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            val event = firstValue as RumRawEvent.StopResourceWithError
            assertThat(event.key).isEqualTo(key)
            assertThat(event.statusCode).isEqualTo(statusCode.toLong())
            assertThat(event.message).isEqualTo(message)
            assertThat(event.source).isEqualTo(source)
            assertThat(event.throwable).isEqualTo(throwable)
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `delegates addError to rootScope`(
        @StringForgery(StringForgeryType.ALPHABETICAL) message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable
    ) {
        testedMonitor.addError(message, source, throwable, fakeAttributes)
        Thread.sleep(200)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            val event = firstValue as RumRawEvent.AddError
            assertThat(event.message).isEqualTo(message)
            assertThat(event.source).isEqualTo(source)
            assertThat(event.throwable).isEqualTo(throwable)
            assertThat(event.isFatal).isFalse()
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `delegates viewTreeChanged to rootScope`() {
        testedMonitor.viewTreeChanged()
        Thread.sleep(200)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            assertThat(firstValue).isInstanceOf(RumRawEvent.ViewTreeChanged::class.java)
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `delegates waitForResourceTiming to rootScope`(
        @StringForgery(StringForgeryType.ALPHABETICAL) key: String
    ) {
        testedMonitor.waitForResourceTiming(key)
        Thread.sleep(200)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            assertThat(firstValue).isEqualTo(RumRawEvent.WaitForResourceTiming(key))
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `delegates addResourceTiming to rootScope`(
        @StringForgery(StringForgeryType.ALPHABETICAL) key: String,
        @Forgery timing: ResourceTiming
    ) {
        testedMonitor.addResourceTiming(key, timing)
        Thread.sleep(200)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            assertThat(firstValue).isEqualTo(RumRawEvent.AddResourceTiming(key, timing))
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `delegates addCrash to rootScope`(
        @StringForgery(StringForgeryType.ALPHABETICAL) message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable
    ) {
        testedMonitor.addCrash(message, source, throwable)
        Thread.sleep(200)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            val event = firstValue as RumRawEvent.AddError
            assertThat(event.message).isEqualTo(message)
            assertThat(event.source).isEqualTo(source)
            assertThat(event.throwable).isEqualTo(throwable)
            assertThat(event.isFatal).isTrue()
            assertThat(event.attributes).isEmpty()
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `delegates updateViewLoadingTime to rootScope`(forge: Forge) {
        val key = forge.anAsciiString()
        val loadingTime = forge.aLong(min = 1)
        val loadingType = forge.aValueFrom(ViewEvent.LoadingType::class.java)

        testedMonitor.updateViewLoadingTime(key, loadingTime, loadingType)
        Thread.sleep(200)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            assertThat(firstValue).isEqualTo(
                RumRawEvent.UpdateViewLoadingTime(
                    key,
                    loadingTime,
                    loadingType
                )
            )
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `sends keep alive event to rootScope regularly`() {
        argumentCaptor<Runnable> {
            inOrder(mockScope, mockWriter, mockHandler) {
                verify(mockHandler).postDelayed(capture(), eq(DatadogRumMonitor.KEEP_ALIVE_MS))
                verifyZeroInteractions(mockScope)
                val runnable = firstValue
                runnable.run()
                Thread.sleep(200)
                verify(mockHandler).removeCallbacks(same(runnable))
                verify(mockScope).handleEvent(
                    argThat { this is RumRawEvent.KeepAlive },
                    same(mockWriter)
                )
                verify(mockHandler).postDelayed(same(runnable), eq(DatadogRumMonitor.KEEP_ALIVE_MS))
                verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `delays keep alive runnable on other event`() {
        val mockEvent: RumRawEvent = mock()
        val runnable = testedMonitor.keepAliveRunnable

        testedMonitor.handleEvent(mockEvent)
        Thread.sleep(200)

        argumentCaptor<Runnable> {
            inOrder(mockScope, mockWriter, mockHandler) {
                verify(mockHandler).removeCallbacks(same(runnable))
                verify(mockScope).handleEvent(same(mockEvent), same(mockWriter))
                verify(mockHandler).postDelayed(same(runnable), eq(DatadogRumMonitor.KEEP_ALIVE_MS))
                verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `removes callback from handler on stopKeepAliveCallback`() {
        // initial post
        verify(mockHandler).postDelayed(any(), any())

        testedMonitor.stopKeepAliveCallback()

        verify(mockHandler).removeCallbacks(same(testedMonitor.keepAliveRunnable))
        verifyNoMoreInteractions(mockHandler, mockWriter, mockScope)
    }
}
