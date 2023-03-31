/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.advanced

import androidx.annotation.WorkerThread
import com.datadog.android.core.internal.persistence.file.FileMover
import com.datadog.android.core.internal.utils.retryWithDelay
import com.datadog.android.v2.api.InternalLogger
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * A [DataMigrationOperation] that moves all the files in the `fromDir` directory
 * to the `toDir` directory.
 */
internal class MoveDataMigrationOperation(
    internal val fromDir: File?,
    internal val toDir: File?,
    internal val fileMover: FileMover,
    internal val internalLogger: InternalLogger
) : DataMigrationOperation {

    @WorkerThread
    override fun run() {
        if (fromDir == null) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                WARN_NULL_SOURCE_DIR
            )
        } else if (toDir == null) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                WARN_NULL_DEST_DIR
            )
        } else {
            retryWithDelay(MAX_RETRY, RETRY_DELAY_NS, internalLogger) {
                fileMover.moveFiles(fromDir, toDir)
            }
        }
    }

    companion object {
        internal const val WARN_NULL_SOURCE_DIR = "Can't move data from a null directory"
        internal const val WARN_NULL_DEST_DIR = "Can't move data to a null directory"

        private const val MAX_RETRY = 3
        private val RETRY_DELAY_NS = TimeUnit.MILLISECONDS.toNanos(500)
    }
}
