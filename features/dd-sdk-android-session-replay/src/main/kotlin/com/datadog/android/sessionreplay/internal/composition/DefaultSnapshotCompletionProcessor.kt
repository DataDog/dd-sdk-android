/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.internal.async.DataQueueHandler
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
 * this is not an optional enrichment, every view's very first record set depends on it.
 *
 * Also drains [resourceDataQueueHandler] once per processed capture, mirroring the legacy
 * pipeline's own per-draw-cycle call to the same [DataQueueHandler.tryToConsumeItems] (see
 * `WindowsOnDrawListener`/`RecorderWindowCallback`). Resource bytes for any pixel capture this
 * generation resolved (see `PixelFallbackSnapshotProcessor`/`ResourceResolver`) are queued in
 * memory by [ResourceItemCreationHandler][com.datadog.android.sessionreplay.internal.recorder.resources.ResourceItemCreationHandler]
 * well before this point, but never actually written to storage or uploaded until something
 * calls [DataQueueHandler.tryToConsumeItems] - without a caller, those items sat in memory
 * forever, so every composition-tree pixel capture's `resourceId` referenced a resource that had
 * never actually been persisted anywhere.
 */
internal class DefaultSnapshotCompletionProcessor(
    private val rumContextProvider: RumContextProvider,
    private val recordWriter: RecordWriter,
    private val internalLogger: InternalLogger,
    private val wireMapper: CapturedTreeWireMapper = DefaultCapturedTreeWireMapper(),
    private val resourceDataQueueHandler: DataQueueHandler? = null
) : SnapshotCompletionProcessor {

    // Only ever read/written from SnapshotCompletionQueue's single draining thread - same
    // single-threaded-processor assumption legacy RecordedDataProcessor's own prevRumContext relies on.
    private var lastViewContext: SessionReplayRumContext? = null

    override fun process(capture: CompletedSnapshotCapture) {
        // Whatever resources this generation's pixel captures resolved were already queued
        // upstream, in PixelFallbackSnapshotProcessor, before this callback ever fires -
        // unconditional and first, so it runs regardless of which branch below this capture
        // ends up taking (including the early-return/invalid-context guard).
        resourceDataQueueHandler?.tryToConsumeItems()

        val rumContext = rumContextProvider.getRumContext()
        if (!rumContext.isValid() || rumContext.viewId != capture.snapshot.scope.value) {
            capture.generation.expire()
            return
        }

        when (val mapping = wireMapper.mapFullSnapshot(capture.snapshot)) {
            is CaptureWireMappingResult.Success -> {
                if (capture.generation.tryAccept()) {
                    writeViewEndRecordIfViewChanged(rumContext, capture.snapshot.timestamp)
                    val records = mutableListOf<MobileSegment.MobileRecord>()
                    if (lastViewContext?.viewId != rumContext.viewId) {
                        records += viewOpeningRecords(capture)
                        lastViewContext = rumContext
                    }
                    records += mapping.value
                    recordWriter.write(
                        EnrichedRecord(
                            applicationId = rumContext.applicationId,
                            sessionId = rumContext.sessionId,
                            viewId = rumContext.viewId,
                            records = records
                        )
                    )
                }
            }

            is CaptureWireMappingResult.Invalid -> {
                capture.generation.expire()
                // Failure identities and details are per-view, so they are deliberately left out:
                // the message stays constant and only the bounded error codes are attached.
                val errorCodes = mapping.failures.map(CaptureValidationFailure::code).distinct().sorted()
                internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.TELEMETRY,
                    { INVALID_SNAPSHOT_MESSAGE },
                    additionalProperties = mapOf(
                        VALIDATION_ERROR_CODES_PROPERTY to errorCodes.map(Enum<*>::name),
                        VALIDATION_FAILURE_COUNT_PROPERTY to mapping.failures.size
                    )
                )
            }
        }
    }

    private fun writeViewEndRecordIfViewChanged(rumContext: SessionReplayRumContext, timestamp: Long) {
        val previous = lastViewContext ?: return
        if (previous.viewId == rumContext.viewId) return
        recordWriter.write(
            EnrichedRecord(
                applicationId = previous.applicationId,
                sessionId = previous.sessionId,
                viewId = previous.viewId,
                records = listOf(MobileSegment.MobileRecord.ViewEndRecord(timestamp))
            )
        )
    }

    private fun viewOpeningRecords(capture: CompletedSnapshotCapture): List<MobileSegment.MobileRecord> {
        val timestamp = capture.snapshot.timestamp
        val bounds = capture.snapshot.root?.bounds
        return listOf(
            MobileSegment.MobileRecord.MetaRecord(
                timestamp = timestamp,
                data = MobileSegment.Data1(width = bounds?.width ?: 0L, height = bounds?.height ?: 0L)
            ),
            MobileSegment.MobileRecord.FocusRecord(
                timestamp = timestamp,
                data = MobileSegment.Data2(hasFocus = true)
            )
        )
    }

    internal companion object {
        internal const val INVALID_SNAPSHOT_MESSAGE = "Dropping invalid completed composition snapshot"
        internal const val VALIDATION_ERROR_CODES_PROPERTY = "validation_error_codes"
        internal const val VALIDATION_FAILURE_COUNT_PROPERTY = "validation_failure_count"
    }
}
