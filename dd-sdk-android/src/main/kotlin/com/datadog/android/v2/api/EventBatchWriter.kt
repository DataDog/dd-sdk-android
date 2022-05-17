/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.api

/**
 * Utility allowing [FeatureScope] to write events in the storage to be uploaded asynchronously.
 */
interface EventBatchWriter {

    /**
     * @return the metadata of the current writeable file
     */
    fun currentMetadata(): ByteArray?

    /**
     * Writes the content of the event to the current available batch.
     * @param event the event to write
     * @param eventId a unique identifier for the event (used to identify events in
     * the [BatchWriterListener] callbacks).
     * @param newMetadata the updated metadata
     */
    fun write(
        event: ByteArray,
        eventId: String,
        newMetadata: ByteArray
    )
}
