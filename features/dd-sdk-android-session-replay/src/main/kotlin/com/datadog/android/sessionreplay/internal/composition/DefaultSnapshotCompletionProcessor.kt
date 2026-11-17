/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.internal.processor.EnrichedRecord
import com.datadog.android.sessionreplay.internal.storage.RecordWriter
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import com.datadog.android.sessionreplay.internal.utils.SessionReplayRumContext
import com.datadog.android.sessionreplay.model.MobileSegment

/**
 * Bundles composition wire-mapping with the view-lifecycle records the player needs around it -
 * mirrors legacy `RecordedDataProcessor`'s new-view handling: every genuinely new RUM view opens
 * with a [MobileSegment.MobileRecord.MetaRecord] (viewport size) and
 * [MobileSegment.MobileRecord.FocusRecord] before its first wireframe content, and the previous
 * view (if any) is closed with a [MobileSegment.MobileRecord.ViewEndRecord]. Without a Meta
 * record, a player following this rrweb-derived protocol has no viewport to render into at all -
 * this is not an optional enrichment, every view's very first record set depends on it. When the
 * window size changes within the same view (e.g. a rotation), a
 * [MobileSegment.MobileIncrementalData.ViewportResizeData] record precedes the snapshot, as legacy
 * does for orientation changes.
 */
internal class DefaultSnapshotCompletionProcessor(
    private val rumContextProvider: RumContextProvider,
    private val recordWriter: RecordWriter,
    private val internalLogger: InternalLogger,
    private val wireMapper: CapturedTreeWireMapper = DefaultCapturedTreeWireMapper()
) : SnapshotCompletionProcessor {

    // Only ever read/written from SnapshotCompletionQueue's single draining thread - same
    // single-threaded-processor assumption legacy RecordedDataProcessor's own prevRumContext relies on.
    private var lastViewContext: SessionReplayRumContext? = null
    private var lastViewportSize: CapturedViewportSize? = null

    override fun process(capture: CompletedSnapshotCapture) {
        val rumContext = rumContextProvider.getRumContext()
        if (!rumContext.isValid() || rumContext.viewId != capture.snapshot.scope.value) {
            capture.generation.expire()
            return
        }

        when (val mapping = wireMapper.mapFullSnapshot(capture.snapshot)) {
            is CaptureWireMappingResult.Success -> {
                if (capture.generation.tryAccept()) {
                    writeViewEndRecordIfViewChanged(rumContext, capture.snapshot.timestamp)
                    recordWriter.write(
                        EnrichedRecord(
                            applicationId = rumContext.applicationId,
                            sessionId = rumContext.sessionId,
                            viewId = rumContext.viewId,
                            records = viewLifecycleRecords(rumContext, capture.snapshot) + mapping.value
                        )
                    )
                }
            }

            is CaptureWireMappingResult.Invalid -> {
                capture.generation.expire()
                // Failure identities and details are per-view, so they are deliberately left out:
                // the message stays constant and only the bounded error codes are attached.
                val errorCodes = mapping.failures.map { it.code }.distinct().sorted()
                internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.TELEMETRY,
                    { INVALID_SNAPSHOT_MESSAGE },
                    additionalProperties = mapOf(
                        VALIDATION_ERROR_CODES_PROPERTY to errorCodes.map { it.name },
                        VALIDATION_FAILURE_COUNT_PROPERTY to mapping.failures.size
                    )
                )
            }
        }
    }

    private fun writeViewEndRecordIfViewChanged(rumContext: SessionReplayRumContext, timestamp: Long) {
        val previous = lastViewContext ?: return
        if (previous.isSameViewAs(rumContext)) return
        recordWriter.write(
            EnrichedRecord(
                applicationId = previous.applicationId,
                sessionId = previous.sessionId,
                viewId = previous.viewId,
                records = listOf(MobileSegment.MobileRecord.ViewEndRecord(timestamp))
            )
        )
    }

    private fun viewLifecycleRecords(
        rumContext: SessionReplayRumContext,
        snapshot: CapturedFullSnapshot
    ): List<MobileSegment.MobileRecord> {
        val viewportSize = snapshot.viewportSize
        return if (lastViewContext?.isSameViewAs(rumContext) != true) {
            lastViewContext = rumContext
            lastViewportSize = viewportSize
            viewOpeningRecords(snapshot.timestamp, viewportSize)
        } else if (viewportSize != lastViewportSize) {
            lastViewportSize = viewportSize
            listOf(viewportResizeRecord(snapshot.timestamp, viewportSize))
        } else {
            emptyList()
        }
    }

    private fun viewOpeningRecords(
        timestamp: Long,
        viewportSize: CapturedViewportSize
    ): List<MobileSegment.MobileRecord> {
        return listOf(
            MobileSegment.MobileRecord.MetaRecord(
                timestamp = timestamp,
                data = MobileSegment.Data1(width = viewportSize.width, height = viewportSize.height)
            ),
            MobileSegment.MobileRecord.FocusRecord(
                timestamp = timestamp,
                data = MobileSegment.Data2(hasFocus = true)
            )
        )
    }

    private fun viewportResizeRecord(
        timestamp: Long,
        viewportSize: CapturedViewportSize
    ): MobileSegment.MobileRecord.MobileIncrementalSnapshotRecord {
        return MobileSegment.MobileRecord.MobileIncrementalSnapshotRecord(
            timestamp = timestamp,
            data = MobileSegment.MobileIncrementalData.ViewportResizeData(
                width = viewportSize.width,
                height = viewportSize.height
            )
        )
    }

    // Same identity check as legacy RecordedDataProcessor.isNewView.
    private fun SessionReplayRumContext.isSameViewAs(other: SessionReplayRumContext): Boolean =
        applicationId == other.applicationId && sessionId == other.sessionId && viewId == other.viewId

    internal companion object {
        internal const val INVALID_SNAPSHOT_MESSAGE = "Dropping invalid completed composition snapshot"
        internal const val VALIDATION_ERROR_CODES_PROPERTY = "validation_error_codes"
        internal const val VALIDATION_FAILURE_COUNT_PROPERTY = "validation_failure_count"
    }
}
