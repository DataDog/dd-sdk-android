/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.single

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.persistence.file.FileOrchestrator
import com.datadog.android.core.internal.persistence.file.mkdirsSafe
import java.io.File

internal class SingleFileOrchestrator(
    private val file: File,
    private val internalLogger: InternalLogger
) : FileOrchestrator {

    // region FileOrchestrator

    @WorkerThread
    override fun getWritableFile(): File? {
        file.parentFile?.mkdirsSafe(internalLogger)
        return file
    }

    @WorkerThread
    override fun getReadableFile(excludeFiles: Set<File>): File? {
        file.parentFile?.mkdirsSafe(internalLogger)
        return if (file in excludeFiles) {
            null
        } else {
            file
        }
    }

    @WorkerThread
    override fun getAllFiles(): List<File> {
        file.parentFile?.mkdirsSafe(internalLogger)
        return listOf(file)
    }

    @WorkerThread
    override fun getRootDir(): File? {
        return null
    }

    @WorkerThread
    override fun getFlushableFiles(): List<File> {
        return getAllFiles()
    }

    @WorkerThread
    override fun getMetadataFile(file: File): File? {
        return null
    }

    override fun getRootDirName(): String? {
        return file.parentFile?.nameWithoutExtension
    }

    // single file orchestrator has a single file, so this is essentially a noop implementation
    override fun decrementAndGetPendingFilesCount(): Int {
        return 0
    }

    // endregion
}
