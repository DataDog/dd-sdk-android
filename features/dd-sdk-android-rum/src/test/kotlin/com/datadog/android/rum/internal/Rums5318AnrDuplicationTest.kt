/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal

// Reproduces RUMS-5318: ANR duplication on API 30+ when trackNonFatalAnrs=true
// Both ANRDetectorRunnable (session N) and DatadogLateCrashReporter (session N+1)
// report the same physical ANR event. The lastFatalAnrSent guard only prevents
// duplication from session N+1 onward — not between N and N+1.
//
// Root cause: On API 30+, SDK v3 introduced ApplicationExitInfo-based fatal ANR
// reporting. When customers explicitly call `.trackNonFatalAnrs(true)`, the watchdog
// thread (ANRDetectorRunnable) fires during session N, and then on the NEXT launch
// (session N+1), consumeLastFatalAnr() fires DatadogLateCrashReporter.handleAnrCrash()
// for the SAME physical ANR. The lastFatalAnrSent dedup guard uses null-vs-timestamp
// comparison and only prevents re-reporting from session N+2 onward.

import android.app.ApplicationExitInfo
import android.os.Build
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
import com.datadog.android.core.internal.system.BuildSdkVersionProvider
import com.datadog.android.rum.internal.anr.AndroidTraceParser
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.utils.forge.Configurator
import com.google.gson.JsonObject
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

/**
 * Regression tests for RUMS-5318: ANR events are reported twice on API 30+ when
 * `trackNonFatalAnrs=true`.
 *
 * On API 30+, SDK v3 has TWO mechanisms that can both fire for the same physical ANR:
 *
 *   Session N (while ANR is happening):
 *     ANRDetectorRunnable.run()
 *       → GlobalRumMonitor.addError("Application Not Responding", ...) = ANR event #1
 *     [App terminates due to ANR — lastFatalAnrSent is NEVER written by the watchdog path]
 *
 *   Session N+1 (next app launch):
 *     Rum.enable() → rumFeature.consumeLastFatalAnr()
 *       → DatadogLateCrashReporter.handleAnrCrash(anrExitInfo, ...)
 *       → sdkCore.lastFatalAnrSent returns null (watchdog never set it)
 *       → dedup guard: null == anrExitInfo.timestamp → false → REPORTS ANR = ANR event #2
 *
 *   Session N+2 (second launch after ANR):
 *     handleAnrCrash() → sdkCore.lastFatalAnrSent returns T (set in session N+1)
 *     → dedup guard: T == T → true → SKIPS (correctly)
 *
 * The fix would require the watchdog path to also write lastFatalAnrSent (or equivalent),
 * so that session N+1's handleAnrCrash() can detect the duplicate and skip it.
 */
