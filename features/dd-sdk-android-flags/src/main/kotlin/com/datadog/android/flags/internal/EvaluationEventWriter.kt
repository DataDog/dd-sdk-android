/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.android.flags.model.BatchedFlagEvaluations
import com.datadog.tools.annotation.NoOpImplementation

/**
 * Writer interface for persisting evaluation events to storage.
 *
 * Storage Model:
 * - Individual [BatchedFlagEvaluations.FlagEvaluation] objects are written to storage
 * - Each evaluation is stored as a separate record (not batched at write time)
 * - At upload time, the SDK Core collects all stored evaluations and wraps them
 *   in a single [BatchedFlagEvaluations] payload with top-level context
 *
 * Batching Strategy:
 * - Write: Individual records → Storage
 * - Upload: Storage → Batch assembly → Network request
 * - This allows flexible batch sizing and fresh context at upload time
 *
 * Thread Safety Requirements:
 * - Implementations MUST be thread-safe and handle concurrent write() calls correctly
 * - write() may be called concurrently from EvaluationEventsProcessor.flush()
 * - The atomic flush flag in EvaluationEventsProcessor ensures only one flush
 *   operation occurs at a time, but implementations should still be thread-safe
 *   as write() is called from background threads
 *
 * Concurrency Model:
 * - Single flush at a time (via AtomicBoolean in processor)
 * - But flush writes multiple events sequentially
 * - SDK Core storage has its own internal thread-safety
 * - Implementations should use synchronized blocks if needed
 *
 * Example Flow:
 * 1. EvaluationEventsProcessor aggregates evaluations in memory
 * 2. flush() is triggered (time/size/shutdown)
 * 3. Processor converts aggregations to FlagEvaluation objects
 * 4. write() is called for each evaluation (sequential within flush)
 * 5. Each evaluation stored as individual JSON record
 * 6. SDK Core's upload scheduler reads batch of records
 * 7. EvaluationsRequestFactory wraps all in BatchedFlagEvaluations
 * 8. Single HTTP request to /api/v2/flagevaluations
 *
 * @see EvaluationEventsProcessor
 * @see com.datadog.android.flags.internal.net.EvaluationsRequestFactory
 */
@NoOpImplementation
internal interface EvaluationEventWriter {
    /**
     * Writes a single flag evaluation event to persistent storage.
     *
     * The event is serialized and stored as an individual record. Multiple events
     * will be collected and batched together at upload time.
     *
     * Thread Safety: This method must be thread-safe and handle concurrent calls correctly.
     * However, the EvaluationEventsProcessor atomic flush flag ensures only one flush
     * (and thus one sequence of write calls) occurs at a time.
     *
     * @param event the evaluation event to write to storage
     */
    fun write(event: BatchedFlagEvaluations.FlagEvaluation)
}
