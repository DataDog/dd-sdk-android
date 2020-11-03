/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.domain

internal data class FilePersistenceConfig(
    val recentDelayMs: Long = MAX_DELAY_BETWEEN_MESSAGES_MS,
    val maxBatchSize: Long = MAX_BATCH_SIZE,
    val maxItemsPerBatch: Int = MAX_ITEMS_PER_BATCH,
    val oldFileThreshold: Long = OLD_FILE_THRESHOLD,
    val maxDiskSpace: Long = MAX_DISK_SPACE
) {
    companion object {
        internal const val MAX_BATCH_SIZE: Long = 4 * 1024 * 1024 // 4 MB
        internal const val MAX_ITEMS_PER_BATCH: Int = 500
        internal const val OLD_FILE_THRESHOLD: Long = 18L * 60L * 60L * 1000L // 18 hours
        internal const val MAX_DISK_SPACE: Long = 128 * MAX_BATCH_SIZE // 512 MB
        internal const val MAX_DELAY_BETWEEN_MESSAGES_MS = 5000L
    }
}
