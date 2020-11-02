/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.ConnectivityManager
import android.util.Log as AndroidLog
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.error.internal.CrashReportsFeature
import com.datadog.android.log.internal.LogsFeature
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.log.internal.user.MutableUserInfoProvider
import com.datadog.android.log.internal.user.UserInfo
import com.datadog.android.privacy.Consent
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.tracing.internal.TracesFeature
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockContext
import com.datadog.android.utils.mockDevLogHandler
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.invokeMethod
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.File
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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

    @StringForgery(type = StringForgeryType.HEXADECIMAL)
    lateinit var fakeToken: String

    @StringForgery
    lateinit var fakePackageName: String

    @StringForgery(regex = "\\d(\\.\\d){3}")
    lateinit var fakePackageVersion: String

    @StringForgery(regex = "[a-zA-Z0-9_:./-]{0,195}[a-zA-Z0-9_./-]")
    lateinit var fakeEnvName: String

    @TempDir
    lateinit var tempRootDir: File

    lateinit var fakeConsent: Consent

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeConsent = forge.aValueFrom(Consent::class.java)
        mockDevLogHandler = mockDevLogHandler()
        mockAppContext = mockContext(fakePackageName, fakePackageVersion)
        whenever(mockAppContext.filesDir).thenReturn(tempRootDir)
        whenever(mockAppContext.applicationContext) doReturn mockAppContext
        whenever(mockAppContext.getSystemService(Context.CONNECTIVITY_SERVICE))
            .doReturn(mockConnectivityMgr)
    }

    @AfterEach
    fun `tear down`() {
        Datadog.isDebug = false
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
        @StringForgery(type = StringForgeryType.HEXADECIMAL) id: String,
        @StringForgery name: String,
        @StringForgery(regex = "\\w+@\\w+") email: String
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
        @StringForgery(type = StringForgeryType.HEXADECIMAL) id: String,
        @StringForgery name: String,
        @StringForgery(regex = "\\w+@\\w+") email: String
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
    fun `𝕄 return true 𝕎 initialize(context, consent, config) + isInitialized()`(
        @Forgery applicationId: UUID
    ) {
        // Given
        val config = DatadogConfig.Builder(fakeToken, fakeEnvName, applicationId)
            .build()

        // When
        Datadog.initialize(mockAppContext, fakeConsent, config)
        val initialized = Datadog.isInitialized()

        // Then
        assertThat(initialized).isTrue()
    }

    @Test
    fun `𝕄 initialisze the ConsentProvider 𝕎 initializing)`(
        @Forgery applicationId: UUID
    ) {
        // Given
        val config = DatadogConfig.Builder(fakeToken, fakeEnvName, applicationId)
            .build()

        // When
        Datadog.initialize(mockAppContext, fakeConsent, config)

        // Then
        assertThat(CoreFeature.consentProvider.getConsent()).isEqualTo(fakeConsent)
    }

    @Test
    fun `M return false and log an error W initialize() {envName not valid, isDebug=false}`(
        forge: Forge,
        @Forgery applicationId: UUID
    ) {
        // Given
        stubContextAsNotDebuggable(mockAppContext)
        val fakeBadEnvName = forge.aStringMatching("^[\\$%\\*@][a-zA-Z0-9_:./-]{0,200}")
        val config = DatadogConfig.Builder(fakeToken, fakeBadEnvName, applicationId)
            .build()

        // When
        Datadog.initialize(mockAppContext, fakeConsent, config)
        val initialized = Datadog.isInitialized()

        // Then
        verify(mockDevLogHandler).handleLog(AndroidLog.ERROR, Datadog.MESSAGE_ENV_NAME_NOT_VALID)
        assertThat(initialized).isFalse()
    }

    @Test
    fun `M throw an exception W initialize() {envName not valid, isDebug=true}`(
        forge: Forge,
        @Forgery applicationId: UUID
    ) {
        // Given
        stubContextAsDebuggable(mockAppContext)
        val fakeBadEnvName = forge.aStringMatching("^[\\$%\\*@][a-zA-Z0-9_:./-]{0,200}")
        val config = DatadogConfig.Builder(fakeToken, fakeBadEnvName, applicationId)
            .build()

        // When
        assertThatThrownBy {
            Datadog.initialize(
                mockAppContext,
                fakeConsent,
                config
            )
        }.isInstanceOf(java.lang.IllegalArgumentException::class.java)
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
        Datadog.initialize(mockAppContext, fakeConsent, config)

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
        Datadog.initialize(mockAppContext, fakeConsent, config)

        // Then
        assertThat(CoreFeature.initialized.get()).isTrue()
        assertThat(LogsFeature.initialized.get()).isEqualTo(logsEnabled)
        assertThat(CrashReportsFeature.initialized.get()).isEqualTo(crashReportEnabled)
        assertThat(TracesFeature.initialized.get()).isEqualTo(tracesEnabled)
        assertThat(RumFeature.initialized.get()).isEqualTo(rumEnabled)
    }

    // region Deprecated

    // endregion

    @Test
    fun `𝕄 return true 𝕎 initialize(context, config) + isInitialized()`(
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
    fun `𝕄 bypass GDPR by default 𝕎 initialize(context, config) + isInitialized()`(
        @Forgery applicationId: UUID
    ) {
        // Given
        val config = DatadogConfig.Builder(fakeToken, fakeEnvName, applicationId)
            .build()

        // When
        Datadog.initialize(mockAppContext, config)

        // Then
        assertThat(CoreFeature.consentProvider.getConsent()).isEqualTo(Consent.GRANTED)
    }

    @Test
    fun `𝕄 initialize features 𝕎 initialize(context, config) deprecated method`(
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

    // region Internal

    private fun stubContextAsDebuggable(mockContext: Context) {
        val applicationInfo = mockContext.applicationInfo
        applicationInfo.flags = ApplicationInfo.FLAG_DEBUGGABLE
    }

    private fun stubContextAsNotDebuggable(mockContext: Context) {
        val applicationInfo = mockContext.applicationInfo
        applicationInfo.flags = ApplicationInfo.FLAG_DEBUGGABLE.inv()
    }

    // endregion
}
