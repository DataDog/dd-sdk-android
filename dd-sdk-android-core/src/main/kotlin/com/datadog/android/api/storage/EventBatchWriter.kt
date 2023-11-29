/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.api.storage

import androidx.annotation.WorkerThread

/**
 * Writer allowing [FeatureScope] to write events in the storage exposing current batch metadata.
 */
interface EventBatchWriter {

    /**
     * @return the metadata of the current writeable batch
     */
    @WorkerThread
    fun currentMetadata(): ByteArray?

    /**
     * Writes the content of the event to the current available batch.
     * @param event the event to write (content + metadata)
     * @param batchMetadata the optional updated batch metadata
     *
     * @return true if event was written, false otherwise.
     */
    @WorkerThread
    fun write(
        event: RawBatchEvent,
        batchMetadata: ByteArray?
    ): Boolean
}
