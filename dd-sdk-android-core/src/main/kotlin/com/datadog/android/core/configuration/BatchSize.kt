/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.configuration

/**
 * Defines the policy when batching data together.
 * Smaller batches will means smaller but more network requests,
 * whereas larger batches will mean fewer but larger network requests.
 */
@Suppress("MagicNumber")
enum class BatchSize(
    internal val windowDurationMs: Long
) {

    /** Prefer small batches. **/
    SMALL(5000L),

    /** Prefer medium sized batches. **/
    MEDIUM(15000L),

    /** Prefer large batches. **/
    LARGE(60000L);
}
