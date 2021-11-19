/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.monitor

import android.os.Handler
import com.datadog.android.core.internal.net.FirstPartyHostDetector
import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.RumSessionListener
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.domain.event.ResourceTiming
import com.datadog.android.rum.internal.domain.scope.RumApplicationScope
import com.datadog.android.rum.internal.domain.scope.RumRawEvent
import com.datadog.android.rum.internal.domain.scope.RumScope
import com.datadog.android.rum.internal.vitals.VitalMonitor
import com.datadog.android.rum.model.ViewEvent
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
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.same
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
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
    lateinit var mockWriter: DataWriter<Any>

    @Mock
    lateinit var mockHandler: Handler

    @Mock
    lateinit var mockDetector: FirstPartyHostDetector

    @Mock
    lateinit var mockCpuVitalMonitor: VitalMonitor

    @Mock
    lateinit var mockMemoryVitalMonitor: VitalMonitor

    @Mock
    lateinit var mockFrameRateVitalMonitor: VitalMonitor

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @Mock
    lateinit var mockSessionListener: RumSessionListener

    @StringForgery(regex = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    lateinit var fakeApplicationId: String

    lateinit var fakeAttributes: Map<String, Any?>

    @FloatForgery(min = 0f, max = 100f)
    var fakeSamplingRate: Float = 0f

    @LongForgery(TIMESTAMP_MIN, TIMESTAMP_MAX)
    var fakeTimestamp: Long = 0L

    @BoolForgery
    var fakeBackgroundTrackingEnabled: Boolean = false

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeAttributes = forge.exhaustiveAttributes()
        testedMonitor = DatadogRumMonitor(
            fakeApplicationId,
            fakeSamplingRate,
            fakeBackgroundTrackingEnabled,
            mockWriter,
            mockHandler,
            mockDetector,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            mockTimeProvider,
            mockSessionListener
        )
        testedMonitor.setFieldValue("rootScope", mockScope)
    }

    @Test
    fun `creates root scope`() {
        testedMonitor = DatadogRumMonitor(
            fakeApplicationId,
            fakeSamplingRate,
            fakeBackgroundTrackingEnabled,
            mockWriter,
            mockHandler,
            mockDetector,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            mockTimeProvider,
            mockSessionListener
        )

        val rootScope = testedMonitor.rootScope
        check(rootScope is RumApplicationScope)
        assertThat(rootScope.samplingRate).isEqualTo(fakeSamplingRate)
        assertThat(rootScope.backgroundTrackingEnabled).isEqualTo(fakeBackgroundTrackingEnabled)
    }

    @Test
    fun `M delegate event to rootScope W startView()`(
        @StringForgery(type = StringForgeryType.ASCII) key: String,
        @StringForgery name: String
    ) {
        testedMonitor.startView(key, name, fakeAttributes)
        Thread.sleep(PROCESSING_DELAY)

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
    fun `M delegate event to rootScope W stopView()`(
        @StringForgery(type = StringForgeryType.ASCII) key: String
    ) {
        testedMonitor.stopView(key, fakeAttributes)
        Thread.sleep(PROCESSING_DELAY)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            val event = firstValue as RumRawEvent.StopView
            assertThat(event.key).isEqualTo(key)
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W addUserAction()`(
        @Forgery type: RumActionType,
        @StringForgery name: String
    ) {
        testedMonitor.addUserAction(type, name, fakeAttributes)
        Thread.sleep(PROCESSING_DELAY)

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
    fun `M delegate event to rootScope W startUserAction()`(
        @Forgery type: RumActionType,
        @StringForgery name: String
    ) {
        testedMonitor.startUserAction(type, name, fakeAttributes)
        Thread.sleep(PROCESSING_DELAY)

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
    fun `M delegate event to rootScope W stopUserAction()`() {
        @Suppress("DEPRECATION")
        testedMonitor.stopUserAction(fakeAttributes)
        Thread.sleep(PROCESSING_DELAY)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            val event = firstValue as RumRawEvent.StopAction
            assertThat(event.type).isNull()
            assertThat(event.name).isNull()
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W stopUserAction()`(
        @Forgery type: RumActionType,
        @StringForgery name: String
    ) {
        testedMonitor.stopUserAction(type, name, fakeAttributes)
        Thread.sleep(PROCESSING_DELAY)

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
    fun `M delegate event to rootScope W startResource()`(
        @StringForgery key: String,
        @StringForgery method: String,
        @StringForgery(regex = "http(s?)://[a-z]+\\.com/[a-z]+") url: String
    ) {
        testedMonitor.startResource(key, method, url, fakeAttributes)
        Thread.sleep(PROCESSING_DELAY)

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
    fun `M delegate event to rootScope W stopResource()`(
        @StringForgery key: String,
        @IntForgery(200, 600) statusCode: Int,
        @LongForgery(0, 1024) size: Long,
        @Forgery kind: RumResourceKind
    ) {
        testedMonitor.stopResource(key, statusCode, size, kind, fakeAttributes)
        Thread.sleep(PROCESSING_DELAY)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            val event = firstValue as RumRawEvent.StopResource
            assertThat(event.key).isEqualTo(key)
            assertThat(event.statusCode).isEqualTo(statusCode.toLong())
            assertThat(event.kind).isEqualTo(kind)
            assertThat(event.size).isEqualTo(size)
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W stopResource() {without status code nor size}`(
        @StringForgery key: String,
        @Forgery kind: RumResourceKind
    ) {
        testedMonitor.stopResource(key, null, null, kind, fakeAttributes)
        Thread.sleep(PROCESSING_DELAY)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            val event = firstValue as RumRawEvent.StopResource
            assertThat(event.key).isEqualTo(key)
            assertThat(event.statusCode).isNull()
            assertThat(event.kind).isEqualTo(kind)
            assertThat(event.size).isNull()
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W stopResourceWithError()`(
        @StringForgery key: String,
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @IntForgery(200, 600) statusCode: Int,
        @Forgery throwable: Throwable
    ) {
        testedMonitor.stopResourceWithError(
            key,
            statusCode,
            message,
            source,
            throwable,
            fakeAttributes
        )
        Thread.sleep(PROCESSING_DELAY)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            val event = firstValue as RumRawEvent.StopResourceWithError
            assertThat(event.key).isEqualTo(key)
            assertThat(event.statusCode).isEqualTo(statusCode.toLong())
            assertThat(event.message).isEqualTo(message)
            assertThat(event.source).isEqualTo(source)
            assertThat(event.throwable).isEqualTo(throwable)
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W stopResourceWithError() {without status code}`(
        @StringForgery key: String,
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable
    ) {
        testedMonitor.stopResourceWithError(key, null, message, source, throwable, fakeAttributes)
        Thread.sleep(PROCESSING_DELAY)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            val event = firstValue as RumRawEvent.StopResourceWithError
            assertThat(event.key).isEqualTo(key)
            assertThat(event.statusCode).isNull()
            assertThat(event.message).isEqualTo(message)
            assertThat(event.source).isEqualTo(source)
            assertThat(event.throwable).isEqualTo(throwable)
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W addError`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable
    ) {
        testedMonitor.addError(message, source, throwable, fakeAttributes)
        Thread.sleep(PROCESSING_DELAY)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            val event = firstValue as RumRawEvent.AddError
            assertThat(event.message).isEqualTo(message)
            assertThat(event.source).isEqualTo(source)
            assertThat(event.throwable).isEqualTo(throwable)
            assertThat(event.stacktrace).isNull()
            assertThat(event.isFatal).isFalse()
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W onAddErrorWithStacktrace`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @StringForgery stacktrace: String
    ) {
        testedMonitor.addErrorWithStacktrace(message, source, stacktrace, fakeAttributes)
        Thread.sleep(PROCESSING_DELAY)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            val event = firstValue as RumRawEvent.AddError
            assertThat(event.message).isEqualTo(message)
            assertThat(event.source).isEqualTo(source)
            assertThat(event.throwable).isNull()
            assertThat(event.stacktrace).isEqualTo(stacktrace)
            assertThat(event.isFatal).isFalse()
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W viewTreeChanged()`() {
        val eventTime = Time()
        testedMonitor.viewTreeChanged(eventTime)
        Thread.sleep(PROCESSING_DELAY)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            check(firstValue is RumRawEvent.ViewTreeChanged)
            assertThat(firstValue.eventTime).isEqualTo(eventTime)
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W waitForResourceTiming()`(
        @StringForgery key: String
    ) {
        testedMonitor.waitForResourceTiming(key)
        Thread.sleep(PROCESSING_DELAY)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            val event = firstValue
            check(event is RumRawEvent.WaitForResourceTiming)
            assertThat(event.key).isEqualTo(key)
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W addResourceTiming()`(
        @StringForgery key: String,
        @Forgery timing: ResourceTiming
    ) {
        testedMonitor.addResourceTiming(key, timing)
        Thread.sleep(PROCESSING_DELAY)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            val event = firstValue
            check(event is RumRawEvent.AddResourceTiming)
            assertThat(event.key).isEqualTo(key)
            assertThat(event.timing).isEqualTo(timing)
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W addCustomTiming()`(
        @StringForgery name: String
    ) {
        testedMonitor.addTiming(name)
        Thread.sleep(PROCESSING_DELAY)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            val event = firstValue
            check(event is RumRawEvent.AddCustomTiming)
            assertThat(event.name).isEqualTo(name)
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `M delegate event to rootScope on current thread W addCrash()`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable
    ) {
        // Given
        testedMonitor.drainExecutorService()

        // When
        testedMonitor.addCrash(message, source, throwable)
        Thread.sleep(PROCESSING_DELAY)

        // Then
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
    fun `M delegate event to rootScope W updateViewLoadingTime()`(forge: Forge) {
        val key = forge.anAsciiString()
        val loadingTime = forge.aLong(min = 1)
        val loadingType = forge.aValueFrom(ViewEvent.LoadingType::class.java)

        testedMonitor.updateViewLoadingTime(key, loadingTime, loadingType)
        Thread.sleep(PROCESSING_DELAY)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            val event = firstValue
            check(event is RumRawEvent.UpdateViewLoadingTime)
            assertThat(event.key).isEqualTo(key)
            assertThat(event.loadingTime).isEqualTo(loadingTime)
            assertThat(event.loadingType).isEqualTo(loadingType)
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W resetSession()`() {

        testedMonitor.resetSession()
        Thread.sleep(PROCESSING_DELAY)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            val event = firstValue
            check(event is RumRawEvent.ResetSession)
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `M delegate event to rootScope with timestamp W startView()`(
        @StringForgery(type = StringForgeryType.ASCII) key: String,
        @StringForgery name: String
    ) {
        val attributes = fakeAttributes + (RumAttributes.INTERNAL_TIMESTAMP to fakeTimestamp)

        testedMonitor.startView(key, name, attributes)
        Thread.sleep(PROCESSING_DELAY)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            val event = firstValue as RumRawEvent.StartView
            assertThat(event.eventTime.timestamp).isEqualTo(fakeTimestamp)
            assertThat(event.key).isEqualTo(key)
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
            assertThat(event.name).isEqualTo(name)
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `M delegate event to rootScope with timestamp W stopView()`(
        @StringForgery(type = StringForgeryType.ASCII) key: String
    ) {
        val attributes = fakeAttributes + (RumAttributes.INTERNAL_TIMESTAMP to fakeTimestamp)

        testedMonitor.stopView(key, attributes)
        Thread.sleep(PROCESSING_DELAY)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            val event = firstValue as RumRawEvent.StopView
            assertThat(event.eventTime.timestamp).isEqualTo(fakeTimestamp)
            assertThat(event.key).isEqualTo(key)
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `M delegate event to rootScope with timestamp W addUserAction()`(
        @Forgery type: RumActionType,
        @StringForgery name: String
    ) {
        val attributes = fakeAttributes + (RumAttributes.INTERNAL_TIMESTAMP to fakeTimestamp)

        testedMonitor.addUserAction(type, name, attributes)
        Thread.sleep(PROCESSING_DELAY)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            val event = firstValue as RumRawEvent.StartAction
            assertThat(event.eventTime.timestamp).isEqualTo(fakeTimestamp)
            assertThat(event.type).isEqualTo(type)
            assertThat(event.name).isEqualTo(name)
            assertThat(event.waitForStop).isFalse()
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `M delegate event to rootScope with timestamp W startUserAction()`(
        @Forgery type: RumActionType,
        @StringForgery name: String
    ) {
        val attributes = fakeAttributes + (RumAttributes.INTERNAL_TIMESTAMP to fakeTimestamp)

        testedMonitor.startUserAction(type, name, attributes)
        Thread.sleep(PROCESSING_DELAY)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            val event = firstValue as RumRawEvent.StartAction
            assertThat(event.eventTime.timestamp).isEqualTo(fakeTimestamp)
            assertThat(event.type).isEqualTo(type)
            assertThat(event.name).isEqualTo(name)
            assertThat(event.waitForStop).isTrue()
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `M delegate event to rootScope with timestamp W stopUserAction()`(
        @Forgery type: RumActionType,
        @StringForgery name: String
    ) {
        val attributes = fakeAttributes + (RumAttributes.INTERNAL_TIMESTAMP to fakeTimestamp)

        testedMonitor.stopUserAction(type, name, attributes)
        Thread.sleep(PROCESSING_DELAY)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            val event = firstValue as RumRawEvent.StopAction
            assertThat(event.eventTime.timestamp).isEqualTo(fakeTimestamp)
            assertThat(event.type).isEqualTo(type)
            assertThat(event.name).isEqualTo(name)
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `M delegate event to rootScope with timestamp W startResource()`(
        @StringForgery key: String,
        @StringForgery method: String,
        @StringForgery(regex = "http(s?)://[a-z]+\\.com/[a-z]+") url: String
    ) {
        val attributes = fakeAttributes + (RumAttributes.INTERNAL_TIMESTAMP to fakeTimestamp)

        testedMonitor.startResource(key, method, url, attributes)
        Thread.sleep(PROCESSING_DELAY)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            val event = firstValue as RumRawEvent.StartResource
            assertThat(event.eventTime.timestamp).isEqualTo(fakeTimestamp)
            assertThat(event.key).isEqualTo(key)
            assertThat(event.method).isEqualTo(method)
            assertThat(event.url).isEqualTo(url)
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `M delegate event to rootScope with timestamp W stopResource()`(
        @StringForgery key: String,
        @IntForgery(200, 600) statusCode: Int,
        @LongForgery(0, 1024) size: Long,
        @Forgery kind: RumResourceKind
    ) {
        val attributes = fakeAttributes + (RumAttributes.INTERNAL_TIMESTAMP to fakeTimestamp)

        testedMonitor.stopResource(key, statusCode, size, kind, attributes)
        Thread.sleep(PROCESSING_DELAY)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            val event = firstValue as RumRawEvent.StopResource
            assertThat(event.eventTime.timestamp).isEqualTo(fakeTimestamp)
            assertThat(event.key).isEqualTo(key)
            assertThat(event.statusCode).isEqualTo(statusCode.toLong())
            assertThat(event.kind).isEqualTo(kind)
            assertThat(event.size).isEqualTo(size)
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `M delegate event to rootScope with timestamp W addError`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable
    ) {
        val attributes = fakeAttributes + (RumAttributes.INTERNAL_TIMESTAMP to fakeTimestamp)

        testedMonitor.addError(message, source, throwable, attributes)
        Thread.sleep(PROCESSING_DELAY)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            val event = firstValue as RumRawEvent.AddError
            assertThat(event.eventTime.timestamp).isEqualTo(fakeTimestamp)
            assertThat(event.message).isEqualTo(message)
            assertThat(event.source).isEqualTo(source)
            assertThat(event.throwable).isEqualTo(throwable)
            assertThat(event.stacktrace).isNull()
            assertThat(event.isFatal).isFalse()
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `M delegate event to rootScope with timestamp W onAddErrorWithStacktrace`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @StringForgery stacktrace: String
    ) {
        val attributes = fakeAttributes + (RumAttributes.INTERNAL_TIMESTAMP to fakeTimestamp)

        testedMonitor.addErrorWithStacktrace(message, source, stacktrace, attributes)
        Thread.sleep(PROCESSING_DELAY)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            val event = firstValue as RumRawEvent.AddError
            assertThat(event.eventTime.timestamp).isEqualTo(fakeTimestamp)
            assertThat(event.message).isEqualTo(message)
            assertThat(event.source).isEqualTo(source)
            assertThat(event.throwable).isNull()
            assertThat(event.stacktrace).isEqualTo(stacktrace)
            assertThat(event.isFatal).isFalse()
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `M delegate event to rootScope with error type W addError`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @StringForgery errorType: String
    ) {
        val fakeAttributesWithErrorType =
            fakeAttributes + (RumAttributes.INTERNAL_ERROR_TYPE to errorType)
        testedMonitor.addError(message, source, throwable, fakeAttributesWithErrorType)
        Thread.sleep(PROCESSING_DELAY)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            val event = firstValue as RumRawEvent.AddError
            assertThat(event.message).isEqualTo(message)
            assertThat(event.source).isEqualTo(source)
            assertThat(event.throwable).isEqualTo(throwable)
            assertThat(event.stacktrace).isNull()
            assertThat(event.isFatal).isFalse()
            assertThat(event.type).isEqualTo(errorType)
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributesWithErrorType)
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W error type onAddErrorWithStacktrace`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @StringForgery stacktrace: String,
        @StringForgery errorType: String
    ) {
        val fakeAttributesWithErrorType =
            fakeAttributes + (RumAttributes.INTERNAL_ERROR_TYPE to errorType)
        testedMonitor.addErrorWithStacktrace(
            message,
            source,
            stacktrace,
            fakeAttributesWithErrorType
        )
        Thread.sleep(PROCESSING_DELAY)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            val event = firstValue as RumRawEvent.AddError
            assertThat(event.message).isEqualTo(message)
            assertThat(event.source).isEqualTo(source)
            assertThat(event.throwable).isNull()
            assertThat(event.stacktrace).isEqualTo(stacktrace)
            assertThat(event.isFatal).isFalse()
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributesWithErrorType)
            assertThat(event.type).isEqualTo(errorType)
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W addLongTask`(
        @LongForgery duration: Long,
        @StringForgery target: String
    ) {
        testedMonitor.addLongTask(duration, target)
        Thread.sleep(PROCESSING_DELAY)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            val event = firstValue as RumRawEvent.AddLongTask
            assertThat(event.durationNs).isEqualTo(duration)
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W eventSent {action}`(
        @StringForgery viewId: String
    ) {
        testedMonitor.eventSent(viewId, EventType.ACTION)
        Thread.sleep(PROCESSING_DELAY)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            val event = firstValue as RumRawEvent.ActionSent
            assertThat(event.viewId).isEqualTo(viewId)
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W eventSent {resource}`(
        @StringForgery viewId: String
    ) {
        testedMonitor.eventSent(viewId, EventType.RESOURCE)
        Thread.sleep(PROCESSING_DELAY)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            val event = firstValue as RumRawEvent.ResourceSent
            assertThat(event.viewId).isEqualTo(viewId)
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W eventSent {error}`(
        @StringForgery viewId: String
    ) {
        testedMonitor.eventSent(viewId, EventType.ERROR)
        Thread.sleep(PROCESSING_DELAY)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            val event = firstValue as RumRawEvent.ErrorSent
            assertThat(event.viewId).isEqualTo(viewId)
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W eventSent {longTask}`(
        @StringForgery viewId: String
    ) {
        testedMonitor.eventSent(viewId, EventType.LONG_TASK)
        Thread.sleep(PROCESSING_DELAY)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            val event = firstValue as RumRawEvent.LongTaskSent
            assertThat(event.viewId).isEqualTo(viewId)
            assertThat(event.isFrozenFrame).isFalse()
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W eventSent {frozenFrame}`(
        @StringForgery viewId: String
    ) {
        testedMonitor.eventSent(viewId, EventType.FROZEN_FRAME)
        Thread.sleep(PROCESSING_DELAY)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            val event = firstValue as RumRawEvent.LongTaskSent
            assertThat(event.viewId).isEqualTo(viewId)
            assertThat(event.isFrozenFrame).isTrue()
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W eventDropped {action}`(
        @StringForgery viewId: String
    ) {
        testedMonitor.eventDropped(viewId, EventType.ACTION)
        Thread.sleep(PROCESSING_DELAY)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            val event = firstValue as RumRawEvent.ActionDropped
            assertThat(event.viewId).isEqualTo(viewId)
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W eventDropped {resource}`(
        @StringForgery viewId: String
    ) {
        testedMonitor.eventDropped(viewId, EventType.RESOURCE)
        Thread.sleep(PROCESSING_DELAY)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            val event = firstValue as RumRawEvent.ResourceDropped
            assertThat(event.viewId).isEqualTo(viewId)
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W eventDropped {error}`(
        @StringForgery viewId: String
    ) {
        testedMonitor.eventDropped(viewId, EventType.ERROR)
        Thread.sleep(PROCESSING_DELAY)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            val event = firstValue as RumRawEvent.ErrorDropped
            assertThat(event.viewId).isEqualTo(viewId)
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W eventDropped {longTask}`(
        @StringForgery viewId: String
    ) {
        testedMonitor.eventDropped(viewId, EventType.LONG_TASK)
        Thread.sleep(PROCESSING_DELAY)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            val event = firstValue as RumRawEvent.LongTaskDropped
            assertThat(event.viewId).isEqualTo(viewId)
            assertThat(event.isFrozenFrame).isFalse()
        }
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W eventDropped {frozenFrame}`(
        @StringForgery viewId: String
    ) {
        testedMonitor.eventDropped(viewId, EventType.FROZEN_FRAME)
        Thread.sleep(PROCESSING_DELAY)

        argumentCaptor<RumRawEvent> {
            verify(mockScope).handleEvent(capture(), same(mockWriter))

            val event = firstValue as RumRawEvent.LongTaskDropped
            assertThat(event.viewId).isEqualTo(viewId)
            assertThat(event.isFrozenFrame).isTrue()
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
                Thread.sleep(PROCESSING_DELAY)
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
        Thread.sleep(PROCESSING_DELAY)

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
        // When
        testedMonitor.stopKeepAliveCallback()

        // Then
        // initial post
        verify(mockHandler).postDelayed(any(), any())
        verify(mockHandler).removeCallbacks(same(testedMonitor.keepAliveRunnable))
        verifyNoMoreInteractions(mockHandler, mockWriter, mockScope)
    }

    @Test
    fun `M drain the executor queue W drainExecutorService()`(forge: Forge) {
        // Given
        val blockingQueue = LinkedBlockingQueue<Runnable>(forge.aList { mock() })
        val mockExecutor: ScheduledThreadPoolExecutor = mock()
        whenever(mockExecutor.queue).thenReturn(blockingQueue)
        testedMonitor = DatadogRumMonitor(
            fakeApplicationId,
            fakeSamplingRate,
            fakeBackgroundTrackingEnabled,
            mockWriter,
            mockHandler,
            mockDetector,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            mockTimeProvider,
            mockSessionListener,
            mockExecutor
        )

        // When
        testedMonitor.drainExecutorService()

        // Then
        blockingQueue.forEach {
            verify(it).run()
        }
    }

    @Test
    fun `M delegate event to rootScope W sendWebViewEvent()`() {
        // When
        testedMonitor.sendWebViewEvent()
        Thread.sleep(PROCESSING_DELAY)

        // Then
        verify(mockScope).handleEvent(
            argThat { this is RumRawEvent.WebViewEvent },
            same(mockWriter)
        )
        verifyNoMoreInteractions(mockScope, mockWriter)
    }

    @Test
    fun `M shutdown with wait the persistence executor W drainAndShutdownExecutors()`() {
        // Given
        val mockExecutorService: ExecutorService = mock()
        testedMonitor = DatadogRumMonitor(
            fakeApplicationId,
            fakeSamplingRate,
            fakeBackgroundTrackingEnabled,
            mockWriter,
            mockHandler,
            mockDetector,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            mockTimeProvider,
            mockSessionListener,
            mockExecutorService
        )

        // When
        testedMonitor.drainExecutorService()

        // Then
        inOrder(mockExecutorService) {
            verify(mockExecutorService).shutdown()
            verify(mockExecutorService).awaitTermination(10, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `M not schedule any task W executor service shutdown()`() {
        // Given
        val mockExecutorService: ExecutorService = mock()
        testedMonitor = DatadogRumMonitor(
            fakeApplicationId,
            fakeSamplingRate,
            fakeBackgroundTrackingEnabled,
            mockWriter,
            mockHandler,
            mockDetector,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            mockTimeProvider,
            mockSessionListener,
            mockExecutorService
        )
        whenever(mockExecutorService.isShutdown).thenReturn(true)

        // When
        testedMonitor.handleEvent(mock())

        // Then
        verify(mockExecutorService, never()).submit(any())
    }

    companion object {
        const val TIMESTAMP_MIN = 1000000000000
        const val TIMESTAMP_MAX = 2000000000000
        const val PROCESSING_DELAY = 100L
    }
}