@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class Rums5318AnrDuplicationTest {

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

    // region RUMS-5318 — ANR duplication: session N+1 dedup guard does NOT protect against
    //                    the first cross-session duplication

    /**
     * Proves RUMS-5318:
     * The lastFatalAnrSent dedup guard is insufficient to prevent ANR duplication on API 30+
     * when trackNonFatalAnrs=true.
     *
     * On session N+1 (first launch after the ANR), lastFatalAnrSent=null because the
     * ANRDetectorRunnable watchdog path NEVER writes to lastFatalAnrSent. The dedup guard
     * checks `anrExitInfo.timestamp == lastFatalAnrSent` → `T == null` → false, so
     * handleAnrCrash() proceeds to write a second ANR error event.
     *
     * A correct implementation would require the watchdog to also persist the ANR timestamp
     * (e.g., via writeLastFatalAnrSent or a dedicated writeLastNonFatalAnrSent), so that
     * on the next launch handleAnrCrash() detects the duplicate and skips it.
     *
     * EXPECTED (fixed): handleAnrCrash() should NOT write any events when the watchdog already
     *   reported the same ANR in the previous session. This test FAILS on buggy code because
     *   there is no mechanism to propagate the watchdog's report across the session boundary.
     */
    @Test
    fun `M NOT report duplicate ANR W handleAnrCrash() { watchdog already reported in previous session }`(
        @LongForgery(min = 1) fakeTimestamp: Long,
        @Forgery viewEvent: ViewEvent,
        forge: Forge
    ) {
        // Given - session N+1: app restarts after ANR, lastFatalAnrSent=null because the
        // ANRDetectorRunnable watchdog in session N did NOT call writeLastFatalAnrSent
        // (the watchdog path has no mechanism to set this flag).
        val fakeViewEvent = viewEvent.copy(
            date = System.currentTimeMillis() - forge.aLong(
                min = 0L,
                max = DatadogLateCrashReporter.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD - 1000
            )
        )
        val fakeViewEventJson = fakeViewEvent.toJson().asJsonObject
        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson)) doReturn fakeViewEvent

        // lastFatalAnrSent=null: watchdog in session N never wrote this flag
        // (ANRDetectorRunnable only calls GlobalRumMonitor.addError(), not writeLastFatalAnrSent)
        whenever(mockSdkCore.lastFatalAnrSent) doReturn null

        val fakeThreadsDump = anrCrashThreadDump(forge)
        whenever(mockAndroidTraceParser.parse(any())) doReturn fakeThreadsDump

        val mockAnrExitInfo = mock<ApplicationExitInfo>().apply {
            whenever(traceInputStream) doReturn mock()
            whenever(timestamp) doReturn fakeTimestamp
        }

        // When - DatadogLateCrashReporter fires in session N+1 via consumeLastFatalAnr()
        // At this point, the watchdog already reported the same physical ANR in session N.
        testedHandler.handleAnrCrash(mockAnrExitInfo, fakeViewEventJson, mockRumWriter)

        // Then - EXPECTED (fixed behavior): NO events should be written because the watchdog
        // already reported this ANR in session N. However, the current code has NO way to
        // detect this cross-session duplication, so it WILL write the ANR again.
        //
        // This test FAILS on the buggy code, proving the duplication bug in RUMS-5318.
        verifyNoInteractions(mockRumWriter)
    }

    /**
     * Proves the EXACT dedup guard boundary from RUMS-5318:
     *
     * - Session N+1: lastFatalAnrSent=null → handleAnrCrash REPORTS the ANR (1 ErrorEvent written)
     *   → This is the DUPLICATE when trackNonFatalAnrs=true (watchdog reported in session N)
     * - Session N+2: lastFatalAnrSent=T   → handleAnrCrash SKIPS (0 additional events written)
     *   → This is the ONLY duplication the current guard prevents
     *
     * The guard was designed to prevent N+2, N+3, etc. re-reporting, not the N→N+1 duplication.
     */
    @Test
    fun `M write on first launch then skip on second launch W handleAnrCrash() { dedup only guards N+2 not N+1 }`(
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

        val fakeThreadsDump = anrCrashThreadDump(forge)
        whenever(mockAndroidTraceParser.parse(any())) doReturn fakeThreadsDump

        val mockAnrExitInfo = mock<ApplicationExitInfo>().apply {
            whenever(traceInputStream) doReturn mock()
            whenever(timestamp) doReturn fakeTimestamp
        }

        // --- Session N+1: lastFatalAnrSent=null (watchdog did NOT set it) ---
        whenever(mockSdkCore.lastFatalAnrSent) doReturn null

        // When (session N+1 call - this is where duplication occurs)
        testedHandler.handleAnrCrash(mockAnrExitInfo, fakeViewEventJson, mockRumWriter)

        // Then: session N+1 DOES report — this is ANR event #2 (duplicate of watchdog event #1)
        // proving the lastFatalAnrSent dedup guard does NOT prevent N→N+1 duplication
        verify(mockRumWriter, times(2)).write(eq(mockEventBatchWriter), any(), eq(EventType.CRASH))
        verify(mockSdkCore).writeLastFatalAnrSent(fakeTimestamp)

        // --- Session N+2: lastFatalAnrSent=fakeTimestamp (written in session N+1) ---
        whenever(mockSdkCore.lastFatalAnrSent) doReturn fakeTimestamp

        val sessionN2Handler = DatadogLateCrashReporter(
            sdkCore = mockSdkCore,
            rumEventDeserializer = mockRumEventDeserializer,
            androidTraceParser = mockAndroidTraceParser
        )
        sessionN2Handler.handleAnrCrash(mockAnrExitInfo, fakeViewEventJson, mockRumWriter)

        // Then: session N+2 is correctly blocked by the dedup guard (no new writes)
        // total writes still == 2 (guard prevents N+2 duplication but NOT N→N+1)
        verify(mockRumWriter, times(2)).write(eq(mockEventBatchWriter), any(), eq(EventType.CRASH))
    }

    // endregion

    // region RUMS-5318 — isTrackNonFatalAnrsEnabledByDefault confirms opt-in condition for API 30+

    /**
     * Proves RUMS-5318 prerequisite:
     * On API 30+ (Build.VERSION_CODES.R and above), watchdog (non-fatal) ANR tracking is DISABLED
     * by default. Duplication only occurs when customers EXPLICITLY call `.trackNonFatalAnrs(true)`.
     * This test documents the API 30 boundary.
     */
    @Test
    fun `M return false W isTrackNonFatalAnrsEnabledByDefault() { API 30+ disables watchdog by default }`() {
        // Given
        val mockBuildSdkVersionProvider = mock<BuildSdkVersionProvider>()
        whenever(mockBuildSdkVersionProvider.version) doReturn Build.VERSION_CODES.R

        // When
        val isEnabled = RumFeature.isTrackNonFatalAnrsEnabledByDefault(mockBuildSdkVersionProvider)

        // Then - watchdog is disabled by default on API 30+
        // Customers who call .trackNonFatalAnrs(true) re-enable it and trigger RUMS-5318
        assertThat(isEnabled).isFalse()
    }

    /**
     * Proves RUMS-5318 prerequisite (API < 30 is safe):
     * On API < 30, ApplicationExitInfo is not available (no fatal ANR reporting), so watchdog is
     * enabled by default. No duplication can occur on API < 30.
     */
    @Test
    fun `M return true W isTrackNonFatalAnrsEnabledByDefault() { API below 30 uses watchdog only }`() {
        // Given
        val mockBuildSdkVersionProvider = mock<BuildSdkVersionProvider>()
        whenever(mockBuildSdkVersionProvider.version) doReturn Build.VERSION_CODES.Q

        // When
        val isEnabled = RumFeature.isTrackNonFatalAnrsEnabledByDefault(mockBuildSdkVersionProvider)

        // Then - watchdog enabled by default on API < 30 (safe, no ApplicationExitInfo path)
        assertThat(isEnabled).isTrue()
    }

    // endregion

    // region helpers

    private fun anrCrashThreadDump(forge: Forge): List<ThreadDump> {
        val otherThreads = forge.aList { forge.getForgery<ThreadDump>() }.map { it.copy(crashed = false) }
        val mainThread = forge.getForgery<ThreadDump>().copy(name = "main", crashed = true)
        return forge.shuffle(otherThreads + mainThread)
    }

    // endregion
}
