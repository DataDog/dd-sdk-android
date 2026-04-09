/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.ndk.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.firstValue
import org.mockito.kotlin.secondValue
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.File
import java.util.UUID
import java.util.concurrent.ExecutorService

/**
 * Reproduction tests for RUMS-5754:
 * "React Native Android: NDK crashes (SIGABRT/SIGSEGV) not reported in RUM Error Tracking"
 *
 * Root cause: [DatadogNdkCrashHandler.readCrashData] always deletes the crash log in a `finally`
 * block, but [DatadogNdkCrashHandler.handleNdkCrashLog] only sends the RUM event when
 * `lastViewEvent != null`. In React Native, the SDK initialises from JS after
 * `MainActivity.onResume()`, so no ViewEvent is ever written. On the next launch,
 * `lastViewEvent` is null, the RUM event gate fails silently, the crash log has already been
 * deleted in the `finally` block, and the NDK crash is permanently lost.
 *
 * These tests FAIL on the current implementation, demonstrating the silent data-loss bug.
 */
@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogNdkCrashHandlerReproduceRUMS5754Test {

    private lateinit var testedHandler: DatadogNdkCrashHandler

    @Mock
    lateinit var mockExecutorService: ExecutorService

    @Mock
    lateinit var mockNdkCrashLogDeserializer: Deserializer<String, NdkCrashLog>

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockRumFeatureScope: FeatureScope

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Forgery
    lateinit var fakeNdkCrashLog: NdkCrashLog

    @Captor
    lateinit var captureRunnable: ArgumentCaptor<Runnable>

    @TempDir
    lateinit var tempDir: File

    private lateinit var fakeNdkCacheDir: File

    @BeforeEach
    fun `set up`() {
        fakeNdkCacheDir = File(tempDir, DatadogNdkCrashHandler.NDK_CRASH_REPORTS_FOLDER_NAME)

        whenever(
            mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)
        ) doReturn mockRumFeatureScope
    }

    /**
     * RUMS-5754 – Test 1: Silent data loss when no RUM session exists (React Native scenario).
     *
     * Prove that when [lastRumViewEventProvider] returns null AND a valid crash_log file exists
     * on disk, the full pipeline (prepareData + handleNdkCrash) results in:
     *   (1) NO RUM crash event sent to the RUM feature, AND
     *   (2) the crash_log file is permanently deleted from disk.
     *
     * This documents the silent data-loss: the NDK crash is read from disk, the crash log is
     * unconditionally deleted in the `finally` block of readCrashData(), but the crash is never
     * reported because lastViewEvent == null.
     *
     * EXPECTED (correct behaviour): either the crash IS reported (even without a lastViewEvent),
     * OR the crash log is NOT deleted until a RUM event can be sent.
     * ACTUAL (buggy behaviour): crash log deleted, no event sent → crash permanently lost.
     */
    @Test
    fun `M send RUM crash event W prepareData and handleNdkCrash { null lastViewEvent, React Native scenario }`() {
        // Given – simulate React Native: no prior RUM session, so lastViewEvent provider returns null
        testedHandler = DatadogNdkCrashHandler(
            tempDir,
            mockExecutorService,
            mockNdkCrashLogDeserializer,
            mockInternalLogger,
            lastRumViewEventProvider = { null } // React Native: no ViewEvent ever written
        )

        fakeNdkCacheDir.mkdirs()
        val crashLogFile = File(fakeNdkCacheDir, DatadogNdkCrashHandler.CRASH_DATA_FILE_NAME)
        crashLogFile.writeText(fakeNdkCrashLog.toJson())
        whenever(mockNdkCrashLogDeserializer.deserialize(fakeNdkCrashLog.toJson())) doReturn fakeNdkCrashLog

        // When – prepareData() reads crash log and deletes it in finally block
        testedHandler.prepareData()
        verify(mockExecutorService).execute(captureRunnable.capture())
        captureRunnable.firstValue.run()

        // Then – REPRODUCTION: crash log is deleted (data already lost at this point)
        // This assertion documents that the file is gone before the event can be sent
        assertThat(crashLogFile)
            .withFailMessage(
                "RUMS-5754: crash_log was deleted in finally block even though lastViewEvent " +
                    "is null — crash data is permanently lost before handleNdkCrashLog() " +
                    "has a chance to decide whether to send it."
            )
            .doesNotExist()

        // When – handleNdkCrash() is called (e.g. after RUM feature registers)
        testedHandler.handleNdkCrash(mockSdkCore)
        verify(mockExecutorService, org.mockito.kotlin.times(2)).execute(captureRunnable.capture())
        captureRunnable.secondValue.run()

        // Then – REPRODUCTION: the RUM feature SHOULD have received the crash event, but it does not.
        // This assertion FAILS on the buggy implementation, proving the crash is silently dropped.
        verify(mockRumFeatureScope).sendEvent(
            org.mockito.kotlin.argThat { event ->
                @Suppress("UNCHECKED_CAST")
                (event as? Map<String, Any?>)?.get("type") == "ndk_crash"
            }
        )
    }

    /**
     * RUMS-5754 – Test 2: Crash log is unconditionally deleted before the null-check gate.
     *
     * Prove that after [DatadogNdkCrashHandler.prepareData] completes with a null lastViewEvent,
     * [DatadogNdkCrashHandler.lastNdkCrashLog] is populated (crash was read) but the crash_log
     * file is already deleted from disk.
     *
     * This documents the ordering problem: the `finally` block in readCrashData() runs
     * clearCrashLog() BEFORE handleNdkCrashLog() has evaluated the null-check gate.
     * The crash data exists in memory but the on-disk file is gone — there is no recovery path.
     */
    @Test
    fun `M preserve crash log file W prepareData { null lastViewEvent, crash log must not be deleted before RUM event is sent }`() {
        // Given – simulate React Native: no prior RUM session
        testedHandler = DatadogNdkCrashHandler(
            tempDir,
            mockExecutorService,
            mockNdkCrashLogDeserializer,
            mockInternalLogger,
            lastRumViewEventProvider = { null } // React Native: no ViewEvent
        )

        fakeNdkCacheDir.mkdirs()
        val crashLogFile = File(fakeNdkCacheDir, DatadogNdkCrashHandler.CRASH_DATA_FILE_NAME)
        crashLogFile.writeText(fakeNdkCrashLog.toJson())
        whenever(mockNdkCrashLogDeserializer.deserialize(fakeNdkCrashLog.toJson())) doReturn fakeNdkCrashLog

        // When
        testedHandler.prepareData()
        verify(mockExecutorService).execute(captureRunnable.capture())
        captureRunnable.firstValue.run()

        // Then – crash was successfully read into memory
        assertThat(testedHandler.lastNdkCrashLog)
            .isEqualTo(fakeNdkCrashLog)

        // Then – REPRODUCTION: crash log file SHOULD still exist on disk so it can be
        // re-processed if sending fails or lastViewEvent arrives later.
        // This assertion FAILS on the buggy implementation, proving premature deletion.
        assertThat(crashLogFile)
            .withFailMessage(
                "RUMS-5754: crash_log file was deleted in the finally block of readCrashData() " +
                    "even though lastViewEvent is null. The crash data exists in memory " +
                    "(lastNdkCrashLog is non-null) but the on-disk file is gone — " +
                    "there is no recovery path if the process dies before handleNdkCrash() runs."
            )
            .exists()
    }

    /**
     * RUMS-5754 – Test 3: Full pipeline — NDK crash with no prior RUM session yields zero events.
     *
     * End-to-end integration of the bug: simulate a fresh React Native app install where
     * no lastViewEvent has been written. After the full pipeline runs (prepareData then
     * handleNdkCrash), the RUM feature MUST have received a crash event.
     *
     * This test FAILS on the current implementation because:
     * 1. readCrashData() sets lastRumViewEvent = null (provider returns null)
     * 2. clearCrashLog() deletes the crash_log in the finally block
     * 3. handleNdkCrashLog() sees lastViewEvent == null → skips sending the event
     * 4. Result: zero events sent, crash permanently lost
     */
    @Test
    fun `M send RUM event W full pipeline { fresh React Native install, no prior RUM session }`(
        forge: Forge
    ) {
        // Given – fresh install: no last_view_event file, crash_log file exists
        testedHandler = DatadogNdkCrashHandler(
            tempDir,
            mockExecutorService,
            mockNdkCrashLogDeserializer,
            mockInternalLogger,
            lastRumViewEventProvider = { null } // No RUM session ever started
        )

        fakeNdkCacheDir.mkdirs()
        File(fakeNdkCacheDir, DatadogNdkCrashHandler.CRASH_DATA_FILE_NAME)
            .writeText(fakeNdkCrashLog.toJson())
        whenever(mockNdkCrashLogDeserializer.deserialize(fakeNdkCrashLog.toJson())) doReturn fakeNdkCrashLog

        // When – SDK starts, prepareData() called first
        testedHandler.prepareData()
        verify(mockExecutorService).execute(captureRunnable.capture())
        captureRunnable.firstValue.run()

        // Then – at this point crash log is already deleted (the bug), and lastViewEvent is null

        // When – RUM feature registers, handleNdkCrash() called
        testedHandler.handleNdkCrash(mockSdkCore)
        verify(mockExecutorService, org.mockito.kotlin.times(2)).execute(captureRunnable.capture())
        captureRunnable.secondValue.run()

        // Then – REPRODUCTION: RUM feature MUST have received a crash event.
        // This assertion FAILS on the buggy implementation, proving the crash is silently dropped.
        verify(mockRumFeatureScope).sendEvent(
            org.mockito.kotlin.argThat { event ->
                @Suppress("UNCHECKED_CAST")
                (event as? Map<String, Any?>)?.let { map ->
                    map["type"] == "ndk_crash" &&
                        map["signalName"] == fakeNdkCrashLog.signalName
                } ?: false
            }
        )
    }

    // region Internal

    private class ViewEvent(val applicationId: String, val sessionId: String, val viewId: String) {
        fun toJson(): com.google.gson.JsonObject {
            return com.google.gson.JsonObject().apply {
                add("application", com.google.gson.JsonObject().apply { addProperty("id", applicationId) })
                add("session", com.google.gson.JsonObject().apply { addProperty("id", sessionId) })
                add("view", com.google.gson.JsonObject().apply { addProperty("id", viewId) })
            }
        }
    }

    private fun Forge.aFakeViewEvent(): ViewEvent = ViewEvent(
        applicationId = getForgery<UUID>().toString(),
        sessionId = getForgery<UUID>().toString(),
        viewId = getForgery<UUID>().toString()
    )

    // endregion
}
