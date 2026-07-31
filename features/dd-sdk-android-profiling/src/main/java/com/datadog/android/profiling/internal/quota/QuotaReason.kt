/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.quota

internal enum class QuotaReason(val rawValue: String) {
    QUOTA_OK("quota_ok"),
    QUOTA_EXCEEDED("quota_exceeded"),
    ORG_DISABLED("org_disabled"),
    BACKEND_UNAVAILABLE("backend_unavailable"),
    UNDEFINED("undefined"),
    TIMEOUT("timeout"),
    API_ERROR("api-error")
}
