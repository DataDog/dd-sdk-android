/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal

import android.app.Application
import com.datadog.android.Datadog
import com.datadog.android.DatadogConfig
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.data.upload.DataUploadScheduler
import com.datadog.android.core.internal.data.upload.NoOpUploadScheduler
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.core.internal.privacy.TrackingConsentProvider
import com.datadog.android.core.internal.system.SystemInfoProvider
import com.datadog.android.log.internal.domain.LogFileStrategy
import com.datadog.android.plugin.DatadogPlugin
import com.datadog.android.plugin.DatadogPluginConfig
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockContext
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.File
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import okhttp3.OkHttpClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class LogsFeatureTest {

    lateinit var mockAppContext: Application

    @Mock
    lateinit var mockNetworkInfoProvider: NetworkInfoProvider

    @Mock
    lateinit var mockSystemInfoProvider: SystemInfoProvider

    @Mock
    lateinit var mockOkHttpClient: OkHttpClient

    @Mock
    lateinit var mockScheduledThreadPoolExecutor: ScheduledThreadPoolExecutor

    @Mock
    lateinit var mockPersistenceExecutorService: ExecutorService

    lateinit var trackingConsentProvider: ConsentProvider

    lateinit var fakeConfig: DatadogConfig.FeatureConfig

    lateinit var fakePackageName: String
    lateinit var fakePackageVersion: String

    @TempDir
    lateinit var tempRootDir: File

    @BeforeEach
    fun `set up`(forge: Forge) {
        trackingConsentProvider = TrackingConsentProvider()
        CoreFeature.isMainProcess = true
        fakeConfig = DatadogConfig.FeatureConfig(
            clientToken = forge.anHexadecimalString(),
            applicationId = forge.getForgery(),
            endpointUrl = forge.getForgery<URL>().toString(),
            envName = forge.anAlphabeticalString()
        )

        fakePackageName = forge.anAlphabeticalString()
        fakePackageVersion = forge.aStringMatching("\\d(\\.\\d){3}")

        mockAppContext = mockContext(fakePackageName, fakePackageVersion)
        whenever(mockAppContext.filesDir).thenReturn(tempRootDir)
        whenever(mockAppContext.applicationContext) doReturn mockAppContext
    }

    @AfterEach
    fun `tear down`() {
        LogsFeature.stop()
        CoreFeature.stop()
    }

    @Test
    fun `initializes persistence strategy`() {
        LogsFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider,
            mockScheduledThreadPoolExecutor,
            mockPersistenceExecutorService,
            trackingConsentProvider
        )

        val persistenceStrategy = LogsFeature.persistenceStrategy

        assertThat(persistenceStrategy)
            .isInstanceOf(LogFileStrategy::class.java)
    }

    @Test
    fun `initializes uploader thread`() {
        LogsFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider,
            mockScheduledThreadPoolExecutor,
            mockPersistenceExecutorService,
            trackingConsentProvider
        )

        val dataUploadScheduler = LogsFeature.dataUploadScheduler

        assertThat(dataUploadScheduler)
            .isInstanceOf(DataUploadScheduler::class.java)
    }

    @Test
    fun `initializes from configuration`() {
        LogsFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider,
            mockScheduledThreadPoolExecutor,
            mockPersistenceExecutorService,
            trackingConsentProvider
        )

        val clientToken = LogsFeature.clientToken
        val endpointUrl = LogsFeature.endpointUrl

        assertThat(clientToken).isEqualTo(fakeConfig.clientToken)
        assertThat(endpointUrl).isEqualTo(fakeConfig.endpointUrl)
    }

    @Test
    fun `ignores if initialize called more than once`(forge: Forge) {
        Datadog.setVerbosity(android.util.Log.VERBOSE)
        LogsFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider,
            mockScheduledThreadPoolExecutor,
            mockPersistenceExecutorService,
            trackingConsentProvider
        )
        val persistenceStrategy = LogsFeature.persistenceStrategy
        val dataUploadScheduler = LogsFeature.dataUploadScheduler
        val clientToken = LogsFeature.clientToken
        val endpointUrl = LogsFeature.endpointUrl

        fakeConfig = DatadogConfig.FeatureConfig(
            clientToken = forge.anHexadecimalString(),
            applicationId = forge.getForgery(),
            endpointUrl = forge.getForgery<URL>().toString(),
            envName = forge.anAlphabeticalString()
        )
        LogsFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider,
            mockScheduledThreadPoolExecutor,
            mockPersistenceExecutorService,
            trackingConsentProvider
        )
        val persistenceStrategy2 = LogsFeature.persistenceStrategy
        val dataUploadScheduler2 = LogsFeature.dataUploadScheduler
        val clientToken2 = LogsFeature.clientToken
        val endpointUrl2 = LogsFeature.endpointUrl

        assertThat(persistenceStrategy).isSameAs(persistenceStrategy2)
        assertThat(dataUploadScheduler).isSameAs(dataUploadScheduler2)
        assertThat(clientToken).isSameAs(clientToken2)
        assertThat(endpointUrl).isSameAs(endpointUrl2)
    }

    @Test
    fun `it will register the provided plugin when feature was initialized`(
        forge: Forge
    ) {
        // Given
        val plugins: List<DatadogPlugin> = forge.aList(forge.anInt(min = 1, max = 10)) {
            mock<DatadogPlugin>()
        }

        // When
        LogsFeature.initialize(
            mockAppContext,
            fakeConfig.copy(plugins = plugins),
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider,
            mockScheduledThreadPoolExecutor,
            mockPersistenceExecutorService,
            trackingConsentProvider
        )

        val argumentCaptor = argumentCaptor<DatadogPluginConfig>()
        // Then
        val mockPlugins = plugins.toTypedArray()
        inOrder(*mockPlugins) {
            mockPlugins.forEach {
                verify(it).register(argumentCaptor.capture())
            }
        }

        argumentCaptor.allValues.forEach {
            assertThat(it).isInstanceOf(DatadogPluginConfig.LogsPluginConfig::class.java)
            assertThat(it.context).isEqualTo(mockAppContext)
            assertThat(it.serviceName).isEqualTo(CoreFeature.serviceName)
            assertThat(it.envName).isEqualTo(fakeConfig.envName)
            assertThat(it.featurePersistenceDirName).isEqualTo(LogFileStrategy.AUTHORIZED_FOLDER)
            assertThat(it.context).isEqualTo(mockAppContext)
        }
    }

    @Test
    fun `it will unregister the provided plugin when stop called`(
        forge: Forge
    ) {
        // Given
        val plugins: List<DatadogPlugin> = forge.aList(forge.anInt(min = 1, max = 10)) {
            mock<DatadogPlugin>()
        }
        LogsFeature.initialize(
            mockAppContext,
            fakeConfig.copy(plugins = plugins),
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider,
            mockScheduledThreadPoolExecutor,
            mockPersistenceExecutorService,
            trackingConsentProvider
        )

        // When
        LogsFeature.stop()

        // Then
        val mockPlugins = plugins.toTypedArray()
        inOrder(*mockPlugins) {
            mockPlugins.forEach {
                verify(it).unregister()
            }
        }
    }

    @Test
    fun `will use a NoOpUploadScheduler if this is not the application main process`() {
        // Given
        CoreFeature.isMainProcess = false

        // When
        LogsFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider,
            mockScheduledThreadPoolExecutor,
            mockPersistenceExecutorService,
            trackingConsentProvider
        )

        // Then
        assertThat(LogsFeature.dataUploadScheduler).isInstanceOf(NoOpUploadScheduler::class.java)
    }

    @Test
    fun `clears all files on local storage on request`(
        @StringForgery(type = StringForgeryType.NUMERICAL) fileName: String,
        @StringForgery content: String
    ) {
        val fakeDir = File(tempRootDir, LogFileStrategy.AUTHORIZED_FOLDER)
        fakeDir.mkdirs()
        val fakeFile = File(fakeDir, fileName)
        fakeFile.writeText(content)

        // When
        LogsFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider,
            mockScheduledThreadPoolExecutor,
            mockPersistenceExecutorService,
            trackingConsentProvider
        )
        LogsFeature.clearAllData()

        // Then
        assertThat(fakeFile).doesNotExist()
    }
}
