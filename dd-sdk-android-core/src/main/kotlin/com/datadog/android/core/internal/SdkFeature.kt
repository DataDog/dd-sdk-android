/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal

import android.app.Application
import android.content.Context
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureEventReceiver
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.StorageBackedFeature
import com.datadog.android.api.net.RequestFactory
import com.datadog.android.api.storage.FeatureStorageConfiguration
import com.datadog.android.api.storage.datastore.DataStoreHandler
import com.datadog.android.core.configuration.UploadSchedulerStrategy
import com.datadog.android.core.internal.configuration.DataUploadConfiguration
import com.datadog.android.core.internal.data.upload.DataFlusher
import com.datadog.android.core.internal.data.upload.DataOkHttpUploader
import com.datadog.android.core.internal.data.upload.DataUploadScheduler
import com.datadog.android.core.internal.data.upload.DataUploader
import com.datadog.android.core.internal.data.upload.DefaultUploadSchedulerStrategy
import com.datadog.android.core.internal.data.upload.NoOpDataUploader
import com.datadog.android.core.internal.data.upload.NoOpUploadScheduler
import com.datadog.android.core.internal.data.upload.UploadScheduler
import com.datadog.android.core.internal.lifecycle.ProcessLifecycleMonitor
import com.datadog.android.core.internal.metrics.BatchMetricsDispatcher
import com.datadog.android.core.internal.metrics.MetricsDispatcher
import com.datadog.android.core.internal.metrics.NoOpMetricsDispatcher
import com.datadog.android.core.internal.persistence.AbstractStorage
import com.datadog.android.core.internal.persistence.ConsentAwareStorage
import com.datadog.android.core.internal.persistence.NoOpStorage
import com.datadog.android.core.internal.persistence.Storage
import com.datadog.android.core.internal.persistence.datastore.DataStoreFileHandler
import com.datadog.android.core.internal.persistence.datastore.DataStoreFileHelper
import com.datadog.android.core.internal.persistence.datastore.DatastoreFileReader
import com.datadog.android.core.internal.persistence.datastore.DatastoreFileWriter
import com.datadog.android.core.internal.persistence.datastore.NoOpDataStoreHandler
import com.datadog.android.core.internal.persistence.file.FileMover
import com.datadog.android.core.internal.persistence.file.FileOrchestrator
import com.datadog.android.core.internal.persistence.file.FilePersistenceConfig
import com.datadog.android.core.internal.persistence.file.FileReaderWriter
import com.datadog.android.core.internal.persistence.file.NoOpFileOrchestrator
import com.datadog.android.core.internal.persistence.file.advanced.FeatureFileOrchestrator
import com.datadog.android.core.internal.persistence.file.batch.BatchFileReaderWriter
import com.datadog.android.core.internal.persistence.tlvformat.TLVBlockFileReader
import com.datadog.android.core.internal.utils.executeSafe
import com.datadog.android.core.internal.utils.getSafe
import com.datadog.android.core.internal.utils.submitSafe
import com.datadog.android.core.persistence.PersistenceStrategy
import com.datadog.android.internal.profiler.BenchmarkSdkUploads
import com.datadog.android.internal.profiler.GlobalBenchmark
import com.datadog.android.privacy.TrackingConsentProviderCallback
import com.datadog.android.security.Encryption
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock

