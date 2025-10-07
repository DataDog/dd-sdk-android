/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal.repository.net

import com.datadog.android.flags.featureflags.internal.model.FlagsContext
import com.datadog.android.flags.featureflags.model.EvaluationContext
import okhttp3.Request

/**
 * Factory for creating HTTP requests to fetch precomputed flag assignments.
 *
 * This factory follows the RequestFactory pattern used throughout the SDK,
 * separating request construction from execution. It builds OkHttp Request objects
 * that can be executed by a downloader/executor component.
 */
internal interface PrecomputedAssignmentsRequestFactory {

    /**
     * Creates an OkHttp Request for fetching precomputed flag assignments.
     *
     * This method constructs a complete HTTP POST request including:
     * - URL (endpoint) determination based on site or custom configuration
     * - Headers (authentication, content-type, etc.)
     * - Request body (evaluation context data)
     *
     * @param context The evaluation context containing targeting key and custom attributes
     *                for flag evaluation
     * @param flagsContext The flags context containing SDK configuration, authentication,
     *                     site information, and custom endpoint settings
     * @return A fully-formed OkHttp Request ready for execution, or null if the request
     *         cannot be constructed (e.g., invalid endpoint, JSON serialization error)
     */
    fun create(context: EvaluationContext, flagsContext: FlagsContext): Request?
}
