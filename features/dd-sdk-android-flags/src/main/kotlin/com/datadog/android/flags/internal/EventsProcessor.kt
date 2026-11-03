/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.UnparsedFlag
import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation
internal interface EventsProcessor {
    /**
     * Records the exposure and writes it when it was not recorded yet.
     * @return true if the same exposure was already recorded for this subject.
     */
    fun processEvent(flagKey: String, context: EvaluationContext, data: UnparsedFlag): Boolean

    /** @return true if the same exposure is recorded for this subject. Records nothing. */
    fun isRecorded(flagKey: String, context: EvaluationContext, data: UnparsedFlag): Boolean
}
