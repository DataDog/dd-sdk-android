/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tracing.internal

import android.app.Application
import com.datadog.android.Datadog
import com.datadog.android.DatadogConfig
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.data.upload.DataUploadScheduler
import com.datadog.android.core.internal.data.upload.NoOpUploadScheduler
import com.datadog.android.core.internal.domain.Serializer
import com.datadog.android.core.internal.domain.batching.ConsentAwareDataWriter
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.core.internal.privacy.TrackingConsentProvider
import com.datadog.android.core.internal.system.SystemInfoProvider
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.log.internal.user.UserInfoProvider
import com.datadog.android.plugin.DatadogPlugin
import com.datadog.android.plugin.DatadogPluginConfig
import com.datadog.android.tracing.internal.domain.TracingFileStrategy
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockContext
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.getFieldValue
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
internal class TracesFeatureTest {

    lateinit var mockAppContext: Application

    @Mock
    lateinit var mockNetworkInfoProvider: NetworkInfoProvider

    @Mock
    lateinit var mockSystemInfoProvider: SystemInfoProvider

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @Mock
    lateinit var mockUserInfoProvider: UserInfoProvider

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
        CoreFeature.isMainProcess = true
        trackingConsentProvider = TrackingConsentProvider()
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
        TracesFeature.stop()
        CoreFeature.stop()
    }

    @Test
    fun `initializes persistence strategy with env`() {
        TracesFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockUserInfoProvider,
            mockSystemInfoProvider,
            mockTimeProvider,
            mockScheduledThreadPoolExecutor,
            mockPersistenceExecutorService,
            trackingConsentProvider
        )

        val persistenceStrategy = TracesFeature.persistenceStrategy
        assertThat(persistenceStrategy)
            .isInstanceOf(TracingFileStrategy::class.java)
        val consentAwareDataWriter =
            TracesFeature.persistenceStrategy.getWriter() as ConsentAwareDataWriter
        val writer = consentAwareDataWriter.getInternalWriter()
        val serializer: Serializer<*> = writer.getFieldValue("serializer")
        val envName: String = serializer.getFieldValue("envName")
        assertThat(envName).isEqualTo(fakeConfig.envName)
    }

    @Test
    fun `initializes persistence strategy without env`() {
        fakeConfig = fakeConfig.copy(envName = "")
        TracesFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockUserInfoProvider,
            mockSystemInfoProvider,
            mockTimeProvider,
            mockScheduledThreadPoolExecutor,
            mockPersistenceExecutorService,
            trackingConsentProvider
        )

        val persistenceStrategy = TracesFeature.persistenceStrategy
        assertThat(persistenceStrategy)
            .isInstanceOf(TracingFileStrategy::class.java)
        val consentAwareDataWriter =
            TracesFeature.persistenceStrategy.getWriter() as ConsentAwareDataWriter
        val writer = consentAwareDataWriter.getInternalWriter()
        val serializer: Serializer<*> = writer.getFieldValue("serializer")
        val envName: String = serializer.getFieldValue("envName")
        assertThat(envName).isEqualTo("")
    }

    @Test
    fun `initializes uploader thread`() {
        TracesFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockUserInfoProvider,
            mockSystemInfoProvider,
            mockTimeProvider,
            mockScheduledThreadPoolExecutor,
            mockPersistenceExecutorService,
            trackingConsentProvider
        )

        val dataUploadScheduler = TracesFeature.dataUploadScheduler

        assertThat(dataUploadScheduler)
            .isInstanceOf(DataUploadScheduler::class.java)
    }

    @Test
    fun `initializes from configuration`() {
        TracesFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockUserInfoProvider,
            mockSystemInfoProvider,
            mockTimeProvider,
            mockScheduledThreadPoolExecutor,
            mockPersistenceExecutorService,
            trackingConsentProvider
        )

        val clientToken = TracesFeature.clientToken
        val endpointUrl = TracesFeature.endpointUrl

        assertThat(clientToken).isEqualTo(fakeConfig.clientToken)
        assertThat(endpointUrl).isEqualTo(fakeConfig.endpointUrl)
    }

    @Test
    fun `ignores if initialize called more than once`(forge: Forge) {
        Datadog.setVerbosity(android.util.Log.VERBOSE)
        TracesFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockUserInfoProvider,
            mockSystemInfoProvider,
            mockTimeProvider,
            mockScheduledThreadPoolExecutor,
            mockPersistenceExecutorService,
            trackingConsentProvider
        )
        val persistenceStrategy = TracesFeature.persistenceStrategy
        val dataUploadScheduler = TracesFeature.dataUploadScheduler
        val clientToken = TracesFeature.clientToken
        val endpointUrl = TracesFeature.endpointUrl

        fakeConfig = DatadogConfig.FeatureConfig(
            clientToken = forge.anHexadecimalString(),
            applicationId = forge.getForgery(),
            endpointUrl = forge.getForgery<URL>().toString(),
            envName = forge.anAlphabeticalString()
        )
        TracesFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockUserInfoProvider,
            mockSystemInfoProvider,
            mockTimeProvider,
            mockScheduledThreadPoolExecutor,
            mockPersistenceExecutorService,
            trackingConsentProvider
        )
        val persistenceStrategy2 = TracesFeature.persistenceStrategy
        val dataUploadScheduler2 = TracesFeature.dataUploadScheduler
        val clientToken2 = TracesFeature.clientToken
        val endpointUrl2 = TracesFeature.endpointUrl

        assertThat(persistenceStrategy).isSameAs(persistenceStrategy2)
        assertThat(dataUploadScheduler).isSameAs(dataUploadScheduler2)
        assertThat(clientToken).isSameAs(clientToken2)
        assertThat(endpointUrl).isSameAs(endpointUrl2)
    }

    @Test
    fun `it will register the provided plugin when feature is initialized`(
        forge: Forge
    ) {
        // Given
        val plugins: List<DatadogPlugin> = forge.aList(forge.anInt(min = 1, max = 10)) {
            mock<DatadogPlugin>()
        }

        // When
        TracesFeature.initialize(
            mockAppContext,
            fakeConfig.copy(plugins = plugins),
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockUserInfoProvider,
            mockSystemInfoProvider,
            mockTimeProvider,
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
            assertThat(it).isInstanceOf(DatadogPluginConfig.TracingPluginConfig::class.java)
            assertThat(it.context).isEqualTo(mockAppContext)
            assertThat(it.serviceName).isEqualTo(CoreFeature.serviceName)
            assertThat(it.envName).isEqualTo(fakeConfig.envName)
            assertThat(it.featurePersistenceDirName)
                .isEqualTo(TracingFileStrategy.AUTHORIZED_FOLDER)
            assertThat(it.context).isEqualTo(mockAppContext)
        }
    }

    @Test
    fun `it unregister the provided plugin when stop called`(
        forge: Forge
    ) {
        // Given
        val plugins: List<DatadogPlugin> = forge.aList(forge.anInt(min = 1, max = 10)) {
            mock<DatadogPlugin>()
        }

        TracesFeature.initialize(
            mockAppContext,
            fakeConfig.copy(plugins = plugins),
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockUserInfoProvider,
            mockSystemInfoProvider,
            mockTimeProvider,
            mockScheduledThreadPoolExecutor,
            mockPersistenceExecutorService,
            trackingConsentProvider
        )

        // When
        TracesFeature.stop()

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
        TracesFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockUserInfoProvider,
            mockSystemInfoProvider,
            mockTimeProvider,
            mockScheduledThreadPoolExecutor,
            mockPersistenceExecutorService,
            trackingConsentProvider
        )

        // Then
        assertThat(TracesFeature.dataUploadScheduler).isInstanceOf(NoOpUploadScheduler::class.java)
    }

    @Test
    fun `clears all files on local storage on request`(
        @StringForgery(type = StringForgeryType.NUMERICAL) fileName: String,
        @StringForgery content: String
    ) {
        val fakeDir = File(tempRootDir, TracingFileStrategy.AUTHORIZED_FOLDER)
        fakeDir.mkdirs()
        val fakeFile = File(fakeDir, fileName)
        fakeFile.writeText(content)

        // When
        TracesFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockUserInfoProvider,
            mockSystemInfoProvider,
            mockTimeProvider,
            mockScheduledThreadPoolExecutor,
            mockPersistenceExecutorService,
            trackingConsentProvider
        )
        TracesFeature.clearAllData()

        // Then
        assertThat(fakeFile).doesNotExist()
    }
}
