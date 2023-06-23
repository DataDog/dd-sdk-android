/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal

import android.content.Context
import com.datadog.android.core.internal.data.upload.NoOpUploadScheduler
import com.datadog.android.core.internal.data.upload.UploadScheduler
import com.datadog.android.core.internal.persistence.file.FileMover
import com.datadog.android.core.internal.persistence.file.FileOrchestrator
import com.datadog.android.core.internal.persistence.file.FileReaderWriter
import com.datadog.android.core.internal.persistence.file.NoOpFileOrchestrator
import com.datadog.android.core.internal.persistence.file.advanced.FeatureFileOrchestrator
import com.datadog.android.core.internal.persistence.file.batch.BatchFileReaderWriter
import com.datadog.android.privacy.TrackingConsentProviderCallback
import com.datadog.android.v2.api.EventBatchWriter
import com.datadog.android.v2.api.Feature
import com.datadog.android.v2.api.FeatureEventReceiver
import com.datadog.android.v2.api.FeatureScope
import com.datadog.android.v2.api.FeatureStorageConfiguration
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.RequestFactory
import com.datadog.android.v2.api.StorageBackedFeature
import com.datadog.android.v2.api.context.DatadogContext
import com.datadog.android.v2.core.internal.NoOpContextProvider
import com.datadog.android.v2.core.internal.data.upload.DataFlusher
import com.datadog.android.v2.core.internal.data.upload.DataUploadScheduler
import com.datadog.android.v2.core.internal.net.DataOkHttpUploader
import com.datadog.android.v2.core.internal.net.DataUploader
import com.datadog.android.v2.core.internal.net.NoOpDataUploader
import com.datadog.android.v2.core.internal.storage.ConsentAwareStorage
import com.datadog.android.v2.core.internal.storage.NoOpStorage
import com.datadog.android.v2.core.internal.storage.Storage
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@Suppress("TooManyFunctions")
internal class SdkFeature(
    internal val coreFeature: CoreFeature,
    internal val wrappedFeature: Feature,
    internal val internalLogger: InternalLogger
) : FeatureScope {

    internal val initialized = AtomicBoolean(false)
    internal val eventReceiver = AtomicReference<FeatureEventReceiver>(null)

    internal var storage: Storage = NoOpStorage()
    internal var uploader: DataUploader = NoOpDataUploader()
    internal var uploadScheduler: UploadScheduler = NoOpUploadScheduler()
    internal var fileOrchestrator: FileOrchestrator = NoOpFileOrchestrator()

    // region SDK Feature

    fun initialize(context: Context) {
        if (initialized.get()) {
            return
        }

        if (wrappedFeature is StorageBackedFeature) {
            storage = createStorage(wrappedFeature.name, wrappedFeature.storageConfiguration)
        }

        wrappedFeature.onInitialize(context)

        if (wrappedFeature is StorageBackedFeature) {
            setupUploader(wrappedFeature.requestFactory)
        }

        if (wrappedFeature is TrackingConsentProviderCallback) {
            coreFeature.trackingConsentProvider.registerCallback(wrappedFeature)
        }

        initialized.set(true)
    }

    fun isInitialized(): Boolean {
        return initialized.get()
    }

    fun clearAllData() {
        @Suppress("ThreadSafety") // TODO RUMM-1503 delegate to another thread
        storage.dropAll()
    }

    fun stop() {
        if (initialized.get()) {
            wrappedFeature.onStop()

            if (wrappedFeature is TrackingConsentProviderCallback) {
                coreFeature.trackingConsentProvider.unregisterCallback(wrappedFeature)
            }
            uploadScheduler.stopScheduling()
            uploadScheduler = NoOpUploadScheduler()
            storage = NoOpStorage()
            uploader = NoOpDataUploader()
            fileOrchestrator = NoOpFileOrchestrator()

            initialized.set(false)
        }
    }

    // endregion

    // region FeatureScope

    override fun withWriteContext(
        forceNewBatch: Boolean,
        callback: (DatadogContext, EventBatchWriter) -> Unit
    ) {
        // TODO RUMM-0000 thread safety. Thread switch happens in Storage right now. Open questions:
        // * what if caller wants to have a sync operation, without thread switch
        // * should context read and write be on the dedicated thread? risk - time gap between
        // caller and context
        val contextProvider = coreFeature.contextProvider
        if (contextProvider is NoOpContextProvider) return
        val context = contextProvider.context
        storage.writeCurrentBatch(context, forceNewBatch) { callback(context, it) }
    }

    override fun sendEvent(event: Any) {
        val receiver = eventReceiver.get()
        if (receiver == null) {
            internalLogger.log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.USER,
                { NO_EVENT_RECEIVER.format(Locale.US, wrappedFeature.name) }
            )
        } else {
            receiver.onReceive(event)
        }
    }

    // Allow unchecked cast here. Anyway if there is a mismatch and crash happens, it will be
    // caught during our tests
    @Suppress("UNCHECKED_CAST")
    override fun <T : Feature> unwrap(): T = wrappedFeature as T

    // endregion

    // region Internal

    private fun setupUploader(requestFactory: RequestFactory) {
        uploadScheduler = if (coreFeature.isMainProcess) {
            uploader = createUploader(requestFactory)
            DataUploadScheduler(
                storage,
                uploader,
                coreFeature.contextProvider,
                coreFeature.networkInfoProvider,
                coreFeature.systemInfoProvider,
                coreFeature.uploadFrequency,
                coreFeature.uploadExecutorService,
                internalLogger
            )
        } else {
            NoOpUploadScheduler()
        }
        uploadScheduler.startScheduling()
    }

    // region Feature setup

    private fun createStorage(
        featureName: String,
        storageConfiguration: FeatureStorageConfiguration
    ): Storage {
        val fileOrchestrator = FeatureFileOrchestrator(
            consentProvider = coreFeature.trackingConsentProvider,
            storageDir = coreFeature.storageDir,
            featureName = featureName,
            executorService = coreFeature.persistenceExecutorService,
            internalLogger = internalLogger
        )
        this.fileOrchestrator = fileOrchestrator

        return ConsentAwareStorage(
            executorService = coreFeature.persistenceExecutorService,
            grantedOrchestrator = fileOrchestrator.grantedOrchestrator,
            pendingOrchestrator = fileOrchestrator.pendingOrchestrator,
            batchEventsReaderWriter = BatchFileReaderWriter.create(
                internalLogger = internalLogger,
                encryption = coreFeature.localDataEncryption
            ),
            batchMetadataReaderWriter = FileReaderWriter.create(
                internalLogger = internalLogger,
                encryption = coreFeature.localDataEncryption
            ),
            fileMover = FileMover(internalLogger),
            internalLogger = internalLogger,
            filePersistenceConfig = coreFeature.buildFilePersistenceConfig().copy(
                maxBatchSize = storageConfiguration.maxBatchSize,
                maxItemSize = storageConfiguration.maxItemSize,
                maxItemsPerBatch = storageConfiguration.maxItemsPerBatch,
                oldFileThreshold = storageConfiguration.oldBatchThreshold
            )
        )
    }

    private fun createUploader(requestFactory: RequestFactory): DataUploader {
        return DataOkHttpUploader(
            requestFactory = requestFactory,
            internalLogger = internalLogger,
            callFactory = coreFeature.okHttpClient,
            sdkVersion = coreFeature.sdkVersion,
            androidInfoProvider = coreFeature.androidInfoProvider
        )
    }

    // endregion

    // Used for nightly tests only
    internal fun flushStoredData() {
        // TODO RUMM-0000 should it just accept storage?
        val flusher = DataFlusher(
            coreFeature.contextProvider,
            fileOrchestrator,
            BatchFileReaderWriter.create(internalLogger, coreFeature.localDataEncryption),
            FileReaderWriter.create(internalLogger, coreFeature.localDataEncryption),
            FileMover(internalLogger),
            internalLogger
        )
        @Suppress("ThreadSafety")
        flusher.flush(uploader)
    }

    // endregion

    companion object {
        const val NO_EVENT_RECEIVER =
            "Feature \"%s\" has no event receiver registered, ignoring event."
    }
}
