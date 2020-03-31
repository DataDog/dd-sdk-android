/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.util.Log as AndroidLog
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.lifecycle.ProcessLifecycleMonitor
import com.datadog.android.log.EndpointUpdateStrategy
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.log.internal.user.MutableUserInfoProvider
import com.datadog.android.log.internal.user.UserInfo
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockContext
import com.datadog.android.utils.mockDevLogHandler
import com.datadog.tools.unit.assertj.ByteArrayOutputStreamAssert.Companion.assertThat
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.invokeMethod
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.File
import java.util.Locale
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
internal class DatadogTest {

    lateinit var mockAppContext: Application

    @Mock
    lateinit var mockConnectivityMgr: ConnectivityManager
    lateinit var mockDevLogHandler: LogHandler

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

        mockDevLogHandler = mockDevLogHandler()
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
    fun `ignores if initialize called more than once`() {
        Datadog.initialize(mockAppContext, fakeToken)
        Datadog.setVerbosity(AndroidLog.VERBOSE)

        Datadog.initialize(mockAppContext, fakeToken)

        verify(mockDevLogHandler)
            .handleLog(
                AndroidLog.WARN,
                Datadog.MESSAGE_ALREADY_INITIALIZED
            )
    }

    @Test
    fun `stop called without initialize will do nothing`() {
        Datadog.invokeMethod("stop")
        verifyZeroInteractions(mockAppContext)
    }

    @Test
    fun `does nothing on setEndpointUrl with Discard strategy`(
        forge: Forge
    ) {
        val newEndpoint = forge.aStringMatching("https://[a-z]+\\.[a-z]{3}")
        Datadog.initialize(mockAppContext, fakeToken)
        Datadog.setVerbosity(AndroidLog.VERBOSE)

        Datadog.setEndpointUrl(newEndpoint, EndpointUpdateStrategy.DISCARD_OLD_DATA)

        verify(mockDevLogHandler)
            .handleLog(
                AndroidLog.WARN,
                String.format(Locale.US, Datadog.MESSAGE_DEPRECATED, "setEndpointUrl()")
            )
    }

    @Test
    fun `does nothing on setEndpointUrl with Update strategy`(
        forge: Forge
    ) {
        val newEndpoint = forge.aStringMatching("https://[a-z]+\\.[a-z]{3}")
        Datadog.initialize(mockAppContext, fakeToken)
        Datadog.setVerbosity(AndroidLog.VERBOSE)

        Datadog.setEndpointUrl(newEndpoint, EndpointUpdateStrategy.SEND_OLD_DATA_TO_NEW_ENDPOINT)

        verify(mockDevLogHandler)
            .handleLog(
                AndroidLog.WARN,
                String.format(Locale.US, Datadog.MESSAGE_DEPRECATED, "setEndpointUrl()")
            )
    }

    @Test
    fun `update user infos`(forge: Forge) {
        val id = forge.anHexadecimalString()
        val name = forge.anAlphabeticalString()
        val email = forge.aStringMatching("\\w+@\\w+")
        val mockUserInfoProvider = mock<MutableUserInfoProvider>()
        CoreFeature.userInfoProvider = mockUserInfoProvider

        Datadog.setUserInfo(id, name, email)

        verify(mockUserInfoProvider).setUserInfo(UserInfo(id, name, email))
    }

    @Test
    fun `it will initialize the LifecycleMonitor`() {
        // when
        val application = mockAppContext
        Datadog.initialize(mockAppContext, fakeToken)

        // then
        argumentCaptor<Application.ActivityLifecycleCallbacks> {
            verify(application).registerActivityLifecycleCallbacks(capture())
            assertThat(firstValue).isInstanceOf(ProcessLifecycleMonitor::class.java)
        }
    }

    @Test
    fun `it will not initialize the LifecycleMonitor if provided context is not App Context`() {
        val mockContext = mockContext<Context>()
        Datadog.initialize(mockContext, fakeToken)
    }
}
