/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.single

import com.datadog.android.core.internal.persistence.file.FileOrchestrator
import java.io.File

internal class SingleFileOrchestrator(
    private val file: File
) : FileOrchestrator {

    // region FileOrchestrator

    override fun getWritableFile(dataSize: Int): File? {
        return file
    }

    override fun getReadableFile(excludeFiles: Set<File>): File? {
        return if (file in excludeFiles) {
            null
        } else {
            file
        }
    }

    override fun getAllFiles(): List<File> {
        return listOf(file)
    }

    override fun getRootDir(): File? {
        return null
    }

    // endregion
}
