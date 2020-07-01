/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.error.internal

import android.app.Application
import com.datadog.android.Datadog
import com.datadog.android.DatadogConfig
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.data.upload.DataUploadScheduler
import com.datadog.android.core.internal.data.upload.NoOpUploadScheduler
import com.datadog.android.core.internal.domain.FilePersistenceStrategy
import com.datadog.android.core.internal.net.DataOkHttpUploader
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.system.SystemInfoProvider
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.log.internal.user.UserInfoProvider
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
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.File
import java.net.URL
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
internal class CrashReportsFeatureTest {

    lateinit var mockAppContext: Application

    @Mock
    lateinit var mockNetworkInfoProvider: NetworkInfoProvider

    @Mock
    lateinit var mockUserInfoProvider: UserInfoProvider

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @Mock
    lateinit var mockSystemInfoProvider: SystemInfoProvider

    @Mock
    lateinit var mockOkHttpClient: OkHttpClient

    @Mock
    lateinit var mockScheduledThreadPoolExecutor: ScheduledThreadPoolExecutor

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
        CoreFeature.stop()
        CrashReportsFeature.stop()
    }

    @Test
    fun `initializes persistence strategy`() {
        CrashReportsFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockUserInfoProvider,
            mockTimeProvider,
            mockSystemInfoProvider,
            mockScheduledThreadPoolExecutor
        )

        val persistenceStrategy = CrashReportsFeature.persistenceStrategy

        assertThat(persistenceStrategy)
            .isInstanceOf(FilePersistenceStrategy::class.java)
    }

    @Test
    fun `initializes uploader thread`() {
        CrashReportsFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockUserInfoProvider,
            mockTimeProvider,
            mockSystemInfoProvider,
            mockScheduledThreadPoolExecutor
        )

        val uploader = CrashReportsFeature.uploader
        val dataUploadScheduler = CrashReportsFeature.dataUploadScheduler

        assertThat(uploader)
            .isInstanceOf(DataOkHttpUploader::class.java)
        assertThat(dataUploadScheduler)
            .isInstanceOf(DataUploadScheduler::class.java)
    }

    @Test
    fun `initializes from configuration`() {
        CrashReportsFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockUserInfoProvider,
            mockTimeProvider,
            mockSystemInfoProvider,
            mockScheduledThreadPoolExecutor
        )

        val clientToken = CrashReportsFeature.clientToken
        val endpointUrl = CrashReportsFeature.endpointUrl

        assertThat(clientToken).isEqualTo(fakeConfig.clientToken)
        assertThat(endpointUrl).isEqualTo(fakeConfig.endpointUrl)
    }

    @Test
    fun `initializes crash reporter`() {
        CrashReportsFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockUserInfoProvider,
            mockTimeProvider,
            mockSystemInfoProvider,
            mockScheduledThreadPoolExecutor
        )

        val handler = Thread.getDefaultUncaughtExceptionHandler()

        assertThat(handler)
            .isInstanceOf(DatadogExceptionHandler::class.java)
    }

    @Test
    fun `restores original crash reporter on stop`() {
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        val handler: Thread.UncaughtExceptionHandler = mock()
        Thread.setDefaultUncaughtExceptionHandler(handler)

        CrashReportsFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockUserInfoProvider,
            mockTimeProvider,
            mockSystemInfoProvider,
            mockScheduledThreadPoolExecutor
        )
        CrashReportsFeature.stop()

        val finalHandler = Thread.getDefaultUncaughtExceptionHandler()
        assertThat(finalHandler)
            .isSameAs(handler)
        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
    }

    @Test
    fun `ignores if initialize called more than once`(forge: Forge) {
        Datadog.setVerbosity(android.util.Log.VERBOSE)
        CrashReportsFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockUserInfoProvider,
            mockTimeProvider,
            mockSystemInfoProvider,
            mockScheduledThreadPoolExecutor
        )
        val persistenceStrategy = CrashReportsFeature.persistenceStrategy
        val uploader = CrashReportsFeature.uploader
        val clientToken = CrashReportsFeature.clientToken
        val endpointUrl = CrashReportsFeature.endpointUrl

        fakeConfig = DatadogConfig.FeatureConfig(
            clientToken = forge.anHexadecimalString(),
            applicationId = forge.getForgery(),
            endpointUrl = forge.getForgery<URL>().toString(),
            envName = forge.anAlphabeticalString()
        )
        CrashReportsFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockUserInfoProvider,
            mockTimeProvider,
            mockSystemInfoProvider,
            mockScheduledThreadPoolExecutor
        )
        val persistenceStrategy2 = CrashReportsFeature.persistenceStrategy
        val uploader2 = CrashReportsFeature.uploader
        val clientToken2 = CrashReportsFeature.clientToken
        val endpointUrl2 = CrashReportsFeature.endpointUrl

        assertThat(persistenceStrategy).isSameAs(persistenceStrategy2)
        assertThat(uploader).isSameAs(uploader2)
        assertThat(clientToken).isSameAs(clientToken2)
        assertThat(endpointUrl).isSameAs(endpointUrl2)
    }

    @Test
    fun `it will register and unregister the provided plugin when initialise and stop called`(
        forge: Forge
    ) {
        // given
        val plugins: List<DatadogPlugin> = forge.aList(forge.anInt(min = 1, max = 10)) {
            mock<DatadogPlugin>()
        }

        // when
        CrashReportsFeature.initialize(
            mockAppContext,
            fakeConfig.copy(plugins = plugins),
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockUserInfoProvider,
            mockTimeProvider,
            mockSystemInfoProvider,
            mockScheduledThreadPoolExecutor
        )
        CrashReportsFeature.stop()

        val argumentCaptor = argumentCaptor<DatadogPluginConfig>()
        // then
        val mockedPlugins = plugins.toTypedArray()
        inOrder(*mockedPlugins) {
            mockedPlugins.forEach {
                verify(it).register(argumentCaptor.capture())
            }
            mockedPlugins.forEach {
                verify(it).unregister()
            }
        }

        argumentCaptor.allValues.forEach {
            assertThat(it).isInstanceOf(DatadogPluginConfig.CrashReportsPluginConfig::class.java)
            assertThat(it.context).isEqualTo(mockAppContext)
            assertThat(it.serviceName).isEqualTo(CoreFeature.serviceName)
            assertThat(it.envName).isEqualTo(fakeConfig.envName)
            assertThat(it.featureFolderName).isEqualTo(CrashLogFileStrategy.CRASH_REPORTS_FOLDER)
            assertThat(it.context).isEqualTo(mockAppContext)
        }
    }

    @Test
    fun `will use a NoOpUploadScheduler if this is not the application main process`() {
        // given
        CoreFeature.isMainProcess = false

        // when
        CrashReportsFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockUserInfoProvider,
            mockTimeProvider,
            mockSystemInfoProvider,
            mockScheduledThreadPoolExecutor
        )

        // then
        assertThat(CrashReportsFeature.dataUploadScheduler)
            .isInstanceOf(NoOpUploadScheduler::class.java)
    }
}
