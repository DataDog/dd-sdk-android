/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android.error.internal

import android.app.Application
import com.datadog.android.Datadog
import com.datadog.android.DatadogConfig
import com.datadog.android.core.internal.data.upload.DataUploadHandlerThread
import com.datadog.android.core.internal.domain.FilePersistenceStrategy
import com.datadog.android.core.internal.net.DataOkHttpUploader
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.system.SystemInfoProvider
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.log.internal.user.UserInfoProvider
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockContext
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.File
import java.net.URL
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

    lateinit var fakeConfig: DatadogConfig.FeatureConfig

    lateinit var fakePackageName: String
    lateinit var fakePackageVersion: String

    @TempDir
    lateinit var rootDir: File

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeConfig = DatadogConfig.FeatureConfig(
            clientToken = forge.anHexadecimalString(),
            applicationId = forge.getForgery(),
            endpointUrl = forge.getForgery<URL>().toString(),
            serviceName = forge.anAlphabeticalString(),
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
            mockSystemInfoProvider
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
            mockSystemInfoProvider
        )

        val uploader = CrashReportsFeature.uploader
        val uploadHandlerThread = CrashReportsFeature.uploadHandlerThread

        assertThat(uploader)
            .isInstanceOf(DataOkHttpUploader::class.java)
        assertThat(uploadHandlerThread)
            .isInstanceOf(DataUploadHandlerThread::class.java)
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
            mockSystemInfoProvider
        )

        val clientToken = CrashReportsFeature.clientToken
        val endpointUrl = CrashReportsFeature.endpointUrl
        val serviceName = CrashReportsFeature.serviceName

        assertThat(clientToken).isEqualTo(fakeConfig.clientToken)
        assertThat(endpointUrl).isEqualTo(fakeConfig.endpointUrl)
        assertThat(serviceName).isEqualTo(fakeConfig.serviceName)
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
            mockSystemInfoProvider
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
            mockSystemInfoProvider
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
            mockSystemInfoProvider
        )
        val persistenceStrategy = CrashReportsFeature.persistenceStrategy
        val uploader = CrashReportsFeature.uploader
        val clientToken = CrashReportsFeature.clientToken
        val endpointUrl = CrashReportsFeature.endpointUrl
        val serviceName = CrashReportsFeature.serviceName

        fakeConfig = DatadogConfig.FeatureConfig(
            clientToken = forge.anHexadecimalString(),
            applicationId = forge.getForgery(),
            endpointUrl = forge.getForgery<URL>().toString(),
            serviceName = forge.anAlphabeticalString(),
            envName = forge.anAlphabeticalString()
        )
        CrashReportsFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockUserInfoProvider,
            mockTimeProvider,
            mockSystemInfoProvider
        )
        val persistenceStrategy2 = CrashReportsFeature.persistenceStrategy
        val uploader2 = CrashReportsFeature.uploader
        val clientToken2 = CrashReportsFeature.clientToken
        val endpointUrl2 = CrashReportsFeature.endpointUrl
        val serviceName2 = CrashReportsFeature.serviceName

        assertThat(persistenceStrategy).isSameAs(persistenceStrategy2)
        assertThat(uploader).isSameAs(uploader2)
        assertThat(clientToken).isSameAs(clientToken2)
        assertThat(endpointUrl).isSameAs(endpointUrl2)
        assertThat(serviceName).isSameAs(serviceName2)
    }
}
