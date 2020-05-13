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
import com.datadog.android.core.internal.data.Writer
import com.datadog.android.core.internal.data.upload.DataUploadScheduler
import com.datadog.android.core.internal.data.upload.NoOpUploadScheduler
import com.datadog.android.core.internal.domain.AsyncWriterFilePersistenceStrategy
import com.datadog.android.core.internal.domain.Serializer
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.system.SystemInfoProvider
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.log.internal.user.UserInfoProvider
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockContext
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.getFieldValue
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
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

    lateinit var fakeConfig: DatadogConfig.FeatureConfig

    lateinit var fakePackageName: String
    lateinit var fakePackageVersion: String

    @TempDir
    lateinit var rootDir: File

    @BeforeEach
    fun `set up`(forge: Forge) {
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
        whenever(mockAppContext.filesDir).thenReturn(rootDir)
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
            mockPersistenceExecutorService
        )

        val persistenceStrategy = TracesFeature.persistenceStrategy
        assertThat(persistenceStrategy)
            .isInstanceOf(AsyncWriterFilePersistenceStrategy::class.java)
        val writer = TracesFeature.persistenceStrategy.getWriter()
        val delegateWriter: Writer<*> = writer.getFieldValue("writer")
        val serializer: Serializer<*> = delegateWriter.getFieldValue("serializer")
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
            mockPersistenceExecutorService
        )

        val persistenceStrategy = TracesFeature.persistenceStrategy
        assertThat(persistenceStrategy)
            .isInstanceOf(AsyncWriterFilePersistenceStrategy::class.java)
        val writer = TracesFeature.persistenceStrategy.getWriter()
        val delegateWriter: Writer<*> = writer.getFieldValue("writer")
        val serializer: Serializer<*> = delegateWriter.getFieldValue("serializer")
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
            mockPersistenceExecutorService
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
            mockPersistenceExecutorService
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
            mockPersistenceExecutorService
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
            mockPersistenceExecutorService
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
    fun `will use a NoOpUploadScheduler if this is not the application main process`() {
        // given
        CoreFeature.isMainProcess = false

        // when
        TracesFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockUserInfoProvider,
            mockSystemInfoProvider,
            mockTimeProvider,
            mockScheduledThreadPoolExecutor,
            mockPersistenceExecutorService
        )

        // then
        assertThat(TracesFeature.dataUploadScheduler).isInstanceOf(NoOpUploadScheduler::class.java)
    }
}
