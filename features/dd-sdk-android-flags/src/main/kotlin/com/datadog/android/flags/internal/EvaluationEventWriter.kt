/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.android.flags.model.BatchedFlagEvaluations
import com.datadog.tools.annotation.NoOpImplementation

/**
 * Writer interface for persisting evaluation events.
 */
@NoOpImplementation
internal interface EvaluationEventWriter {
    /**
     * Writes an evaluation event.
     *
     * @param event the evaluation event to write
     */
    fun write(event: BatchedFlagEvaluations.FlagEvaluation)
}
