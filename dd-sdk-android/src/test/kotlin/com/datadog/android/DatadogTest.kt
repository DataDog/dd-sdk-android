/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.util.Log as AndroidLog
import com.datadog.android.core.internal.data.Reader
import com.datadog.android.core.internal.domain.PersistenceStrategy
import com.datadog.android.core.internal.lifecycle.ProcessLifecycleMonitor
import com.datadog.android.core.internal.net.DataOkHttpUploader
import com.datadog.android.core.internal.net.DataUploader
import com.datadog.android.core.internal.net.info.BroadcastReceiverNetworkInfoProvider
import com.datadog.android.core.internal.net.info.CallbackNetworkInfoProvider
import com.datadog.android.core.internal.system.BroadcastReceiverSystemInfoProvider
import com.datadog.android.error.internal.DatadogExceptionHandler
import com.datadog.android.log.EndpointUpdateStrategy
import com.datadog.android.log.assertj.containsInstanceOf
import com.datadog.android.log.internal.domain.Log
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockContext
import com.datadog.tools.unit.annotations.SystemOutStream
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.extensions.SystemOutputExtension
import com.datadog.tools.unit.getFieldValue
import com.datadog.tools.unit.getStaticValue
import com.datadog.tools.unit.invokeMethod
import com.datadog.tools.unit.lastLine
import com.datadog.tools.unit.setStaticValue
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.isA
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.ByteArrayOutputStream
import java.io.File
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Protocol
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
internal class DatadogTest {

    lateinit var mockAppContext: Application
    @Mock
    lateinit var mockLogStrategy: PersistenceStrategy<Log>
    @Mock
    lateinit var mockConnectivityMgr: ConnectivityManager

    lateinit var fakeToken: String

    lateinit var fakePackageName: String
    lateinit var fakePackageVersion: String

    @TempDir
    lateinit var rootDir: File

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeToken = forge.anHexadecimalString()
        fakePackageName = forge.anAlphabeticalString()
        fakePackageVersion = forge.aStringMatching("\\d(\\.\\d){3}")

