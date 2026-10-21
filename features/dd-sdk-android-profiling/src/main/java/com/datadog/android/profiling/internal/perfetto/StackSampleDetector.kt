/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.perfetto

import com.datadog.android.api.InternalLogger
import java.io.File

/**
 * Detects stack sampling traces which hold no callstack sample.
 */
internal interface StackSampleDetector {

    /**
     * @param traceFile the file of a Perfetto trace.
     * @param internalLogger the logger to report a trace file which can't be read.
     * @return true if the whole trace could be read and none of its `perf_sample` packets has a callstack. A
     * trace which can't be read (missing file, malformed or compressed data) returns false, so that a
     * profile is never reported as empty without certainty.
     */
    fun hasNoStackSamples(traceFile: File, internalLogger: InternalLogger): Boolean
}
