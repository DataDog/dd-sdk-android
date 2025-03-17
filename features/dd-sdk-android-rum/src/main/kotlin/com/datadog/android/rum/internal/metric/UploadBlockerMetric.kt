/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.metric

internal enum class UploadBlockerMetric(val key: String) {
    METRICS_KEY("[Mobile Metric] Batch Blocked"),
    TRACK_KEY("track"),
    BATCH_BLOCKED_KEY("batch blocked"),
    FAILURE_KEY("failure"),
    BLOCKERS_KEY("blockers"),
    UPLOAD_DELAY_KEY("upload_delay")
}
