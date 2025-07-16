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
import com.datadog.android.Datadog
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.internal.net.info.BroadcastReceiverNetworkInfoProvider
import com.datadog.android.core.internal.net.info.CallbackNetworkInfoProvider
import com.datadog.android.core.internal.net.info.NoOpNetworkInfoProvider
import com.datadog.android.core.internal.persistence.file.FilePersistenceConfig
import com.datadog.android.core.internal.persistence.file.batch.BatchFileReaderWriter
import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.core.internal.privacy.NoOpConsentProvider
import com.datadog.android.core.internal.privacy.TrackingConsentProvider
import com.datadog.android.core.internal.system.BroadcastReceiverSystemInfoProvider
import com.datadog.android.core.internal.system.NoOpSystemInfoProvider
import com.datadog.android.core.internal.time.AppStartTimeProvider
import com.datadog.android.core.internal.time.KronosTimeProvider
import com.datadog.android.core.internal.time.NoOpTimeProvider
import com.datadog.android.core.internal.user.DatadogUserInfoProvider
import com.datadog.android.core.internal.user.NoOpMutableUserInfoProvider
import com.datadog.android.core.persistence.PersistenceStrategy
import com.datadog.android.core.thread.FlushableExecutorService
import com.datadog.android.ndk.internal.DatadogNdkCrashHandler
import com.datadog.android.ndk.internal.NoOpNdkCrashHandler
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.security.Encryption
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.assertj.containsInstanceOf
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.google.gson.JsonObject
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.AdvancedForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.MapForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.Authenticator
import okhttp3.CipherSuite
import okhttp3.ConnectionSpec
import okhttp3.Protocol
import okhttp3.TlsVersion
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.net.Proxy
import java.util.Locale
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.experimental.xor

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

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockPersistenceExecutorService: FlushableExecutorService

    @Mock
    lateinit var mockScheduledExecutorService: ScheduledExecutorService

    @Mock
    lateinit var mockAppStartTimeProvider: AppStartTimeProvider

    @Forgery
    lateinit var fakeConfig: Configuration

    @Forgery
    lateinit var fakeConsent: TrackingConsent

    @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL)
    lateinit var fakeSdkInstanceId: String

    @Forgery
    lateinit var fakeBuildId: UUID

    @BeforeEach
    fun `set up`() {
        CoreFeature.disableKronosBackgroundSync = true
        testedFeature = CoreFeature(
            mockInternalLogger,
            mockAppStartTimeProvider,
            executorServiceFactory = { _, _, _ -> mockPersistenceExecutorService },
            scheduledExecutorServiceFactory = { _, _, _ -> mockScheduledExecutorService }
        )
        whenever(appContext.mockInstance.getSystemService(Context.CONNECTIVITY_SERVICE))
            .doReturn(mockConnectivityMgr)
        whenever(
            appContext.mockInstance.assets.open(CoreFeature.BUILD_ID_FILE_NAME)
        ) doReturn fakeBuildId.toString().byteInputStream()
        whenever(mockPersistenceExecutorService.execute(any())) doAnswer {
            it.getArgument<Runnable>(0).run()
        }
    }

    @AfterEach
    fun `tear down`() {
        testedFeature.stop()
    }

    // region initialization

    @Test
    fun `M initialize time sync W initialize`() {
        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(testedFeature.kronosClock).isNotNull()
    }

    @Test
    fun `M initialize time provider W initialize`() {
        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(testedFeature.timeProvider)
            .isInstanceOf(KronosTimeProvider::class.java)
    }

    @Test
    fun `M initialize system info provider W initialize`() {
        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(testedFeature.systemInfoProvider)
            .isInstanceOf(BroadcastReceiverSystemInfoProvider::class.java)
    }

    @Test
    @Disabled // RUM-10684: ApiLevelExtension is not able to set API level property
    fun `M initialize network info provider W initialize`() {
        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
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
    @Disabled // RUM-10684: ApiLevelExtension is not able to set API level property
    fun `M initialize network info provider W initialize {N}`() {
        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
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
                .isTrue
            verify(mockConnectivityMgr)
                .registerDefaultNetworkCallback(isA<CallbackNetworkInfoProvider>())
        }
    }

    @Test
    fun `M initialize user info provider W initialize`() {
        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(testedFeature.userInfoProvider)
            .isInstanceOf(DatadogUserInfoProvider::class.java)
    }

    @Test
    fun `M initialise the consent provider W initialize`() {
        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
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
    fun `M initialise the datadog context provider W initialize`() {
        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(testedFeature.contextProvider)
            .isInstanceOf(DatadogContextProvider::class.java)
    }

    @Test
    fun `M initializes first party hosts resolver W initialize`() {
        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(testedFeature.firstPartyHostHeaderTypeResolver.knownHosts.keys)
            .containsAll(
                fakeConfig.coreConfig.firstPartyHostsWithHeaderTypes.keys.map {
                    it.lowercase(
                        Locale.US
                    )
                }
            )
    }

    @Test
    fun `M initializes app info W initialize()`() {
        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(testedFeature.clientToken).isEqualTo(fakeConfig.clientToken)
        assertThat(testedFeature.packageName).isEqualTo(appContext.fakePackageName)
        assertThat(testedFeature.packageVersionProvider.version).isEqualTo(appContext.fakeVersionName)
        assertThat(testedFeature.serviceName).isEqualTo(fakeConfig.service)
        assertThat(testedFeature.envName).isEqualTo(fakeConfig.env)
        assertThat(testedFeature.variant).isEqualTo(fakeConfig.variant)
        assertThat(testedFeature.contextRef.get()).isEqualTo(appContext.mockInstance)
        assertThat(testedFeature.batchSize).isEqualTo(fakeConfig.coreConfig.batchSize)
        assertThat(testedFeature.uploadFrequency).isEqualTo(fakeConfig.coreConfig.uploadFrequency)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.TIRAMISU)
    fun `M initializes app info W initialize() { TIRAMISU }`() {
        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(testedFeature.clientToken).isEqualTo(fakeConfig.clientToken)
        assertThat(testedFeature.packageName).isEqualTo(appContext.fakePackageName)
        assertThat(testedFeature.packageVersionProvider.version)
            .isEqualTo(appContext.fakeVersionName)
        assertThat(testedFeature.serviceName).isEqualTo(fakeConfig.service)
        assertThat(testedFeature.envName).isEqualTo(fakeConfig.env)
        assertThat(testedFeature.variant).isEqualTo(fakeConfig.variant)
        assertThat(testedFeature.contextRef.get()).isEqualTo(appContext.mockInstance)
        assertThat(testedFeature.batchSize).isEqualTo(fakeConfig.coreConfig.batchSize)
        assertThat(testedFeature.uploadFrequency).isEqualTo(fakeConfig.coreConfig.uploadFrequency)
    }

    @Test
    fun `M initializes app info W initialize() {null serviceName}`() {
        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeConfig.copy(service = null),
            fakeConsent
        )

        // Then
        assertThat(testedFeature.clientToken).isEqualTo(fakeConfig.clientToken)
        assertThat(testedFeature.packageName).isEqualTo(appContext.fakePackageName)
        assertThat(testedFeature.packageVersionProvider.version)
            .isEqualTo(appContext.fakeVersionName)
        assertThat(testedFeature.serviceName).isEqualTo(appContext.fakePackageName)
        assertThat(testedFeature.envName).isEqualTo(fakeConfig.env)
        assertThat(testedFeature.variant).isEqualTo(fakeConfig.variant)
        assertThat(testedFeature.contextRef.get()).isEqualTo(appContext.mockInstance)
        assertThat(testedFeature.batchSize).isEqualTo(fakeConfig.coreConfig.batchSize)
        assertThat(testedFeature.uploadFrequency).isEqualTo(fakeConfig.coreConfig.uploadFrequency)
    }

    @Test
    fun `M initializes app info W initialize() {null versionName}`() {
        // Given
        appContext.fakePackageInfo.versionName = null
        whenever(appContext.mockInstance.getSystemService(Context.CONNECTIVITY_SERVICE))
            .doReturn(mockConnectivityMgr)

        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(testedFeature.clientToken).isEqualTo(fakeConfig.clientToken)
        assertThat(testedFeature.packageName).isEqualTo(appContext.fakePackageName)
        assertThat(testedFeature.packageVersionProvider.version)
            .isEqualTo(appContext.fakeVersionCode.toString())
        assertThat(testedFeature.serviceName).isEqualTo(fakeConfig.service)
        assertThat(testedFeature.envName).isEqualTo(fakeConfig.env)
        assertThat(testedFeature.variant).isEqualTo(fakeConfig.variant)
        assertThat(testedFeature.contextRef.get()).isEqualTo(appContext.mockInstance)
        assertThat(testedFeature.batchSize).isEqualTo(fakeConfig.coreConfig.batchSize)
        assertThat(testedFeature.uploadFrequency).isEqualTo(fakeConfig.coreConfig.uploadFrequency)
    }

    @Test
    fun `M initializes app info W initialize() {unknown package name}`() {
        // Given
        @Suppress("DEPRECATION")
        whenever(appContext.mockPackageManager.getPackageInfo(appContext.fakePackageName, 0))
            .doThrow(PackageManager.NameNotFoundException())
        whenever(appContext.mockInstance.getSystemService(Context.CONNECTIVITY_SERVICE))
            .doReturn(mockConnectivityMgr)

        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(testedFeature.clientToken).isEqualTo(fakeConfig.clientToken)
        assertThat(testedFeature.packageName).isEqualTo(appContext.fakePackageName)
        assertThat(testedFeature.packageVersionProvider.version)
            .isEqualTo(CoreFeature.DEFAULT_APP_VERSION)
        assertThat(testedFeature.serviceName).isEqualTo(fakeConfig.service)
        assertThat(testedFeature.envName).isEqualTo(fakeConfig.env)
        assertThat(testedFeature.variant).isEqualTo(fakeConfig.variant)
        assertThat(testedFeature.contextRef.get()).isEqualTo(appContext.mockInstance)
        assertThat(testedFeature.batchSize).isEqualTo(fakeConfig.coreConfig.batchSize)
        assertThat(testedFeature.uploadFrequency).isEqualTo(fakeConfig.coreConfig.uploadFrequency)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.TIRAMISU)
    fun `M initializes app info W initialize() {unknown package name, TIRAMISU}`() {
        // Given
        whenever(
            appContext.mockPackageManager.getPackageInfo(
                appContext.fakePackageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        )
            .doThrow(PackageManager.NameNotFoundException())
        whenever(appContext.mockInstance.getSystemService(Context.CONNECTIVITY_SERVICE))
            .doReturn(mockConnectivityMgr)

        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(testedFeature.clientToken).isEqualTo(fakeConfig.clientToken)
        assertThat(testedFeature.packageName).isEqualTo(appContext.fakePackageName)
        assertThat(testedFeature.packageVersionProvider.version).isEqualTo(
            CoreFeature.DEFAULT_APP_VERSION
        )
        assertThat(testedFeature.serviceName).isEqualTo(fakeConfig.service)
        assertThat(testedFeature.envName).isEqualTo(fakeConfig.env)
        assertThat(testedFeature.variant).isEqualTo(fakeConfig.variant)
        assertThat(testedFeature.contextRef.get()).isEqualTo(appContext.mockInstance)
        assertThat(testedFeature.batchSize).isEqualTo(fakeConfig.coreConfig.batchSize)
        assertThat(testedFeature.uploadFrequency).isEqualTo(fakeConfig.coreConfig.uploadFrequency)
    }

    @Test
    fun `M initializes build ID W initialize()`() {
        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(testedFeature.appBuildId).isEqualTo(fakeBuildId.toString())
    }

    @Test
    fun `M initializes build ID W initialize() { asset manager is closed }`() {
        // Given
        whenever(
            appContext.mockInstance.assets.open(CoreFeature.BUILD_ID_FILE_NAME)
        ) doThrow RuntimeException()

        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(testedFeature.appBuildId).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            message = CoreFeature.BUILD_ID_READ_ERROR,
            throwableClass = RuntimeException::class.java
        )
    }

    @Test
    fun `M initializes build ID W initialize() { build ID file is missing }`() {
        // Given
        whenever(
            appContext.mockInstance.assets.open(CoreFeature.BUILD_ID_FILE_NAME)
        ) doThrow FileNotFoundException()

        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(testedFeature.appBuildId).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.INFO,
            InternalLogger.Target.USER,
            CoreFeature.BUILD_ID_IS_MISSING_INFO_MESSAGE
        )
    }

    @Test
    fun `M initializes build ID W initialize() { IOException during build ID read }`() {
        // Given
        val mockBrokenStream = mock<InputStream>().apply {
            whenever(read(any())) doThrow IOException()
        }
        whenever(
            appContext.mockInstance.assets.open(CoreFeature.BUILD_ID_FILE_NAME)
        ) doReturn mockBrokenStream

        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(testedFeature.appBuildId).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            message = CoreFeature.BUILD_ID_READ_ERROR,
            throwableClass = IOException::class.java
        )
    }

    @Test
    fun `M initialize okhttp with strict network policy W initialize()`() {
        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeConfig.copy(coreConfig = fakeConfig.coreConfig.copy(needsClearTextHttp = false)),
            fakeConsent
        )

        // Then
        val okHttpClient = testedFeature.okHttpClient
        assertThat(okHttpClient.protocols)
            .containsExactly(Protocol.HTTP_2, Protocol.HTTP_1_1)
        assertThat(okHttpClient.callTimeoutMillis)
            .isEqualTo(CoreFeature.NETWORK_TIMEOUT_MS.toInt())
        assertThat(okHttpClient.connectionSpecs)
            .hasSize(1)

        val connectionSpec = okHttpClient.connectionSpecs.first()

        assertThat(connectionSpec.isTls).isTrue()
        assertThat(connectionSpec.tlsVersions)
            .containsExactly(TlsVersion.TLS_1_2, TlsVersion.TLS_1_3)
        assertThat(connectionSpec.cipherSuites).containsExactly(
            CipherSuite.TLS_AES_128_GCM_SHA256,
            CipherSuite.TLS_AES_256_GCM_SHA384,
            CipherSuite.TLS_CHACHA20_POLY1305_SHA256,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384
        )
    }

    @Test
    fun `M initialize okhttp with no network policy W initialize() {needsClearText}`() {
        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeConfig.copy(coreConfig = fakeConfig.coreConfig.copy(needsClearTextHttp = true)),
            fakeConsent
        )

        // Then
        val okHttpClient = testedFeature.okHttpClient
        assertThat(okHttpClient.protocols)
            .containsExactly(Protocol.HTTP_2, Protocol.HTTP_1_1)
        assertThat(okHttpClient.callTimeoutMillis)
            .isEqualTo(CoreFeature.NETWORK_TIMEOUT_MS.toInt())
        assertThat(okHttpClient.connectionSpecs)
            .containsExactly(ConnectionSpec.CLEARTEXT)
    }

    @Test
    fun `M initialize okhttp with proxy W initialize() {proxy configured}`() {
        // When
        val proxy: Proxy = mock()
        val proxyAuth: Authenticator = mock()
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeConfig.copy(
                coreConfig = fakeConfig.coreConfig.copy(
                    proxy = proxy,
                    proxyAuth = proxyAuth
                )
            ),
            fakeConsent
        )

        // Then
        val okHttpClient = testedFeature.okHttpClient
        assertThat(okHttpClient.proxy).isSameAs(proxy)
        assertThat(okHttpClient.proxyAuthenticator).isSameAs(proxyAuth)
    }

    @Test
    fun `M initialize okhttp without proxy W initialize() {proxy not configured}`() {
        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeConfig.copy(coreConfig = fakeConfig.coreConfig.copy(proxy = null)),
            fakeConsent
        )

        // Then
        val okHttpClient = testedFeature.okHttpClient
        assertThat(okHttpClient.proxy).isNull()
        assertThat(okHttpClient.proxyAuthenticator).isEqualTo(Authenticator.NONE)
    }

    @Test
    fun `M initialize executors W initialize()`() {
        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(testedFeature.uploadExecutorService).isNotNull()
        assertThat(testedFeature.persistenceExecutorService).isNotNull()
    }

    @Test
    fun `M initialize only once W initialize() twice`(
        @Forgery otherConfig: Configuration
    ) {
        // Given
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeConfig,
            fakeConsent
        )

        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            otherConfig,
            fakeConsent
        )

        // Then
        assertThat(testedFeature.clientToken).isEqualTo(fakeConfig.clientToken)
        assertThat(testedFeature.packageName).isEqualTo(appContext.fakePackageName)
        assertThat(testedFeature.packageVersionProvider.version)
            .isEqualTo(appContext.fakeVersionName)
        assertThat(testedFeature.serviceName).isEqualTo(fakeConfig.service)
        assertThat(testedFeature.envName).isEqualTo(fakeConfig.env)
        assertThat(testedFeature.variant).isEqualTo(fakeConfig.variant)
        assertThat(testedFeature.contextRef.get()).isEqualTo(appContext.mockInstance)
        assertThat(testedFeature.batchSize).isEqualTo(fakeConfig.coreConfig.batchSize)
        assertThat(testedFeature.uploadFrequency).isEqualTo(fakeConfig.coreConfig.uploadFrequency)
    }

    @Test
    fun `M detect current process W initialize() {main process}`(
        @StringForgery otherProcessName: String
    ) {
        // Given
        val mockActivityManager = mock<ActivityManager>()
        whenever(appContext.mockInstance.getSystemService(Context.ACTIVITY_SERVICE)).thenReturn(
            mockActivityManager
        )
        val myProcess = forgeAppProcessInfo(
            Process.myPid(),
            appContext.fakePackageName
        )
        val otherProcess = forgeAppProcessInfo(
            Process.myPid() + 1,
            otherProcessName
        )
        whenever(mockActivityManager.runningAppProcesses)
            .thenReturn(listOf(myProcess, otherProcess))

        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(testedFeature.isMainProcess).isTrue()
    }

    @Test
    fun `M detect current process W initialize() {secondary process}`(
        @StringForgery otherProcessName: String
    ) {
        // Given
        val mockActivityManager = mock<ActivityManager>()
        whenever(appContext.mockInstance.getSystemService(Context.ACTIVITY_SERVICE)).thenReturn(
            mockActivityManager
        )
        val myProcess = forgeAppProcessInfo(Process.myPid(), otherProcessName)
        val otherProcess = forgeAppProcessInfo(
            Process.myPid() + 1,
            appContext.fakePackageName
        )
        whenever(mockActivityManager.runningAppProcesses)
            .thenReturn(listOf(myProcess, otherProcess))

        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(testedFeature.isMainProcess).isFalse()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            CoreFeature.SDK_INITIALIZED_IN_SECONDARY_PROCESS_WARNING_MESSAGE
        )
    }

    @Test
    fun `M detect current process W initialize() {unknown process}`(
        @StringForgery otherProcessName: String
    ) {
        // Given
        val mockActivityManager = mock<ActivityManager>()
        whenever(appContext.mockInstance.getSystemService(Context.ACTIVITY_SERVICE)).thenReturn(
            mockActivityManager
        )
        val otherProcess = forgeAppProcessInfo(
            Process.myPid() + 1,
            otherProcessName
        )
        whenever(mockActivityManager.runningAppProcesses)
            .thenReturn(listOf(otherProcess))

        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(testedFeature.isMainProcess).isTrue()
    }

    @Test
    fun `M build config W buildFilePersistenceConfig()`() {
        // Given
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
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
            .isEqualTo(fakeConfig.coreConfig.batchSize.windowDurationMs)
    }

    @Test
    fun `M initialize the NdkCrashHandler data W initialize() {main process}`(
        @TempDir tempDir: File,
        @StringForgery otherProcessName: String
    ) {
        // Given
        val mockActivityManager = mock<ActivityManager>()
        whenever(appContext.mockInstance.getSystemService(Context.ACTIVITY_SERVICE)).thenReturn(
            mockActivityManager
        )
        val myProcess = forgeAppProcessInfo(
            Process.myPid(),
            appContext.fakePackageName
        )
        val otherProcess = forgeAppProcessInfo(
            Process.myPid() + 1,
            otherProcessName
        )
        whenever(mockActivityManager.runningAppProcesses)
            .thenReturn(listOf(myProcess, otherProcess))
        whenever(appContext.mockInstance.cacheDir) doReturn tempDir

        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
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
            }
    }

    @Test
    fun `M initialize the NdkCrashHandler data W initialize() {source type override}`(
        @TempDir tempDir: File,
        @StringForgery otherProcessName: String
    ) {
        // Given
        fakeConfig = fakeConfig.copy(
            additionalConfig = fakeConfig.additionalConfig.toMutableMap().apply {
                put(Datadog.DD_NATIVE_SOURCE_TYPE, "ndk+il2cpp")
            }
        )
        val mockActivityManager = mock<ActivityManager>()
        whenever(appContext.mockInstance.getSystemService(Context.ACTIVITY_SERVICE)).thenReturn(
            mockActivityManager
        )
        val myProcess = forgeAppProcessInfo(
            Process.myPid(),
            appContext.fakePackageName
        )
        val otherProcess = forgeAppProcessInfo(
            Process.myPid() + 1,
            otherProcessName
        )
        whenever(mockActivityManager.runningAppProcesses)
            .thenReturn(listOf(myProcess, otherProcess))
        whenever(appContext.mockInstance.cacheDir) doReturn tempDir

        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(testedFeature.ndkCrashHandler)
            .isInstanceOfSatisfying(DatadogNdkCrashHandler::class.java) {
                assertThat(it.nativeCrashSourceType).isEqualTo("ndk+il2cpp")
            }
    }

    @Test
    fun `M not initialize the NdkCrashHandler data W initialize() {not main process}`(
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
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(testedFeature.ndkCrashHandler).isInstanceOf(NoOpNdkCrashHandler::class.java)
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

    @Test
    fun `M initialise encryption W initialize`(
        @IntForgery(-128, 128) fakeByte: Int
    ) {
        // Given
        val mockEncryption = mock<Encryption>()
        whenever(mockEncryption.encrypt(any())) doAnswer { invocation ->
            (invocation.getArgument(0) as ByteArray)
                .map { it xor fakeByte.toByte() }
                .toByteArray()
        }
        fakeConfig = fakeConfig.copy(
            coreConfig = fakeConfig.coreConfig.copy(
                encryption = mockEncryption
            )
        )

        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(testedFeature.localDataEncryption).isNotNull()
            .isSameAs(mockEncryption)
    }

    @Test
    fun `M initialise persistence strategy W initialize`() {
        // Given
        val mockPersistenceStrategyFactory = mock<PersistenceStrategy.Factory>()
        fakeConfig = fakeConfig.copy(
            coreConfig = fakeConfig.coreConfig.copy(
                persistenceStrategyFactory = mockPersistenceStrategyFactory
            )
        )

        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(testedFeature.persistenceStrategyFactory).isNotNull()
            .isSameAs(mockPersistenceStrategyFactory)
    }

    // endregion

    @Test
    fun `M return last fatal ANR sent W lastFatalAnrSent`(
        @TempDir tempDir: File,
        @LongForgery(min = 0L) fakeLastFatalAnrSent: Long
    ) {
        // Given
        testedFeature.storageDir = tempDir
        File(tempDir, CoreFeature.LAST_FATAL_ANR_SENT_FILE_NAME)
            .writeText(fakeLastFatalAnrSent.toString())

        // When
        val lastFatalAnrSent = testedFeature.lastFatalAnrSent

        // Then
        assertThat(lastFatalAnrSent).isEqualTo(fakeLastFatalAnrSent)
    }

    @Test
    fun `M return null W lastFatalAnrSent { no file }`(
        @TempDir tempDir: File
    ) {
        // Given
        testedFeature.storageDir = tempDir

        // When
        val lastFatalAnrSent = testedFeature.lastFatalAnrSent

        // Then
        assertThat(lastFatalAnrSent).isNull()
    }

    @Test
    fun `M return null W lastFatalAnrSent { file contains not a number }`(
        @TempDir tempDir: File,
        @StringForgery fakeBrokenLastFatalAnrSent: String
    ) {
        // Given
        testedFeature.storageDir = tempDir
        File(tempDir, CoreFeature.LAST_FATAL_ANR_SENT_FILE_NAME)
            .writeText(fakeBrokenLastFatalAnrSent)

        // When
        val lastFatalAnrSent = testedFeature.lastFatalAnrSent

        // Then
        assertThat(lastFatalAnrSent).isNull()
    }

    @Test
    fun `M delete last fatal ANR sent W deleteLastFatalAnrSent`(
        @TempDir tempDir: File,
        @LongForgery fakeLastFatalAnrSent: Long
    ) {
        // Given
        testedFeature.storageDir = tempDir
        File(tempDir, CoreFeature.LAST_FATAL_ANR_SENT_FILE_NAME)
            .writeText(fakeLastFatalAnrSent.toString())

        // When
        testedFeature.deleteLastFatalAnrSent()

        // Then
        assertThat(File(tempDir, CoreFeature.LAST_FATAL_ANR_SENT_FILE_NAME)).doesNotExist()
    }

    @Test
    fun `M write last view event W writeLastViewEvent`(
        @TempDir tempDir: File,
        @StringForgery viewEvent: String
    ) {
        // Given
        val fakeViewEvent = viewEvent.toByteArray()

        testedFeature.storageDir = tempDir

        // When
        testedFeature.writeLastViewEvent(fakeViewEvent)

        // Then
        val lastViewEventFile = File(
            tempDir,
            CoreFeature.LAST_RUM_VIEW_EVENT_FILE_NAME
        )
        assertThat(lastViewEventFile).exists()

        val fileContent = lastViewEventFile.readBytes()
        // file will have batch file format, so beginning will contain some metadata,
        // we need to skip it for the comparison
        val payload = fileContent.takeLast(fakeViewEvent.size).toByteArray()
        assertThat(payload).isEqualTo(fakeViewEvent)
    }

    @Test
    fun `M delete last view event W deleteLastViewEvent`(
        @TempDir tempDir: File,
        @StringForgery fakeViewEvent: String
    ) {
        // Given
        testedFeature.storageDir = tempDir
        File(tempDir, CoreFeature.LAST_RUM_VIEW_EVENT_FILE_NAME)
            .writeText(fakeViewEvent)

        // When
        testedFeature.deleteLastViewEvent()

        // Then
        assertThat(File(tempDir, CoreFeature.LAST_RUM_VIEW_EVENT_FILE_NAME)).doesNotExist()
    }

    @Suppress("DEPRECATION")
    @Test
    fun `M delete last view event W deleteLastViewEvent { legacy NDK location }`(
        @TempDir tempDir: File,
        @StringForgery fakeViewEvent: String
    ) {
        // Given
        testedFeature.storageDir = tempDir
        DatadogNdkCrashHandler.getLastViewEventFile(tempDir)
            .apply {
                parentFile?.mkdirs()
            }
            .writeText(fakeViewEvent)

        // When
        testedFeature.deleteLastViewEvent()

        // Then
        assertThat(DatadogNdkCrashHandler.getLastViewEventFile(tempDir)).doesNotExist()
    }

    @Test
    fun `M return null W lastViewEvent { no last view event written }`(
        @TempDir tempDir: File
    ) {
        // Given
        testedFeature.storageDir = tempDir

        // When
        val lastViewEvent = testedFeature.lastViewEvent

        // Then
        assertThat(lastViewEvent).isNull()
    }

    @Test
    fun `M return last view event W lastViewEvent`(
        @TempDir tempDir: File,
        @Forgery fakeViewEvent: JsonObject
    ) {
        // Given
        testedFeature.storageDir = tempDir
        testedFeature.writeLastViewEvent(fakeViewEvent.toString().toByteArray())

        // When
        val lastViewEvent = testedFeature.lastViewEvent

        // Then
        assertThat(lastViewEvent.toString()).isEqualTo(fakeViewEvent.toString())
        // file must be deleted once view event is read
        assertThat(File(tempDir, CoreFeature.LAST_RUM_VIEW_EVENT_FILE_NAME)).doesNotExist()
    }

    @Test
    fun `M return last view event W lastViewEvent { check old NDK location }`(
        @TempDir tempDir: File,
        @Forgery fakeViewEvent: JsonObject
    ) {
        // Given
        testedFeature.storageDir = tempDir

        @Suppress("DEPRECATION")
        val legacyNdkViewEventFile = DatadogNdkCrashHandler.getLastViewEventFile(tempDir)
        legacyNdkViewEventFile.parentFile?.mkdirs()

        BatchFileReaderWriter
            .create(internalLogger = mock(), encryption = null)
            .writeData(
                legacyNdkViewEventFile,
                RawBatchEvent(fakeViewEvent.toString().toByteArray()),
                append = false
            )

        // When
        val lastViewEvent = testedFeature.lastViewEvent

        // Then
        assertThat(lastViewEvent.toString()).isEqualTo(fakeViewEvent.toString())
    }

    @Test
    fun `M return app start time W appStartTimeNs`(
        @LongForgery(min = 0L) fakeAppStartTimeNs: Long
    ) {
        // Given
        whenever(mockAppStartTimeProvider.appStartTimeNs) doReturn fakeAppStartTimeNs

        // When
        val appStartTimeNs = testedFeature.appStartTimeNs

        // Then
        assertThat(appStartTimeNs).isEqualTo(fakeAppStartTimeNs)
    }

    // region shutdown

    @Test
    fun `M cleanup NdkCrashHandler W stop()`() {
        // Given
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeConfig,
            fakeConsent
        )

        // When
        testedFeature.stop()

        // Then
        assertThat(testedFeature.ndkCrashHandler).isInstanceOf(NoOpNdkCrashHandler::class.java)
    }

    @Test
    fun `M cleanup app info W stop()`() {
        // Given
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
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
        assertThat(testedFeature.contextRef.get()).isNull()
    }

    @Test
    fun `M cleanup providers W stop()`() {
        // Given
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeConfig,
            fakeConsent
        )

        // When
        testedFeature.stop()

        // Then
        assertThat(testedFeature.firstPartyHostHeaderTypeResolver.knownHosts)
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
    fun `M shut down executors W stop()`() {
        // Given
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
            fakeConfig,
            fakeConsent
        )
        val mockUploadExecutorService: ScheduledThreadPoolExecutor = mock()
        testedFeature.uploadExecutorService = mockUploadExecutorService
        val mockPersistenceExecutorService: FlushableExecutorService = mock()
        testedFeature.persistenceExecutorService = mockPersistenceExecutorService

        // When
        testedFeature.stop()

        // Then
        verify(mockUploadExecutorService).shutdownNow()
        verify(mockPersistenceExecutorService).shutdownNow()
    }

    @Test
    fun `M unregister tracking consent callbacks W stop()`() {
        // Given
        testedFeature.initialize(
            appContext.mockInstance,
            fakeSdkInstanceId,
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
    fun `M clean up feature context W stop()`(
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
            fakeConfig,
            fakeConsent
        )

        val blockingQueue = LinkedBlockingQueue<Runnable>(forge.aList { mock() })
        val persistenceExecutor: FlushableExecutorService = mock()
        whenever(persistenceExecutor.drainTo(any())) doAnswer { invocation ->
            blockingQueue.forEach {
                @Suppress("UNCHECKED_CAST")
                (invocation.arguments[0] as MutableCollection<Any>).add(it)
            }
        }
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
            fakeConfig,
            fakeConsent
        )

        val mockPersistenceExecutorService: FlushableExecutorService = mock()
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
        processName: String
    ): ActivityManager.RunningAppProcessInfo {
        return ActivityManager.RunningAppProcessInfo().apply {
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
