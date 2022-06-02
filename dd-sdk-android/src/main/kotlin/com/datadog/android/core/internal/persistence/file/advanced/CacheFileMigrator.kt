/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.advanced

import androidx.annotation.WorkerThread
import com.datadog.android.core.internal.persistence.file.ChunkedFileHandler
import com.datadog.android.core.internal.persistence.file.FileOrchestrator
import com.datadog.android.log.Logger
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException

internal class CacheFileMigrator(
    private val fileHandler: ChunkedFileHandler,
    private val executorService: ExecutorService,
    private val internalLogger: Logger
) : DataMigrator<Boolean> {

    // region DataMigrator

    @WorkerThread
    override fun migrateData(
        previousState: Boolean?,
        previousFileOrchestrator: FileOrchestrator,
        newState: Boolean,
        newFileOrchestrator: FileOrchestrator
    ) {
        if (!newState) return

        val sourceDir = previousFileOrchestrator.getRootDir()
        val cacheDir = newFileOrchestrator.getRootDir()

        val moveOperation = MoveDataMigrationOperation(
            sourceDir,
            cacheDir,
            fileHandler,
            internalLogger
        )
        val deleteOperation = WipeDataMigrationOperation(
            sourceDir,
            fileHandler,
            internalLogger
        )

        try {
            @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
            executorService.submit(moveOperation)
        } catch (e: RejectedExecutionException) {
            internalLogger.e(DataMigrator.ERROR_REJECTED, e)
        }
        try {
            @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
            executorService.submit(deleteOperation)
        } catch (e: RejectedExecutionException) {
            internalLogger.e(DataMigrator.ERROR_REJECTED, e)
        }
    }

    // endregion
}
