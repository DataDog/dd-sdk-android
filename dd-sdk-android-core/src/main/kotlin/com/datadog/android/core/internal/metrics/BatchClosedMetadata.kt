/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.metrics

internal data class BatchClosedMetadata(
    internal val lastTimeWasUsedInMs: Long,
    internal val forcedNew: Boolean,
    internal val eventsCount: Long
)
