/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal.model

import com.datadog.android.DatadogSite

/**
 * Internal context containing Datadog configuration needed for feature flag requests.
 *
 * @param applicationId The Datadog application ID. May be null when the SDK is not fully initialized
 *                      or when running in certain test environments where app ID is not required.
 * @param clientToken The client token for authenticating requests to Datadog
 * @param site The Datadog site (e.g., US1, EU1) for routing requests
 * @param env The environment name (e.g., prod, staging) for context
 */
internal data class FlagsContext(
    val applicationId: String?,
    val clientToken: String,
    val site: DatadogSite,
    val env: String
)
