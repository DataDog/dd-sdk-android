/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.advanced

import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.log.Logger
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException

internal class ScheduledWriter<T : Any>(
    internal val delegateWriter: DataWriter<T>,
    internal val executorService: ExecutorService,
    private val internalLogger: Logger
) : DataWriter<T> {

    // region DataWriter

    override fun write(element: T) {
        try {
            executorService.submit {
                delegateWriter.write(element)
            }
        } catch (e: RejectedExecutionException) {
            internalLogger.e(ERROR_REJECTED, e)
        }
    }

    override fun write(data: List<T>) {
        try {
            executorService.submit {
                delegateWriter.write(data)
            }
        } catch (e: RejectedExecutionException) {
            internalLogger.e(ERROR_REJECTED, e)
        }
    }

    // endregion

    companion object {
        internal const val ERROR_REJECTED = "Unable to schedule writing on the executor"
    }
}
