/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:Suppress("DEPRECATION")

package com.datadog.android.core.internal

import android.app.Application
import android.content.Context
import com.datadog.android.core.internal.data.upload.NoOpUploadScheduler
import com.datadog.android.core.internal.data.upload.UploadScheduler
import com.datadog.android.core.internal.persistence.file.NoOpFileOrchestrator
import com.datadog.android.plugin.DatadogPlugin
import com.datadog.android.plugin.DatadogPluginConfig
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.config.CoreFeatureTestConfiguration
import com.datadog.android.utils.config.InternalLoggerTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.EventBatchWriter
import com.datadog.android.v2.api.Feature
import com.datadog.android.v2.api.FeatureEventReceiver
import com.datadog.android.v2.api.FeatureStorageConfiguration
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.api.StorageBackedFeature
import com.datadog.android.v2.api.context.DatadogContext
import com.datadog.android.v2.core.internal.NoOpContextProvider
import com.datadog.android.v2.core.internal.data.upload.DataUploadScheduler
import com.datadog.android.v2.core.internal.net.DataOkHttpUploader
import com.datadog.android.v2.core.internal.net.NoOpDataUploader
import com.datadog.android.v2.core.internal.storage.NoOpStorage
import com.datadog.android.v2.core.internal.storage.Storage
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
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
    lateinit var mockSdkCore: SdkCore

    @Forgery
    lateinit var fakeConsent: TrackingConsent

    @Forgery
    lateinit var fakeStorageConfiguration: FeatureStorageConfiguration

    @StringForgery
    lateinit var fakeFeatureName: String

    private lateinit var mockPlugins: List<DatadogPlugin>

    @BeforeEach
    fun `set up`(forge: Forge) {
        whenever(coreFeature.mockTrackingConsentProvider.getConsent()) doReturn fakeConsent
        whenever(mockWrappedFeature.name) doReturn fakeFeatureName
        whenever(mockWrappedFeature.requestFactory) doReturn mock()
        whenever(mockWrappedFeature.storageConfiguration) doReturn fakeStorageConfiguration

        mockPlugins = forge.aList { mock() }

        testedFeature = SdkFeature(
            coreFeature = coreFeature.mockInstance,
            wrappedFeature = mockWrappedFeature
        )
    }

    @Test
    fun `𝕄 mark itself as initialized 𝕎 initialize()`() {
        // When
        testedFeature.initialize(mockSdkCore, appContext.mockInstance, mockPlugins)

        // Then
        assertThat(testedFeature.isInitialized()).isTrue()
    }

    @Test
    fun `𝕄 initialize uploader 𝕎 initialize()`() {
        // When
        testedFeature.initialize(mockSdkCore, appContext.mockInstance, mockPlugins)

        // Then
        assertThat(testedFeature.uploadScheduler)
            .isInstanceOf(DataUploadScheduler::class.java)
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
    fun `𝕄 not initialize storage and uploader 𝕎 initialize() { simple feature }`() {
        // Given
        val mockSimpleFeature = mock<Feature>().apply {
            whenever(name) doReturn fakeFeatureName
        }
        testedFeature = SdkFeature(
            coreFeature = coreFeature.mockInstance,
            wrappedFeature = mockSimpleFeature
        )

        // When
        testedFeature.initialize(mockSdkCore, appContext.mockInstance, mockPlugins)

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
    fun `𝕄 register plugins 𝕎 initialize()`() {
        // When
        testedFeature.initialize(mockSdkCore, appContext.mockInstance, mockPlugins)

        // Then
        argumentCaptor<DatadogPluginConfig> {
            mockPlugins.forEach {
                verify(it).register(capture())
            }
            allValues.forEach {
                assertThat(it.context).isEqualTo(appContext.mockInstance)
                assertThat(it.storageDir).isSameAs(coreFeature.fakeStorageDir)
                assertThat(it.serviceName).isEqualTo(coreFeature.fakeServiceName)
                assertThat(it.envName).isEqualTo(coreFeature.fakeEnvName)
                assertThat(it.context).isEqualTo(appContext.mockInstance)
                assertThat(it.trackingConsent).isEqualTo(fakeConsent)
            }
        }
    }

    @Test
    fun `𝕄 register plugins as TrackingConsentCallback 𝕎 initialize()`() {
        // When
        testedFeature.initialize(mockSdkCore, appContext.mockInstance, mockPlugins)

        // Then
        mockPlugins.forEach {
            verify(coreFeature.mockTrackingConsentProvider).registerCallback(it)
        }
    }

    @Test
    fun `𝕄 unregister plugins 𝕎 stop()`() {
        // Given
        testedFeature.initialize(mockSdkCore, appContext.mockInstance, mockPlugins)
        mockPlugins.forEach {
            verify(it).register(any())
        }

        // When
        testedFeature.stop()

        // Then
        mockPlugins.forEach {
            verify(it).unregister()
            verifyNoMoreInteractions(it)
        }
    }

    @Test
    fun `𝕄 stop scheduler 𝕎 stop()`() {
        // Given
        testedFeature.initialize(mockSdkCore, appContext.mockInstance, mockPlugins)
        val mockUploadScheduler: UploadScheduler = mock()
        testedFeature.uploadScheduler = mockUploadScheduler

        // When
        testedFeature.stop()

        // Then
        verify(mockUploadScheduler).stopScheduling()
    }

    @Test
    fun `𝕄 cleanup data 𝕎 stop()`() {
        // Given
        testedFeature.initialize(mockSdkCore, appContext.mockInstance, mockPlugins)

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
    }

    @Test
    fun `𝕄 mark itself as not initialized 𝕎 stop()`() {
        // Given
        testedFeature.initialize(mockSdkCore, appContext.mockInstance, mockPlugins)

        // When
        testedFeature.stop()

        // Then
        assertThat(testedFeature.isInitialized()).isFalse()
    }

    @Test
    fun `𝕄 call wrapped feature onStop 𝕎 stop()`() {
        // Given
        testedFeature.initialize(mockSdkCore, appContext.mockInstance, mockPlugins)

        // When
        testedFeature.stop()

        // Then
        verify(mockWrappedFeature).onStop()
    }

    @Test
    fun `𝕄 initialize only once 𝕎 initialize() twice`() {
        // Given
        testedFeature.initialize(mockSdkCore, appContext.mockInstance, mockPlugins)
        val uploadScheduler = testedFeature.uploadScheduler
        val uploader = testedFeature.uploader
        val storage = testedFeature.storage
        val fileOrchestrator = testedFeature.fileOrchestrator

        // When
        testedFeature.initialize(mockSdkCore, appContext.mockInstance, mockPlugins)

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
        testedFeature.initialize(mockSdkCore, appContext.mockInstance, mockPlugins)

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
        verifyZeroInteractions(mockStorage)
        verifyZeroInteractions(callback)
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
        verify(logger.mockInternalLogger).log(
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
            wrappedFeature = fakeFeature
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
            wrappedFeature = fakeFeature
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

        override fun onInitialize(sdkCore: SdkCore, appContext: Context) {
            // no-op
        }

        override fun onStop() {
            // no-op
        }
    }

    class AnotherFakeFeature(override val name: String) : Feature {

        override fun onInitialize(sdkCore: SdkCore, appContext: Context) {
            // no-op
        }

        override fun onStop() {
            // no-op
        }
    }

    // endregion

    companion object {
        val appContext = ApplicationContextTestConfiguration(Application::class.java)
        val coreFeature = CoreFeatureTestConfiguration(appContext)
        val logger = InternalLoggerTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(appContext, coreFeature, logger)
        }
    }
}
