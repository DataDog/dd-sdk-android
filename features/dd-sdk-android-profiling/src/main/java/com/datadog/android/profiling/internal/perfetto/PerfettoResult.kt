/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.perfetto

/**
 * Result of a profiling request made through [androidx.core.os.requestProfiling].
 *
 * @param start the start time of the profiling in milliseconds since epoch.
 * @param end the end time of the profiling in milliseconds since epoch.
 * @param resultFilePath the path to the file containing the profiling result.
 */
internal data class PerfettoResult(
    val errorCode: Int,
    val errorMessage: String?,
    val start: Long,
    val end: Long,
    val resultFilePath: String,
    val fileSize: Long
)
