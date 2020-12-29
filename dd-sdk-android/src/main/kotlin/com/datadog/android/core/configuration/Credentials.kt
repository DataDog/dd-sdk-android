/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.configuration

/**
 * Provides the credentials and identifying information for all the data tracked with the SDK.
 * @param clientToken your API key of type Client Token
 * @param envName the environment name that will be sent with each event. This can be used to
 * filter your events on different environments (e.g.: "staging" vs. "production").
 * @param variant the variant of your application, which should be the value from your
 * `BuildConfig.FLAVOR` constant
 * @param rumApplicationId your applicationId for RUM events
 * @param serviceName the service name (if set to null, it'll be set to your application's
 * package name, e.g.: com.example.android)
 */
data class Credentials(
    val clientToken: String,
    val envName: String,
    val variant: String,
    val rumApplicationId: String?,
    val serviceName: String? = null
)
