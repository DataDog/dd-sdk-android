/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

/**
 * Internal event to pass the Time To Initial Display (TTID) value in nanoseconds.
 *
 * @param durationNs The TTID value in nanoseconds.
 * @param applicationId The Id of the application of RUM.
 * @param sessionId The Id of the RUM session where TTID is captured
 * @param vitalId The Id of the TTID vital event
 * @param viewId The Id of the view where TTID is captured
 * @param viewName The name of the view where TTID is captured
 */
data class TTIDEvent(
    val durationNs: Long,
    val applicationId: String,
    val sessionId: String,
    val vitalId: String,
    val viewId: String?,
    val viewName: String?
)
