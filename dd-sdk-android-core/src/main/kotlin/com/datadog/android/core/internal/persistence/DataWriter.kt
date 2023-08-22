/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence

import androidx.annotation.WorkerThread
import com.datadog.tools.annotation.NoOpImplementation

/**
 * A class able to write a data (or list of data) with type [T] in a persistence location
 * (e.g. file, database, …).
 * @param T the type of data to store
 */
@NoOpImplementation
internal interface DataWriter<T : Any> {

    /**
     * Writes the element into the relevant location.
     */
    @WorkerThread
    fun write(element: T)

    /**
     * Writes a list of elements into the relevant location.
     */
    @WorkerThread
    fun write(data: List<T>)
}
