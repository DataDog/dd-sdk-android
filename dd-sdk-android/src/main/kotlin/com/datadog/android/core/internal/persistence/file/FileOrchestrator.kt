/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file

import com.datadog.tools.annotation.NoOpImplementation
import java.io.File

/**
 * A class that will manage set of files that can be read/written to.
 *
 * The contract of this class is that:
 * - a File that can be written to cannot be read;
 * - a File that can be read cannot be written to;
 */
@NoOpImplementation
internal interface FileOrchestrator {

    /**
     * @param dataSize the size of the data to write (in bytes)
     * @return a File with enough space to write `dataSize` bytes, or null if no space is available
     * or the disk can't be written to.
     */
    fun getWritableFile(dataSize: Int): File?

    /**
     * @param excludeFiles a set of files to exclude from the readable files
     * @return a File that can be read from, or null is no file is available yet.
     */
    fun getReadableFile(excludeFiles: Set<File>): File?

    /**
     * @return a List of all flushable files. A flushable file is any file (readable or writable)
     * which contains valid data and is ready to be uploaded to the events endpoint.
     */
    fun getFlushableFiles(): List<File>

    /**
     * @return a list of files in this orchestrator (both writable and readable)
     */
    fun getAllFiles(): List<File>

    /**
     * @return the root directory of this orchestrator, or null if the root directory is not
     * available (e.g.: because of a SecurityException)
     */
    fun getRootDir(): File?
}
