/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.integration.tests.utils

import com.datadog.android.api.storage.EventType
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.core.persistence.PersistenceStrategy
import java.security.MessageDigest

internal class HeapBasedPersistenceStrategy : PersistenceStrategy {

    @Volatile
    private var batchMetadata: ByteArray? = null
    private val storedEvents = HashSet<RawBatchEvent>()
    private val lockedBatches: MutableMap<String, PersistenceStrategy.Batch> = mutableMapOf()

    override fun currentMetadata(): ByteArray? {
        return batchMetadata
    }

    override fun write(event: RawBatchEvent, batchMetadata: ByteArray?, eventType: EventType): Boolean {
        synchronized(storedEvents) {
            storedEvents.add(event)
        }
        this.batchMetadata = batchMetadata
        return true
    }

    override fun lockAndReadNext(): PersistenceStrategy.Batch? {
        val currentBatch: List<RawBatchEvent>
        synchronized(storedEvents) {
            currentBatch = storedEvents.toList()
        }
        if (currentBatch.isNotEmpty()) {
            val batchId = currentBatch.toBatchId()
            val batch = PersistenceStrategy.Batch(batchId, batchMetadata, currentBatch)
            lockedBatches[batchId] = batch
            return batch
        }
        return null
    }

    override fun unlockAndKeep(batchId: String) {
        synchronized(storedEvents) {
            lockedBatches.remove(batchId)?.let {
                storedEvents.addAll(it.events)
            }
        }
    }

    override fun unlockAndDelete(batchId: String) {
        val batch = synchronized(lockedBatches) {
            lockedBatches.remove(batchId)
        }
        synchronized(storedEvents) {
            batch?.events?.forEach { event ->
                storedEvents.remove(event)
            }
        }
    }

    override fun dropAll() {
        synchronized(storedEvents) {
            storedEvents.clear()
        }
        synchronized(lockedBatches) {
            lockedBatches.clear()
        }
        batchMetadata = null
    }

    override fun migrateData(targetStrategy: PersistenceStrategy) {
        val heapBasedPersistenceStrategy = targetStrategy as HeapBasedPersistenceStrategy
        synchronized(storedEvents) {
            heapBasedPersistenceStrategy.storedEvents.addAll(storedEvents)
            storedEvents.clear()
        }
        heapBasedPersistenceStrategy.batchMetadata = batchMetadata
    }

    private fun List<RawBatchEvent>.toBatchId(): String {
        val data = this.map { it.data }.reduce { acc, bytes -> acc + bytes }
        return try {
            val digest = MessageDigest.getInstance("SHA-1")
            val hashBytes = digest.digest(data)
            return hashBytes.toString()
        } catch (e: Throwable) {
            ""
        }
    }
}
