/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file

import androidx.annotation.WorkerThread
import java.io.File

internal interface FileWriter<T> {

    /**
     * Writes data as a [T] into a file.
     * @type T type of the data to write
     * @param file the file to write to
     * @param data the data to write
     * @param append whether to append data at the end of the file or overwrite
     * @return whether the write operation was successful
     */
    @WorkerThread
    fun writeData(
        file: File,
        data: T,
        append: Boolean
    ): Boolean
}
