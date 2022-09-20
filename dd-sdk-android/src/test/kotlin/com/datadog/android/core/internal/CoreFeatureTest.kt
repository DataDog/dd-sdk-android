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
import android.content.pm.PackageManager
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
import com.datadog.android.v2.core.internal.DatadogContextProvider
import com.datadog.android.v2.core.internal.NoOpContextProvider
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
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.isA
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.AdvancedForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.MapForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.File
import java.net.Proxy
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import okhttp3.Authenticator
import okhttp3.CipherSuite
import okhttp3.ConnectionSpec
import okhttp3.Protocol
import okhttp3.TlsVersion
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
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class CoreFeatureTest {

    private lateinit var testedFeature: CoreFeature

    @Mock
    lateinit var mockConnectivityMgr: ConnectivityManager

    @Forgery
    lateinit var fakeCredentials: Credentials

    @Forgery
    lateinit var fakeConfig: Configuration.Core

    @Forgery
    lateinit var fakeConsent: TrackingConsent

    @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL)
    lateinit var fakeSdkInstanceId: String

    @StringForgery(regex = "[a-zA-Z0-9_:./-]{0,195}[a-zA-Z0-9_./-]")
    lateinit var fakeEnvName: String

    @BeforeEach
    fun `set up`() {
        testedFeature = CoreFeature()
        whenever(appContext.mockInstance.getSystemService(Context.CONNECTIVITY_SERVICE))
            .doReturn(mockConnectivityMgr)
    }

    @AfterEach
    fun `tear down`() {
        testedFeature.stop()
    }

    // region initialization

    @Test
    fun `𝕄 initialize time sync 𝕎 initialize`() {
        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(testedFeature.kronosClock).isNotNull()
    }

    @Test
    fun `𝕄 initialize time provider 𝕎 initialize`() {
        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(testedFeature.timeProvider)
            .isInstanceOf(KronosTimeProvider::class.java)
    }

    @Test
    fun `𝕄 initialize system info provider 𝕎 initialize`() {
        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(testedFeature.systemInfoProvider)
            .isInstanceOf(BroadcastReceiverSystemInfoProvider::class.java)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun `𝕄 initialize network info provider 𝕎 initialize {LOLLIPOP}`() {
        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
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
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
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
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(testedFeature.userInfoProvider)
            .isInstanceOf(DatadogUserInfoProvider::class.java)
    }

    @Test
    fun `𝕄 initialise the consent provider 𝕎 initialize`() {
        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(testedFeature.trackingConsentProvider)
            .isInstanceOf(TrackingConsentProvider::class.java)
        assertThat(testedFeature.trackingConsentProvider.getConsent())
            .isEqualTo(fakeConsent)
    }

    @Test
    fun `𝕄 initialise the datadog context provider 𝕎 initialize`() {
        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(testedFeature.contextProvider)
            .isInstanceOf(DatadogContextProvider::class.java)
    }

    @Test
    fun `𝕄 initializes first party hosts detector 𝕎 initialize`() {
        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(testedFeature.firstPartyHostDetector.knownHosts)
            .containsAll(fakeConfig.firstPartyHosts.map { it.lowercase(Locale.US) })
    }

    @Test
    fun `𝕄 initializes app info 𝕎 initialize()`() {
        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(testedFeature.clientToken).isEqualTo(fakeCredentials.clientToken)
        assertThat(testedFeature.packageName).isEqualTo(appContext.fakePackageName)
        assertThat(testedFeature.packageVersionProvider.version)
            .isEqualTo(appContext.fakeVersionName)
        assertThat(testedFeature.serviceName).isEqualTo(fakeCredentials.serviceName)
        assertThat(testedFeature.envName).isEqualTo(fakeCredentials.envName)
        assertThat(testedFeature.variant).isEqualTo(fakeCredentials.variant)
        assertThat(testedFeature.rumApplicationId).isEqualTo(fakeCredentials.rumApplicationId)
        assertThat(testedFeature.contextRef.get()).isEqualTo(appContext.mockInstance)
        assertThat(testedFeature.batchSize).isEqualTo(fakeConfig.batchSize)
        assertThat(testedFeature.uploadFrequency).isEqualTo(fakeConfig.uploadFrequency)
    }

    @Test
    fun `𝕄 initializes app info 𝕎 initialize() {null serviceName}`() {
        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeCredentials.copy(serviceName = null),
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(testedFeature.clientToken).isEqualTo(fakeCredentials.clientToken)
        assertThat(testedFeature.packageName).isEqualTo(appContext.fakePackageName)
        assertThat(testedFeature.packageVersionProvider.version)
            .isEqualTo(appContext.fakeVersionName)
        assertThat(testedFeature.serviceName).isEqualTo(appContext.fakePackageName)
        assertThat(testedFeature.envName).isEqualTo(fakeCredentials.envName)
        assertThat(testedFeature.variant).isEqualTo(fakeCredentials.variant)
        assertThat(testedFeature.rumApplicationId).isEqualTo(fakeCredentials.rumApplicationId)
        assertThat(testedFeature.contextRef.get()).isEqualTo(appContext.mockInstance)
        assertThat(testedFeature.batchSize).isEqualTo(fakeConfig.batchSize)
        assertThat(testedFeature.uploadFrequency).isEqualTo(fakeConfig.uploadFrequency)
    }

    @Test
    fun `𝕄 initializes app info 𝕎 initialize() {null rumApplicationId}`() {
        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeCredentials.copy(rumApplicationId = null),
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(testedFeature.clientToken).isEqualTo(fakeCredentials.clientToken)
        assertThat(testedFeature.packageName).isEqualTo(appContext.fakePackageName)
        assertThat(testedFeature.packageVersionProvider.version)
            .isEqualTo(appContext.fakeVersionName)
        assertThat(testedFeature.serviceName).isEqualTo(fakeCredentials.serviceName)
        assertThat(testedFeature.envName).isEqualTo(fakeCredentials.envName)
        assertThat(testedFeature.variant).isEqualTo(fakeCredentials.variant)
        assertThat(testedFeature.rumApplicationId).isNull()
        assertThat(testedFeature.contextRef.get()).isEqualTo(appContext.mockInstance)
        assertThat(testedFeature.batchSize).isEqualTo(fakeConfig.batchSize)
        assertThat(testedFeature.uploadFrequency).isEqualTo(fakeConfig.uploadFrequency)
    }

    @Test
    fun `𝕄 initializes app info 𝕎 initialize() {null versionName}`() {
        // Given
        appContext.fakePackageInfo.versionName = null
        whenever(appContext.mockInstance.getSystemService(Context.CONNECTIVITY_SERVICE))
            .doReturn(mockConnectivityMgr)

        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeCredentials.copy(rumApplicationId = null),
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(testedFeature.clientToken).isEqualTo(fakeCredentials.clientToken)
        assertThat(testedFeature.packageName).isEqualTo(appContext.fakePackageName)
        assertThat(testedFeature.packageVersionProvider.version)
            .isEqualTo(appContext.fakeVersionCode.toString())
        assertThat(testedFeature.serviceName).isEqualTo(fakeCredentials.serviceName)
        assertThat(testedFeature.envName).isEqualTo(fakeCredentials.envName)
        assertThat(testedFeature.variant).isEqualTo(fakeCredentials.variant)
        assertThat(testedFeature.rumApplicationId).isNull()
        assertThat(testedFeature.contextRef.get()).isEqualTo(appContext.mockInstance)
        assertThat(testedFeature.batchSize).isEqualTo(fakeConfig.batchSize)
        assertThat(testedFeature.uploadFrequency).isEqualTo(fakeConfig.uploadFrequency)
    }

    @Test
    fun `𝕄 initializes app info 𝕎 initialize() {unknown package name}`() {
        // Given
        whenever(appContext.mockPackageManager.getPackageInfo(appContext.fakePackageName, 0))
            .doThrow(PackageManager.NameNotFoundException())
        whenever(appContext.mockInstance.getSystemService(Context.CONNECTIVITY_SERVICE))
            .doReturn(mockConnectivityMgr)

        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeCredentials.copy(rumApplicationId = null),
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(testedFeature.clientToken).isEqualTo(fakeCredentials.clientToken)
        assertThat(testedFeature.packageName).isEqualTo(appContext.fakePackageName)
        assertThat(testedFeature.packageVersionProvider.version)
            .isEqualTo(CoreFeature.DEFAULT_APP_VERSION)
        assertThat(testedFeature.serviceName).isEqualTo(fakeCredentials.serviceName)
        assertThat(testedFeature.envName).isEqualTo(fakeCredentials.envName)
        assertThat(testedFeature.variant).isEqualTo(fakeCredentials.variant)
        assertThat(testedFeature.rumApplicationId).isNull()
        assertThat(testedFeature.contextRef.get()).isEqualTo(appContext.mockInstance)
        assertThat(testedFeature.batchSize).isEqualTo(fakeConfig.batchSize)
        assertThat(testedFeature.uploadFrequency).isEqualTo(fakeConfig.uploadFrequency)
    }

    @Test
    fun `𝕄 initialize okhttp with strict network policy 𝕎 initialize()`() {
        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeCredentials,
            fakeConfig.copy(needsClearTextHttp = false),
            fakeConsent
        )

        // Then
        val okHttpClient = testedFeature.okHttpClient
        assertThat(okHttpClient.protocols())
            .containsExactly(Protocol.HTTP_2, Protocol.HTTP_1_1)
        assertThat(okHttpClient.callTimeoutMillis())
            .isEqualTo(CoreFeature.NETWORK_TIMEOUT_MS.toInt())
        assertThat(okHttpClient.connectionSpecs())
            .hasSize(1)

        val connectionSpec = okHttpClient.connectionSpecs().first()

        assertThat(connectionSpec.isTls).isTrue()
        assertThat(connectionSpec.tlsVersions())
            .containsExactly(TlsVersion.TLS_1_2, TlsVersion.TLS_1_3)
        assertThat(connectionSpec.supportsTlsExtensions()).isTrue()
        assertThat(connectionSpec.cipherSuites()).containsExactly(
            CipherSuite.TLS_AES_128_GCM_SHA256,
            CipherSuite.TLS_AES_256_GCM_SHA384,
            CipherSuite.TLS_CHACHA20_POLY1305_SHA256,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384,
            CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA256,
            CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA256
        )
    }

    @Test
    fun `𝕄 initialize okhttp with no network policy 𝕎 initialize() {needsClearText}`() {
        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeCredentials,
            fakeConfig.copy(needsClearTextHttp = true),
            fakeConsent
        )

        // Then
        val okHttpClient = testedFeature.okHttpClient
        assertThat(okHttpClient.protocols())
            .containsExactly(Protocol.HTTP_2, Protocol.HTTP_1_1)
        assertThat(okHttpClient.callTimeoutMillis())
            .isEqualTo(CoreFeature.NETWORK_TIMEOUT_MS.toInt())
        assertThat(okHttpClient.connectionSpecs())
            .containsExactly(ConnectionSpec.CLEARTEXT)
    }

    @Test
    fun `𝕄 initialize okhttp with proxy 𝕎 initialize() {proxy configured}`() {
        // When
        val proxy: Proxy = mock()
        val proxyAuth: Authenticator = mock()
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeCredentials,
            fakeConfig.copy(proxy = proxy, proxyAuth = proxyAuth),
            fakeConsent
        )

        // Then
        val okHttpClient = testedFeature.okHttpClient
        assertThat(okHttpClient.proxy()).isSameAs(proxy)
        assertThat(okHttpClient.proxyAuthenticator()).isSameAs(proxyAuth)
    }

    @Test
    fun `𝕄 initialize okhttp without proxy 𝕎 initialize() {proxy not configured}`() {
        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeCredentials,
            fakeConfig.copy(proxy = null),
            fakeConsent
        )

        // Then
        val okHttpClient = testedFeature.okHttpClient
        assertThat(okHttpClient.proxy()).isNull()
        assertThat(okHttpClient.proxyAuthenticator()).isEqualTo(Authenticator.NONE)
    }

    @Test
    fun `𝕄 initialize executors 𝕎 initialize()`() {
        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(testedFeature.uploadExecutorService).isNotNull()
        assertThat(testedFeature.persistenceExecutorService).isNotNull()
    }

    @Test
    fun `𝕄 initialize only once 𝕎 initialize() twice`(
        @Forgery otherCredentials: Credentials
    ) {
        // Given
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            otherCredentials,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(testedFeature.clientToken).isEqualTo(fakeCredentials.clientToken)
        assertThat(testedFeature.packageName).isEqualTo(appContext.fakePackageName)
        assertThat(testedFeature.packageVersionProvider.version)
            .isEqualTo(appContext.fakeVersionName)
        assertThat(testedFeature.serviceName).isEqualTo(fakeCredentials.serviceName)
        assertThat(testedFeature.envName).isEqualTo(fakeCredentials.envName)
        assertThat(testedFeature.variant).isEqualTo(fakeCredentials.variant)
        assertThat(testedFeature.rumApplicationId).isEqualTo(fakeCredentials.rumApplicationId)
        assertThat(testedFeature.contextRef.get()).isEqualTo(appContext.mockInstance)
        assertThat(testedFeature.batchSize).isEqualTo(fakeConfig.batchSize)
        assertThat(testedFeature.uploadFrequency).isEqualTo(fakeConfig.uploadFrequency)
    }

    @Test
    fun `𝕄 detect current process 𝕎 initialize() {main process}`(
        @StringForgery otherProcessName: String,
        @IntForgery processImportance: Int
    ) {
        // Given
        val mockActivityManager = mock<ActivityManager>()
        whenever(appContext.mockInstance.getSystemService(Context.ACTIVITY_SERVICE)).thenReturn(
            mockActivityManager
        )
        val myProcess = forgeAppProcessInfo(
            Process.myPid(),
            appContext.fakePackageName,
            processImportance
        )
        val otherProcess = forgeAppProcessInfo(
            Process.myPid() + 1,
            otherProcessName,
            processImportance + 1
        )
        whenever(mockActivityManager.runningAppProcesses)
            .thenReturn(listOf(myProcess, otherProcess))

        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(testedFeature.isMainProcess).isTrue()
        assertThat(CoreFeature.processImportance).isEqualTo(processImportance)
    }

    @Test
    fun `𝕄 detect current process 𝕎 initialize() {secondary process}`(
        @StringForgery otherProcessName: String,
        @IntForgery processImportance: Int
    ) {
        // Given
        val mockActivityManager = mock<ActivityManager>()
        whenever(appContext.mockInstance.getSystemService(Context.ACTIVITY_SERVICE)).thenReturn(
            mockActivityManager
        )
        val myProcess = forgeAppProcessInfo(Process.myPid(), otherProcessName, processImportance)
        val otherProcess = forgeAppProcessInfo(
            Process.myPid() + 1,
            appContext.fakePackageName,
            processImportance
        )
        whenever(mockActivityManager.runningAppProcesses)
            .thenReturn(listOf(myProcess, otherProcess))

        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(testedFeature.isMainProcess).isFalse()
        assertThat(CoreFeature.processImportance).isEqualTo(processImportance)
    }

    @Test
    fun `𝕄 detect current process 𝕎 initialize() {unknown process}`(
        @StringForgery otherProcessName: String,
        @IntForgery otherProcessImportance: Int
    ) {
        // Given
        val mockActivityManager = mock<ActivityManager>()
        whenever(appContext.mockInstance.getSystemService(Context.ACTIVITY_SERVICE)).thenReturn(
            mockActivityManager
        )
        val otherProcess = forgeAppProcessInfo(
            Process.myPid() + 1,
            otherProcessName,
            otherProcessImportance
        )
        whenever(mockActivityManager.runningAppProcesses)
            .thenReturn(listOf(otherProcess))

        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(testedFeature.isMainProcess).isTrue()
        assertThat(CoreFeature.processImportance)
            .isEqualTo(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND)
    }

    @Test
    fun `𝕄 build config 𝕎 buildFilePersistenceConfig()`() {
        // Given
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // When
        val config = testedFeature.buildFilePersistenceConfig()

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
        @TempDir tempDir: File,
        @StringForgery otherProcessName: String,
        @IntForgery processImportance: Int
    ) {
        // Given
        val mockActivityManager = mock<ActivityManager>()
        whenever(appContext.mockInstance.getSystemService(Context.ACTIVITY_SERVICE)).thenReturn(
            mockActivityManager
        )
        val myProcess = forgeAppProcessInfo(
            Process.myPid(),
            appContext.fakePackageName,
            processImportance
        )
        val otherProcess = forgeAppProcessInfo(
            Process.myPid() + 1,
            otherProcessName,
            processImportance + 1
        )
        whenever(mockActivityManager.runningAppProcesses)
            .thenReturn(listOf(myProcess, otherProcess))
        whenever(appContext.mockInstance.cacheDir) doReturn tempDir

        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(testedFeature.ndkCrashHandler)
            .isInstanceOfSatisfying(DatadogNdkCrashHandler::class.java) {
                assertThat(it.ndkCrashDataDirectory.parentFile).isEqualTo(
                    File(
                        tempDir,
                        CoreFeature.DATADOG_STORAGE_DIR_NAME.format(Locale.US, fakeSdkInstanceId)
                    )
                )
                assertThat(it.timeProvider)
                    .isInstanceOf(KronosTimeProvider::class.java)
            }
    }

    @Test
    fun `𝕄 not initialize the NdkCrashHandler data 𝕎 initialize() {not main process}`(
        @StringForgery otherProcessName: String,
        @IntForgery processImportance: Int
    ) {
        // Given
        val mockActivityManager = mock<ActivityManager>()
        whenever(appContext.mockInstance.getSystemService(Context.ACTIVITY_SERVICE)).thenReturn(
            mockActivityManager
        )
        val myProcess = forgeAppProcessInfo(Process.myPid(), otherProcessName, processImportance)
        whenever(mockActivityManager.runningAppProcesses)
            .thenReturn(listOf(myProcess))

        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(testedFeature.ndkCrashHandler).isInstanceOf(NoOpNdkCrashHandler::class.java)
    }

    @Test
    fun `M initialize webViewTrackingHosts W initialize()`() {
        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(testedFeature.webViewTrackingHosts).isEqualTo(fakeConfig.webViewTrackingHosts)
    }

    @Test
    fun `M initialize storage directory W initialize()`(
        @TempDir tempDir: File
    ) {
        // Given
        whenever(appContext.mockInstance.cacheDir) doReturn tempDir

        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(testedFeature.storageDir).isEqualTo(
            File(
                tempDir,
                CoreFeature.DATADOG_STORAGE_DIR_NAME.format(Locale.US, fakeSdkInstanceId)
            )
        )
    }

    // endregion

    // region shutdown

    @Test
    fun `𝕄 cleanup NdkCrashHandler 𝕎 stop()`() {
        // Given
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // When
        testedFeature.stop()

        // Then
        assertThat(testedFeature.ndkCrashHandler).isInstanceOf(NoOpNdkCrashHandler::class.java)
    }

    @Test
    fun `𝕄 cleanup app info 𝕎 stop()`() {
        // Given
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // When
        testedFeature.stop()

        // Then
        assertThat(testedFeature.clientToken).isEqualTo("")
        assertThat(testedFeature.packageName).isEqualTo("")
        assertThat(testedFeature.packageVersionProvider.version).isEqualTo("")
        assertThat(testedFeature.serviceName).isEqualTo("")
        assertThat(testedFeature.envName).isEqualTo("")
        assertThat(testedFeature.variant).isEqualTo("")
        assertThat(testedFeature.rumApplicationId).isNull()
        assertThat(testedFeature.contextRef.get()).isNull()
    }

    @Test
    fun `𝕄 cleanup providers 𝕎 stop()`() {
        // Given
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // When
        testedFeature.stop()

        // Then
        assertThat(testedFeature.firstPartyHostDetector.knownHosts)
            .isEmpty()
        assertThat(testedFeature.networkInfoProvider)
            .isInstanceOf(NoOpNetworkInfoProvider::class.java)
        assertThat(testedFeature.systemInfoProvider)
            .isInstanceOf(NoOpSystemInfoProvider::class.java)
        assertThat(testedFeature.timeProvider)
            .isInstanceOf(NoOpTimeProvider::class.java)
        assertThat(testedFeature.trackingConsentProvider)
            .isInstanceOf(NoOpConsentProvider::class.java)
        assertThat(testedFeature.userInfoProvider)
            .isInstanceOf(NoOpMutableUserInfoProvider::class.java)
        assertThat(testedFeature.contextProvider)
            .isInstanceOf(NoOpContextProvider::class.java)
    }

    @Test
    fun `𝕄 shut down executors 𝕎 stop()`() {
        // Given
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )
        val mockUploadExecutorService: ScheduledThreadPoolExecutor = mock()
        testedFeature.uploadExecutorService = mockUploadExecutorService
        val mockPersistenceExecutorService: ExecutorService = mock()
        testedFeature.persistenceExecutorService = mockPersistenceExecutorService

        // When
        testedFeature.stop()

        // Then
        verify(mockUploadExecutorService).shutdownNow()
        verify(mockPersistenceExecutorService).shutdownNow()
    }

    @Test
    fun `𝕄 unregister tracking consent callbacks 𝕎 stop()`() {
        // Given
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )
        val mockConsentProvider: ConsentProvider = mock()
        testedFeature.trackingConsentProvider = mockConsentProvider

        // When
        testedFeature.stop()

        // Then
        verify(mockConsentProvider).unregisterAllCallbacks()
    }

    @Test
    fun `𝕄 clean up feature context 𝕎 stop()`(
        @StringForgery feature: String,
        @MapForgery(
            key = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHABETICAL)]),
            value = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHABETICAL)])
        ) context: Map<String, String>
    ) {
        // Given
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )
        testedFeature.featuresContext[feature] = context

        // When
        testedFeature.stop()

        // Then
        assertThat(testedFeature.featuresContext).isEmpty()
    }

    @Test
    fun `M drain the persistence executor queue W drainAndShutdownExecutors()`(forge: Forge) {
        // Given
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        val blockingQueue = LinkedBlockingQueue<Runnable>(forge.aList { mock() })
        val persistenceExecutor =
            ThreadPoolExecutor(1, 1, 1, TimeUnit.SECONDS, blockingQueue)
        testedFeature.persistenceExecutorService = persistenceExecutor

        // When
        testedFeature.drainAndShutdownExecutors()

        // Then
        blockingQueue.forEach {
            verify(it).run()
        }
    }

    @Test
    fun `M drain the upload executor queue W drainAndShutdownExecutors()`(forge: Forge) {
        // Given
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        val blockingQueue = LinkedBlockingQueue<Runnable>(forge.aList { mock() })
        val mockUploadExecutor: ScheduledThreadPoolExecutor = mock()
        whenever(mockUploadExecutor.queue).thenReturn(blockingQueue)
        testedFeature.uploadExecutorService = mockUploadExecutor

        // When
        testedFeature.drainAndShutdownExecutors()

        // Then
        blockingQueue.forEach {
            verify(it).run()
        }
    }

    @Test
    fun `M shutdown with wait the persistence executor W drainAndShutdownExecutors()`() {
        // Given
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        val mockPersistenceExecutorService: ExecutorService = mock()
        testedFeature.persistenceExecutorService = mockPersistenceExecutorService

        // When
        testedFeature.drainAndShutdownExecutors()

        // Then
        inOrder(mockPersistenceExecutorService) {
            verify(mockPersistenceExecutorService).shutdown()
            verify(mockPersistenceExecutorService).awaitTermination(10, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `M shutdown with wait the upload executor W drainAndShutdownExecutors()`() {
        // Given
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        val blockingQueue = LinkedBlockingQueue<Runnable>()
        val mockUploadService: ScheduledThreadPoolExecutor = mock()
        whenever(mockUploadService.queue).thenReturn(blockingQueue)
        testedFeature.uploadExecutorService = mockUploadService

        // When
        testedFeature.drainAndShutdownExecutors()

        // Then
        inOrder(mockUploadService) {
            verify(mockUploadService).shutdown()
            verify(mockUploadService).awaitTermination(10, TimeUnit.SECONDS)
        }
    }

    // endregion

    // region Internal

    private fun forgeAppProcessInfo(
        processId: Int,
        processName: String,
        importance: Int
    ): ActivityManager.RunningAppProcessInfo {
        return ActivityManager.RunningAppProcessInfo().apply {
            this.processName = processName
            this.pid = processId
            this.importance = importance
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
