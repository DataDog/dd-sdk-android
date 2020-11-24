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
import com.datadog.android.DatadogConfig
import com.datadog.android.core.internal.net.info.BroadcastReceiverNetworkInfoProvider
import com.datadog.android.core.internal.net.info.CallbackNetworkInfoProvider
import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.core.internal.privacy.NoOpConsentProvider
import com.datadog.android.core.internal.system.BroadcastReceiverSystemInfoProvider
import com.datadog.android.core.internal.time.NoOpTimeProvider
import com.datadog.android.log.internal.user.NoOpMutableUserInfoProvider
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockContext
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.assertj.containsInstanceOf
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.isA
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.Locale
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadPoolExecutor
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
    ExtendWith(ApiLevelExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class CoreFeatureTest {

    lateinit var mockAppContext: Application

    @Mock
    lateinit var mockConnectivityMgr: ConnectivityManager
    lateinit var fakePackageName: String
    lateinit var fakePackageVersion: String
    lateinit var fakeConsent: TrackingConsent

    @StringForgery(regex = "[a-zA-Z0-9_:./-]{0,195}[a-zA-Z0-9_./-]")
    lateinit var fakeEnvName: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeConsent = forge.aValueFrom(TrackingConsent::class.java)
        fakePackageName = forge.anAlphabeticalString()
        fakePackageVersion = forge.aStringMatching("\\d(\\.\\d){3}")

        mockAppContext = mockContext(fakePackageName, fakePackageVersion)
        whenever(mockAppContext.applicationContext) doReturn mockAppContext
        whenever(mockAppContext.getSystemService(Context.CONNECTIVITY_SERVICE))
            .doReturn(mockConnectivityMgr)
    }

    @AfterEach
    fun `tear down`() {
        CoreFeature.stop()
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun `registers broadcast receivers on initialize (Lollipop)`() {
        CoreFeature.initialize(mockAppContext, fakeConsent, DatadogConfig.CoreConfig())

        val broadcastReceiverCaptor = argumentCaptor<BroadcastReceiver>()
        verify(mockAppContext, atLeastOnce())
            .registerReceiver(broadcastReceiverCaptor.capture(), any())

        assertThat(broadcastReceiverCaptor.allValues)
            .containsInstanceOf(BroadcastReceiverNetworkInfoProvider::class.java)
            .containsInstanceOf(BroadcastReceiverSystemInfoProvider::class.java)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.N)
    fun `registers receivers and callbacks on initialize (Nougat)`() {
        CoreFeature.initialize(mockAppContext, fakeConsent, DatadogConfig.CoreConfig())

        val broadcastReceiverCaptor = argumentCaptor<BroadcastReceiver>()
        verify(mockAppContext, atLeastOnce())
            .registerReceiver(broadcastReceiverCaptor.capture(), any())
        assertThat(broadcastReceiverCaptor.allValues)
            .allMatch { it is BroadcastReceiverSystemInfoProvider }
        verify(mockConnectivityMgr)
            .registerDefaultNetworkCallback(isA<CallbackNetworkInfoProvider>())
    }

    @Test
    fun `initializes time provider`() {
        CoreFeature.initialize(mockAppContext, fakeConsent, DatadogConfig.CoreConfig())

        assertThat(CoreFeature.timeProvider)
            .isNotInstanceOf(NoOpTimeProvider::class.java)
    }

    @Test
    fun `initializes user info provider`() {
        CoreFeature.initialize(mockAppContext, fakeConsent, DatadogConfig.CoreConfig())

        assertThat(CoreFeature.userInfoProvider)
            .isNotInstanceOf(NoOpMutableUserInfoProvider::class.java)
    }

    @Test
    fun `initializes app info`() {
        CoreFeature.initialize(mockAppContext, fakeConsent, DatadogConfig.CoreConfig())

        assertThat(CoreFeature.packageName).isEqualTo(fakePackageName)
        assertThat(CoreFeature.packageVersion).isEqualTo(fakePackageVersion)
    }

    @Test
    fun `initializes first party hosts detector`(
        @StringForgery(regex = "([a-zA-Z0-9]{3,9}\\.){1,4}[a-z]{3}") hosts: List<String>
    ) {
        CoreFeature.initialize(mockAppContext, fakeConsent, DatadogConfig.CoreConfig(hosts = hosts))

        val lowercaseHosts = hosts.map { it.toLowerCase(Locale.US) }
        assertThat(CoreFeature.firstPartyHostDetector.knownHosts).containsAll(lowercaseHosts)
    }

    @Test
    fun `initializes all dependencies at initialize with null version name`(
        @IntForgery(min = 0) versionCode: Int
    ) {
        mockAppContext = mockContext(fakePackageName, null, versionCode)
        whenever(mockAppContext.applicationContext) doReturn mockAppContext
        whenever(mockAppContext.getSystemService(Context.CONNECTIVITY_SERVICE))
            .doReturn(mockConnectivityMgr)

        CoreFeature.initialize(mockAppContext, fakeConsent, DatadogConfig.CoreConfig())

        assertThat(CoreFeature.packageName).isEqualTo(fakePackageName)
        assertThat(CoreFeature.packageVersion).isEqualTo(versionCode.toString())
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun `add strict network policy for https endpoints on 21+`(forge: Forge) {
        CoreFeature.initialize(mockAppContext, fakeConsent, DatadogConfig.CoreConfig())

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
    fun `add compatibility network policy for https endpoints on 19+`(forge: Forge) {
        CoreFeature.initialize(mockAppContext, fakeConsent, DatadogConfig.CoreConfig())

        val okHttpClient = CoreFeature.okHttpClient
        assertThat(okHttpClient.protocols())
            .containsExactly(Protocol.HTTP_2, Protocol.HTTP_1_1)
        assertThat(okHttpClient.callTimeoutMillis())
            .isEqualTo(CoreFeature.NETWORK_TIMEOUT_MS.toInt())
        assertThat(okHttpClient.connectionSpecs())
            .containsExactly(ConnectionSpec.MODERN_TLS)
    }

    @Test
    fun `no network policy for custom endpoints`(forge: Forge) {
        CoreFeature.initialize(
            mockAppContext,
            fakeConsent,
            DatadogConfig.CoreConfig(needsClearTextHttp = true)
        )

        val okHttpClient = CoreFeature.okHttpClient
        assertThat(okHttpClient.protocols())
            .containsExactly(Protocol.HTTP_2, Protocol.HTTP_1_1)
        assertThat(okHttpClient.callTimeoutMillis())
            .isEqualTo(CoreFeature.NETWORK_TIMEOUT_MS.toInt())
        assertThat(okHttpClient.connectionSpecs())
            .containsExactly(ConnectionSpec.CLEARTEXT)
    }

    @Test
    fun `stop will shutdown the executors`() {
        // Given
        CoreFeature.initialize(
            mockAppContext,
            fakeConsent,
            DatadogConfig.CoreConfig(needsClearTextHttp = true)
        )
        val mockThreadPoolExecutor: ThreadPoolExecutor = mock()
        CoreFeature.dataPersistenceExecutorService = mockThreadPoolExecutor
        val mockScheduledThreadPoolExecutor: ScheduledThreadPoolExecutor = mock()
        CoreFeature.dataUploadScheduledExecutor = mockScheduledThreadPoolExecutor

        // When
        CoreFeature.stop()

        // Then
        verify(mockThreadPoolExecutor).shutdownNow()
        verify(mockScheduledThreadPoolExecutor).shutdownNow()
    }

    @Test
    fun `if custom service name not provided will use the package name`() {
        // Given
        CoreFeature.initialize(
            mockAppContext,
            fakeConsent,
            DatadogConfig.CoreConfig(serviceName = null)
        )

        // Then
        assertThat(CoreFeature.serviceName).isEqualTo(mockAppContext.packageName)
    }

    @Test
    fun `if custom service name provided will use this instead of the package name`(forge: Forge) {
        // Given
        val serviceName = forge.anAlphabeticalString()
        CoreFeature.initialize(
            mockAppContext,
            fakeConsent,
            DatadogConfig.CoreConfig(serviceName = serviceName)
        )

        // Then
        assertThat(CoreFeature.serviceName).isEqualTo(serviceName)
    }

    @Test
    fun `if this process name matches the package name it will be marked as main process`(
        forge: Forge
    ) {
        // Given
        val mockActivityManager = mock<ActivityManager>()
        whenever(mockAppContext.getSystemService(Context.ACTIVITY_SERVICE)).thenReturn(
            mockActivityManager
        )
        val myProcess = forgeAppProcessInfo(
            Process.myPid(),
            fakePackageName
        )
        val otherProcess = forgeAppProcessInfo(
            Process.myPid() + 1,
            forge.anAlphabeticalString()
        )
        otherProcess.processName = forge.anAlphabeticalString()
        otherProcess.pid = Process.myPid() + 1
        whenever(mockActivityManager.runningAppProcesses)
            .thenReturn(
                listOf(myProcess, otherProcess)
            )

        // When
        CoreFeature.initialize(mockAppContext, fakeConsent, DatadogConfig.CoreConfig())

        // Then
        assertThat(CoreFeature.isMainProcess).isTrue()
    }

    @Test
    fun `if this process does not match the package name it will be marked as secondary process`(
        forge: Forge
    ) {
        // Given
        val mockActivityManager = mock<ActivityManager>()
        whenever(mockAppContext.getSystemService(Context.ACTIVITY_SERVICE)).thenReturn(
            mockActivityManager
        )
        val myProcess = forgeAppProcessInfo(
            Process.myPid(),
            fakePackageName + forge.anAlphabeticalString(size = 1)
        )
        val otherProcess = forgeAppProcessInfo(
            Process.myPid() + 1,
            forge.anAlphabeticalString()
        )
        otherProcess.processName = forge.anAlphabeticalString()
        otherProcess.pid = Process.myPid() + 1
        whenever(mockActivityManager.runningAppProcesses)
            .thenReturn(
                listOf(myProcess, otherProcess)
            )

        // When
        CoreFeature.initialize(mockAppContext, fakeConsent, DatadogConfig.CoreConfig())

        // Then
        assertThat(CoreFeature.isMainProcess).isFalse()
    }

    @Test
    fun `will mark it as main process by default if could not be found in the list`(forge: Forge) {
        // Given
        val mockActivityManager = mock<ActivityManager>()
        whenever(mockAppContext.getSystemService(Context.ACTIVITY_SERVICE)).thenReturn(
            mockActivityManager
        )
        val otherProcess = forgeAppProcessInfo(
            Process.myPid() + 1,
            forge.anAlphabeticalString()
        )
        otherProcess.processName = forge.anAlphabeticalString()
        otherProcess.pid = Process.myPid() + 1
        whenever(mockActivityManager.runningAppProcesses)
            .thenReturn(
                listOf(otherProcess)
            )

        // When
        CoreFeature.initialize(mockAppContext, fakeConsent, DatadogConfig.CoreConfig())

        // Then
        assertThat(CoreFeature.isMainProcess).isTrue()
    }

    @Test
    fun `M initialise the env name W provided from Config`() {
        // WHEN
        CoreFeature.initialize(
            mockAppContext,
            fakeConsent,
            DatadogConfig.CoreConfig(envName = fakeEnvName)
        )

        // THEN
        assertThat(CoreFeature.envName).isEqualTo(fakeEnvName)
    }

    @Test
    fun `M initialise the ConsentProvider`() {
        // WHEN
        CoreFeature.initialize(
            mockAppContext,
            fakeConsent,
            DatadogConfig.CoreConfig(envName = fakeEnvName)
        )

        // THEN
        assertThat(CoreFeature.trackingConsentProvider.getConsent()).isEqualTo(fakeConsent)
    }

    @Test
    fun `M use a NoOpConsentProvider by default`() {
        assertThat(CoreFeature.trackingConsentProvider)
            .isInstanceOf(NoOpConsentProvider::class.java)
    }

    @Test
    fun `M unregister an use a NoOpConsentProvider W stopped`() {
        // GIVEN
        val mockedConsentProvider: ConsentProvider = mock()
        CoreFeature.initialize(
            mockAppContext,
            fakeConsent,
            DatadogConfig.CoreConfig(envName = fakeEnvName)
        )
        CoreFeature.trackingConsentProvider = mockedConsentProvider

        // WHEN
        CoreFeature.stop()

        // THEN
        verify(mockedConsentProvider).unregisterAllCallbacks()
        assertThat(CoreFeature.trackingConsentProvider).isInstanceOf(
            NoOpConsentProvider::class.java
        )
    }

    // region internal

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
}
