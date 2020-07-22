/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.plugin

/**
 * Provides information on the RUM feature context.
 * @param applicationId the RUM Application ID provided when initialising the SDK.
 * @param sessionId the unique ID of the current RUM Session.
 * @param viewId the unique ID of the current tracked RUM View.
 */
data class DatadogRumContext(
    val applicationId: String? = null,
    val sessionId: String? = null,
    val viewId: String? = null
)
