/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.anr

import com.datadog.android.internal.profiling.ProfilingAnrDetectedEvent

internal interface ProfilingTriggerListener {
    fun onAnrDetected(event: ProfilingAnrDetectedEvent)

    fun onOutOfMemoryDetected(detectedAtMs: Long, resultFilePath: String)

    fun onMemoryAnomalyDetected(detectedAtMs: Long, resultFilePath: String)
}
