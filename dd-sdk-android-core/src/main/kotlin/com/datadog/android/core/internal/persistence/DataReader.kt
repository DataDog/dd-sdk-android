/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence

import androidx.annotation.WorkerThread
import com.datadog.tools.annotation.NoOpImplementation

/**
 * A class able to read data from a persistence location (e.g. file, database, …).
 */
@NoOpImplementation
internal interface DataReader {

    /**
     * Reads the next piece of data and lock it so that it can't be read or written to by anyone.
     */
    @WorkerThread
    fun lockAndReadNext(): Batch?

    /**
     * Marks the data as read and releases it to be read/written to by someone else.
     */
    @WorkerThread
    fun release(data: Batch)

    /**
     * Marks the data as read and deletes it.
     */
    @WorkerThread
    fun drop(data: Batch)

    /**
     * Drop all available data.
     */
    @WorkerThread
    fun dropAll()
}
