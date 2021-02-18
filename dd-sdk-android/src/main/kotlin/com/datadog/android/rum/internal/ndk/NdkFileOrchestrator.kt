/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.ndk

import com.datadog.android.core.internal.data.Orchestrator
import java.io.File

internal class NdkFileOrchestrator(val file: File) : Orchestrator {
    override fun getAllFiles(): Array<out File> {
        return emptyArray()
    }

    override fun getWritableFile(itemSize: Int): File? {
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.createNewFile()
        }
        return file
    }

    override fun getReadableFile(excludeFileNames: Set<String>): File? {
        return null
    }

    override fun reset() {
        // NoOp
    }
}
