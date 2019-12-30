/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android

import android.content.BroadcastReceiver
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import com.datadog.android.core.internal.data.Reader
import com.datadog.android.log.EndpointUpdateStrategy
import com.datadog.android.log.assertj.containsInstanceOf
import com.datadog.android.log.forge.Configurator
import com.datadog.android.log.internal.LogStrategy
import com.datadog.android.log.internal.net.BroadcastReceiverNetworkInfoProvider
import com.datadog.android.log.internal.net.CallbackNetworkInfoProvider
import com.datadog.android.log.internal.net.LogOkHttpUploader
import com.datadog.android.log.internal.net.LogUploader
import com.datadog.android.log.internal.system.BroadcastReceiverSystemInfoProvider
import com.datadog.android.utils.mockContext
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.getFieldValue
import com.datadog.tools.unit.getStaticValue
import com.datadog.tools.unit.invokeMethod
import com.datadog.tools.unit.setStaticValue
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.isA
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
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
    ExtendWith(ApiLevelExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogTest {

    lateinit var mockContext: Context
    @Mock
    lateinit var mockLogStrategy: LogStrategy
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

        mockContext = mockContext(fakePackageName, fakePackageVersion)
        whenever(mockContext.filesDir).thenReturn(rootDir)
        whenever(mockContext.applicationContext) doReturn mockContext
        whenever(mockContext.getSystemService(Context.CONNECTIVITY_SERVICE))
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
        Datadog.initialize(mockContext, fakeToken)

        assertThat(Datadog.packageName).isEqualTo(fakePackageName)
        assertThat(Datadog.packageVersion).isEqualTo(fakePackageVersion)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun `registers broadcast receivers on initialize (Lollipop)`() {
        Datadog.initialize(mockContext, fakeToken)

        val broadcastReceiverCaptor = argumentCaptor<BroadcastReceiver>()
        verify(mockContext, times(3)).registerReceiver(broadcastReceiverCaptor.capture(), any())

        assertThat(broadcastReceiverCaptor.allValues)
            .containsInstanceOf(BroadcastReceiverNetworkInfoProvider::class.java)
            .containsInstanceOf(BroadcastReceiverSystemInfoProvider::class.java)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.N)
    fun `registers receivers and callbacks on initialize (Nougat)`() {
        Datadog.initialize(mockContext, fakeToken)

        val broadcastReceiverCaptor = argumentCaptor<BroadcastReceiver>()
        verify(mockContext, times(2)).registerReceiver(broadcastReceiverCaptor.capture(), any())
        assertThat(broadcastReceiverCaptor.allValues)
            .allMatch { it is BroadcastReceiverSystemInfoProvider }

        verify(mockConnectivityMgr)
            .registerDefaultNetworkCallback(isA<CallbackNetworkInfoProvider>())
    }

    @Test
    fun `fails if initialize called twice`() {
        Datadog.initialize(mockContext, fakeToken)

        assertThrows<IllegalStateException> {
            Datadog.initialize(mockContext, fakeToken)
        }
    }

    @Test
    fun `fails if stop called without initialize`() {
        assertThrows<IllegalStateException> {
            Datadog.invokeMethod("stop")
        }
    }

    @Test
    fun `drop logs on setEndpointUrl with Discard strategy`(forge: Forge) {
        val mockReader: Reader = mock()
        val mockUploader: LogUploader = mock()
        whenever(mockLogStrategy.getLogReader()) doReturn mockReader
        val newEndpoint = forge.aStringMatching("https://[a-z]+\\.[a-z]{3}")
        Datadog.initialize(mockContext, fakeToken)
        Datadog.javaClass.setStaticValue("logStrategy", mockLogStrategy)
        Datadog.javaClass.setStaticValue("uploader", mockUploader)

        Datadog.setEndpointUrl(newEndpoint, EndpointUpdateStrategy.DISCARD_OLD_LOGS)

        verify(mockReader).dropAllBatches()
        verify(mockUploader).setEndpoint(newEndpoint)
    }

    @Test
    fun `keep logs on setEndpointUrl with Update strategy`(forge: Forge) {
        val mockReader: Reader = mock()
        val mockUploader: LogUploader = mock()
        whenever(mockLogStrategy.getLogReader()) doReturn mockReader
        val newEndpoint = forge.aStringMatching("https://[a-z]+\\.[a-z]{3}")
        Datadog.initialize(mockContext, fakeToken)
        Datadog.javaClass.setStaticValue("logStrategy", mockLogStrategy)
        Datadog.javaClass.setStaticValue("uploader", mockUploader)

        Datadog.setEndpointUrl(newEndpoint, EndpointUpdateStrategy.SEND_OLD_LOGS_TO_NEW_ENDPOINT)

        verify(mockReader, never()).dropAllBatches()
        verify(mockUploader).setEndpoint(newEndpoint)
    }

    @Test
    fun `add strict network policy for https endpoints`(forge: Forge) {
        val endpoint = forge.aStringMatching("https://[a-z]+\\.[a-z]{3}")

        Datadog.initialize(mockContext, fakeToken, endpoint)

        val uploader: LogOkHttpUploader = Datadog.javaClass.getStaticValue("uploader")
        val okHttpClient: OkHttpClient = uploader.getFieldValue("client")

        assertThat(okHttpClient.protocols)
            .containsExactly(Protocol.HTTP_2, Protocol.HTTP_1_1)
        assertThat(okHttpClient.callTimeoutMillis)
            .isEqualTo(Datadog.NETWORK_TIMEOUT_MS.toInt())
        assertThat(okHttpClient.connectionSpecs)
            .containsExactly(ConnectionSpec.RESTRICTED_TLS)
    }

    @Test
    fun `no network policy for http endpoints`(forge: Forge) {
        val endpoint = forge.aStringMatching("http://[a-z]+\\.[a-z]{3}")

        Datadog.initialize(mockContext, fakeToken, endpoint)

        val uploader: LogOkHttpUploader = Datadog.javaClass.getStaticValue("uploader")
        val okHttpClient: OkHttpClient = uploader.getFieldValue("client")

        assertThat(okHttpClient.protocols)
            .containsExactly(Protocol.HTTP_2, Protocol.HTTP_1_1)
        assertThat(okHttpClient.callTimeoutMillis)
            .isEqualTo(Datadog.NETWORK_TIMEOUT_MS.toInt())
        assertThat(okHttpClient.connectionSpecs)
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
}
