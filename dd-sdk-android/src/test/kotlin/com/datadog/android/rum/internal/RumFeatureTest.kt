/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal

import android.app.Application
import com.datadog.android.Datadog
import com.datadog.android.DatadogConfig
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.data.upload.DataUploadScheduler
import com.datadog.android.core.internal.domain.AsyncWriterFilePersistenceStrategy
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.system.SystemInfoProvider
import com.datadog.android.log.internal.user.UserInfoProvider
import com.datadog.android.rum.internal.tracking.UserActionTrackingStrategy
import com.datadog.android.rum.internal.tracking.ViewTreeChangeTrackingStrategy
import com.datadog.android.rum.tracking.ViewTrackingStrategy
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockContext
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.getFieldValue
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
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
internal class RumFeatureTest {

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

    @Mock
    lateinit var mockUserInfoProvider: UserInfoProvider

    lateinit var fakeConfig: DatadogConfig.RumConfig

    lateinit var fakePackageName: String
    lateinit var fakePackageVersion: String

    @TempDir
    lateinit var rootDir: File

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeConfig = DatadogConfig.RumConfig(
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
        RumFeature.stop()
        CoreFeature.stop()
    }

    @Test
    fun `initializes GlobalRum context`() {
        RumFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider,
            mockScheduledThreadPoolExecutor,
            mockPersistenceExecutorService,
            mockUserInfoProvider
        )
    }

    @Test
    fun `initializes persistence strategy`() {
        RumFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider,
            mockScheduledThreadPoolExecutor,
            mockPersistenceExecutorService,
            mockUserInfoProvider
        )

        val persistenceStrategy = RumFeature.persistenceStrategy
        assertThat(persistenceStrategy)
            .isInstanceOf(AsyncWriterFilePersistenceStrategy::class.java)
        val reader = RumFeature.persistenceStrategy.getReader()
        val suffix: String = reader.getFieldValue("suffix")
        assertThat(suffix).isEqualTo("")
        val prefix: String = reader.getFieldValue("prefix")
        assertThat(prefix).isEqualTo("")
    }

    @Test
    fun `initializes uploader thread`() {
        RumFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider,
            mockScheduledThreadPoolExecutor,
            mockPersistenceExecutorService,
            mockUserInfoProvider
        )

        val dataUploadScheduler = RumFeature.dataUploadScheduler

        assertThat(dataUploadScheduler)
            .isInstanceOf(DataUploadScheduler::class.java)
    }

    @Test
    fun `initializes the userInfoProvider`() {
        RumFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider,
            mockScheduledThreadPoolExecutor,
            mockPersistenceExecutorService,
            mockUserInfoProvider
        )

        assertThat(RumFeature.userInfoProvider).isEqualTo(mockUserInfoProvider)
    }

    @Test
    fun `initializes from configuration`() {
        RumFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider,
            mockScheduledThreadPoolExecutor,
            mockPersistenceExecutorService,
            mockUserInfoProvider
        )

        val clientToken = RumFeature.clientToken
        val endpointUrl = RumFeature.endpointUrl

        assertThat(clientToken).isEqualTo(fakeConfig.clientToken)
        assertThat(endpointUrl).isEqualTo(fakeConfig.endpointUrl)
    }

    @Test
    fun `ignores if initialize called more than once`(forge: Forge) {
        Datadog.setVerbosity(android.util.Log.VERBOSE)
        RumFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider,
            mockScheduledThreadPoolExecutor,
            mockPersistenceExecutorService,
            mockUserInfoProvider
        )
        val persistenceStrategy = RumFeature.persistenceStrategy
        val dataUploadScheduler = RumFeature.dataUploadScheduler
        val clientToken = RumFeature.clientToken
        val endpointUrl = RumFeature.endpointUrl
        val userInfoProvider = RumFeature.userInfoProvider

        fakeConfig = DatadogConfig.RumConfig(
            clientToken = forge.anHexadecimalString(),
            applicationId = forge.getForgery(),
            endpointUrl = forge.getForgery<URL>().toString(),
            envName = forge.anAlphabeticalString(),
            userActionTrackingStrategy = mock(),
            viewTrackingStrategy = mock()
        )
        RumFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider,
            mockScheduledThreadPoolExecutor,
            mockPersistenceExecutorService,
            mockUserInfoProvider
        )
        val persistenceStrategy2 = RumFeature.persistenceStrategy
        val dataUploadScheduler2 = RumFeature.dataUploadScheduler
        val clientToken2 = RumFeature.clientToken
        val endpointUrl2 = RumFeature.endpointUrl

        assertThat(persistenceStrategy).isSameAs(persistenceStrategy2)
        assertThat(dataUploadScheduler).isSameAs(dataUploadScheduler2)
        assertThat(clientToken).isSameAs(clientToken2)
        assertThat(endpointUrl).isSameAs(endpointUrl2)
        assertThat(userInfoProvider).isSameAs(RumFeature.userInfoProvider)
    }

    @Test
    fun `will not register any callback if no instrumentation feature enabled`() {
        // when
        RumFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider,
            mockScheduledThreadPoolExecutor,
            mockPersistenceExecutorService,
            mockUserInfoProvider
        )

        // then
        verify(mockAppContext, never()).registerActivityLifecycleCallbacks(argThat {
            this is ViewTrackingStrategy || this is UserActionTrackingStrategy
        })
    }

    @Test
    fun `will register the strategy when tracking gestures enabled`() {
        // given
        val trackGesturesStrategy: UserActionTrackingStrategy = mock()
        fakeConfig = fakeConfig.copy(userActionTrackingStrategy = trackGesturesStrategy)

        // when
        RumFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider,
            mockScheduledThreadPoolExecutor,
            mockPersistenceExecutorService,
            mockUserInfoProvider
        )

        // then
        verify(trackGesturesStrategy).register(mockAppContext)
    }

    @Test
    fun `will register the strategy when track screen strategy provided`() {
        // given
        val viewTrackingStrategy: ViewTrackingStrategy = mock()
        fakeConfig = fakeConfig.copy(viewTrackingStrategy = viewTrackingStrategy)

        // when
        RumFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider,
            mockScheduledThreadPoolExecutor,
            mockPersistenceExecutorService,
            mockUserInfoProvider
        )

        // then
        verify(viewTrackingStrategy).register(mockAppContext)
    }

    @Test
    fun `will always register the viewTree strategy`() {
        // when
        RumFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider,
            mockScheduledThreadPoolExecutor,
            mockPersistenceExecutorService,
            mockUserInfoProvider
        )

        // then
        verify(mockAppContext).registerActivityLifecycleCallbacks(argThat {
            this is ViewTreeChangeTrackingStrategy
        })
    }
}
