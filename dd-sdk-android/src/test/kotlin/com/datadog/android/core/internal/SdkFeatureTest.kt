/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal

import android.app.Application
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.internal.data.upload.NoOpUploadScheduler
import com.datadog.android.core.internal.data.upload.UploadScheduler
import com.datadog.android.core.internal.net.DataUploader
import com.datadog.android.core.internal.persistence.DataReader
import com.datadog.android.core.internal.persistence.NoOpPersistenceStrategy
import com.datadog.android.core.internal.persistence.PersistenceStrategy
import com.datadog.android.error.internal.CrashReportsFeature
import com.datadog.android.log.internal.LogsFeature
import com.datadog.android.plugin.DatadogPluginConfig
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.tracing.internal.TracingFeature
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.config.CoreFeatureTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.core.internal.data.upload.DataFlusher
import com.datadog.android.v2.core.internal.data.upload.DataUploadScheduler
import com.datadog.android.v2.core.internal.net.DataOkHttpUploader
import com.datadog.android.webview.internal.log.WebViewLogsFeature
import com.datadog.android.webview.internal.rum.WebViewRumFeature
import com.datadog.tools.unit.annotations.ProhibitLeavingStaticMocksIn
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.ProhibitLeavingStaticMocksExtension
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ProhibitLeavingStaticMocksExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
@ProhibitLeavingStaticMocksIn(
    CoreFeature::class,
    RumFeature::class,
    LogsFeature::class,
    TracingFeature::class,
    WebViewLogsFeature::class,
    WebViewRumFeature::class,
    CrashReportsFeature::class
)
internal abstract class SdkFeatureTest<T : Any, C : Configuration.Feature, F : SdkFeature<T, C>> {

    lateinit var testedFeature: F

    @Mock
    lateinit var mockPersistenceStrategy: PersistenceStrategy<T>

    @Mock
    lateinit var mockReader: DataReader

    @Mock
    lateinit var mockUploader: DataUploader

    lateinit var fakeConfigurationFeature: C

    @Forgery
    lateinit var fakeConsent: TrackingConsent

    @BeforeEach
    fun `set up`(forge: Forge) {
        whenever(mockPersistenceStrategy.getReader()) doReturn mockReader
        whenever(coreFeature.mockTrackingConsentProvider.getConsent()) doReturn fakeConsent

        fakeConfigurationFeature = forgeConfiguration(forge)
        testedFeature = createTestedFeature()
    }

    abstract fun createTestedFeature(): F

    abstract fun forgeConfiguration(forge: Forge): C

    abstract fun featureDirName(): String

    @Test
    fun `𝕄 mark itself as initialized 𝕎 initialize()`() {
        // When
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // Then
        assertThat(testedFeature.isInitialized()).isTrue()
    }

    @Test
    fun `𝕄 initialize uploader 𝕎 initialize()`() {
        // When
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

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

        assertThat(testedFeature.uploader)
            .isInstanceOf(DataOkHttpUploader::class.java)

        val uploader = testedFeature.uploader as DataOkHttpUploader
        assertThat(uploader.callFactory).isSameAs(coreFeature.mockInstance.okHttpClient)
    }

    @Test
    fun `𝕄 register plugins 𝕎 initialize()`() {
        // Given
        assumeTrue(fakeConfigurationFeature.plugins.isNotEmpty())

        // When
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // Then
        argumentCaptor<DatadogPluginConfig> {
            fakeConfigurationFeature.plugins.forEach {
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
        // Given
        assumeTrue(fakeConfigurationFeature.plugins.isNotEmpty())

        // When
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // Then
        fakeConfigurationFeature.plugins.forEach {
            verify(coreFeature.mockTrackingConsentProvider).registerCallback(it)
        }
    }

    @Test
    fun `𝕄 unregister plugins 𝕎 stop()`() {
        // Given
        assumeTrue(fakeConfigurationFeature.plugins.isNotEmpty())
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)
        fakeConfigurationFeature.plugins.forEach {
            verify(it).register(any())
        }

        // When
        testedFeature.stop()

        // Then
        fakeConfigurationFeature.plugins.forEach {
            verify(it).unregister()
            verifyNoMoreInteractions(it)
        }
    }

    @Test
    fun `𝕄 stop scheduler 𝕎 stop()`() {
        // Given
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)
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
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // When
        testedFeature.stop()

        // Then
        assertThat(testedFeature.persistenceStrategy)
            .isInstanceOf(NoOpPersistenceStrategy::class.java)
        assertThat(testedFeature.uploadScheduler)
            .isInstanceOf(NoOpUploadScheduler::class.java)
    }

    @Test
    fun `𝕄 mark itself as not initialized 𝕎 stop()`() {
        // Given
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // When
        testedFeature.stop()

        // Then
        assertThat(testedFeature.isInitialized()).isFalse()
    }

    @Test
    fun `𝕄 initialize only once 𝕎 initialize() twice`(
        forge: Forge
    ) {
        // Given
        val otherConfig = forgeConfiguration(forge)
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)
        val persistenceStrategy = testedFeature.persistenceStrategy
        val uploadScheduler = testedFeature.uploadScheduler

        // When
        testedFeature.initialize(appContext.mockInstance, otherConfig)

        // Then
        assertThat(testedFeature.persistenceStrategy).isSameAs(persistenceStrategy)
        assertThat(testedFeature.uploadScheduler).isSameAs(uploadScheduler)
    }

    @Test
    fun `𝕄 not setup uploader 𝕎 initialize() in secondary process`() {
        // Given
        whenever(testedFeature.coreFeature.isMainProcess) doReturn false

        // When
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // Then
        assertThat(testedFeature.uploadScheduler).isInstanceOf(NoOpUploadScheduler::class.java)
    }

    @Test
    fun `𝕄 clear local storage 𝕎 clearAllData()`() {
        // Given
        testedFeature.persistenceStrategy = mockPersistenceStrategy

        // When
        testedFeature.clearAllData()

        // Then
        verify(mockReader).dropAll()
    }

    @Test
    fun `M call the DataFlusher W flushData`() {
        // Given
        val mockDataFlusher: DataFlusher = mock()
        whenever(mockPersistenceStrategy.getFlusher()).thenReturn(mockDataFlusher)
        testedFeature.persistenceStrategy = mockPersistenceStrategy

        // When
        testedFeature.flushStoredData()

        // Then
        verify(mockDataFlusher).flush(testedFeature.uploader)
    }

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
