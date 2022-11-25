/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.batch

import androidx.annotation.WorkerThread
import java.io.File

internal interface BatchFileReader {

    /**
     * Reads data from the given file.
     *  @param file the file to read from
     *  @return the list of events as [ByteArray] data stored in a file.
     */
    @WorkerThread
    fun readData(
        file: File
    ): List<ByteArray>
}
