/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence

import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.FeatureStorageConfiguration
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.core.internal.metrics.RemovalReason
import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.core.internal.utils.submitSafe
import com.datadog.android.core.persistence.NoOpPersistenceStrategy
import com.datadog.android.core.persistence.PersistenceStrategy
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.privacy.TrackingConsentProviderCallback
import java.util.concurrent.ExecutorService

internal class AbstractStorage(
    internal val sdkCoreId: String?,
    private val featureName: String,
    internal val persistenceStrategyFactory: PersistenceStrategy.Factory,
    private val executorService: ExecutorService,
    private val internalLogger: InternalLogger,
    internal val storageConfiguration: FeatureStorageConfiguration,
    consentProvider: ConsentProvider
) : Storage, TrackingConsentProviderCallback {

    private val grantedPersistenceStrategy: PersistenceStrategy by lazy {
        persistenceStrategyFactory.create(
            "$sdkCoreId/$featureName/${TrackingConsent.GRANTED}",
            storageConfiguration.maxItemsPerBatch,
            storageConfiguration.maxBatchSize
        )
    }

    private val pendingPersistenceStrategy: PersistenceStrategy by lazy {
        persistenceStrategyFactory.create(
            "$sdkCoreId/$featureName/${TrackingConsent.PENDING}",
            storageConfiguration.maxItemsPerBatch,
            storageConfiguration.maxBatchSize
        )
    }

    private val notGrantedPersistenceStrategy: PersistenceStrategy = NoOpPersistenceStrategy()

    init {
        @Suppress("LeakingThis")
        consentProvider.registerCallback(this)
    }

    // region Storage

    @AnyThread
    override fun writeCurrentBatch(
        datadogContext: DatadogContext,
        forceNewBatch: Boolean,
        callback: (EventBatchWriter) -> Unit
    ) {
        val strategy = when (datadogContext.trackingConsent) {
            TrackingConsent.GRANTED -> grantedPersistenceStrategy
            TrackingConsent.PENDING -> pendingPersistenceStrategy
            TrackingConsent.NOT_GRANTED -> notGrantedPersistenceStrategy
        }

        executorService.submitSafe("Data write", internalLogger) {
            val writer = object : EventBatchWriter {
                @WorkerThread
                override fun currentMetadata(): ByteArray? {
                    return strategy.currentMetadata()
                }

                @WorkerThread
                override fun write(event: RawBatchEvent, batchMetadata: ByteArray?): Boolean {
                    return strategy.write(event, batchMetadata)
                }
            }
            callback.invoke(writer)
        }
    }

    @WorkerThread
    override fun readNextBatch(
        noBatchCallback: () -> Unit,
        readBatchCallback: (BatchId, BatchReader) -> Unit
    ) {
        val batch = grantedPersistenceStrategy.lockAndReadNext()
        if (batch == null) {
            noBatchCallback()
        } else {
            val batchId = BatchId(batch.batchId)
            val reader = object : BatchReader {

                @WorkerThread
                override fun currentMetadata(): ByteArray? {
                    return batch.metadata
                }

                @WorkerThread
                override fun read(): List<RawBatchEvent> {
                    return batch.events
                }
            }
            readBatchCallback.invoke(batchId, reader)
        }
    }

    @WorkerThread
    override fun confirmBatchRead(
        batchId: BatchId,
        removalReason: RemovalReason,
        callback: (BatchConfirmation) -> Unit
    ) {
        val confirmation = object : BatchConfirmation {
            @WorkerThread
            override fun markAsRead(deleteBatch: Boolean) {
                if (deleteBatch) {
                    grantedPersistenceStrategy.unlockAndDelete(batchId.id)
                } else {
                    grantedPersistenceStrategy.unlockAndKeep(batchId.id)
                }
            }
        }
        callback(confirmation)
    }

    override fun dropAll() {
        @Suppress("ThreadSafety")
        executorService.submitSafe("Data drop", internalLogger) {
            grantedPersistenceStrategy.dropAll()
            pendingPersistenceStrategy.dropAll()
        }
    }

    // endregion

    // region TrackingConsentProviderCallback

    override fun onConsentUpdated(
        previousConsent: TrackingConsent,
        newConsent: TrackingConsent
    ) {
        @Suppress("ThreadSafety")
        executorService.submitSafe("Data migration", internalLogger) {
            if (previousConsent == TrackingConsent.PENDING) {
                when (newConsent) {
                    TrackingConsent.GRANTED -> pendingPersistenceStrategy.migrateData(grantedPersistenceStrategy)
                    TrackingConsent.NOT_GRANTED -> pendingPersistenceStrategy.dropAll()
                    TrackingConsent.PENDING -> {
                        // Nothing to do
                    }
                }
            }
        }
    }

    // endregion
}
