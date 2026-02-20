/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.advanced

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.generated.DdSdkAndroidCoreLogger
import com.datadog.android.core.internal.persistence.file.FileMover
import com.datadog.android.core.internal.utils.retryWithDelay
import com.datadog.android.internal.time.TimeProvider
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * A [DataMigrationOperation] that delete all the files in the `targetDir` directory.
 */
internal class WipeDataMigrationOperation(
    internal val targetDir: File?,
    internal val fileMover: FileMover,
    internal val internalLogger: InternalLogger,
    internal val timeProvider: TimeProvider
) : DataMigrationOperation {

    @WorkerThread
    override fun run() {
        if (targetDir == null) {
            DdSdkAndroidCoreLogger(internalLogger).logWarnNullDir()
        } else {
            retryWithDelay(MAX_RETRY, RETRY_DELAY_NS, internalLogger, timeProvider) {
                fileMover.delete(targetDir)
            }
        }
    }

    companion object {
        private const val MAX_RETRY = 3
        private val RETRY_DELAY_NS = TimeUnit.MILLISECONDS.toNanos(500)
    }
}
