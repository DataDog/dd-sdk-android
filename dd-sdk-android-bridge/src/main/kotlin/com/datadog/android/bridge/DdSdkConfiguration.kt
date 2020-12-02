/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.bridge

/**
 * A configuration object to initialize Datadog's features.
 * @param clientToken A valid Datadog client token.
 * @param env The application’s environment, for example: prod, pre-prod, staging, etc.
 * @param applicationId The RUM application ID.
 */
data class DdSdkConfiguration(
    val clientToken: String,
    val env: String,
    val applicationId: String? = null
)
