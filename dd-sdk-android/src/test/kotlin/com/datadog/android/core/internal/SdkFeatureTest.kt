/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:Suppress("DEPRECATION")

package com.datadog.android.core.internal

import android.app.Application
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
import com.datadog.android.v2.api.FeatureEventReceiver
import com.datadog.android.v2.api.FeatureUploadConfiguration
import com.datadog.android.v2.api.InternalLogger
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
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
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

    @Forgery
    lateinit var fakeConsent: TrackingConsent

    @StringForgery
    lateinit var fakeFeatureName: String

    private lateinit var mockPlugins: List<DatadogPlugin>

    @BeforeEach
    fun `set up`(forge: Forge) {
        whenever(coreFeature.mockTrackingConsentProvider.getConsent()) doReturn fakeConsent

        mockPlugins = forge.aList { mock() }

        testedFeature = SdkFeature(
            coreFeature = coreFeature.mockInstance,
            featureName = fakeFeatureName,
            storageConfiguration = forge.getForgery(),
            uploadConfiguration = FeatureUploadConfiguration(
                requestFactory = mock()
            )
        )
    }

    @Test
    fun `𝕄 mark itself as initialized 𝕎 initialize()`() {
        // When
        testedFeature.initialize(appContext.mockInstance, mockPlugins)

        // Then
        assertThat(testedFeature.isInitialized()).isTrue()
    }

    @Test
    fun `𝕄 initialize uploader 𝕎 initialize()`() {
        // When
        testedFeature.initialize(appContext.mockInstance, mockPlugins)

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
    fun `𝕄 register plugins 𝕎 initialize()`() {
        // When
        testedFeature.initialize(appContext.mockInstance, mockPlugins)

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
        testedFeature.initialize(appContext.mockInstance, mockPlugins)

        // Then
        mockPlugins.forEach {
            verify(coreFeature.mockTrackingConsentProvider).registerCallback(it)
        }
    }

    @Test
    fun `𝕄 unregister plugins 𝕎 stop()`() {
        // Given
        testedFeature.initialize(appContext.mockInstance, mockPlugins)
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
        testedFeature.initialize(appContext.mockInstance, mockPlugins)
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
        testedFeature.initialize(appContext.mockInstance, mockPlugins)

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
        testedFeature.initialize(appContext.mockInstance, mockPlugins)

        // When
        testedFeature.stop()

        // Then
        assertThat(testedFeature.isInitialized()).isFalse()
    }

    @Test
    fun `𝕄 initialize only once 𝕎 initialize() twice`() {
        // Given
        testedFeature.initialize(appContext.mockInstance, mockPlugins)
        val uploadScheduler = testedFeature.uploadScheduler
        val uploader = testedFeature.uploader
        val storage = testedFeature.storage
        val fileOrchestrator = testedFeature.fileOrchestrator

        // When
        testedFeature.initialize(appContext.mockInstance, mockPlugins)

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
        testedFeature.initialize(appContext.mockInstance, mockPlugins)

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
        verify(logger.mockInternalLogger).log(
            InternalLogger.Level.INFO,
            InternalLogger.Target.USER,
            SdkFeature.NO_EVENT_RECEIVER.format(Locale.US, fakeFeatureName)
        )
    }

    // endRegion

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
