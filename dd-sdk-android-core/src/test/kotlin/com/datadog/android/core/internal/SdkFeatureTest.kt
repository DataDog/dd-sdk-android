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
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureEventReceiver
import com.datadog.android.api.feature.StorageBackedFeature
import com.datadog.android.api.storage.FeatureStorageConfiguration
import com.datadog.android.api.storage.datastore.DataStoreHandler
import com.datadog.android.core.configuration.BatchProcessingLevel
import com.datadog.android.core.configuration.BatchSize
import com.datadog.android.core.configuration.UploadFrequency
import com.datadog.android.core.internal.SdkFeature.Companion.BATCH_COUNT_METRIC_NAME
import com.datadog.android.core.internal.SdkFeature.Companion.METER_NAME
import com.datadog.android.core.internal.configuration.DataUploadConfiguration
import com.datadog.android.core.internal.data.upload.DataOkHttpUploader
import com.datadog.android.core.internal.data.upload.DataUploadRunnable
import com.datadog.android.core.internal.data.upload.DataUploadScheduler
import com.datadog.android.core.internal.data.upload.DefaultUploadSchedulerStrategy
import com.datadog.android.core.internal.data.upload.NoOpDataUploader
import com.datadog.android.core.internal.data.upload.NoOpUploadScheduler
import com.datadog.android.core.internal.data.upload.UploadScheduler
import com.datadog.android.core.internal.lifecycle.ProcessLifecycleMonitor
import com.datadog.android.core.internal.metrics.BatchMetricsDispatcher
import com.datadog.android.core.internal.metrics.BatchMetricsDispatcher.Companion.TRACK_KEY
import com.datadog.android.core.internal.metrics.NoOpMetricsDispatcher
import com.datadog.android.core.internal.persistence.AbstractStorage
import com.datadog.android.core.internal.persistence.ConsentAwareStorage
import com.datadog.android.core.internal.persistence.NoOpStorage
import com.datadog.android.core.internal.persistence.Storage
import com.datadog.android.core.internal.persistence.datastore.NoOpDataStoreHandler
import com.datadog.android.core.internal.persistence.file.FilePersistenceConfig
import com.datadog.android.core.internal.persistence.file.NoOpFileOrchestrator
import com.datadog.android.core.internal.persistence.file.batch.BatchFileOrchestrator
import com.datadog.android.core.persistence.PersistenceStrategy
import com.datadog.android.internal.profiler.BenchmarkMeter
import com.datadog.android.internal.profiler.BenchmarkSdkUploads
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.privacy.TrackingConsentProviderCallback
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.config.CoreFeatureTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.forge.aThrowable
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class SdkFeatureTest {

    private lateinit var testedFeature: SdkFeature

    @Mock
    lateinit var mockStorage: Storage

    @Mock
    lateinit var mockDataStore: DataStoreHandler

    @Mock
    lateinit var mockWrappedFeature: StorageBackedFeature

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockBenchmarkSdkUploads: BenchmarkSdkUploads

    @Mock
    lateinit var mockContextProvider: ContextProvider

    @Forgery
    lateinit var fakeConsent: TrackingConsent

    @Forgery
    lateinit var fakeStorageConfiguration: FeatureStorageConfiguration

    @StringForgery
    lateinit var fakeInstanceId: String

    @StringForgery
    lateinit var fakeFeatureName: String

    private lateinit var fakeCoreUploadFrequency: UploadFrequency

    private lateinit var fakeCoreBatchSize: BatchSize

    private lateinit var fakeCoreBatchProcessingLevel: BatchProcessingLevel

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeCoreUploadFrequency = forge.aValueFrom(UploadFrequency::class.java)
        fakeCoreBatchSize = forge.aValueFrom(BatchSize::class.java)
        fakeCoreBatchProcessingLevel = forge.aValueFrom(BatchProcessingLevel::class.java)
        whenever(coreFeature.mockInstance.batchSize).thenReturn(fakeCoreBatchSize)
        whenever(coreFeature.mockInstance.uploadFrequency).thenReturn(fakeCoreUploadFrequency)
        whenever(coreFeature.mockInstance.batchProcessingLevel)
            .thenReturn(fakeCoreBatchProcessingLevel)
        whenever(coreFeature.mockTrackingConsentProvider.getConsent()) doReturn fakeConsent
        whenever(coreFeature.mockInstance.initialized) doReturn AtomicBoolean(true)
        whenever(mockWrappedFeature.name) doReturn fakeFeatureName
        whenever(mockWrappedFeature.requestFactory) doReturn mock()
        whenever(mockWrappedFeature.storageConfiguration) doReturn fakeStorageConfiguration
        whenever(coreFeature.mockContextExecutorService.execute(any())) doAnswer {
            it.getArgument<Runnable>(0).run()
        }
        testedFeature = SdkFeature(
            coreFeature = coreFeature.mockInstance,
            contextProvider = mockContextProvider,
            wrappedFeature = mockWrappedFeature,
            internalLogger = mockInternalLogger
        )
    }

    @Test
    fun `M mark itself as initialized W initialize()`() {
        // When
        testedFeature.initialize(appContext.mockInstance, fakeInstanceId)

        // Then
        assertThat(testedFeature.isInitialized()).isTrue()
    }

    @Test
    fun `M register ProcessLifecycleMonitor for MetricsDispatcher W initialize()`() {
        // When
        testedFeature.initialize(appContext.mockInstance, fakeInstanceId)

        // Then
        argumentCaptor<Application.ActivityLifecycleCallbacks> {
            verify((appContext.mockInstance)).registerActivityLifecycleCallbacks(capture())
            assertThat(firstValue).isInstanceOf(ProcessLifecycleMonitor::class.java)
            assertThat((firstValue as ProcessLifecycleMonitor).callback)
                .isInstanceOf(BatchMetricsDispatcher::class.java)
        }
    }

    @Test
    fun `M not throw W initialize(){ no app context }`() {
        // When
        assertDoesNotThrow {
            testedFeature.initialize(mock(), fakeInstanceId)
        }
    }

    @Test
    fun `M initialize uploader W initialize()`() {
        // Given
        val expectedUploadConfiguration = DataUploadConfiguration(
            fakeCoreUploadFrequency,
            fakeCoreBatchProcessingLevel.maxBatchesPerUploadJob
        )

        // When
        testedFeature.initialize(appContext.mockInstance, fakeInstanceId)

        // Then
        assertThat(testedFeature.uploadScheduler).isInstanceOf(DataUploadScheduler::class.java)
        val dataUploadRunnable = (testedFeature.uploadScheduler as DataUploadScheduler).runnable
        val uploadSchedulerStrategy = (dataUploadRunnable.uploadSchedulerStrategy as? DefaultUploadSchedulerStrategy)
        assertThat(uploadSchedulerStrategy?.uploadConfiguration).isEqualTo(expectedUploadConfiguration)
        assertThat(dataUploadRunnable.maxBatchesPerJob).isEqualTo(fakeCoreBatchProcessingLevel.maxBatchesPerUploadJob)
        argumentCaptor<Runnable> {
            verify(coreFeature.mockUploadExecutor).execute(
                argThat { this is DataUploadRunnable }
            )
        }
        assertThat(testedFeature.uploader).isInstanceOf(DataOkHttpUploader::class.java)
    }

    @Test
    fun `M initialize the storage W initialize()`() {
        // Given
        val fakeCorePersistenceConfig = FilePersistenceConfig()
        whenever(coreFeature.mockInstance.buildFilePersistenceConfig())
            .thenReturn(fakeCorePersistenceConfig)

        // When
        testedFeature.initialize(appContext.mockInstance, fakeInstanceId)

        // Then
        assertThat(testedFeature.storage).isInstanceOf(ConsentAwareStorage::class.java)
        val consentAwareStorage = testedFeature.storage as ConsentAwareStorage
        assertThat(consentAwareStorage.filePersistenceConfig.recentDelayMs)
            .isEqualTo(fakeCoreBatchSize.windowDurationMs)
        val pendingFileOrchestrator =
            consentAwareStorage.pendingOrchestrator as BatchFileOrchestrator
        val grantedFileOrchestrator =
            consentAwareStorage.grantedOrchestrator as BatchFileOrchestrator
        val expectedFilePersistenceConfig = fakeCorePersistenceConfig.copy(
            maxBatchSize = fakeStorageConfiguration.maxBatchSize,
            maxItemSize = fakeStorageConfiguration.maxItemSize,
            maxItemsPerBatch = fakeStorageConfiguration.maxItemsPerBatch,
            oldFileThreshold = fakeStorageConfiguration.oldBatchThreshold,
            recentDelayMs = fakeCoreBatchSize.windowDurationMs
        )
        assertThat(pendingFileOrchestrator.config).isEqualTo(expectedFilePersistenceConfig)
        assertThat(grantedFileOrchestrator.config).isEqualTo(expectedFilePersistenceConfig)
    }

    @Test
    fun `M initialize the storage W initialize() {custom persistence strategy}`() {
        // Given
        val mockPersistenceStrategy = mock<PersistenceStrategy.Factory>()
        whenever(coreFeature.mockInstance.persistenceStrategyFactory) doReturn mockPersistenceStrategy

        // When
        testedFeature.initialize(appContext.mockInstance, fakeInstanceId)

        // Then
        assertThat(testedFeature.storage).isInstanceOf(AbstractStorage::class.java)
        val abstractStorage = testedFeature.storage as AbstractStorage
        assertThat(abstractStorage.sdkCoreId).isEqualTo(fakeInstanceId)
        assertThat(abstractStorage.persistenceStrategyFactory)
            .isEqualTo(mockPersistenceStrategy)
    }

    @Test
    fun `M register tracking consent callback W initialize(){feature+TrackingConsentProviderCallback}`() {
        // Given
        val mockFeature = mock<TrackingConsentFeature>()
        whenever(mockFeature.name).thenReturn(fakeFeatureName)
        testedFeature = SdkFeature(
            coreFeature.mockInstance,
            mockContextProvider,
            mockFeature,
            internalLogger = mockInternalLogger
        )

        // When
        testedFeature.initialize(appContext.mockInstance, fakeInstanceId)

        // Then
        verify(coreFeature.mockInstance.trackingConsentProvider)
            .registerCallback(mockFeature)
    }

    @Test
    fun `M not initialize storage and uploader W initialize() { simple feature }`() {
        // Given
        val mockSimpleFeature = mock<Feature>().apply {
            whenever(name) doReturn fakeFeatureName
        }
        testedFeature = SdkFeature(
            coreFeature = coreFeature.mockInstance,
            mockContextProvider,
            wrappedFeature = mockSimpleFeature,
            internalLogger = mockInternalLogger
        )

        // When
        testedFeature.initialize(appContext.mockInstance, fakeInstanceId)

        // Then
        assertThat(testedFeature.isInitialized()).isTrue

        assertThat(testedFeature.uploadScheduler)
            .isInstanceOf(NoOpUploadScheduler::class.java)
        assertThat(testedFeature.uploader)
            .isInstanceOf(NoOpDataUploader::class.java)
        assertThat(testedFeature.storage)
            .isInstanceOf(NoOpStorage::class.java)
        assertThat(testedFeature.fileOrchestrator)
            .isInstanceOf(NoOpFileOrchestrator::class.java)
    }

    @Test
    fun `M stop scheduler W stop()`() {
        // Given
        testedFeature.initialize(appContext.mockInstance, fakeInstanceId)
        val mockUploadScheduler: UploadScheduler = mock()
        testedFeature.uploadScheduler = mockUploadScheduler

        // When
        testedFeature.stop()

        // Then
        verify(mockUploadScheduler).stopScheduling()
    }

    @Test
    fun `M unregister ProcessLifecycleMonitor W stop()`() {
        // Given
        testedFeature.initialize(appContext.mockInstance, fakeInstanceId)

        // When
        testedFeature.stop()

        // Then
        verify(appContext.mockInstance).unregisterActivityLifecycleCallbacks(
            argThat { this is ProcessLifecycleMonitor }
        )
    }

    @Test
    fun `M cleanup data W stop()`() {
        // Given
        testedFeature.initialize(appContext.mockInstance, fakeInstanceId)

        // When
        testedFeature.stop()

        // Then
        assertThat(testedFeature.uploadScheduler)
            .isInstanceOf(NoOpUploadScheduler::class.java)
        assertThat(testedFeature.storage)
            .isInstanceOf(NoOpStorage::class.java)
        assertThat(testedFeature.uploader)
            .isInstanceOf(NoOpDataUploader::class.java)
        assertThat(testedFeature.fileOrchestrator)
            .isInstanceOf(NoOpFileOrchestrator::class.java)
        assertThat(testedFeature.processLifecycleMonitor).isNull()
        assertThat(testedFeature.metricsDispatcher).isInstanceOf(NoOpMetricsDispatcher::class.java)
        assertThat(testedFeature.featureContext).isEmpty()
    }

    @Test
    fun `M mark itself as not initialized W stop()`() {
        // Given
        testedFeature.initialize(appContext.mockInstance, fakeInstanceId)

        // When
        testedFeature.stop()

        // Then
        assertThat(testedFeature.isInitialized()).isFalse()
    }

    @Test
    fun `M call wrapped feature onStop W stop()`() {
        // Given
        testedFeature.initialize(appContext.mockInstance, fakeInstanceId)

        // When
        testedFeature.stop()

        // Then
        verify(mockWrappedFeature).onStop()
    }

    @Test
    fun `M unregister tracking consent callback W stop(){feature+TrackingConsentProviderCallback}`() {
        // Given
        val mockFeature = mock<TrackingConsentFeature>().apply {
            whenever(name) doReturn fakeFeatureName
        }
        testedFeature = SdkFeature(
            coreFeature = coreFeature.mockInstance,
            contextProvider = mockContextProvider,
            wrappedFeature = mockFeature,
            internalLogger = mockInternalLogger
        )
        testedFeature.initialize(appContext.mockInstance, fakeInstanceId)

        // When
        testedFeature.stop()

        // Then
        verify(coreFeature.mockInstance.trackingConsentProvider).unregisterCallback(mockFeature)
    }

    @Test
    fun `M initialize only once W initialize() twice`() {
        // Given
        testedFeature.initialize(appContext.mockInstance, fakeInstanceId)
        val uploadScheduler = testedFeature.uploadScheduler
        val uploader = testedFeature.uploader
        val storage = testedFeature.storage
        val fileOrchestrator = testedFeature.fileOrchestrator

        // When
        testedFeature.initialize(appContext.mockInstance, fakeInstanceId)

        // Then
        assertThat(testedFeature.uploadScheduler).isSameAs(uploadScheduler)
        assertThat(testedFeature.uploader).isSameAs(uploader)
        assertThat(testedFeature.storage).isSameAs(storage)
        assertThat(testedFeature.fileOrchestrator).isSameAs(fileOrchestrator)
    }

    @Test
    fun `M not setup uploader W initialize() in secondary process`() {
        // Given
        whenever(testedFeature.coreFeature.isMainProcess) doReturn false

        // When
        testedFeature.initialize(appContext.mockInstance, fakeInstanceId)

        // Then
        assertThat(testedFeature.uploadScheduler).isInstanceOf(NoOpUploadScheduler::class.java)
    }

    @Test
    fun `M clear local storage W clearAllData()`() {
        // Given
        testedFeature.storage = mockStorage

        // When
        testedFeature.clearAllData()

        // Then
        verify(mockStorage).dropAll()
    }

    @Test
    fun `M clear data store W clearAllData()`() {
        // Given
        testedFeature.dataStore = mockDataStore

        // When
        testedFeature.clearAllData()

        // Then
        verify(mockDataStore).clearAllData()
    }

    // region FeatureScope

    @Test
    fun `M unregister datastore W stop()`() {
        // Given
        testedFeature.initialize(appContext.mockInstance, fakeInstanceId)

        assertThat(testedFeature.dataStore)
            .isNotInstanceOf(NoOpDataStoreHandler::class.java)

        // When
        testedFeature.stop()

        // Then
        assertThat(testedFeature.dataStore).isInstanceOf(NoOpDataStoreHandler::class.java)
    }

    @Test
    fun `M register datastore W initialize()`() {
        // When
        testedFeature.initialize(appContext.mockInstance, fakeInstanceId)

        // Then
        assertThat(testedFeature.dataStore)
            .isNotInstanceOf(NoOpDataStoreHandler::class.java)
    }

    @Test
    fun `M provide write context W withWriteContext(callback)`(
        @Forgery fakeContext: DatadogContext,
        @StringForgery fakeWithFeatureContexts: Set<String>,
        @Mock mockEventWriteScope: EventWriteScope
    ) {
        // Given
        testedFeature.storage = mockStorage
        val callback = mock<(DatadogContext, EventWriteScope) -> Unit>()
        whenever(mockContextProvider.getContext(fakeWithFeatureContexts)) doReturn fakeContext

        whenever(
            mockStorage.getEventWriteScope(fakeContext)
        ) doReturn mockEventWriteScope

        // When
        testedFeature.withWriteContext(fakeWithFeatureContexts, callback = callback)

        // Then
        verify(callback).invoke(
            fakeContext,
            mockEventWriteScope
        )
    }

    @Test
    fun `M not provide write context W withWriteContext(callback) { CoreFeature is not initialized }`(
        @StringForgery fakeWithFeatureContexts: Set<String>
    ) {
        // Given
        testedFeature.storage = mockStorage
        val callback = mock<(DatadogContext, EventWriteScope) -> Unit>()
        whenever(coreFeature.mockInstance.initialized) doReturn AtomicBoolean(false)

        // When
        testedFeature.withWriteContext(fakeWithFeatureContexts, callback = callback)

        // Then
        verifyNoInteractions(callback, mockContextProvider, mockStorage)
    }

    @Test
    fun `M provide Datadog context W withContext(callback)`(
        @Forgery fakeContext: DatadogContext,
        @StringForgery fakeWithFeatureContexts: Set<String>
    ) {
        // Given
        testedFeature.storage = mockStorage
        val callback = mock<(DatadogContext) -> Unit>()
        whenever(mockContextProvider.getContext(fakeWithFeatureContexts)) doReturn fakeContext

        // When
        testedFeature.withContext(fakeWithFeatureContexts, callback = callback)

        // Then
        verify(callback).invoke(fakeContext)
    }

    @Test
    fun `M not provide Datadog context W withContext(callback) { CoreFeature is not initialized }`(
        @StringForgery fakeWithFeatureContexts: Set<String>
    ) {
        // Given
        testedFeature.storage = mockStorage
        val callback = mock<(DatadogContext) -> Unit>()
        whenever(coreFeature.mockInstance.initialized) doReturn AtomicBoolean(false)

        // When
        testedFeature.withContext(fakeWithFeatureContexts, callback = callback)

        // Then
        verifyNoInteractions(callback, mockContextProvider)
    }

    @Test
    fun `M provide write context W getWriteContextSync()`(
        @Forgery fakeContext: DatadogContext,
        @StringForgery fakeWithFeatureContexts: Set<String>,
        @Mock mockEventWriteScope: EventWriteScope
    ) {
        // Given
        testedFeature.storage = mockStorage
        whenever(mockContextProvider.getContext(fakeWithFeatureContexts)) doReturn fakeContext
        whenever(coreFeature.mockInstance.contextExecutorService.submit(any<Callable<*>>())) doAnswer {
            val callable = it.getArgument<Callable<Pair<DatadogContext, EventWriteScope>>>(0)
            mock<Future<*>>().apply {
                whenever(get()) doAnswer { callable.call() }
            }
        }

        whenever(
            mockStorage.getEventWriteScope(fakeContext)
        ) doReturn mockEventWriteScope

        // When
        val writeContext = testedFeature.getWriteContextSync(fakeWithFeatureContexts)

        // Then
        checkNotNull(writeContext)
        assertThat(writeContext.first).isEqualTo(fakeContext)
        assertThat(writeContext.second).isEqualTo(mockEventWriteScope)
    }

    @Test
    fun `M provide null write context W getWriteContextSync() { task rejected }`(
        @Forgery fakeContext: DatadogContext,
        @Mock mockEventWriteScope: EventWriteScope
    ) {
        // Given
        testedFeature.storage = mockStorage
        whenever(
            coreFeature.mockInstance.contextExecutorService.submit(any<Callable<*>>())
        ) doThrow RejectedExecutionException()

        whenever(
            mockStorage.getEventWriteScope(fakeContext)
        ) doReturn mockEventWriteScope

        // When
        val writeContext = testedFeature.getWriteContextSync()

        // Then
        assertThat(writeContext).isNull()
    }

    @Test
    fun `M provide null write context W getWriteContextSync() { failed to get task result }`(
        @Forgery fakeContext: DatadogContext,
        @Mock mockEventWriteScope: EventWriteScope,
        forge: Forge
    ) {
        // Given
        testedFeature.storage = mockStorage
        val throwable = forge.anElementFrom(
            CancellationException(),
            ExecutionException(forge.aThrowable()),
            InterruptedException()
        )
        whenever(coreFeature.mockInstance.contextExecutorService.submit(any<Callable<*>>())) doAnswer {
            mock<Future<*>>().apply {
                whenever(get()) doThrow throwable
            }
        }

        whenever(
            mockStorage.getEventWriteScope(fakeContext)
        ) doReturn mockEventWriteScope

        // When
        val writeContext = testedFeature.getWriteContextSync()

        // Then
        assertThat(writeContext).isNull()
    }

    @Test
    fun `M provide null write context W getWriteContextSync() { CoreFeature is not initialized }`() {
        // Given
        whenever(coreFeature.mockInstance.initialized) doReturn AtomicBoolean(false)

        // When
        val writeContext = testedFeature.getWriteContextSync()

        // Then
        assertThat(writeContext).isNull()
        verifyNoInteractions(mockContextProvider, mockStorage)
    }

    @Test
    fun `M send event W sendEvent(event)`() {
        // Given
        val mockEventReceiver = mock<FeatureEventReceiver>()
        testedFeature.eventReceiver.set(mockEventReceiver)
        val fakeEvent = Any()

        // When
        testedFeature.sendEvent(fakeEvent)

        // Then
        verify(mockEventReceiver).onReceive(fakeEvent)
    }

    @Test
    fun `M notify no receiver W sendEvent(event)`() {
        // Given
        val fakeEvent = Any()

        // When
        testedFeature.sendEvent(fakeEvent)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.INFO,
            InternalLogger.Target.USER,
            SdkFeature.NO_EVENT_RECEIVER.format(Locale.US, fakeFeatureName)
        )
    }

    @Test
    fun `M give wrapped feature W unwrap()`(
        @StringForgery fakeFeatureName: String
    ) {
        // Given
        val fakeFeature = FakeFeature(fakeFeatureName)

        testedFeature = SdkFeature(
            coreFeature = coreFeature.mockInstance,
            contextProvider = mockContextProvider,
            wrappedFeature = fakeFeature,
            internalLogger = mockInternalLogger
        )

        // When
        val unwrappedFeature = testedFeature.unwrap<FakeFeature>()

        // Then
        assertThat(unwrappedFeature).isSameAs(fakeFeature)
    }

    @Test
    fun `M throw exception W unwrap() { wrong class }`(
        @StringForgery fakeFeatureName: String
    ) {
        // Given
        val fakeFeature = FakeFeature(fakeFeatureName)

        testedFeature = SdkFeature(
            coreFeature = coreFeature.mockInstance,
            contextProvider = mockContextProvider,
            wrappedFeature = fakeFeature,
            internalLogger = mockInternalLogger
        )

        // When + Then
        assertThrows<ClassCastException> {
            // strange enough nothing is thrown if we don't save the result.
            // Kotlin compiler removing/optimizing code unused?
            @Suppress("UNUSED_VARIABLE")
            val result = testedFeature.unwrap<AnotherFakeFeature>()
        }
    }

    // endregion

    // region Feature fakes

    class FakeFeature(override val name: String) : Feature {

        override fun onInitialize(appContext: Context) {
            // no-op
        }

        override fun onStop() {
            // no-op
        }
    }

    class AnotherFakeFeature(override val name: String) : Feature {

        override fun onInitialize(appContext: Context) {
            // no-op
        }

        override fun onStop() {
            // no-op
        }
    }

    class TrackingConsentFeature(override val name: String) :
        Feature,
        TrackingConsentProviderCallback {

        override fun onInitialize(appContext: Context) {
            // no-op
        }

        override fun onStop() {
            // no-op
        }

        override fun onConsentUpdated(
            previousConsent: TrackingConsent,
            newConsent: TrackingConsent
        ) {
            // no-op
        }
    }

    // endregion

    // region Batch Count Benchmark

    @Test
    fun `M send batch count benchmark metrics W initialize`(
        @Mock mockContext: Context
    ) {
        // Given
        val mockMeter: BenchmarkMeter = mock()
        whenever(mockBenchmarkSdkUploads.getMeter(METER_NAME))
            .thenReturn(mockMeter)

        testedFeature = SdkFeature(
            coreFeature = coreFeature.mockInstance,
            contextProvider = mockContextProvider,
            wrappedFeature = mockWrappedFeature,
            internalLogger = mockInternalLogger,
            benchmarkSdkUploads = mockBenchmarkSdkUploads
        )

        // When
        testedFeature.initialize(mockContext, fakeInstanceId)

        // Then
        verify(
            mockBenchmarkSdkUploads
                .getMeter(METER_NAME)
        )
            .createObservableGauge(
                metricName = eq(BATCH_COUNT_METRIC_NAME),
                tags = eq(mapOf(TRACK_KEY to fakeFeatureName)),
                callback = any()
            )
    }

    // endregion

    companion object {
        val appContext = ApplicationContextTestConfiguration(Application::class.java)
        val coreFeature = CoreFeatureTestConfiguration(appContext)

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(appContext, coreFeature)
        }
    }
}
