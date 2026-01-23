/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file

import androidx.annotation.WorkerThread
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
     * @return a File with enough space to write data, or null if no space is available
     * or the disk can't be written to.
     */
    @WorkerThread
    fun getWritableFile(): File?

    /**
     * @param excludeFiles a set of files to exclude from the readable files
     * @return a File that can be read from, or null is no file is available yet.
     */
    @WorkerThread
    fun getReadableFile(excludeFiles: Set<File>): File?

    /**
     * @return a List of all flushable files. A flushable file is any file (readable or writable)
     * which contains valid data and is ready to be uploaded to the events endpoint.
     */
    @WorkerThread
    fun getFlushableFiles(): List<File>

    /**
     * @return a list of files in this orchestrator (both writable and readable)
     */
    @WorkerThread
    fun getAllFiles(): List<File>

    /**
     * @return the root directory of this orchestrator, or null if the root directory is not
     * available (e.g.: because of a SecurityException)
     */
    @WorkerThread
    fun getRootDir(): File?

    /**
     * @return the metadata file for a given file, or null if there is no such.
     */
    @WorkerThread
    fun getMetadataFile(file: File): File?

    /**
     * @return the number of pending files in the orchestrator, after decrementing by 1.
     */
    fun decrementAndGetPendingFilesCount(): Int

    /**
     * Notifies the orchestrator that a file has been deleted.
     *
     * @param file the file that was deleted
     */
    fun onFileDeleted(file: File)

    /**
     * Refreshes the internal file list from disk.
     */
    @WorkerThread
    fun refreshFilesFromDisk()
}
