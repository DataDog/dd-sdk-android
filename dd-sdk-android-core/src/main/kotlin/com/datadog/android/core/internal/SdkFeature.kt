/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal

import android.app.Application
import android.content.Context
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureEventReceiver
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.StorageBackedFeature
import com.datadog.android.api.net.RequestFactory
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.FeatureStorageConfiguration
import com.datadog.android.core.configuration.BatchProcessingLevel
import com.datadog.android.core.configuration.UploadFrequency
import com.datadog.android.core.internal.configuration.DataUploadConfiguration
import com.datadog.android.core.internal.data.upload.DataOkHttpUploader
import com.datadog.android.core.internal.data.upload.NoOpUploadScheduler
import com.datadog.android.core.internal.data.upload.UploadScheduler
import com.datadog.android.core.internal.data.upload.v2.DataFlusher
import com.datadog.android.core.internal.data.upload.v2.DataUploadScheduler
import com.datadog.android.core.internal.data.upload.v2.DataUploader
import com.datadog.android.core.internal.data.upload.v2.NoOpDataUploader
import com.datadog.android.core.internal.lifecycle.ProcessLifecycleMonitor
import com.datadog.android.core.internal.metrics.BatchMetricsDispatcher
import com.datadog.android.core.internal.metrics.MetricsDispatcher
import com.datadog.android.core.internal.metrics.NoOpMetricsDispatcher
import com.datadog.android.core.internal.persistence.ConsentAwareStorage
import com.datadog.android.core.internal.persistence.NoOpStorage
import com.datadog.android.core.internal.persistence.Storage
import com.datadog.android.core.internal.persistence.file.FileMover
import com.datadog.android.core.internal.persistence.file.FileOrchestrator
import com.datadog.android.core.internal.persistence.file.FilePersistenceConfig
import com.datadog.android.core.internal.persistence.file.FileReaderWriter
import com.datadog.android.core.internal.persistence.file.NoOpFileOrchestrator
import com.datadog.android.core.internal.persistence.file.advanced.FeatureFileOrchestrator
import com.datadog.android.core.internal.persistence.file.batch.BatchFileReaderWriter
import com.datadog.android.privacy.TrackingConsentProviderCallback
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
    internal var metricsDispatcher: MetricsDispatcher = NoOpMetricsDispatcher()
    internal var processLifecycleMonitor: ProcessLifecycleMonitor? = null

    // region SdkFeature

    fun initialize(context: Context) {
        if (initialized.get()) {
            return
        }

        var dataUploadConfiguration: DataUploadConfiguration? = null
        if (wrappedFeature is StorageBackedFeature) {
            val uploadFrequency = resolveUploadFrequency()
            val batchProcessingLevel = resolveBatchProcessingLevel()
            dataUploadConfiguration = DataUploadConfiguration(
                uploadFrequency,
                batchProcessingLevel.maxBatchesPerUploadJob
            )
            val storageConfiguration = wrappedFeature.storageConfiguration
            val recentDelayMs = resolveBatchingDelay(coreFeature, storageConfiguration)
            val filePersistenceConfig = coreFeature.buildFilePersistenceConfig().copy(
                maxBatchSize = storageConfiguration.maxBatchSize,
                maxItemSize = storageConfiguration.maxItemSize,
                maxItemsPerBatch = storageConfiguration.maxItemsPerBatch,
                oldFileThreshold = storageConfiguration.oldBatchThreshold,
                recentDelayMs = recentDelayMs
            )
            setupMetricsDispatcher(dataUploadConfiguration, filePersistenceConfig, context)

            storage = createStorage(wrappedFeature.name, filePersistenceConfig)
        }

        wrappedFeature.onInitialize(context)

        if (wrappedFeature is StorageBackedFeature && dataUploadConfiguration != null) {
            setupUploader(wrappedFeature.requestFactory, dataUploadConfiguration)
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
            metricsDispatcher = NoOpMetricsDispatcher()
            (coreFeature.contextRef.get() as? Application)
                ?.unregisterActivityLifecycleCallbacks(processLifecycleMonitor)
            processLifecycleMonitor = null
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

    private fun setupMetricsDispatcher(
        dataUploadConfiguration: DataUploadConfiguration,
        filePersistenceConfig: FilePersistenceConfig,
        context: Context
    ) {
        metricsDispatcher = BatchMetricsDispatcher(
            wrappedFeature.name,
            dataUploadConfiguration,
            filePersistenceConfig,
            internalLogger,
            coreFeature.timeProvider
        ).apply {
            if (context is Application) {
                processLifecycleMonitor = ProcessLifecycleMonitor(this)
                context.registerActivityLifecycleCallbacks(
                    processLifecycleMonitor
                )
            }
        }
    }

    private fun resolveBatchingDelay(
        coreFeature: CoreFeature,
        featureStorageConfiguration: FeatureStorageConfiguration
    ): Long {
        return featureStorageConfiguration.batchSize?.windowDurationMs
            ?: coreFeature.batchSize.windowDurationMs
    }

    private fun resolveUploadFrequency(): UploadFrequency {
        return if (wrappedFeature is StorageBackedFeature) {
            wrappedFeature.storageConfiguration.uploadFrequency ?: coreFeature.uploadFrequency
        } else {
            coreFeature.uploadFrequency
        }
    }
    private fun resolveBatchProcessingLevel(): BatchProcessingLevel {
        return if (wrappedFeature is StorageBackedFeature) {
            wrappedFeature.storageConfiguration.batchProcessingLevel
                ?: coreFeature.batchProcessingLevel
        } else {
            coreFeature.batchProcessingLevel
        }
    }

    private fun setupUploader(
        requestFactory: RequestFactory,
        uploadConfiguration: DataUploadConfiguration
    ) {
        uploadScheduler = if (coreFeature.isMainProcess) {
            uploader = createUploader(requestFactory)
            DataUploadScheduler(
                storage,
                uploader,
                coreFeature.contextProvider,
                coreFeature.networkInfoProvider,
                coreFeature.systemInfoProvider,
                uploadConfiguration,
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
        filePersistenceConfig: FilePersistenceConfig
    ): Storage {
        val fileOrchestrator = FeatureFileOrchestrator(
            consentProvider = coreFeature.trackingConsentProvider,
            storageDir = coreFeature.storageDir,
            featureName = featureName,
            executorService = coreFeature.persistenceExecutorService,
            filePersistenceConfig = filePersistenceConfig,
            internalLogger = internalLogger,
            metricsDispatcher = metricsDispatcher
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
            filePersistenceConfig = filePersistenceConfig,
            metricsDispatcher = metricsDispatcher
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
