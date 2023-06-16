/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.core.internal.storage

import androidx.annotation.WorkerThread

internal interface BatchReader {

    /**
     * @return the metadata of the current readable file
     */
    @WorkerThread
    fun currentMetadata(): ByteArray?

    @WorkerThread
    fun read(): List<ByteArray>
}
