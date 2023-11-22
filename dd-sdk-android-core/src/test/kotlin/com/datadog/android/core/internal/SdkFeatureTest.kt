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
import com.datadog.android.api.feature.StorageBackedFeature
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.FeatureStorageConfiguration
import com.datadog.android.core.configuration.BatchProcessingLevel
import com.datadog.android.core.configuration.BatchSize
import com.datadog.android.core.configuration.UploadFrequency
import com.datadog.android.core.internal.configuration.DataUploadConfiguration
import com.datadog.android.core.internal.data.upload.DataOkHttpUploader
import com.datadog.android.core.internal.data.upload.NoOpUploadScheduler
import com.datadog.android.core.internal.data.upload.UploadScheduler
import com.datadog.android.core.internal.data.upload.v2.DataUploadScheduler
import com.datadog.android.core.internal.data.upload.v2.NoOpDataUploader
import com.datadog.android.core.internal.lifecycle.ProcessLifecycleMonitor
import com.datadog.android.core.internal.metrics.BatchMetricsDispatcher
import com.datadog.android.core.internal.metrics.NoOpMetricsDispatcher
import com.datadog.android.core.internal.persistence.ConsentAwareStorage
import com.datadog.android.core.internal.persistence.NoOpStorage
import com.datadog.android.core.internal.persistence.Storage
import com.datadog.android.core.internal.persistence.file.FilePersistenceConfig
import com.datadog.android.core.internal.persistence.file.NoOpFileOrchestrator
import com.datadog.android.core.internal.persistence.file.batch.BatchFileOrchestrator
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.privacy.TrackingConsentProviderCallback
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.config.CoreFeatureTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Locale
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class SdkFeatureTest {

    lateinit var testedFeature: SdkFeature

    @Mock
    lateinit var mockStorage: Storage

    @Mock
    lateinit var mockWrappedFeature: StorageBackedFeature

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Forgery
    lateinit var fakeConsent: TrackingConsent

    @Forgery
    lateinit var fakeStorageConfiguration: FeatureStorageConfiguration

    @StringForgery
    lateinit var fakeFeatureName: String

    private lateinit var fakeCoreUploadFrequency: UploadFrequency

    private lateinit var fakeCoreBatchSize: BatchSize

    private lateinit var fakeCoreBatchProcessingLevel: BatchProcessingLevel

    @BeforeEach
    fun `set up`(forge: Forge) {
        // make sure this has a clean state
        fakeStorageConfiguration = fakeStorageConfiguration.copy(
            uploadFrequency = null,
            batchSize = null,
            batchProcessingLevel = null
        )
        fakeCoreUploadFrequency = forge.aValueFrom(UploadFrequency::class.java)
        fakeCoreBatchSize = forge.aValueFrom(BatchSize::class.java)
        fakeCoreBatchProcessingLevel = forge.aValueFrom(BatchProcessingLevel::class.java)
        whenever(coreFeature.mockInstance.batchSize).thenReturn(fakeCoreBatchSize)
        whenever(coreFeature.mockInstance.uploadFrequency).thenReturn(fakeCoreUploadFrequency)
        whenever(coreFeature.mockInstance.batchProcessingLevel)
            .thenReturn(fakeCoreBatchProcessingLevel)
        whenever(coreFeature.mockTrackingConsentProvider.getConsent()) doReturn fakeConsent
        whenever(mockWrappedFeature.name) doReturn fakeFeatureName
        whenever(mockWrappedFeature.requestFactory) doReturn mock()
        whenever(mockWrappedFeature.storageConfiguration) doReturn fakeStorageConfiguration
        testedFeature = SdkFeature(
            coreFeature = coreFeature.mockInstance,
            wrappedFeature = mockWrappedFeature,
            internalLogger = mockInternalLogger
        )
    }

    @Test
    fun `𝕄 mark itself as initialized 𝕎 initialize()`() {
        // When
        testedFeature.initialize(appContext.mockInstance)

        // Then
        assertThat(testedFeature.isInitialized()).isTrue()
    }

    @Test
    fun `M register ProcessLifecycleMonitor for MetricsDispatcher W initialize()`() {
        // When
        testedFeature.initialize(appContext.mockInstance)

        // Then
        argumentCaptor<Application.ActivityLifecycleCallbacks>() {
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
            testedFeature.initialize(mock())
        }
    }

    @Test
    fun `𝕄 initialize uploader 𝕎 initialize()`() {
        // Given
        val expectedUploadConfiguration = DataUploadConfiguration(
            fakeCoreUploadFrequency,
            fakeCoreBatchProcessingLevel.maxBatchesPerUploadJob
        )

        // When
        testedFeature.initialize(appContext.mockInstance)

        // Then
        assertThat(testedFeature.uploadScheduler)
            .isInstanceOf(DataUploadScheduler::class.java)
        val dataUploadRunnable = (testedFeature.uploadScheduler as DataUploadScheduler).runnable
        assertThat(dataUploadRunnable.minDelayMs).isEqualTo(expectedUploadConfiguration.minDelayMs)
        assertThat(dataUploadRunnable.maxDelayMs).isEqualTo(expectedUploadConfiguration.maxDelayMs)
        assertThat(dataUploadRunnable.currentDelayIntervalMs)
            .isEqualTo(expectedUploadConfiguration.defaultDelayMs)
        assertThat(dataUploadRunnable.maxBatchesPerJob)
            .isEqualTo(fakeCoreBatchProcessingLevel.maxBatchesPerUploadJob)
        argumentCaptor<Runnable> {
            verify(coreFeature.mockUploadExecutor).schedule(
                any(),
                any(),
                eq(TimeUnit.MILLISECONDS)
            )
        }
        assertThat(testedFeature.uploader).isInstanceOf(DataOkHttpUploader::class.java)
    }

    @Test
    fun `𝕄 use the storage frequency if set 𝕎 initialize()`(forge: Forge) {
        // Given
        val fakeUploadFrequency = forge.aValueFrom(UploadFrequency::class.java)
        val expectedUploadConfiguration: DataUploadConfiguration =
            forge.getForgery<DataUploadConfiguration>().copy(frequency = fakeUploadFrequency)
        val fakeStorageConfig = mockWrappedFeature.storageConfiguration
            .copy(uploadFrequency = fakeUploadFrequency)
        whenever(mockWrappedFeature.storageConfiguration)
            .thenReturn(fakeStorageConfig)

        // When
        testedFeature.initialize(appContext.mockInstance)

        // Then
        assertThat(testedFeature.uploadScheduler)
            .isInstanceOf(DataUploadScheduler::class.java)
        val dataUploadRunnable = (testedFeature.uploadScheduler as DataUploadScheduler).runnable
        assertThat(dataUploadRunnable.minDelayMs).isEqualTo(expectedUploadConfiguration.minDelayMs)
        assertThat(dataUploadRunnable.maxDelayMs).isEqualTo(expectedUploadConfiguration.maxDelayMs)
        assertThat(dataUploadRunnable.currentDelayIntervalMs)
            .isEqualTo(expectedUploadConfiguration.defaultDelayMs)
    }

    @Test
    fun `𝕄 use the storage batchProcessingLevel if set 𝕎 initialize()`(forge: Forge) {
        // Given
        val fakeBatchProcessingLevel = forge.aValueFrom(BatchProcessingLevel::class.java)
        val fakeStorageConfig = mockWrappedFeature.storageConfiguration
            .copy(batchProcessingLevel = fakeBatchProcessingLevel)
        whenever(mockWrappedFeature.storageConfiguration)
            .thenReturn(fakeStorageConfig)

        // When
        testedFeature.initialize(appContext.mockInstance)

        // Then
        assertThat(testedFeature.uploadScheduler)
            .isInstanceOf(DataUploadScheduler::class.java)
        val dataUploadRunnable = (testedFeature.uploadScheduler as DataUploadScheduler).runnable
        assertThat(dataUploadRunnable.maxBatchesPerJob)
            .isEqualTo(fakeBatchProcessingLevel.maxBatchesPerUploadJob)
    }

    @Test
    fun `𝕄 initialize the storage 𝕎 initialize()`() {
        // Given
        val fakeCorePersistenceConfig = FilePersistenceConfig()
        whenever(coreFeature.mockInstance.buildFilePersistenceConfig())
            .thenReturn(fakeCorePersistenceConfig)

        // When
        testedFeature.initialize(appContext.mockInstance)

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
            recentDelayMs = fakeStorageConfiguration.batchSize?.windowDurationMs
                ?: fakeCoreBatchSize.windowDurationMs
        )
        assertThat(pendingFileOrchestrator.config).isEqualTo(expectedFilePersistenceConfig)
        assertThat(grantedFileOrchestrator.config).isEqualTo(expectedFilePersistenceConfig)
    }

    @Test
    fun `𝕄 use the storage batchSize if set 𝕎 initialize()`(forge: Forge) {
        // Given
        val fakeBatchSize = forge.aValueFrom(BatchSize::class.java)
        val fakeStorageConfig = mockWrappedFeature.storageConfiguration
            .copy(batchSize = fakeBatchSize)
        whenever(mockWrappedFeature.storageConfiguration)
            .thenReturn(fakeStorageConfig)

        // When
        testedFeature.initialize(appContext.mockInstance)

        // Then
        assertThat(testedFeature.storage).isInstanceOf(ConsentAwareStorage::class.java)
        val consentAwareStorage = testedFeature.storage as ConsentAwareStorage
        assertThat(consentAwareStorage.filePersistenceConfig.recentDelayMs)
            .isEqualTo(fakeBatchSize.windowDurationMs)
    }

    @Test
    fun `𝕄 register tracking consent callback 𝕎 initialize(){feature+TrackingConsentProviderCallback}`() {
        // Given
        val mockFeature = mock<TrackingConsentFeature>()
        testedFeature = SdkFeature(
            coreFeature.mockInstance,
            mockFeature,
            internalLogger = mockInternalLogger
        )

        // When
        testedFeature.initialize(appContext.mockInstance)

        // Then
        verify(coreFeature.mockInstance.trackingConsentProvider)
            .registerCallback(mockFeature)
    }

    @Test
    fun `𝕄 not initialize storage and uploader 𝕎 initialize() { simple feature }`() {
        // Given
        val mockSimpleFeature = mock<Feature>().apply {
            whenever(name) doReturn fakeFeatureName
        }
        testedFeature = SdkFeature(
            coreFeature = coreFeature.mockInstance,
            wrappedFeature = mockSimpleFeature,
            internalLogger = mockInternalLogger
        )

        // When
        testedFeature.initialize(appContext.mockInstance)

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
    fun `𝕄 stop scheduler 𝕎 stop()`() {
        // Given
        testedFeature.initialize(appContext.mockInstance)
        val mockUploadScheduler: UploadScheduler = mock()
        testedFeature.uploadScheduler = mockUploadScheduler

        // When
        testedFeature.stop()

        // Then
        verify(mockUploadScheduler).stopScheduling()
    }

    @Test
    fun `𝕄 unregister ProcessLifecycleMonitor 𝕎 stop()`() {
        // Given
        testedFeature.initialize(appContext.mockInstance)

        // When
        testedFeature.stop()

        // Then
        verify(appContext.mockInstance).unregisterActivityLifecycleCallbacks(
            argThat { this is ProcessLifecycleMonitor }
        )
    }

    @Test
    fun `𝕄 cleanup data 𝕎 stop()`() {
        // Given
        testedFeature.initialize(appContext.mockInstance)

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
    }

    @Test
    fun `𝕄 mark itself as not initialized 𝕎 stop()`() {
        // Given
        testedFeature.initialize(appContext.mockInstance)

        // When
        testedFeature.stop()

        // Then
        assertThat(testedFeature.isInitialized()).isFalse()
    }

    @Test
    fun `𝕄 call wrapped feature onStop 𝕎 stop()`() {
        // Given
        testedFeature.initialize(appContext.mockInstance)

        // When
        testedFeature.stop()

        // Then
        verify(mockWrappedFeature).onStop()
    }

    @Test
    fun `𝕄 unregister tracking consent callback 𝕎 stop(){feature+TrackingConsentProviderCallback}`() {
        // Given
        val mockFeature = mock<TrackingConsentFeature>().apply {
            whenever(name) doReturn fakeFeatureName
        }
        testedFeature = SdkFeature(
            coreFeature = coreFeature.mockInstance,
            wrappedFeature = mockFeature,
            internalLogger = mockInternalLogger
        )
        testedFeature.initialize(appContext.mockInstance)

        // When
        testedFeature.stop()

        // Then
        verify(coreFeature.mockInstance.trackingConsentProvider).unregisterCallback(mockFeature)
    }

    @Test
    fun `𝕄 initialize only once 𝕎 initialize() twice`() {
        // Given
        testedFeature.initialize(appContext.mockInstance)
        val uploadScheduler = testedFeature.uploadScheduler
        val uploader = testedFeature.uploader
        val storage = testedFeature.storage
        val fileOrchestrator = testedFeature.fileOrchestrator

        // When
        testedFeature.initialize(appContext.mockInstance)

        // Then
        assertThat(testedFeature.uploadScheduler).isSameAs(uploadScheduler)
        assertThat(testedFeature.uploader).isSameAs(uploader)
        assertThat(testedFeature.storage).isSameAs(storage)
        assertThat(testedFeature.fileOrchestrator).isSameAs(fileOrchestrator)
    }

    @Test
    fun `𝕄 not setup uploader 𝕎 initialize() in secondary process`() {
        // Given
        whenever(testedFeature.coreFeature.isMainProcess) doReturn false

        // When
        testedFeature.initialize(appContext.mockInstance)

        // Then
        assertThat(testedFeature.uploadScheduler).isInstanceOf(NoOpUploadScheduler::class.java)
    }

    @Test
    fun `𝕄 clear local storage 𝕎 clearAllData()`() {
        // Given
        testedFeature.storage = mockStorage

        // When
        testedFeature.clearAllData()

        // Then
        verify(mockStorage).dropAll()
    }

    // region FeatureScope

    @Test
    fun `𝕄 provide write context 𝕎 withWriteContext(callback)`(
        @BoolForgery forceNewBatch: Boolean,
        @Forgery fakeContext: DatadogContext,
        @Mock mockBatchWriter: EventBatchWriter
    ) {
        // Given
        testedFeature.storage = mockStorage
        val callback = mock<(DatadogContext, EventBatchWriter) -> Unit>()
        whenever(coreFeature.mockInstance.contextProvider.context) doReturn fakeContext

        whenever(
            mockStorage.writeCurrentBatch(
                eq(fakeContext),
                eq(forceNewBatch),
                any()
            )
        ) doAnswer {
            val storageCallback = it.getArgument<(EventBatchWriter) -> Unit>(2)
            storageCallback.invoke(mockBatchWriter)
        }

        // When
        testedFeature.withWriteContext(forceNewBatch, callback = callback)

        // Then
        verify(callback).invoke(
            fakeContext,
            mockBatchWriter
        )
    }

    @Test
    fun `𝕄 do nothing 𝕎 withWriteContext(callback) { no Datadog context }`(
        @BoolForgery forceNewBatch: Boolean
    ) {
        // Given
        testedFeature.storage = mockStorage
        val callback = mock<(DatadogContext, EventBatchWriter) -> Unit>()

        whenever(coreFeature.mockInstance.contextProvider) doReturn NoOpContextProvider()

        // When
        testedFeature.withWriteContext(forceNewBatch, callback = callback)

        // Then
        verifyNoInteractions(mockStorage)
        verifyNoInteractions(callback)
    }

    @Test
    fun `𝕄 send event 𝕎 sendEvent(event)`() {
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
    fun `𝕄 notify no receiver 𝕎 sendEvent(event)`() {
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
    fun `𝕄 give wrapped feature 𝕎 unwrap()`(
        @StringForgery fakeFeatureName: String
    ) {
        // Given
        val fakeFeature = FakeFeature(fakeFeatureName)

        testedFeature = SdkFeature(
            coreFeature = coreFeature.mockInstance,
            wrappedFeature = fakeFeature,
            internalLogger = mockInternalLogger
        )

        // When
        val unwrappedFeature = testedFeature.unwrap<FakeFeature>()

        // Then
        assertThat(unwrappedFeature).isSameAs(fakeFeature)
    }

    @Test
    fun `𝕄 throw exception 𝕎 unwrap() { wrong class }`(
        @StringForgery fakeFeatureName: String
    ) {
        // Given
        val fakeFeature = FakeFeature(fakeFeatureName)

        testedFeature = SdkFeature(
            coreFeature = coreFeature.mockInstance,
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
