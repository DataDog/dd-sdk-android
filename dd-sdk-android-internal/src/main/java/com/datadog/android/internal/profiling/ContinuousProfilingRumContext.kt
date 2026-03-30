/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.profiling

/**
 * RUM context attached to continuous profiling events.
 *
 * @param applicationId The RUM application ID.
 * @param sessionId The ID of the active RUM session.
 * @param viewId The ID of the active RUM view, or null if unavailable.
 * @param viewName The name of the active RUM view, or null if unavailable.
 */
// TODO RUM-15334: Commonize ContinuousProfilingRumContext & TTIDRumContext
data class ContinuousProfilingRumContext(
    val applicationId: String,
    val sessionId: String,
    val viewId: String?,
    val viewName: String?
)
