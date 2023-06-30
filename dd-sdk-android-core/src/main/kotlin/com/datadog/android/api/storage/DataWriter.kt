/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.api.storage

import androidx.annotation.WorkerThread

/**
 * Interface to be implemented by the class which wants to write arbitrary data with the
 * given [EventBatchWriter].
 */
interface DataWriter<T> {
    /**
     * Writes the element with a given [EventBatchWriter].
     *
     * @return true if element was written, false otherwise.
     */
    @WorkerThread
    fun write(writer: EventBatchWriter, element: T): Boolean
}
