/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.domain.metrics

internal fun interface StatsWriter {
    /**
     * Writes the given stat buckets to storage.
     *
     * @param statBuckets the buckets to write.
     * @param forced whether this flush was forced (SDK teardown) rather than periodic.
     */
    fun write(statBuckets: List<ClientStatsBucket>, forced: Boolean)
}
