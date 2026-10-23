/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.profiling.quota

/**
 * The reason provided by the profiling quota admission API.
 *
 * @property rawValue the value sent in RUM events.
 */
enum class QuotaReason(val rawValue: String) {
    /** Quota check passed. */
    QUOTA_OK("quota_ok"),

    /** The organization has exceeded its profiling quota. */
    QUOTA_EXCEEDED("quota_exceeded"),

    /** The organization has profiling disabled. */
    ORG_DISABLED("org_disabled"),

    /** The quota admission API is unavailable or not initialized. */
    BACKEND_UNAVAILABLE("backend_unavailable"),

    /** The quota reason is undefined. */
    UNDEFINED("undefined"),

    /** The quota check timed out on the client side. */
    TIMEOUT("timeout"),

    /** An API error occurred on the client side. */
    API_ERROR("api-error")
}
