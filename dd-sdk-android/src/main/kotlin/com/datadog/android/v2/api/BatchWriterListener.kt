/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.api

import com.datadog.tools.annotation.NoOpImplementation

/**
 * A Listener to be notified when an event is actually written in the storage,
 * or when a write operation failed.
 * @see [EventBatchWriter]
 */
@NoOpImplementation
internal interface BatchWriterListener {
    /**
     * Called whenever data is written successfully.
     * @param eventId the id of the written event
     */
    fun onDataWritten(eventId: String)

    /**
     * Called whenever data failed to be written.
     * @param eventId the id of the event that failed
     */
    fun onDataWriteFailed(eventId: String)
}
