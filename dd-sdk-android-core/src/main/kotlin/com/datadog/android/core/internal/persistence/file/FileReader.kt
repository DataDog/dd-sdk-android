/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file

import androidx.annotation.WorkerThread
import com.datadog.android.internal.telemetry.TelemetryContext
import java.io.File

internal interface FileReader<T> {

    /**
     * Reads data from the given file.
     *  @param file the file to read from
     *  @param telemetryContext context used to report telemetry if the read fails
     *  @return the data
     */
    @WorkerThread
    fun readData(file: File, telemetryContext: TelemetryContext): T
}
