/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal

import android.app.ApplicationExitInfo
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.feature.event.ThreadDump
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.rum.internal.anr.AndroidTraceParser
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.utils.forge.Configurator
import com.google.gson.JsonObject
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

/**
 * Regression tests for RUMS-5318: Significant increase (~2x) in ANRs detected by RUM after
 * upgrading from Android SDK 2.26.0 > 3.0.0.
 *
 * Root cause: race condition in DatadogLateCrashReporter.handleAnrCrash() — writeLastFatalAnrSent
 * is called AFTER rumWriter.write(), so if the withWriteContext callback fires more than once both
 * invocations read lastFatalAnrSent = null and both proceed to write duplicate ANR events.
 */
@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogLateCrashReporterRUMS5318Test {

    private lateinit var testedHandler: LateCrashReporter

    @Mock
    lateinit var mockSdkCore: InternalSdkCore

    @Mock
    lateinit var mockRumFeatureScope: FeatureScope

    @Mock
    lateinit var mockRumWriter: DataWriter<Any>

    @Mock
    lateinit var mockRumEventDeserializer: Deserializer<JsonObject, Any>

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    @Mock
    lateinit var mockEventWriteScope: EventWriteScope

    @Mock
    lateinit var mockAndroidTraceParser: AndroidTraceParser

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope

        whenever(mockEventWriteScope.invoke(any())) doAnswer {
            val callback = it.getArgument<(EventBatchWriter) -> Unit>(0)
            callback.invoke(mockEventBatchWriter)
        }
        whenever(mockRumFeatureScope.withWriteContext(any(), any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventWriteScope) -> Unit>(it.arguments.lastIndex)
            callback.invoke(fakeDatadogContext, mockEventWriteScope)
        }

        testedHandler = DatadogLateCrashReporter(
            sdkCore = mockSdkCore,
            rumEventDeserializer = mockRumEventDeserializer,
            androidTraceParser = mockAndroidTraceParser
        )
    }

    /**
     * RUMS-5318 Test 1: Duplicate ANR report when withWriteContext callback is invoked twice.
     *
     * Simulates the race condition where multiple concurrent calls trigger the withWriteContext
     * callback more than once. Because writeLastFatalAnrSent is called AFTER rumWriter.write() in
     * the pre-fix code, both callback invocations read lastFatalAnrSent = null and both pass the
     * deduplication check, causing duplicate ANR events to be written.
     *
     * Expected (fixed) behaviour: rumWriter.write() is called only ONCE.
     * Pre-fix behaviour: rumWriter.write() is called TWICE — this test FAILS on pre-fix code.
     */
    @Test
    fun `M write ANR event only once W handleAnrCrash() { withWriteContext callback invoked twice }`(
        @LongForgery(min = 1) fakeTimestamp: Long,
        @Forgery viewEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val fakeViewEvent = viewEvent.copy(
            date = System.currentTimeMillis() - forge.aLong(
                min = 0L,
                max = DatadogLateCrashReporter.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD - 1000
            )
        )
        val fakeViewEventJson = fakeViewEvent.toJson().asJsonObject

        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson)) doReturn fakeViewEvent

        val fakeThreadsDump = forge.anrCrashThreadDump()
        whenever(mockAndroidTraceParser.parse(any())) doReturn fakeThreadsDump

        val mockAnrExitInfo = mock<ApplicationExitInfo>().apply {
            whenever(traceInputStream) doReturn mock()
            whenever(timestamp) doReturn fakeTimestamp
        }

        // lastFatalAnrSent returns null on every access — simulates the race where neither
        // concurrent invocation has written the timestamp yet.
        whenever(mockSdkCore.lastFatalAnrSent) doReturn null

        // Simulate the race: withWriteContext fires its callback TWICE (e.g. two concurrent calls
        // or a repeated context update) before writeLastFatalAnrSent can persist the timestamp.
        whenever(mockRumFeatureScope.withWriteContext(any(), any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventWriteScope) -> Unit>(it.arguments.lastIndex)
            callback.invoke(fakeDatadogContext, mockEventWriteScope)
            callback.invoke(fakeDatadogContext, mockEventWriteScope) // second invocation = the race
        }

        // When
        testedHandler.handleAnrCrash(mockAnrExitInfo, fakeViewEventJson, mockRumWriter)

        // Then — expect the error event to be written exactly once; the duplicate must be suppressed.
        // Pre-fix code writes it TWICE because writeLastFatalAnrSent runs after rumWriter.write(),
        // so the second callback invocation still sees lastFatalAnrSent == null and proceeds.
        verify(mockRumWriter, times(1)).write(any(), any(), any())
    }

    /**
     * RUMS-5318 Test 2: writeLastFatalAnrSent must be called BEFORE rumWriter.write().
     *
     * The deduplication guard reads lastFatalAnrSent at the top of the withWriteContext callback.
     * For that guard to work correctly under concurrent callback invocations, the timestamp must be
     * persisted BEFORE any write call, so a second invocation will see a non-null value and bail.
     *
     * Expected (fixed) behaviour: writeLastFatalAnrSent happens before rumWriter.write().
     * Pre-fix behaviour: writeLastFatalAnrSent is the last statement in writeScope, i.e. AFTER
     * rumWriter.write() — this test FAILS on pre-fix code.
     */
    @Test
    fun `M call writeLastFatalAnrSent before rumWriter write W handleAnrCrash()`(
        @LongForgery(min = 1) fakeTimestamp: Long,
        @Forgery viewEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val fakeViewEvent = viewEvent.copy(
            date = System.currentTimeMillis() - forge.aLong(
                min = 0L,
                max = DatadogLateCrashReporter.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD - 1000
            )
        )
        val fakeViewEventJson = fakeViewEvent.toJson().asJsonObject

        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson)) doReturn fakeViewEvent

        val fakeThreadsDump = forge.anrCrashThreadDump()
        whenever(mockAndroidTraceParser.parse(any())) doReturn fakeThreadsDump

        val mockAnrExitInfo = mock<ApplicationExitInfo>().apply {
            whenever(traceInputStream) doReturn mock()
            whenever(timestamp) doReturn fakeTimestamp
        }

        whenever(mockSdkCore.lastFatalAnrSent) doReturn null

        // When
        testedHandler.handleAnrCrash(mockAnrExitInfo, fakeViewEventJson, mockRumWriter)

        // Then — verify that writeLastFatalAnrSent is called BEFORE any call to rumWriter.write().
        // In the pre-fix code writeLastFatalAnrSent is placed last inside writeScope, so this
        // inOrder assertion fails: it sees rumWriter.write() first and writeLastFatalAnrSent second.
        val order = inOrder(mockSdkCore, mockRumWriter)
        order.verify(mockSdkCore).writeLastFatalAnrSent(fakeTimestamp)
        order.verify(mockRumWriter).write(any(), any(), any())
    }

    // region helpers

    private fun Forge.anrCrashThreadDump(): List<ThreadDump> {
        val otherThreads = aList { getForgery<ThreadDump>() }.map { it.copy(crashed = false) }
        val mainThread = getForgery<ThreadDump>().copy(name = "main", crashed = true)
        return otherThreads + mainThread
    }

    // endregion
}
