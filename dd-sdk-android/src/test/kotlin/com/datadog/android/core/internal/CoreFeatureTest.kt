/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal

import android.app.ActivityManager
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.Credentials
import com.datadog.android.core.internal.net.info.BroadcastReceiverNetworkInfoProvider
import com.datadog.android.core.internal.net.info.CallbackNetworkInfoProvider
import com.datadog.android.core.internal.net.info.NoOpNetworkInfoProvider
import com.datadog.android.core.internal.persistence.file.FilePersistenceConfig
import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.core.internal.privacy.NoOpConsentProvider
import com.datadog.android.core.internal.privacy.TrackingConsentProvider
import com.datadog.android.core.internal.system.BroadcastReceiverSystemInfoProvider
import com.datadog.android.core.internal.system.NoOpSystemInfoProvider
import com.datadog.android.core.internal.time.KronosTimeProvider
import com.datadog.android.core.internal.time.NoOpTimeProvider
import com.datadog.android.log.internal.user.DatadogUserInfoProvider
import com.datadog.android.log.internal.user.NoOpMutableUserInfoProvider
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.internal.ndk.DatadogNdkCrashHandler
import com.datadog.android.rum.internal.ndk.NoOpNdkCrashHandler
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.assertj.containsInstanceOf
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.isA
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import okhttp3.ConnectionSpec
import okhttp3.Protocol
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
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
    ExtendWith(ApiLevelExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class CoreFeatureTest {

    @Mock
    lateinit var mockConnectivityMgr: ConnectivityManager

    @Forgery
    lateinit var fakeCredentials: Credentials

    @Forgery
    lateinit var fakeConfig: Configuration.Core

    @Forgery
    lateinit var fakeConsent: TrackingConsent

    @StringForgery(regex = "[a-zA-Z0-9_:./-]{0,195}[a-zA-Z0-9_./-]")
    lateinit var fakeEnvName: String

    @BeforeEach
    fun `set up`() {
        whenever(appContext.mockInstance.getSystemService(Context.CONNECTIVITY_SERVICE))
            .doReturn(mockConnectivityMgr)
    }

    @AfterEach
    fun `tear down`() {
        CoreFeature.stop()
    }

    @Test
    fun `𝕄 initialize time sync 𝕎 initialize`() {
        // When
        CoreFeature.initialize(
            appContext.mockInstance,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(CoreFeature.kronosClock).isNotNull()
    }

    @Test
    fun `𝕄 initialize time provider 𝕎 initialize`() {
        // When
        CoreFeature.initialize(
            appContext.mockInstance,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(CoreFeature.timeProvider)
            .isInstanceOf(KronosTimeProvider::class.java)
    }

    @Test
    fun `𝕄 initialize system info provider 𝕎 initialize`() {
        // When
        CoreFeature.initialize(
            appContext.mockInstance,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(CoreFeature.systemInfoProvider)
            .isInstanceOf(BroadcastReceiverSystemInfoProvider::class.java)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun `𝕄 initialize network info provider 𝕎 initialize {LOLLIPOP}`() {
        // When
        CoreFeature.initialize(
            appContext.mockInstance,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // Then
        argumentCaptor<BroadcastReceiver> {
            verify(appContext.mockInstance, atLeastOnce())
                .registerReceiver(capture(), any())

            assertThat(allValues)
                .containsInstanceOf(BroadcastReceiverSystemInfoProvider::class.java)
                .containsInstanceOf(BroadcastReceiverNetworkInfoProvider::class.java)
        }
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.N)
    fun `𝕄 initialize network info provider 𝕎 initialize {N}`() {
        // When
        CoreFeature.initialize(
            appContext.mockInstance,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // Then
        argumentCaptor<BroadcastReceiver> {
            verify(appContext.mockInstance, atLeastOnce())
                .registerReceiver(capture(), any())

            assertThat(allValues)
                .containsInstanceOf(BroadcastReceiverSystemInfoProvider::class.java)
            assertThat(allValues.none { it is BroadcastReceiverNetworkInfoProvider })
            verify(mockConnectivityMgr)
                .registerDefaultNetworkCallback(isA<CallbackNetworkInfoProvider>())
        }
    }

    @Test
    fun `𝕄 initialize user info provider 𝕎 initialize`() {
        // When
        CoreFeature.initialize(
            appContext.mockInstance,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(CoreFeature.userInfoProvider)
            .isInstanceOf(DatadogUserInfoProvider::class.java)
    }

    @Test
    fun `𝕄 initialise the consent provider 𝕎 initialize`() {
        // When
        CoreFeature.initialize(
            appContext.mockInstance,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(CoreFeature.trackingConsentProvider)
            .isInstanceOf(TrackingConsentProvider::class.java)
        assertThat(CoreFeature.trackingConsentProvider.getConsent())
            .isEqualTo(fakeConsent)
    }

    @Test
    fun `𝕄 initializes first party hosts detector 𝕎 initialize`() {
        // When
        CoreFeature.initialize(
            appContext.mockInstance,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(CoreFeature.firstPartyHostDetector.knownHosts)
            .containsAll(fakeConfig.firstPartyHosts.map { it.toLowerCase(Locale.US) })
    }

    @Test
    fun `𝕄 initializes app info 𝕎 initialize()`() {
        // When
        CoreFeature.initialize(
            appContext.mockInstance,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(CoreFeature.clientToken).isEqualTo(fakeCredentials.clientToken)
        assertThat(CoreFeature.packageName).isEqualTo(appContext.fakePackageName)
        assertThat(CoreFeature.packageVersion).isEqualTo(appContext.fakeVersionName)
        assertThat(CoreFeature.serviceName).isEqualTo(fakeCredentials.serviceName)
        assertThat(CoreFeature.envName).isEqualTo(fakeCredentials.envName)
        assertThat(CoreFeature.variant).isEqualTo(fakeCredentials.variant)
        assertThat(CoreFeature.rumApplicationId).isEqualTo(fakeCredentials.rumApplicationId)
        assertThat(CoreFeature.contextRef.get()).isEqualTo(appContext.mockInstance)
        assertThat(CoreFeature.batchSize).isEqualTo(fakeConfig.batchSize)
        assertThat(CoreFeature.uploadFrequency).isEqualTo(fakeConfig.uploadFrequency)
    }

    @Test
    fun `𝕄 initializes app info 𝕎 initialize() {null serviceName}`() {
        // When
        CoreFeature.initialize(
            appContext.mockInstance,
            fakeCredentials.copy(serviceName = null),
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(CoreFeature.clientToken).isEqualTo(fakeCredentials.clientToken)
        assertThat(CoreFeature.packageName).isEqualTo(appContext.fakePackageName)
        assertThat(CoreFeature.packageVersion).isEqualTo(appContext.fakeVersionName)
        assertThat(CoreFeature.serviceName).isEqualTo(appContext.fakePackageName)
        assertThat(CoreFeature.envName).isEqualTo(fakeCredentials.envName)
        assertThat(CoreFeature.variant).isEqualTo(fakeCredentials.variant)
        assertThat(CoreFeature.rumApplicationId).isEqualTo(fakeCredentials.rumApplicationId)
        assertThat(CoreFeature.contextRef.get()).isEqualTo(appContext.mockInstance)
        assertThat(CoreFeature.batchSize).isEqualTo(fakeConfig.batchSize)
        assertThat(CoreFeature.uploadFrequency).isEqualTo(fakeConfig.uploadFrequency)
    }

    @Test
    fun `𝕄 initializes app info 𝕎 initialize() {null rumApplicationId}`() {
        // When
        CoreFeature.initialize(
            appContext.mockInstance,
            fakeCredentials.copy(rumApplicationId = null),
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(CoreFeature.clientToken).isEqualTo(fakeCredentials.clientToken)
        assertThat(CoreFeature.packageName).isEqualTo(appContext.fakePackageName)
        assertThat(CoreFeature.packageVersion).isEqualTo(appContext.fakeVersionName)
        assertThat(CoreFeature.serviceName).isEqualTo(fakeCredentials.serviceName)
        assertThat(CoreFeature.envName).isEqualTo(fakeCredentials.envName)
        assertThat(CoreFeature.variant).isEqualTo(fakeCredentials.variant)
        assertThat(CoreFeature.rumApplicationId).isNull()
        assertThat(CoreFeature.contextRef.get()).isEqualTo(appContext.mockInstance)
        assertThat(CoreFeature.batchSize).isEqualTo(fakeConfig.batchSize)
        assertThat(CoreFeature.uploadFrequency).isEqualTo(fakeConfig.uploadFrequency)
    }

    @Test
    fun `𝕄 initializes app info 𝕎 initialize() {null versionName}`() {
        // Given
        appContext.fakePackageInfo.versionName = null
        whenever(appContext.mockInstance.getSystemService(Context.CONNECTIVITY_SERVICE))
            .doReturn(mockConnectivityMgr)

        // When
        CoreFeature.initialize(
            appContext.mockInstance,
            fakeCredentials.copy(rumApplicationId = null),
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(CoreFeature.clientToken).isEqualTo(fakeCredentials.clientToken)
        assertThat(CoreFeature.packageName).isEqualTo(appContext.fakePackageName)
        assertThat(CoreFeature.packageVersion).isEqualTo(appContext.fakeVersionCode.toString())
        assertThat(CoreFeature.serviceName).isEqualTo(fakeCredentials.serviceName)
        assertThat(CoreFeature.envName).isEqualTo(fakeCredentials.envName)
        assertThat(CoreFeature.variant).isEqualTo(fakeCredentials.variant)
        assertThat(CoreFeature.rumApplicationId).isNull()
        assertThat(CoreFeature.contextRef.get()).isEqualTo(appContext.mockInstance)
        assertThat(CoreFeature.batchSize).isEqualTo(fakeConfig.batchSize)
        assertThat(CoreFeature.uploadFrequency).isEqualTo(fakeConfig.uploadFrequency)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun `𝕄 initialize okhttp with strict network policy 𝕎 initialize() {LOLLIPOP}`() {
        // When
        CoreFeature.initialize(
            appContext.mockInstance,
            fakeCredentials,
            fakeConfig.copy(needsClearTextHttp = false),
            fakeConsent
        )

        // Then
        val okHttpClient = CoreFeature.okHttpClient
        assertThat(okHttpClient.protocols())
            .containsExactly(Protocol.HTTP_2, Protocol.HTTP_1_1)
        assertThat(okHttpClient.callTimeoutMillis())
            .isEqualTo(CoreFeature.NETWORK_TIMEOUT_MS.toInt())
        assertThat(okHttpClient.connectionSpecs())
            .containsExactly(ConnectionSpec.RESTRICTED_TLS)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.KITKAT)
    fun `𝕄 initialize okhttp with compat network policy 𝕎 initialize() {KITKAT}`() {
        // When
        CoreFeature.initialize(
            appContext.mockInstance,
            fakeCredentials,
            fakeConfig.copy(needsClearTextHttp = false),
            fakeConsent
        )

        // Then
        val okHttpClient = CoreFeature.okHttpClient
        assertThat(okHttpClient.protocols())
            .containsExactly(Protocol.HTTP_2, Protocol.HTTP_1_1)
        assertThat(okHttpClient.callTimeoutMillis())
            .isEqualTo(CoreFeature.NETWORK_TIMEOUT_MS.toInt())
        assertThat(okHttpClient.connectionSpecs())
            .containsExactly(ConnectionSpec.MODERN_TLS)
    }

    @Test
    fun `𝕄 initialize okhttp with no network policy 𝕎 initialize() {needsClearText}`() {
        // When
        CoreFeature.initialize(
            appContext.mockInstance,
            fakeCredentials,
            fakeConfig.copy(needsClearTextHttp = true),
            fakeConsent
        )

        // Then
        val okHttpClient = CoreFeature.okHttpClient
        assertThat(okHttpClient.protocols())
            .containsExactly(Protocol.HTTP_2, Protocol.HTTP_1_1)
        assertThat(okHttpClient.callTimeoutMillis())
            .isEqualTo(CoreFeature.NETWORK_TIMEOUT_MS.toInt())
        assertThat(okHttpClient.connectionSpecs())
            .containsExactly(ConnectionSpec.CLEARTEXT)
    }

    @Test
    fun `𝕄 initialize executors 𝕎 initialize()`() {
        // When
        CoreFeature.initialize(
            appContext.mockInstance,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(CoreFeature.uploadExecutorService).isNotNull()
        assertThat(CoreFeature.persistenceExecutorService).isNotNull()
    }

    @Test
    fun `𝕄 initialize only once 𝕎 initialize() twice`(
        @Forgery otherCredentials: Credentials
    ) {
        // Given
        CoreFeature.initialize(
            appContext.mockInstance,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // When
        CoreFeature.initialize(
            appContext.mockInstance,
            otherCredentials,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(CoreFeature.clientToken).isEqualTo(fakeCredentials.clientToken)
        assertThat(CoreFeature.packageName).isEqualTo(appContext.fakePackageName)
        assertThat(CoreFeature.packageVersion).isEqualTo(appContext.fakeVersionName)
        assertThat(CoreFeature.serviceName).isEqualTo(fakeCredentials.serviceName)
        assertThat(CoreFeature.envName).isEqualTo(fakeCredentials.envName)
        assertThat(CoreFeature.variant).isEqualTo(fakeCredentials.variant)
        assertThat(CoreFeature.rumApplicationId).isEqualTo(fakeCredentials.rumApplicationId)
        assertThat(CoreFeature.contextRef.get()).isEqualTo(appContext.mockInstance)
        assertThat(CoreFeature.batchSize).isEqualTo(fakeConfig.batchSize)
        assertThat(CoreFeature.uploadFrequency).isEqualTo(fakeConfig.uploadFrequency)
    }

    @Test
    fun `𝕄 detect current process 𝕎 initialize() {main process}`(
        @StringForgery otherProcessName: String
    ) {
        // Given
        val mockActivityManager = mock<ActivityManager>()
        whenever(appContext.mockInstance.getSystemService(Context.ACTIVITY_SERVICE)).thenReturn(
            mockActivityManager
        )
        val myProcess = forgeAppProcessInfo(Process.myPid(), appContext.fakePackageName)
        val otherProcess = forgeAppProcessInfo(Process.myPid() + 1, otherProcessName)
        whenever(mockActivityManager.runningAppProcesses)
            .thenReturn(listOf(myProcess, otherProcess))

        // When
        CoreFeature.initialize(
            appContext.mockInstance,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(CoreFeature.isMainProcess).isTrue()
    }

    @Test
    fun `𝕄 detect current process 𝕎 initialize() {secondary process}`(
        @StringForgery otherProcessName: String
    ) {
        // Given
        val mockActivityManager = mock<ActivityManager>()
        whenever(appContext.mockInstance.getSystemService(Context.ACTIVITY_SERVICE)).thenReturn(
            mockActivityManager
        )
        val myProcess = forgeAppProcessInfo(Process.myPid(), otherProcessName)
        val otherProcess = forgeAppProcessInfo(Process.myPid() + 1, appContext.fakePackageName)
        whenever(mockActivityManager.runningAppProcesses)
            .thenReturn(listOf(myProcess, otherProcess))

        // When
        CoreFeature.initialize(
            appContext.mockInstance,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(CoreFeature.isMainProcess).isFalse()
    }

    @Test
    fun `𝕄 detect current process 𝕎 initialize() {unknown process}`(
        @StringForgery otherProcessName: String
    ) {
        // Given
        val mockActivityManager = mock<ActivityManager>()
        whenever(appContext.mockInstance.getSystemService(Context.ACTIVITY_SERVICE)).thenReturn(
            mockActivityManager
        )
        val otherProcess = forgeAppProcessInfo(Process.myPid() + 1, otherProcessName)
        whenever(mockActivityManager.runningAppProcesses)
            .thenReturn(listOf(otherProcess))

        // When
        CoreFeature.initialize(
            appContext.mockInstance,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(CoreFeature.isMainProcess).isTrue()
    }

    @Test
    fun `𝕄 cleanup NdkCrashHandler 𝕎 stop()`() {
        // Given
        CoreFeature.initialize(
            appContext.mockInstance,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // When
        CoreFeature.stop()

        // Then
        assertThat(CoreFeature.ndkCrashHandler).isInstanceOf(NoOpNdkCrashHandler::class.java)
    }

    @Test
    fun `𝕄 cleanup app info 𝕎 stop()`() {
        // Given
        CoreFeature.initialize(
            appContext.mockInstance,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // When
        CoreFeature.stop()

        // Then
        assertThat(CoreFeature.clientToken).isEqualTo("")
        assertThat(CoreFeature.packageName).isEqualTo("")
        assertThat(CoreFeature.packageVersion).isEqualTo("")
        assertThat(CoreFeature.serviceName).isEqualTo("")
        assertThat(CoreFeature.envName).isEqualTo("")
        assertThat(CoreFeature.variant).isEqualTo("")
        assertThat(CoreFeature.rumApplicationId).isNull()
        assertThat(CoreFeature.contextRef.get()).isNull()
    }

    @Test
    fun `𝕄 cleanup providers 𝕎 stop()`() {
        // Given
        CoreFeature.initialize(
            appContext.mockInstance,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // When
        CoreFeature.stop()

        // Then
        assertThat(CoreFeature.firstPartyHostDetector.knownHosts)
            .isEmpty()
        assertThat(CoreFeature.networkInfoProvider)
            .isInstanceOf(NoOpNetworkInfoProvider::class.java)
        assertThat(CoreFeature.systemInfoProvider)
            .isInstanceOf(NoOpSystemInfoProvider::class.java)
        assertThat(CoreFeature.timeProvider)
            .isInstanceOf(NoOpTimeProvider::class.java)
        assertThat(CoreFeature.trackingConsentProvider)
            .isInstanceOf(NoOpConsentProvider::class.java)
        assertThat(CoreFeature.userInfoProvider)
            .isInstanceOf(NoOpMutableUserInfoProvider::class.java)
    }

    @Test
    fun `𝕄 shut down executors 𝕎 stop()`() {
        // Given
        CoreFeature.initialize(
            appContext.mockInstance,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )
        val mockUploadExecutorService: ScheduledThreadPoolExecutor = mock()
        CoreFeature.uploadExecutorService = mockUploadExecutorService
        val mockPersistenceExecutorService: ExecutorService = mock()
        CoreFeature.persistenceExecutorService = mockPersistenceExecutorService

        // When
        CoreFeature.stop()

        // Then
        verify(mockUploadExecutorService).shutdownNow()
        verify(mockPersistenceExecutorService).shutdownNow()
    }

    @Test
    fun `𝕄 unregister tracking consent callbacks 𝕎 stop()`() {
        // Given
        CoreFeature.initialize(
            appContext.mockInstance,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )
        val mockConsentProvider: ConsentProvider = mock()
        CoreFeature.trackingConsentProvider = mockConsentProvider

        // When
        CoreFeature.stop()

        // Then
        verify(mockConsentProvider).unregisterAllCallbacks()
    }

    @Test
    fun `𝕄 build config 𝕎 buildFilePersistenceConfig()`() {
        // Given
        CoreFeature.initialize(
            appContext.mockInstance,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // When
        val config = CoreFeature.buildFilePersistenceConfig()

        // Then
        assertThat(config.maxBatchSize)
            .isEqualTo(FilePersistenceConfig.MAX_BATCH_SIZE)
        assertThat(config.maxDiskSpace)
            .isEqualTo(FilePersistenceConfig.MAX_DISK_SPACE)
        assertThat(config.oldFileThreshold)
            .isEqualTo(FilePersistenceConfig.OLD_FILE_THRESHOLD)
        assertThat(config.maxItemsPerBatch)
            .isEqualTo(FilePersistenceConfig.MAX_ITEMS_PER_BATCH)
        assertThat(config.recentDelayMs)
            .isEqualTo(fakeConfig.batchSize.windowDurationMs)
    }

    @Test
    fun `𝕄 initialize the NdkCrashHandler data 𝕎 initialize() {main process}`(
        @StringForgery otherProcessName: String
    ) {
        // Given
        val mockActivityManager = mock<ActivityManager>()
        whenever(appContext.mockInstance.getSystemService(Context.ACTIVITY_SERVICE)).thenReturn(
            mockActivityManager
        )
        val myProcess = forgeAppProcessInfo(Process.myPid(), appContext.fakePackageName)
        val otherProcess = forgeAppProcessInfo(Process.myPid() + 1, otherProcessName)
        whenever(mockActivityManager.runningAppProcesses)
            .thenReturn(listOf(myProcess, otherProcess))

        // When
        CoreFeature.initialize(
            appContext.mockInstance,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(CoreFeature.ndkCrashHandler).isInstanceOf(DatadogNdkCrashHandler::class.java)
    }

    @Test
    fun `𝕄 not initialize the NdkCrashHandler data 𝕎 initialize() {not main process}`(
        @StringForgery otherProcessName: String
    ) {
        // Given
        val mockActivityManager = mock<ActivityManager>()
        whenever(appContext.mockInstance.getSystemService(Context.ACTIVITY_SERVICE)).thenReturn(
            mockActivityManager
        )
        val myProcess = forgeAppProcessInfo(Process.myPid(), otherProcessName)
        whenever(mockActivityManager.runningAppProcesses)
            .thenReturn(listOf(myProcess))

        // When
        CoreFeature.initialize(
            appContext.mockInstance,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(CoreFeature.ndkCrashHandler).isInstanceOf(NoOpNdkCrashHandler::class.java)
    }

    // region Internal

    private fun forgeAppProcessInfo(
        processId: Int,
        processName: String
    ): ActivityManager.RunningAppProcessInfo {
        return ActivityManager.RunningAppProcessInfo(
            "",
            0,
            emptyArray()
        ).apply {
            this.processName = processName
            this.pid = processId
        }
    }

    // endregion

    companion object {
        val appContext = ApplicationContextTestConfiguration(Application::class.java)

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(appContext)
        }
    }
}
