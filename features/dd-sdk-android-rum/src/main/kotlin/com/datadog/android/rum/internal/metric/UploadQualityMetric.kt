/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.metric

internal enum class UploadQualityMetric(val key: String) {
    UPLOAD_METRIC_KEY("upload_quality"),
    FAILURE_COUNT_KEY("failure_count"),
    BLOCKERS_COUNT_KEY("blockers_count"),
    BLOCKER_KEY("blocker"),
    CYCLE_COUNT_KEY("cycle_count")
}
