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
import com.datadog.android.error.internal.CrashReportsFeature
import com.datadog.android.log.EndpointUpdateStrategy
import com.datadog.android.log.LogAttributes
import com.datadog.android.log.internal.LogsFeature
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.log.internal.user.MutableUserInfoProvider
import com.datadog.android.log.internal.user.UserInfo
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.tracing.internal.TracesFeature
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockContext
import com.datadog.android.utils.mockDevLogHandler
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.invokeMethod
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.RegexForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.File
import java.util.Locale
import java.util.UUID
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

    lateinit var mockDevLogHandler: LogHandler

    @Mock
    lateinit var mockConnectivityMgr: ConnectivityManager

    @StringForgery(StringForgeryType.HEXADECIMAL)
    lateinit var fakeToken: String

    @StringForgery(StringForgeryType.ALPHABETICAL)
    lateinit var fakePackageName: String

    @RegexForgery("\\d(\\.\\d){3}")
    lateinit var fakePackageVersion: String

    @StringForgery(StringForgeryType.ALPHABETICAL)
    lateinit var fakeEnvName: String

    @TempDir
    lateinit var tempRootDir: File

    @BeforeEach
    fun `set up`() {
        mockDevLogHandler = mockDevLogHandler()
        mockAppContext = mockContext(fakePackageName, fakePackageVersion)
        whenever(mockAppContext.filesDir).thenReturn(tempRootDir)
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
    fun `𝕄 do nothing 𝕎 stop() without initialize`() {
        // When
        Datadog.invokeMethod("stop")

        // Then
        verifyZeroInteractions(mockAppContext)
    }

    @Test
    fun `𝕄 update userInfoProvider 𝕎 setUserInfo()`(
        @StringForgery(StringForgeryType.HEXADECIMAL) id: String,
        @StringForgery(StringForgeryType.ALPHABETICAL) name: String,
        @RegexForgery("\\w+@\\w+") email: String
    ) {
        // Given
        val mockUserInfoProvider = mock<MutableUserInfoProvider>()
        CoreFeature.userInfoProvider = mockUserInfoProvider

        // When
        Datadog.setUserInfo(id, name, email)

        // Then
        verify(mockUserInfoProvider).setUserInfo(UserInfo(id, name, email))
    }

    @Test
    fun `𝕄 clears userInfoProvider 𝕎 setUserInfo() with defaults`(
        @StringForgery(StringForgeryType.HEXADECIMAL) id: String,
        @StringForgery(StringForgeryType.ALPHABETICAL) name: String,
        @RegexForgery("\\w+@\\w+") email: String
    ) {
        // Given
        val mockUserInfoProvider = mock<MutableUserInfoProvider>()
        CoreFeature.userInfoProvider = mockUserInfoProvider

        // When
        Datadog.setUserInfo(id, name, email)
        Datadog.setUserInfo()

        // Then
        verify(mockUserInfoProvider).setUserInfo(UserInfo(id, name, email))
        verify(mockUserInfoProvider).setUserInfo(UserInfo(null, null, null))
    }

    @Test
    fun `𝕄 return true 𝕎 initialize() + isInitialized()`(
        @Forgery applicationId: UUID
    ) {
        // Given
        val config = DatadogConfig.Builder(fakeToken, fakeEnvName, applicationId)
            .build()

        // When
        Datadog.initialize(mockAppContext, config)
        val initialized = Datadog.isInitialized()

        // Then
        assertThat(initialized).isTrue()
    }

    @Test
    fun `𝕄 return false 𝕎 isInitialized()`(
        @Forgery applicationId: UUID
    ) {
        // When
        val initialized = Datadog.isInitialized()

        // Then
        assertThat(initialized).isFalse()
    }

    @Test
    fun `𝕄 initialize features 𝕎 initialize()`(
        @Forgery applicationId: UUID
    ) {
        // Given
        val config = DatadogConfig.Builder(fakeToken, fakeEnvName, applicationId)
            .build()

        // When
        Datadog.initialize(mockAppContext, config)

        // Then
        assertThat(CoreFeature.initialized.get()).isTrue()
        assertThat(LogsFeature.initialized.get()).isTrue()
        assertThat(CrashReportsFeature.initialized.get()).isTrue()
        assertThat(TracesFeature.initialized.get()).isTrue()
        assertThat(RumFeature.initialized.get()).isTrue()
    }

    @Test
    fun `𝕄 not initialize features 𝕎 initialize() with features disabled`(
        @Forgery applicationId: UUID,
        @BoolForgery logsEnabled: Boolean,
        @BoolForgery crashReportEnabled: Boolean,
        @BoolForgery tracesEnabled: Boolean,
        @BoolForgery rumEnabled: Boolean
    ) {
        // Given
        val config = DatadogConfig.Builder(fakeToken, fakeEnvName, applicationId)
            .setLogsEnabled(logsEnabled)
            .setCrashReportsEnabled(crashReportEnabled)
            .setTracesEnabled(tracesEnabled)
            .setRumEnabled(rumEnabled)
            .build()

        // When
        Datadog.initialize(mockAppContext, config)

        // Then
        assertThat(CoreFeature.initialized.get()).isTrue()
        assertThat(LogsFeature.initialized.get()).isEqualTo(logsEnabled)
        assertThat(CrashReportsFeature.initialized.get()).isEqualTo(crashReportEnabled)
        assertThat(TracesFeature.initialized.get()).isEqualTo(tracesEnabled)
        assertThat(RumFeature.initialized.get()).isEqualTo(rumEnabled)
    }

    // region Deprecated

    @Test
    fun `𝕄 do nothing 𝕎 setEndpointUrl() with Discard strategy`(
        @RegexForgery("https://[a-z]+\\.[a-z]{3}") newEndpoint: String
    ) {
        // Given
        Datadog.initialize(mockAppContext, fakeToken, fakeEnvName)
        Datadog.setVerbosity(AndroidLog.VERBOSE)

        // When
        Datadog.setEndpointUrl(newEndpoint, EndpointUpdateStrategy.DISCARD_OLD_DATA)

        // Then
        verify(mockDevLogHandler)
            .handleLog(
                AndroidLog.WARN,
                String.format(Locale.US, Datadog.MESSAGE_DEPRECATED, "setEndpointUrl()"),
                tags = setOf(
                    "${LogAttributes.ENV}:$fakeEnvName",
                    "${LogAttributes.APPLICATION_VERSION}:$fakePackageVersion"
                )
            )
    }

    @Test
    fun `𝕄 do nothing 𝕎 setEndpointUrl() with Update strategy`(
        @RegexForgery("https://[a-z]+\\.[a-z]{3}") newEndpoint: String
    ) {
        // Given
        Datadog.initialize(mockAppContext, fakeToken, fakeEnvName)
        Datadog.setVerbosity(AndroidLog.VERBOSE)

        // When
        Datadog.setEndpointUrl(newEndpoint, EndpointUpdateStrategy.SEND_OLD_DATA_TO_NEW_ENDPOINT)

        // Then
        verify(mockDevLogHandler)
            .handleLog(
                AndroidLog.WARN,
                String.format(Locale.US, Datadog.MESSAGE_DEPRECATED, "setEndpointUrl()"),
                tags = setOf(
                    "${LogAttributes.ENV}:$fakeEnvName",
                    "${LogAttributes.APPLICATION_VERSION}:$fakePackageVersion"
                )
            )
    }

    @Test
    fun `𝕄 initialize the LifecycleMonitor 𝕎 initialize()`() {
        // When
        Datadog.initialize(mockAppContext, fakeToken, fakeEnvName)

        // Then
        argumentCaptor<Application.ActivityLifecycleCallbacks> {
            verify(mockAppContext).registerActivityLifecycleCallbacks(capture())
            assertThat(firstValue).isInstanceOf(ProcessLifecycleMonitor::class.java)
        }
    }

    @Test
    fun `𝕄 do nothing 𝕎 initialize() twice`() {
        // Given
        Datadog.initialize(mockAppContext, fakeToken, fakeEnvName)
        Datadog.setVerbosity(AndroidLog.VERBOSE)

        // When
        Datadog.initialize(mockAppContext, fakeToken, fakeEnvName)

        // Then
        verify(mockDevLogHandler)
            .handleLog(
                AndroidLog.WARN,
                Datadog.MESSAGE_ALREADY_INITIALIZED,
                tags = setOf(
                    "${LogAttributes.ENV}:$fakeEnvName",
                    "${LogAttributes.APPLICATION_VERSION}:$fakePackageVersion"
                )
            )
    }

    // endregion
}