        mockAppContext = mockContext(fakePackageName, fakePackageVersion)
        whenever(mockAppContext.filesDir).thenReturn(rootDir)
        whenever(mockAppContext.applicationContext) doReturn mockAppContext
        whenever(mockAppContext.getSystemService(Context.CONNECTIVITY_SERVICE))
            .doReturn(mockConnectivityMgr)
    }

    @AfterEach
    fun `tear down`() {
        try {
            Datadog.invokeMethod("stop")
        } catch (e: IllegalStateException) {
            // nevermind
        }
    }

    @Test
    fun `initializes all dependencies at initialize`() {
        Datadog.initialize(mockAppContext, fakeToken)

        assertThat(Datadog.packageName).isEqualTo(fakePackageName)
        assertThat(Datadog.packageVersion).isEqualTo(fakePackageVersion)
    }

    @Test
    fun `initializes all dependencies at initialize with null version name`(
        @IntForgery(min = 0) versionCode: Int
    ) {
        mockAppContext = mockContext(fakePackageName, null, versionCode)
        whenever(mockAppContext.filesDir).thenReturn(rootDir)
        whenever(mockAppContext.applicationContext) doReturn mockAppContext
        whenever(mockAppContext.getSystemService(Context.CONNECTIVITY_SERVICE))
            .doReturn(mockConnectivityMgr)

        Datadog.initialize(mockAppContext, fakeToken)

        assertThat(Datadog.packageName).isEqualTo(fakePackageName)
        assertThat(Datadog.packageVersion).isEqualTo(versionCode.toString())
    }

    @Test
    fun `initializes crash reporter`() {
        Datadog.initialize(mockAppContext, fakeToken)

        val handler = Thread.getDefaultUncaughtExceptionHandler()

        assertThat(handler)
            .isInstanceOf(DatadogExceptionHandler::class.java)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun `registers broadcast receivers on initialize (Lollipop)`() {
        Datadog.initialize(mockAppContext, fakeToken)

        val broadcastReceiverCaptor = argumentCaptor<BroadcastReceiver>()
        verify(mockAppContext, times(3)).registerReceiver(broadcastReceiverCaptor.capture(), any())

        assertThat(broadcastReceiverCaptor.allValues)
            .containsInstanceOf(BroadcastReceiverNetworkInfoProvider::class.java)
            .containsInstanceOf(BroadcastReceiverSystemInfoProvider::class.java)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.N)
    fun `registers receivers and callbacks on initialize (Nougat)`() {
        Datadog.initialize(mockAppContext, fakeToken)

        val broadcastReceiverCaptor = argumentCaptor<BroadcastReceiver>()
        verify(mockAppContext, times(2)).registerReceiver(broadcastReceiverCaptor.capture(), any())
        assertThat(broadcastReceiverCaptor.allValues)
            .allMatch { it is BroadcastReceiverSystemInfoProvider }

        verify(mockConnectivityMgr)
            .registerDefaultNetworkCallback(isA<CallbackNetworkInfoProvider>())
    }

    @Test
    fun `ignores if initialize called more than once`(
        @SystemOutStream outputStream: ByteArrayOutputStream
    ) {
        Datadog.initialize(mockAppContext, fakeToken)
        Datadog.setVerbosity(AndroidLog.VERBOSE)
        val strategy = Datadog.getLogStrategy()
        val networkInfoProvider = Datadog.getNetworkInfoProvider()
        val userInfoProvider = Datadog.getUserInfoProvider()
        val timeProvider = Datadog.getTimeProvider()

        Datadog.initialize(mockAppContext, fakeToken)
        assertThat(strategy).isSameAs(Datadog.getLogStrategy())
        assertThat(networkInfoProvider).isSameAs(Datadog.getNetworkInfoProvider())
        assertThat(userInfoProvider).isSameAs(Datadog.getUserInfoProvider())
        assertThat(timeProvider).isSameAs(Datadog.getTimeProvider())
        assertThat(outputStream.lastLine())
            .isEqualTo("W/Datadog: The Datadog library has already been initialized.")
    }

    @Test
    fun `fails if stop called without initialize`() {
        assertThrows<IllegalStateException> {
            Datadog.invokeMethod("stop")
        }
    }

    @Test
    fun `does nothing on setEndpointUrl with Discard strategy`(
        forge: Forge,
        @SystemOutStream outputStream: ByteArrayOutputStream
    ) {
        val mockReader: Reader = mock()
        val mockUploader: DataUploader = mock()
        whenever(mockLogStrategy.getReader()) doReturn mockReader
        val newEndpoint = forge.aStringMatching("https://[a-z]+\\.[a-z]{3}")
        Datadog.initialize(mockAppContext, fakeToken)
        Datadog.javaClass.setStaticValue("logStrategy", mockLogStrategy)
        Datadog.javaClass.setStaticValue("uploader", mockUploader)
        Datadog.setVerbosity(AndroidLog.VERBOSE)

        Datadog.setEndpointUrl(newEndpoint, EndpointUpdateStrategy.DISCARD_OLD_LOGS)

        verifyZeroInteractions(mockUploader, mockReader)
        assertThat(outputStream.lastLine())
            .isEqualTo(
                "W/Datadog: setEndpointUrl() has been deprecated. " +
                        "If you need it, submit an issue at " +
                        "https://github.com/DataDog/dd-sdk-android/issues/"
            )
    }

    @Test
    fun `does nothing on setEndpointUrl with Update strategy`(
        forge: Forge,
        @SystemOutStream outputStream: ByteArrayOutputStream
    ) {
        val mockReader: Reader = mock()
        val mockUploader: DataUploader = mock()
        whenever(mockLogStrategy.getReader()) doReturn mockReader
        val newEndpoint = forge.aStringMatching("https://[a-z]+\\.[a-z]{3}")
        Datadog.initialize(mockAppContext, fakeToken)
        Datadog.javaClass.setStaticValue("logStrategy", mockLogStrategy)
        Datadog.javaClass.setStaticValue("uploader", mockUploader)
        Datadog.setVerbosity(AndroidLog.VERBOSE)

        Datadog.setEndpointUrl(newEndpoint, EndpointUpdateStrategy.SEND_OLD_LOGS_TO_NEW_ENDPOINT)

        verifyZeroInteractions(mockUploader, mockReader)
        assertThat(outputStream.lastLine())
            .isEqualTo(
                "W/Datadog: setEndpointUrl() has been deprecated. " +
                        "If you need it, submit an issue at " +
                        "https://github.com/DataDog/dd-sdk-android/issues/"
            )
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun `add strict network policy for https endpoints on 21+`(forge: Forge) {
        val endpoint = forge.aStringMatching("https://[a-z]+\\.[a-z]{3}")

        Datadog.initialize(mockAppContext, fakeToken, endpoint)

        val uploader: DataOkHttpUploader = Datadog.javaClass.getStaticValue("uploader")
        val okHttpClient: OkHttpClient = uploader.getFieldValue("client")

        assertThat(okHttpClient.protocols())
            .containsExactly(Protocol.HTTP_2, Protocol.HTTP_1_1)
        assertThat(okHttpClient.callTimeoutMillis())
            .isEqualTo(Datadog.NETWORK_TIMEOUT_MS.toInt())
        assertThat(okHttpClient.connectionSpecs())
            .containsExactly(ConnectionSpec.RESTRICTED_TLS)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.KITKAT)
    fun `add compatibility network policy for https endpoints on 19+`(forge: Forge) {
        val endpoint = forge.aStringMatching("https://[a-z]+\\.[a-z]{3}")

        Datadog.initialize(mockAppContext, fakeToken, endpoint)

        val uploader: DataOkHttpUploader = Datadog.javaClass.getStaticValue("uploader")
        val okHttpClient: OkHttpClient = uploader.getFieldValue("client")

        assertThat(okHttpClient.protocols())
            .containsExactly(Protocol.HTTP_2, Protocol.HTTP_1_1)
        assertThat(okHttpClient.callTimeoutMillis())
            .isEqualTo(Datadog.NETWORK_TIMEOUT_MS.toInt())
        assertThat(okHttpClient.connectionSpecs())
            .containsExactly(ConnectionSpec.MODERN_TLS)
    }

    @Test
    fun `no network policy for http endpoints`(forge: Forge) {
        val endpoint = forge.aStringMatching("http://[a-z]+\\.[a-z]{3}")

        Datadog.initialize(mockAppContext, fakeToken, endpoint)

        val uploader: DataOkHttpUploader = Datadog.javaClass.getStaticValue("uploader")
        val okHttpClient: OkHttpClient = uploader.getFieldValue("client")

        assertThat(okHttpClient.protocols())
            .containsExactly(Protocol.HTTP_2, Protocol.HTTP_1_1)
        assertThat(okHttpClient.callTimeoutMillis())
            .isEqualTo(Datadog.NETWORK_TIMEOUT_MS.toInt())
        assertThat(okHttpClient.connectionSpecs())
            .containsExactly(ConnectionSpec.CLEARTEXT)
    }

    @Test
    fun `update user infos`(forge: Forge) {
        val id = forge.anHexadecimalString()
        val name = forge.anAlphabeticalString()
        val email = forge.aStringMatching("\\w+@\\w+")

        Datadog.setUserInfo(id, name, email)
        val userInfo = Datadog.getUserInfoProvider().getUserInfo()

        assertThat(userInfo.id).isEqualTo(id)
        assertThat(userInfo.name).isEqualTo(name)
        assertThat(userInfo.email).isEqualTo(email)
    }

    @Test
    fun `it will initialize the LifecycleMonitor`() {
        // when
        val application = mockAppContext
        Datadog.initialize(mockAppContext, fakeToken)

        // then
        val argumentCaptor = argumentCaptor<Application.ActivityLifecycleCallbacks>()
        verify(application).registerActivityLifecycleCallbacks(argumentCaptor.capture())
        assertThat(argumentCaptor.firstValue).isInstanceOf(ProcessLifecycleMonitor::class.java)
    }

    @Test
    fun `it will not initialize the LifecycleMonitor if provided context is not App Context`() {
        val mockContext = mockContext<Context>()
        Datadog.initialize(mockContext, fakeToken)
    }

    @Test
    fun `is initialized will return true if SDK was initialized`() {
        // when
        Datadog.initialize(mockAppContext, fakeToken)

        // then
        assertThat(Datadog.isInitialized()).isTrue()
    }

    @Test
    fun `is initialized will return false if SDK was initialized`() {
        // then
        assertThat(Datadog.isInitialized()).isFalse()
    }

    @Test
    fun `will update the isDebug flag if application is debuggable`() {
        // when
        Datadog.initialize(mockAppContext, fakeToken)

        // then
        if (BuildConfig.DEBUG) {
            assertThat(Datadog.isDebug).isTrue()
        } else {
            assertThat(Datadog.isDebug).isFalse()
        }
    }
}
