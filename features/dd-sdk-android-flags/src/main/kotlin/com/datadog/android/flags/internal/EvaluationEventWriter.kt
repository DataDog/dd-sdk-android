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
 * Implementations must be thread-safe and handle concurrent calls correctly.
 * Evaluations are written as individual records and then batched together at upload time.
 * Managing these records/uploads is the responsibility of the SDK Core's upload scheduler.
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
     *
     * @param event the evaluation event to write to storage
     */
    fun write(event: BatchedFlagEvaluations.FlagEvaluation)

    /**
     * Writes a list of flag evaluation events to persistent storage.
     *
     * The events are serialized and stored as individual records. Multiple events
     * will be collected and batched together at upload time.
     *
     * Thread Safety: This method must be thread-safe and handle concurrent calls correctly.
     *
     * @param events the evaluation events to write to storage
     */
    fun writeAll(events: List<BatchedFlagEvaluations.FlagEvaluation>)
}
