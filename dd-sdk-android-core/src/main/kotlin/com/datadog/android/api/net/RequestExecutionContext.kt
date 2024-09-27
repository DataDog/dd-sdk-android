/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.api.net

/**
 * Provides information about the request execution context such as the number of attempts made to
 * execute the request in case of a retry or the response code of the previous request failure code.
 * @param attemptNumber the number of this attempt for a specific batch.
 * It'll be 1 for the first attempt, and will be incremented each time an upload for the same batch is retried.
 * This takes into account the initial request and all the retries.
 * @param previousResponseCode the response code of the previous request failure code in case of a retry.
 * In case of the initial request, this value will be null.
 */
data class RequestExecutionContext(
    val attemptNumber: Int = 0,
    val previousResponseCode: Int? = null
)
