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
import com.datadog.android.core.internal.data.upload.NoOpUploadScheduler
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.core.internal.privacy.TrackingConsentProvider
import com.datadog.android.core.internal.system.SystemInfoProvider
import com.datadog.android.log.internal.user.UserInfoProvider
import com.datadog.android.plugin.DatadogPlugin
import com.datadog.android.plugin.DatadogPluginConfig
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.internal.domain.RumFileStrategy
import com.datadog.android.rum.internal.monitor.DatadogRumMonitor
import com.datadog.android.rum.internal.tracking.UserActionTrackingStrategy
import com.datadog.android.rum.internal.tracking.ViewTreeChangeTrackingStrategy
import com.datadog.android.rum.tracking.ViewTrackingStrategy
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockContext
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.getFieldValue
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
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

    lateinit var trackingConsentProvider: ConsentProvider

    lateinit var fakeConfig: DatadogConfig.RumConfig

    lateinit var fakePackageName: String
    lateinit var fakePackageVersion: String

    @TempDir
    lateinit var tempRootDir: File

    @BeforeEach
    fun `set up`(forge: Forge) {
        trackingConsentProvider = TrackingConsentProvider()
        fakeConfig = DatadogConfig.RumConfig(
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
        CoreFeature.isMainProcess = true
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
            mockUserInfoProvider,
            trackingConsentProvider
        )

        assertThat(RumFeature.applicationId).isEqualTo(fakeConfig.applicationId)
        assertThat(RumFeature.endpointUrl).isEqualTo(fakeConfig.endpointUrl)
        assertThat(RumFeature.envName).isEqualTo(fakeConfig.envName)
        assertThat(RumFeature.clientToken).isEqualTo(fakeConfig.clientToken)
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
            mockUserInfoProvider,
            trackingConsentProvider
        )

        val persistenceStrategy = RumFeature.persistenceStrategy
        assertThat(persistenceStrategy)
            .isInstanceOf(RumFileStrategy::class.java)
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
            mockUserInfoProvider,
            trackingConsentProvider
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
            mockUserInfoProvider,
            trackingConsentProvider
        )

        assertThat(RumFeature.userInfoProvider).isEqualTo(mockUserInfoProvider)
        assertThat(RumFeature.networkInfoProvider).isEqualTo(mockNetworkInfoProvider)
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
            mockUserInfoProvider,
            trackingConsentProvider
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
            mockUserInfoProvider,
            trackingConsentProvider
        )
        val persistenceStrategy = RumFeature.persistenceStrategy
        val dataUploadScheduler = RumFeature.dataUploadScheduler
        val clientToken = RumFeature.clientToken
        val endpointUrl = RumFeature.endpointUrl
        val userInfoProvider = RumFeature.userInfoProvider
        val networkInfoProvider = RumFeature.networkInfoProvider

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
            mockUserInfoProvider,
            trackingConsentProvider
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
        assertThat(networkInfoProvider).isSameAs(RumFeature.networkInfoProvider)
    }

    @Test
    fun `will not register any callback if no instrumentation feature enabled`() {
        // When
        RumFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider,
            mockScheduledThreadPoolExecutor,
            mockPersistenceExecutorService,
            mockUserInfoProvider,
            trackingConsentProvider
        )

        // Then
        verify(mockAppContext, never()).registerActivityLifecycleCallbacks(
            argThat {
                this is ViewTrackingStrategy || this is UserActionTrackingStrategy
            }
        )
    }

    @Test
    fun `will register the strategy when tracking gestures enabled`() {
        // Given
        val trackGesturesStrategy: UserActionTrackingStrategy = mock()
        fakeConfig = fakeConfig.copy(userActionTrackingStrategy = trackGesturesStrategy)

        // When
        RumFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider,
            mockScheduledThreadPoolExecutor,
            mockPersistenceExecutorService,
            mockUserInfoProvider,
            trackingConsentProvider
        )

        // Then
        verify(trackGesturesStrategy).register(mockAppContext)
    }

    @Test
    fun `will register the strategy when track screen strategy provided`() {
        // Given
        val viewTrackingStrategy: ViewTrackingStrategy = mock()
        fakeConfig = fakeConfig.copy(viewTrackingStrategy = viewTrackingStrategy)

        // When
        RumFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider,
            mockScheduledThreadPoolExecutor,
            mockPersistenceExecutorService,
            mockUserInfoProvider,
            trackingConsentProvider
        )

        // Then
        verify(viewTrackingStrategy).register(mockAppContext)
    }

    @Test
    fun `will always register the viewTree strategy`() {
        // When
        RumFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider,
            mockScheduledThreadPoolExecutor,
            mockPersistenceExecutorService,
            mockUserInfoProvider,
            trackingConsentProvider
        )

        // Then
        verify(mockAppContext).registerActivityLifecycleCallbacks(
            argThat {
                this is ViewTreeChangeTrackingStrategy
            }
        )
    }

    @Test
    fun `will use a NoOpUploadScheduler if this is not the application main process`() {
        // Given
        CoreFeature.isMainProcess = false

        // When
        RumFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider,
            mockScheduledThreadPoolExecutor,
            mockPersistenceExecutorService,
            mockUserInfoProvider,
            trackingConsentProvider
        )

        // Then
        assertThat(RumFeature.dataUploadScheduler).isInstanceOf(NoOpUploadScheduler::class.java)
    }

    @Test
    fun `stops the keep alive callback on stop`() {
        RumFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider,
            mockScheduledThreadPoolExecutor,
            mockPersistenceExecutorService,
            mockUserInfoProvider,
            trackingConsentProvider
        )
        val monitor: DatadogRumMonitor = mock()
        GlobalRum.isRegistered.set(false)
        GlobalRum.registerIfAbsent(monitor)

        RumFeature.stop()

        verify(monitor).stopKeepAliveCallback()
    }

    @Test
    fun `it will register the provided plugin when the feature is initialized`(
        forge: Forge
    ) {
        // Given
        val plugins: List<DatadogPlugin> = forge.aList(forge.anInt(min = 1, max = 10)) {
            mock<DatadogPlugin>()
        }

        // When
        RumFeature.initialize(
            mockAppContext,
            fakeConfig.copy(plugins = plugins),
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider,
            mockScheduledThreadPoolExecutor,
            mockPersistenceExecutorService,
            mockUserInfoProvider,
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
            assertThat(it).isInstanceOf(DatadogPluginConfig.RumPluginConfig::class.java)
            assertThat(it.context).isEqualTo(mockAppContext)
            assertThat(it.serviceName).isEqualTo(CoreFeature.serviceName)
            assertThat(it.envName).isEqualTo(fakeConfig.envName)
            assertThat(it.featurePersistenceDirName).isEqualTo(RumFileStrategy.AUTHORIZED_FOLDER)
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
        RumFeature.initialize(
            mockAppContext,
            fakeConfig.copy(plugins = plugins),
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider,
            mockScheduledThreadPoolExecutor,
            mockPersistenceExecutorService,
            mockUserInfoProvider,
            trackingConsentProvider
        )

        // When
        RumFeature.stop()

        // Then
        val mockPlugins = plugins.toTypedArray()
        inOrder(*mockPlugins) {
            mockPlugins.forEach {
                verify(it).unregister()
            }
        }
    }

    @Test
    fun `clears all files on local storage on request`(
        @StringForgery(type = StringForgeryType.NUMERICAL) fileName: String,
        @StringForgery content: String
    ) {
        val fakeDir = File(tempRootDir, RumFileStrategy.AUTHORIZED_FOLDER)
        fakeDir.mkdirs()
        val fakeFile = File(fakeDir, fileName)
        fakeFile.writeText(content)

        // When
        RumFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider,
            mockScheduledThreadPoolExecutor,
            mockPersistenceExecutorService,
            mockUserInfoProvider,
            trackingConsentProvider
        )
        RumFeature.clearAllData()

        // Then
        assertThat(fakeFile).doesNotExist()
    }
}
