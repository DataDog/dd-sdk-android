/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.core.internal.data.file

import java.io.File
import java.io.FileFilter

internal class FileFilter : FileFilter {

    override fun accept(file: File?): Boolean {
        return file != null &&
            file.isFile &&
            file.name.matches(logFileNameRegex)
    }

    companion object {
        private val logFileNameRegex = Regex("\\d+")
    }
}
