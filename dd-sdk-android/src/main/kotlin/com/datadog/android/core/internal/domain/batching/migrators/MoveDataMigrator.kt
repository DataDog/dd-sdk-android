/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.domain.batching.migrators

import com.datadog.android.core.internal.data.file.FileHandler
import com.datadog.android.core.internal.utils.retryWithDelay
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

internal class MoveDataMigrator(
    internal val pendingFolderPath: String,
    internal val approvedFolderPath: String,
    private val executorService: ExecutorService,
    private val fileHandler: FileHandler = FileHandler()
) : BatchedDataMigrator {
    override fun migrateData() {
        executorService.submit {
            retryWithDelay(
                {
                    fileHandler.moveFiles(
                        File(pendingFolderPath),
                        File(approvedFolderPath)
                    )
                },
                MAX_RETRIES,
                RETRY_DELAY_IN_NANOS
            )
        }
    }

    companion object {
        private const val MAX_RETRIES = 3
        private val RETRY_DELAY_IN_NANOS = TimeUnit.SECONDS.toNanos(1)
    }
}
