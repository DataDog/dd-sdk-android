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

internal class DefaultSnapshotCompletionProcessor(
    private val rumContextProvider: RumContextProvider,
    private val recordWriter: RecordWriter,
    private val internalLogger: InternalLogger,
    private val wireMapper: CapturedTreeWireMapper = DefaultCapturedTreeWireMapper()
) : SnapshotCompletionProcessor {
    override fun process(capture: CompletedSnapshotCapture) {
        val rumContext = rumContextProvider.getRumContext()
        if (!rumContext.isValid() || rumContext.viewId != capture.snapshot.scope.value) {
            capture.generation.expire()
            return
        }

        when (val mapping = wireMapper.mapFullSnapshot(capture.snapshot)) {
            is CaptureWireMappingResult.Success -> {
                if (capture.generation.tryAccept()) {
                    recordWriter.write(
                        EnrichedRecord(
                            applicationId = rumContext.applicationId,
                            sessionId = rumContext.sessionId,
                            viewId = rumContext.viewId,
                            records = listOf(mapping.value)
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

    internal companion object {
        internal const val INVALID_SNAPSHOT_MESSAGE = "Dropping invalid completed composition snapshot"
        internal const val VALIDATION_ERROR_CODES_PROPERTY = "validation_error_codes"
        internal const val VALIDATION_FAILURE_COUNT_PROPERTY = "validation_failure_count"
    }
}
