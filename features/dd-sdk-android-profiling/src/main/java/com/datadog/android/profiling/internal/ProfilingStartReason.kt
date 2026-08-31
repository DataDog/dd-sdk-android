/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

internal enum class ProfilingStartReason(val value: String) {

    APPLICATION_LAUNCH("application_launch"),

    RUM_OPERATION("rum_operation"),

    CONTINUOUS("continuous"),

    OUT_OF_MEMORY("out_of_memory"),

    MEMORY_ANOMALY("memory_anomaly"),

    UNKNOWN("unknown")
}
