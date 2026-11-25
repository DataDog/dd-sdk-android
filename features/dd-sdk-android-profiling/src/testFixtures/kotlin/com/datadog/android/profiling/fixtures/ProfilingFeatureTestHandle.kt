/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.fixtures

import android.os.Build
import androidx.annotation.RequiresApi
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.internal.profiling.ProfilerEvent
import com.datadog.android.internal.profiling.ProfilingRumContext
import com.datadog.android.profiling.ProfilingConfiguration
import com.datadog.android.profiling.internal.NoOpProfiler
import com.datadog.android.profiling.internal.ProfilingFeature
import com.datadog.android.profiling.internal.ProfilingStartReason
import com.datadog.android.profiling.internal.domain.ProfilingBatchMetadata
import com.datadog.android.profiling.internal.perfetto.PerfettoResult
import java.io.File

/**
 * Test handle around an internal [ProfilingFeature] for use from integration tests in
 * other modules. Holds a [NoOpProfiler] so no real Perfetto profiling is started, while
 * the surrounding cross-feature wiring (event receiver, context-update receiver,
 * scheduler bootstrap) runs exactly as in production.
 */
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
class ProfilingFeatureTestHandle internal constructor(
    private val feature: ProfilingFeature
) {

    val lastSeenRumSessionId: String? get() = feature.lastSeenRumSessionId

    val schedulerSessionId: String? get() = feature.continuousProfilingScheduler?.currentSessionId

    val isSchedulerSessionSampled: Boolean
        get() = feature.continuousProfilingScheduler?.currentSessionSampled ?: false

    /**
     * Writes [traceFile] as a manual profile through the feature's real writer, so the file read, the
     * compression and the metadata encoding all run as in production. The file is deleted afterwards.
     */
    fun writeManualProfile(traceFile: File) {
        feature.dataWriter.writeManualProfile(
            profilingResult = PerfettoResult(
                start = 0L,
                startReason = ProfilingStartReason.CONTINUOUS,
                end = 1L,
                resultFilePath = traceFile.absolutePath,
                profileTypes = emptyList()
            ),
            longTasks = emptyList(),
            anrEvents = emptyList(),
            // A manual profile without any RUM event is dropped, so it carries one vital
            vitalEvents = listOf(
                ProfilerEvent.RumVitalEvent(
                    id = "vital-id",
                    name = null,
                    type = ProfilerEvent.RumVitalEvent.Type.TTID,
                    startMs = 0L,
                    durationNs = 1L,
                    rumContext = ProfilingRumContext(
                        applicationId = "application-id",
                        sessionId = "session-id",
                        viewId = null,
                        viewName = null
                    )
                )
            )
        )
    }

    /** The attachments of a saved profile: the Perfetto bytes as stored and the RUM events JSON. */
    class SavedProfile(val perfettoBytes: ByteArray, val rumMobileEventsBytes: ByteArray)

    companion object {

        /**
         * Decodes the metadata bytes the writer attached to a profile event, or null if they are not a
         * profile blob.
         */
        fun decodeSavedProfile(eventMetadata: ByteArray): SavedProfile? {
            val metadata = ProfilingBatchMetadata.fromBytesOrNull(eventMetadata) ?: return null
            return SavedProfile(
                perfettoBytes = metadata.bytes.copyOfRange(
                    metadata.perfettoOffset,
                    metadata.perfettoOffset + metadata.perfettoLength
                ),
                rumMobileEventsBytes = metadata.bytes.copyOfRange(
                    metadata.rumMobileEventsOffset,
                    metadata.rumMobileEventsOffset + metadata.rumMobileEventsLength
                )
            )
        }

        /**
         * Build a [ProfilingFeatureTestHandle] backed by a [NoOpProfiler] and register the
         * feature with [sdkCore], mirroring the production `Profiling.enable` entry point.
         */
        @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
        fun create(
            sdkCore: FeatureSdkCore,
            configuration: ProfilingConfiguration = ProfilingConfiguration.DEFAULT
        ): ProfilingFeatureTestHandle {
            val feature = ProfilingFeature(
                sdkCore = sdkCore,
                configuration = configuration,
                profiler = NoOpProfiler()
            )
            sdkCore.registerFeature(feature)
            return ProfilingFeatureTestHandle(feature)
        }
    }
}
