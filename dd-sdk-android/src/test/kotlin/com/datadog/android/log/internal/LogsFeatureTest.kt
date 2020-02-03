/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android.log.internal

import android.app.Application
import com.datadog.android.Datadog
import com.datadog.android.DatadogConfig
import com.datadog.android.core.internal.data.upload.DataUploadHandlerThread
import com.datadog.android.core.internal.domain.AsyncWriterFilePersistenceStrategy
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.system.SystemInfoProvider
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockContext
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.extensions.SystemOutputExtension
import com.nhaarman.mockitokotlin2.doReturn
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
    ExtendWith(ApiLevelExtension::class),
    ExtendWith(SystemOutputExtension::class)
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

    lateinit var fakeConfig: DatadogConfig.FeatureConfig

    lateinit var fakePackageName: String
    lateinit var fakePackageVersion: String

    @TempDir
    lateinit var rootDir: File

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeConfig = DatadogConfig.FeatureConfig(
            clientToken = forge.anHexadecimalString(),
            endpointUrl = forge.getForgery<URL>().toString(),
            serviceName = forge.anAlphabeticalString()
        )

        fakePackageName = forge.anAlphabeticalString()
        fakePackageVersion = forge.aStringMatching("\\d(\\.\\d){3}")

        mockAppContext = mockContext(fakePackageName, fakePackageVersion)
        whenever(mockAppContext.filesDir).thenReturn(rootDir)
        whenever(mockAppContext.applicationContext) doReturn mockAppContext
    }

    @AfterEach
    fun `tear down`() {
        LogsFeature.stop()
    }

    @Test
    fun `initializes persistence strategy`() {
        LogsFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider
        )

        val persistenceStrategy = LogsFeature.persistenceStrategy

        assertThat(persistenceStrategy)
            .isInstanceOf(AsyncWriterFilePersistenceStrategy::class.java)
    }

    @Test
    fun `initializes uploader thread`() {
        LogsFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider
        )

        val uploadHandlerThread = LogsFeature.uploadHandlerThread

        assertThat(uploadHandlerThread)
            .isInstanceOf(DataUploadHandlerThread::class.java)
    }

    @Test
    fun `initializes from configuration`() {
        LogsFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider
        )

        val clientToken = LogsFeature.clientToken
        val endpointUrl = LogsFeature.endpointUrl
        val serviceName = LogsFeature.serviceName

        assertThat(clientToken).isEqualTo(fakeConfig.clientToken)
        assertThat(endpointUrl).isEqualTo(fakeConfig.endpointUrl)
        assertThat(serviceName).isEqualTo(fakeConfig.serviceName)
    }

    @Test
    fun `ignores if initialize called more than once`(forge: Forge) {
        Datadog.setVerbosity(android.util.Log.VERBOSE)
        LogsFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider
        )
        val persistenceStrategy = LogsFeature.persistenceStrategy
        val uploadHandlerThread = LogsFeature.uploadHandlerThread
        val clientToken = LogsFeature.clientToken
        val endpointUrl = LogsFeature.endpointUrl
        val serviceName = LogsFeature.serviceName

        fakeConfig = DatadogConfig.FeatureConfig(
            clientToken = forge.anHexadecimalString(),
            endpointUrl = forge.getForgery<URL>().toString(),
            serviceName = forge.anAlphabeticalString()
        )
        LogsFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider
        )
        val persistenceStrategy2 = LogsFeature.persistenceStrategy
        val uploadHandlerThread2 = LogsFeature.uploadHandlerThread
        val clientToken2 = LogsFeature.clientToken
        val endpointUrl2 = LogsFeature.endpointUrl
        val serviceName2 = LogsFeature.serviceName

        assertThat(persistenceStrategy).isSameAs(persistenceStrategy2)
        assertThat(uploadHandlerThread).isSameAs(uploadHandlerThread2)
        assertThat(clientToken).isSameAs(clientToken2)
        assertThat(endpointUrl).isSameAs(endpointUrl2)
        assertThat(serviceName).isSameAs(serviceName2)
    }
}
