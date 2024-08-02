/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.persistence

import androidx.annotation.WorkerThread
import com.datadog.android.api.storage.EventType
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.tools.annotation.NoOpImplementation

/**
 * The main strategy used to persist data between the moment it's tracked and created,
 * and the moment it's uploaded to the intake.
 */
@NoOpImplementation
interface PersistenceStrategy {

    /**
     * A factory used to create an instance of a [PersistenceStrategy].
     *
     * Each instance of a persistence strategy should have independent storage.
     * Data written to one instance must not be readable from another one.
     */
    interface Factory {

        /**
         * Creates an instance of a [PersistenceStrategy].
         *
         * @param identifier the identifier for the persistence strategy.
         * @param maxItemsPerBatch the maximum number of individual events in a batch
         * @param maxBatchSize the maximum size (in bytes) of a complete batch
         */
        fun create(
            identifier: String,
            maxItemsPerBatch: Int,
            maxBatchSize: Long
        ): PersistenceStrategy
    }

    /**
     * Describes the content of event batch.
     * @property batchId the unique identifier for a batch
     * @property metadata the metadata attached to the batch
     * @property events the list of events in the batch
     */
    data class Batch(
        val batchId: String,
        val metadata: ByteArray? = null,
        val events: List<RawBatchEvent> = mutableListOf()
    )

    /**
     * @return the metadata of the current writeable batch
     */
    @WorkerThread
    fun currentMetadata(): ByteArray?

    /**
     * Writes the content of the event to the current available batch.
     * @param event the event to write (content + metadata)
     * @param batchMetadata the optional updated batch metadata
     * @param eventType additional information about the event that can impact the way it is stored. Note: events
     * with the CRASH type are being sent as part of the Crash Reporting feature, and implies that the process will
     * exit soon, meaning that those event must be kept synchronously and in a way to be retrieved after the app
     * restarts in a new process (e.g.: on the file system, or in a local database).
     *
     * @return true if event was written, false otherwise.
     */
    @WorkerThread
    fun write(
        event: RawBatchEvent,
        batchMetadata: ByteArray?,
        eventType: EventType
    ): Boolean

    /**
     * Reads the next batch of data and lock it so that it can't be read or written to by anyone.
     */
    @WorkerThread
    fun lockAndReadNext(): Batch?

    /**
     * Marks the batch as unlocked and to be kept to be read again later.
     */
    @WorkerThread
    fun unlockAndKeep(batchId: String)

    /**
     *  Marks the batch as unlocked and to be deleted.
     *  The corresponding batch should not be returned in any call to [lockAndReadNext].
     */
    @WorkerThread
    fun unlockAndDelete(batchId: String)

    /**
     * Drop all data.
     */
    @WorkerThread
    fun dropAll()

    /**
     * Migrate the data to a different [PersistenceStrategy].
     * All readable and ongoing batches must be transferred to the given strategy.
     */
    fun migrateData(targetStrategy: PersistenceStrategy)
}