@Suppress("TooManyFunctions")
internal class SdkFeature(
    internal val coreFeature: CoreFeature,
    internal val contextProvider: ContextProvider,
    internal val wrappedFeature: Feature,
    internal val internalLogger: InternalLogger,
    private val benchmarkSdkUploads: BenchmarkSdkUploads = GlobalBenchmark.getBenchmarkSdkUploads()
) : FeatureScope {

    override var dataStore: DataStoreHandler = NoOpDataStoreHandler()

    internal val initialized = AtomicBoolean(false)
    internal val eventReceiver = AtomicReference<FeatureEventReceiver>(null)
    internal var storage: Storage = NoOpStorage()
    internal var uploader: DataUploader = NoOpDataUploader()
    internal var uploadScheduler: UploadScheduler = NoOpUploadScheduler()
    internal var fileOrchestrator: FileOrchestrator = NoOpFileOrchestrator()
    internal var metricsDispatcher: MetricsDispatcher = NoOpMetricsDispatcher()
    internal var processLifecycleMonitor: ProcessLifecycleMonitor? = null
    internal val featureContextLock: ReadWriteLock = ReentrantReadWriteLock()
    internal val featureContext: MutableMap<String, Any?> = mutableMapOf()

    // region SdkFeature

    fun initialize(context: Context, instanceId: String) {
        if (initialized.get()) {
            return
        }

        if (wrappedFeature is StorageBackedFeature) {
            val uploadFrequency = coreFeature.uploadFrequency
            val batchProcessingLevel = coreFeature.batchProcessingLevel

            val dataUploadConfiguration = DataUploadConfiguration(
                uploadFrequency,
                batchProcessingLevel.maxBatchesPerUploadJob
            )
            val uploadSchedulerStrategy = coreFeature.customUploadSchedulerStrategy
                ?: DefaultUploadSchedulerStrategy(dataUploadConfiguration)
            storage = prepareStorage(
                dataUploadConfiguration,
                wrappedFeature,
                context,
                instanceId,
                coreFeature.persistenceStrategyFactory
            )

            wrappedFeature.onInitialize(context)

            setupUploader(wrappedFeature, uploadSchedulerStrategy, dataUploadConfiguration.maxBatchesPerUploadJob)
        } else {
            wrappedFeature.onInitialize(context)
        }

        if (wrappedFeature is TrackingConsentProviderCallback) {
            coreFeature.trackingConsentProvider.registerCallback(wrappedFeature)
        }

        prepareDataStoreHandler(
            encryption = coreFeature.localDataEncryption
        )

        createBatchCountBenchmark()

        initialized.set(true)

        uploadScheduler.startScheduling()
    }

    fun isInitialized(): Boolean {
        return initialized.get()
    }

    @AnyThread
    fun clearAllData() {
        storage.dropAll()
        dataStore.clearAllData()
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
            dataStore = NoOpDataStoreHandler()
            uploader = NoOpDataUploader()
            fileOrchestrator = NoOpFileOrchestrator()
            metricsDispatcher = NoOpMetricsDispatcher()
            (coreFeature.contextRef.get() as? Application)
                ?.unregisterActivityLifecycleCallbacks(processLifecycleMonitor)
            processLifecycleMonitor = null
            featureContext.clear()
            initialized.set(false)
        }
    }

    // endregion

    // region FeatureScope

    override fun withWriteContext(
        withFeatureContexts: Set<String>,
        callback: (DatadogContext, EventWriteScope) -> Unit
    ) {
        coreFeature.contextExecutorService
            .executeSafe("withWriteContext-${wrappedFeature.name}", internalLogger) {
                if (coreFeature.initialized.get() == false) return@executeSafe
                val context = contextProvider.getContext(withFeatureContexts)
                val eventBatchWriteScope = storage.getEventWriteScope(context)
                callback(context, eventBatchWriteScope)
            }
    }

    override fun withContext(
        withFeatureContexts: Set<String>,
        callback: (datadogContext: DatadogContext) -> Unit
    ) {
        coreFeature.contextExecutorService
            .executeSafe("withContext-${wrappedFeature.name}", internalLogger) {
                if (coreFeature.initialized.get() == false) return@executeSafe
                val context = contextProvider.getContext(withFeatureContexts)
                callback(context)
            }
    }

    internal fun getContextFuture(withFeatureContexts: Set<String>): Future<DatadogContext?>? {
        return coreFeature.contextExecutorService.submitSafe(
            "getContextFuture-${wrappedFeature.name}",
            internalLogger,
            Callable {
                if (coreFeature.initialized.get()) {
                    contextProvider.getContext(withFeatureContexts)
                } else {
                    null
                }
            }
        )
    }

    override fun getWriteContextSync(
        withFeatureContexts: Set<String>
    ): Pair<DatadogContext, EventWriteScope>? {
        val operationName = "getWriteContextSync-${wrappedFeature.name}"
        return coreFeature.contextExecutorService
            .submitSafe(
                operationName,
                internalLogger,
                Callable {
                    if (coreFeature.initialized.get() == false) return@Callable null
                    val context = contextProvider.getContext(withFeatureContexts)
                    val eventBatchWriteScope = storage.getEventWriteScope(context)
                    context to eventBatchWriteScope
                }
            )
            .getSafe(operationName, internalLogger)
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

    private fun createBatchCountBenchmark() {
        val tags = mapOf(
            TRACK_NAME to wrappedFeature.name
        )

        @Suppress("ThreadSafety") // called in worker thread context
        benchmarkSdkUploads
            .getMeter(METER_NAME)
            .createObservableGauge(
                metricName = BATCH_COUNT_METRIC_NAME,
                tags = tags,
                callback = { fileOrchestrator.getFlushableFiles().size.toDouble() }
            )
    }

    private fun setupMetricsDispatcher(
        dataUploadConfiguration: DataUploadConfiguration?,
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

    private fun setupUploader(
        feature: StorageBackedFeature,
        uploadSchedulerStrategy: UploadSchedulerStrategy,
        maxBatchesPerJob: Int
    ) {
        uploadScheduler = if (coreFeature.isMainProcess) {
            uploader = createUploader(feature.requestFactory)
            DataUploadScheduler(
                feature.name,
                storage,
                uploader,
                contextProvider,
                coreFeature.networkInfoProvider,
                coreFeature.systemInfoProvider,
                uploadSchedulerStrategy,
                maxBatchesPerJob,
                coreFeature.uploadExecutorService,
                internalLogger
            )
        } else {
            NoOpUploadScheduler()
        }
    }

    // region Feature setup

    private fun prepareStorage(
        dataUploadConfiguration: DataUploadConfiguration?,
        wrappedFeature: StorageBackedFeature,
        context: Context,
        instanceId: String,
        persistenceStrategyFactory: PersistenceStrategy.Factory?
    ): Storage {
        val storageConfiguration = wrappedFeature.storageConfiguration
        return if (persistenceStrategyFactory == null) {
            val recentDelayMs = coreFeature.batchSize.windowDurationMs
            val filePersistenceConfig = coreFeature.buildFilePersistenceConfig().copy(
                maxBatchSize = storageConfiguration.maxBatchSize,
                maxItemSize = storageConfiguration.maxItemSize,
                maxItemsPerBatch = storageConfiguration.maxItemsPerBatch,
                oldFileThreshold = storageConfiguration.oldBatchThreshold,
                recentDelayMs = recentDelayMs
            )
            setupMetricsDispatcher(dataUploadConfiguration, filePersistenceConfig, context)

            createFileStorage(wrappedFeature.name, filePersistenceConfig)
        } else {
            createCustomStorage(instanceId, wrappedFeature.name, storageConfiguration, persistenceStrategyFactory)
        }
    }

    private fun createCustomStorage(
        instanceId: String,
        featureName: String,
        storageConfiguration: FeatureStorageConfiguration,
        persistenceStrategyFactory: PersistenceStrategy.Factory
    ): Storage {
        return AbstractStorage(
            instanceId,
            featureName,
            persistenceStrategyFactory,
            coreFeature.persistenceExecutorService,
            internalLogger,
            storageConfiguration,
            coreFeature.trackingConsentProvider
        )
    }

    private fun createFileStorage(
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
            metricsDispatcher = metricsDispatcher,
            timeProvider = coreFeature.timeProvider
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
            metricsDispatcher = metricsDispatcher,
            featureName
        )
    }

    private fun createUploader(requestFactory: RequestFactory): DataUploader {
        return DataOkHttpUploader(
            requestFactory = requestFactory,
            internalLogger = internalLogger,
            callFactory = coreFeature.callFactory,
            sdkVersion = coreFeature.sdkVersion,
            androidInfoProvider = coreFeature.androidInfoProvider,
            executionTimer = GlobalBenchmark.createExecutionTimer(
                track = wrappedFeature.name,
                timeProvider = coreFeature.timeProvider
            )
        )
    }

    private fun prepareDataStoreHandler(
        encryption: Encryption?
    ) {
        val fileReaderWriter = FileReaderWriter.create(
            internalLogger,
            encryption
        )

        val dataStoreFileHelper = DataStoreFileHelper(internalLogger)
        val featureName = wrappedFeature.name
        val storageDir = coreFeature.storageDir

        val tlvBlockFileReader = TLVBlockFileReader(
            internalLogger = internalLogger,
            fileReaderWriter = fileReaderWriter
        )

        val dataStoreFileReader = DatastoreFileReader(
            dataStoreFileHelper = dataStoreFileHelper,
            featureName = featureName,
            internalLogger = internalLogger,
            storageDir = storageDir,
            tlvBlockFileReader = tlvBlockFileReader
        )

        val dataStoreFileWriter = DatastoreFileWriter(
            dataStoreFileHelper = dataStoreFileHelper,
            featureName = featureName,
            fileReaderWriter = fileReaderWriter,
            internalLogger = internalLogger,
            storageDir = storageDir
        )

        dataStore = DataStoreFileHandler(
            executorService = coreFeature.persistenceExecutorService,
            internalLogger = internalLogger,
            dataStoreFileReader = dataStoreFileReader,
            datastoreFileWriter = dataStoreFileWriter
        )
    }

    // endregion

    // Used for nightly tests only
    @WorkerThread
    internal fun flushStoredData() {
        val flusher = DataFlusher(
            contextProvider,
            fileOrchestrator,
            BatchFileReaderWriter.create(internalLogger, coreFeature.localDataEncryption),
            FileReaderWriter.create(internalLogger, coreFeature.localDataEncryption),
            FileMover(internalLogger),
            internalLogger
        )
        flusher.flush(uploader)
    }

    // endregion

    companion object {
        const val NO_EVENT_RECEIVER =
            "Feature \"%s\" has no event receiver registered, ignoring event."
        internal const val TRACK_NAME = "track"
        internal const val METER_NAME = "dd-sdk-android"
        internal const val BATCH_COUNT_METRIC_NAME = "android.benchmark.batch_count"
    }
}
