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
import com.datadog.android.core.configuration.BatchSize
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.Credentials
import com.datadog.android.core.configuration.UploadFrequency
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.net.DataOkHttpUploaderV2
import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.core.model.UserInfo
import com.datadog.android.error.internal.CrashReportsFeature
import com.datadog.android.log.internal.LogsFeature
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.log.internal.user.MutableUserInfoProvider
import com.datadog.android.monitoring.internal.InternalLogsFeature
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.tracing.internal.TracingFeature
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.config.MainLooperTestConfiguration
import com.datadog.android.utils.extension.mockChoreographerInstance
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockDevLogHandler
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.invokeMethod
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
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
internal class DatadogTest {

    lateinit var mockDevLogHandler: LogHandler

    @Mock
    lateinit var mockConnectivityMgr: ConnectivityManager

    @StringForgery(type = StringForgeryType.HEXADECIMAL)
    lateinit var fakeToken: String

    @StringForgery
    lateinit var fakeVariant: String

    @StringForgery(regex = "[a-zA-Z0-9_:./-]{0,195}[a-zA-Z0-9_./-]")
    lateinit var fakeEnvName: String

    @StringForgery(regex = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    lateinit var fakeApplicationId: String

    lateinit var fakeConsent: TrackingConsent

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeConsent = forge.aValueFrom(TrackingConsent::class.java)
        mockDevLogHandler = mockDevLogHandler()

        whenever(appContext.mockInstance.getSystemService(Context.CONNECTIVITY_SERVICE))
            .doReturn(mockConnectivityMgr)

        // Prevent crash when initializing RumFeature
        mockChoreographerInstance()

        CoreFeature.sdkVersion = CoreFeature.DEFAULT_SDK_VERSION
        CoreFeature.sourceName = CoreFeature.DEFAULT_SOURCE_NAME
    }

    @AfterEach
    fun `tear down`() {
        Datadog.isDebug = false
        Datadog.setVerbosity(Int.MAX_VALUE)

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
        verifyZeroInteractions(appContext.mockInstance)
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
        verify(mockUserInfoProvider).setUserInfo(
            UserInfo(
                id,
                name,
                email
            )
        )
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
        verify(mockUserInfoProvider).setUserInfo(
            UserInfo(
                id,
                name,
                email
            )
        )
        verify(mockUserInfoProvider).setUserInfo(
            UserInfo(
                null,
                null,
                null
            )
        )
    }

    @Test
    fun `𝕄 return true 𝕎 initialize(context, credential, , consent) + isInitialized()`() {
        // Given
        val credentials = Credentials(fakeToken, fakeEnvName, fakeVariant, fakeApplicationId, null)
        val configuration = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        ).build()

        // When
        Datadog.initialize(appContext.mockInstance, credentials, configuration, fakeConsent)
        val initialized = Datadog.isInitialized()

        // Then
        assertThat(initialized).isTrue()
    }

    @Test
    fun `𝕄 initialize the ConsentProvider 𝕎 initializing()`() {
        // Given
        val credentials = Credentials(fakeToken, fakeEnvName, fakeVariant, fakeApplicationId, null)
        val configuration = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        ).build()

        // When
        Datadog.initialize(appContext.mockInstance, credentials, configuration, fakeConsent)

        // Then
        assertThat(CoreFeature.trackingConsentProvider.getConsent()).isEqualTo(fakeConsent)
    }

    @ParameterizedTest
    @EnumSource(TrackingConsent::class)
    fun `M update the ConsentProvider W setConsent`(fakeConsent: TrackingConsent) {
        // GIVEN
        val mockedConsentProvider: ConsentProvider = mock()
        CoreFeature.trackingConsentProvider = mockedConsentProvider

        // WHEN
        Datadog.setTrackingConsent(fakeConsent)

        // THEN
        verify(CoreFeature.trackingConsentProvider).setConsent(fakeConsent)
    }

    @Test
    fun `M no changes W initialize() { verboseDebug } in non debug mode`() {
        // Given
        appContext.fakeAppInfo.flags =
            appContext.fakeAppInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
        val credentials = Credentials(fakeToken, fakeEnvName, fakeVariant, fakeApplicationId, null)
        val configuration = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        )
            .setUseDeveloperModeWhenDebuggable(true)
            .sampleRumSessions(75.0f)
            .build()

        // When
        Datadog.initialize(appContext.mockInstance, credentials, configuration, fakeConsent)

        // Then
        assertThat(Datadog.libraryVerbosity).isEqualTo(Int.MAX_VALUE)
        assertThat(RumFeature.samplingRate).isEqualTo(75.0f)
        assertThat(CoreFeature.batchSize).isEqualTo(BatchSize.MEDIUM)
        assertThat(CoreFeature.uploadFrequency).isEqualTo(UploadFrequency.AVERAGE)
    }

    @Test
    fun `M overrides configuration W initialize() { verboseDebug } in debug mode`() {
        // Given
        appContext.fakeAppInfo.flags =
            appContext.fakeAppInfo.flags or ApplicationInfo.FLAG_DEBUGGABLE

        val credentials = Credentials(fakeToken, fakeEnvName, fakeVariant, fakeApplicationId, null)
        val configuration = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        )
            .setUseDeveloperModeWhenDebuggable(true)
            .sampleRumSessions(75.0f)
            .build()

        // When
        Datadog.initialize(appContext.mockInstance, credentials, configuration, fakeConsent)

        // Then
        assertThat(Datadog.libraryVerbosity).isEqualTo(AndroidLog.VERBOSE)
        assertThat(RumFeature.samplingRate).isEqualTo(100.0f)
        assertThat(CoreFeature.batchSize).isEqualTo(BatchSize.SMALL)
        assertThat(CoreFeature.uploadFrequency).isEqualTo(UploadFrequency.FREQUENT)
    }

    @Test
    fun `M return false and log an error W initialize() {envName not valid, isDebug=false}`(
        forge: Forge
    ) {
        // Given
        stubApplicationInfo(appContext.mockInstance, isDebuggable = false)
        val fakeBadEnvName = forge.aStringMatching("^[\\$%\\*@][a-zA-Z0-9_:./-]{0,200}")
        val credentials = Credentials(
            fakeToken,
            fakeBadEnvName,
            fakeVariant,
            fakeApplicationId,
            null
        )
        val configuration = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        ).build()

        // When
        Datadog.initialize(appContext.mockInstance, credentials, configuration, fakeConsent)
        val initialized = Datadog.isInitialized()

        // Then
        verify(mockDevLogHandler).handleLog(AndroidLog.ERROR, Datadog.MESSAGE_ENV_NAME_NOT_VALID)
        assertThat(initialized).isFalse()
    }

    @Test
    fun `M throw an exception W initialize() {envName not valid, isDebug=true}`(
        forge: Forge
    ) {
        // Given
        stubApplicationInfo(appContext.mockInstance, isDebuggable = true)
        val badEnvName = forge.aStringMatching("^[\\$%\\*@][a-zA-Z0-9_:./-]{0,200}")
        val credentials = Credentials(fakeToken, badEnvName, fakeVariant, fakeApplicationId, null)
        val configuration = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        ).build()

        // When
        assertThatThrownBy {
            Datadog.initialize(appContext.mockInstance, credentials, configuration, fakeConsent)
        }.isInstanceOf(java.lang.IllegalArgumentException::class.java)
    }

    @Test
    fun `𝕄 return false 𝕎 isInitialized()`() {
        // When
        val initialized = Datadog.isInitialized()

        // Then
        assertThat(initialized).isFalse()
    }

    @Test
    fun `𝕄 initialize features 𝕎 initialize()`() {
        // Given
        val credentials = Credentials(fakeToken, fakeEnvName, fakeVariant, fakeApplicationId, null)
        val configuration = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        ).build()

        // When
        Datadog.initialize(appContext.mockInstance, credentials, configuration, fakeConsent)

        // Then
        assertThat(CoreFeature.initialized.get()).isTrue()
        assertThat(LogsFeature.initialized.get()).isTrue()
        assertThat(CrashReportsFeature.initialized.get()).isTrue()
        assertThat(TracingFeature.initialized.get()).isTrue()
        assertThat(RumFeature.initialized.get()).isTrue()
        assertThat(InternalLogsFeature.initialized.get()).isFalse()
    }

    @RepeatedTest(8)
    fun `𝕄 not initialize features 𝕎 initialize() with features disabled`(
        @BoolForgery logsEnabled: Boolean,
        @BoolForgery tracesEnabled: Boolean,
        @BoolForgery crashReportEnabled: Boolean,
        @BoolForgery rumEnabled: Boolean
    ) {
        // Given
        val credentials = Credentials(fakeToken, fakeEnvName, fakeVariant, fakeApplicationId, null)
        val configuration = Configuration.Builder(
            logsEnabled = logsEnabled,
            tracesEnabled = tracesEnabled,
            crashReportsEnabled = crashReportEnabled,
            rumEnabled = rumEnabled
        ).build()

        // When
        Datadog.initialize(appContext.mockInstance, credentials, configuration, fakeConsent)

        // Then
        assertThat(CoreFeature.initialized.get()).isTrue()
        assertThat(LogsFeature.initialized.get()).isEqualTo(logsEnabled)
        assertThat(CrashReportsFeature.initialized.get()).isEqualTo(crashReportEnabled)
        assertThat(TracingFeature.initialized.get()).isEqualTo(tracesEnabled)
        assertThat(RumFeature.initialized.get()).isEqualTo(rumEnabled)
        assertThat(InternalLogsFeature.initialized.get()).isFalse()
    }

    @Test
    fun `𝕄 log a warning 𝕎 initialize() { null applicationID, rumEnabled }`() {
        // Given
        val credentials = Credentials(fakeToken, fakeEnvName, fakeVariant, null, null)
        val configuration = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        ).build()

        // When
        Datadog.initialize(appContext.mockInstance, credentials, configuration, fakeConsent)

        // Then
        assertThat(CoreFeature.initialized.get()).isTrue()
        assertThat(LogsFeature.initialized.get()).isTrue()
        assertThat(CrashReportsFeature.initialized.get()).isTrue()
        assertThat(TracingFeature.initialized.get()).isTrue()
        assertThat(RumFeature.initialized.get()).isTrue()
        assertThat(InternalLogsFeature.initialized.get()).isFalse()
        verify(mockDevLogHandler).handleLog(
            android.util.Log.WARN,
            Datadog.WARNING_MESSAGE_APPLICATION_ID_IS_NULL
        )
    }

    @Test
    fun `𝕄 do nothing 𝕎 initialize() { null applicationID, rumDisabled }`() {
        // Given
        val credentials = Credentials(fakeToken, fakeEnvName, fakeVariant, null, null)
        val configuration = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = false
        ).build()

        // When
        Datadog.initialize(appContext.mockInstance, credentials, configuration, fakeConsent)

        // Then
        assertThat(CoreFeature.initialized.get()).isTrue()
        assertThat(LogsFeature.initialized.get()).isTrue()
        assertThat(CrashReportsFeature.initialized.get()).isTrue()
        assertThat(TracingFeature.initialized.get()).isTrue()
        assertThat(RumFeature.initialized.get()).isFalse()
        assertThat(InternalLogsFeature.initialized.get()).isFalse()
        verify(mockDevLogHandler, never()).handleLog(
            android.util.Log.WARN,
            Datadog.WARNING_MESSAGE_APPLICATION_ID_IS_NULL
        )
    }

    @Test
    fun `𝕄 initialize InternalLogs 𝕎 initialize() { Internal logs configured }`(
        @StringForgery(StringForgeryType.HEXADECIMAL) clientToken: String,
        @StringForgery(regex = "https://[a-z]+\\.com") url: String
    ) {
        // Given
        val credentials = Credentials(fakeToken, fakeEnvName, fakeVariant, null, null)
        val configuration = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        )
            .setInternalLogsEnabled(clientToken, url)
            .build()

        // When
        Datadog.initialize(appContext.mockInstance, credentials, configuration, fakeConsent)

        // Then
        assertThat(CoreFeature.initialized.get()).isTrue()
        assertThat(LogsFeature.initialized.get()).isTrue()
        assertThat(CrashReportsFeature.initialized.get()).isTrue()
        assertThat(TracingFeature.initialized.get()).isTrue()
        assertThat(RumFeature.initialized.get()).isTrue()
        assertThat(InternalLogsFeature.initialized.get()).isTrue()
    }

    @Test
    fun `𝕄 apply source name 𝕎 applyAdditionalConfig(config) { with source name }`(
        @StringForgery source: String
    ) {
        // Given
        val config = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        )
            .setAdditionalConfiguration(mapOf(Datadog.DD_SOURCE_TAG to source))
            .build()
        val credentials = Credentials(fakeToken, fakeEnvName, fakeVariant, null, null)

        // When
        Datadog.initialize(appContext.mockInstance, credentials, config, TrackingConsent.GRANTED)

        // Then
        assertThat(CoreFeature.sourceName).isEqualTo(source)
        assertThat(
            arrayOf(
                LogsFeature.uploader,
                RumFeature.uploader,
                TracingFeature.uploader,
                CrashReportsFeature.uploader
            )
                .map { (it as DataOkHttpUploaderV2).source }
        )
            .containsOnly(source)
    }

    @Test
    fun `𝕄 use default source name 𝕎 applyAdditionalConfig(config) { with empty source name }`(
        forge: Forge
    ) {
        // Given
        val config = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        )
            .setAdditionalConfiguration(mapOf(Datadog.DD_SOURCE_TAG to forge.aWhitespaceString()))
            .build()
        val credentials = Credentials(fakeToken, fakeEnvName, fakeVariant, null, null)

        // When
        Datadog.initialize(appContext.mockInstance, credentials, config, TrackingConsent.GRANTED)

        // Then
        assertThat(CoreFeature.sourceName).isEqualTo(CoreFeature.DEFAULT_SOURCE_NAME)
        assertThat(
            arrayOf(
                LogsFeature.uploader,
                RumFeature.uploader,
                TracingFeature.uploader,
                CrashReportsFeature.uploader
            )
                .map { (it as DataOkHttpUploaderV2).source }
        )
            .containsOnly(CoreFeature.DEFAULT_SOURCE_NAME)
    }

    @Test
    fun `𝕄 use default source name 𝕎 applyAdditionalConfig(config) { with source name !string }`(
        forge: Forge
    ) {
        // Given
        val config = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        )
            .setAdditionalConfiguration(mapOf(Datadog.DD_SOURCE_TAG to forge.anInt()))
            .build()
        val credentials = Credentials(fakeToken, fakeEnvName, fakeVariant, null, null)

        // When
        Datadog.initialize(appContext.mockInstance, credentials, config, TrackingConsent.GRANTED)

        // Then
        assertThat(CoreFeature.sourceName).isEqualTo(CoreFeature.DEFAULT_SOURCE_NAME)
        assertThat(
            arrayOf(
                LogsFeature.uploader,
                RumFeature.uploader,
                TracingFeature.uploader,
                CrashReportsFeature.uploader
            )
                .map { (it as DataOkHttpUploaderV2).source }
        )
            .containsOnly(CoreFeature.DEFAULT_SOURCE_NAME)
    }

    @Test
    fun `𝕄 use default source name 𝕎 applyAdditionalConfig(config) { without source name }`(
        forge: Forge
    ) {
        // Given
        val config = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        )
            .setAdditionalConfiguration(forge.aMap { anAsciiString() to aString() })
            .build()
        val credentials = Credentials(fakeToken, fakeEnvName, fakeVariant, null, null)

        // When
        Datadog.initialize(appContext.mockInstance, credentials, config, TrackingConsent.GRANTED)

        // Then
        assertThat(CoreFeature.sourceName).isEqualTo(CoreFeature.DEFAULT_SOURCE_NAME)
        assertThat(
            arrayOf(
                LogsFeature.uploader,
                RumFeature.uploader,
                TracingFeature.uploader,
                CrashReportsFeature.uploader
            )
                .map { (it as DataOkHttpUploaderV2).source }
        )
            .containsOnly(CoreFeature.DEFAULT_SOURCE_NAME)
    }

    @Test
    fun `𝕄 apply sdk version 𝕎 applyAdditionalConfig(config) { with sdk version }`(
        @StringForgery sdkVersion: String
    ) {
        // Given
        val config = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        )
            .setAdditionalConfiguration(mapOf(Datadog.DD_SDK_VERSION_TAG to sdkVersion))
            .build()
        val credentials = Credentials(fakeToken, fakeEnvName, fakeVariant, null, null)

        // When
        Datadog.initialize(appContext.mockInstance, credentials, config, TrackingConsent.GRANTED)

        // Then
        assertThat(CoreFeature.sdkVersion).isEqualTo(sdkVersion)
        assertThat(
            arrayOf(
                LogsFeature.uploader,
                RumFeature.uploader,
                TracingFeature.uploader,
                CrashReportsFeature.uploader
            )
                .map { (it as DataOkHttpUploaderV2).sdkVersion }
        )
            .containsOnly(sdkVersion)
    }

    @Test
    fun `𝕄 use default sdk version 𝕎 applyAdditionalConfig(config) { with empty sdk version }`(
        forge: Forge
    ) {
        // Given
        val config = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        )
            .setAdditionalConfiguration(
                mapOf(Datadog.DD_SDK_VERSION_TAG to forge.aWhitespaceString())
            )
            .build()
        val credentials = Credentials(fakeToken, fakeEnvName, fakeVariant, null, null)

        // When
        Datadog.initialize(appContext.mockInstance, credentials, config, TrackingConsent.GRANTED)

        // Then
        assertThat(CoreFeature.sdkVersion).isEqualTo(CoreFeature.DEFAULT_SDK_VERSION)
        assertThat(
            arrayOf(
                LogsFeature.uploader,
                RumFeature.uploader,
                TracingFeature.uploader,
                CrashReportsFeature.uploader
            )
                .map { (it as DataOkHttpUploaderV2).sdkVersion }
        )
            .containsOnly(CoreFeature.DEFAULT_SDK_VERSION)
    }

    @Test
    fun `𝕄 use default sdk version 𝕎 applyAdditionalConfig(config) { with sdk version !string }`(
        forge: Forge
    ) {
        // Given
        val config = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        )
            .setAdditionalConfiguration(mapOf(Datadog.DD_SDK_VERSION_TAG to forge.anInt()))
            .build()
        val credentials = Credentials(fakeToken, fakeEnvName, fakeVariant, null, null)

        // When
        Datadog.initialize(appContext.mockInstance, credentials, config, TrackingConsent.GRANTED)

        // Then
        assertThat(CoreFeature.sdkVersion).isEqualTo(CoreFeature.DEFAULT_SDK_VERSION)
        assertThat(
            arrayOf(
                LogsFeature.uploader,
                RumFeature.uploader,
                TracingFeature.uploader,
                CrashReportsFeature.uploader
            )
                .map { (it as DataOkHttpUploaderV2).sdkVersion }
        )
            .containsOnly(CoreFeature.DEFAULT_SDK_VERSION)
    }

    @Test
    fun `𝕄 use default sdk version 𝕎 applyAdditionalConfig(config) { without sdk version }`(
        forge: Forge
    ) {
        // Given
        val config = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        )
            .setAdditionalConfiguration(forge.aMap { anAsciiString() to aString() })
            .build()
        val credentials = Credentials(fakeToken, fakeEnvName, fakeVariant, null, null)

        // When
        Datadog.initialize(appContext.mockInstance, credentials, config, TrackingConsent.GRANTED)

        // Then
        assertThat(CoreFeature.sdkVersion).isEqualTo(CoreFeature.DEFAULT_SDK_VERSION)
        assertThat(
            arrayOf(
                LogsFeature.uploader,
                RumFeature.uploader,
                TracingFeature.uploader,
                CrashReportsFeature.uploader
            )
                .map { (it as DataOkHttpUploaderV2).sdkVersion }
        )
            .containsOnly(CoreFeature.DEFAULT_SDK_VERSION)
    }

    @Test
    fun `𝕄 enable RUM debugging 𝕎 enableRumDebugging(true)`() {
        // Given
        val config = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        )
            .build()
        val credentials = Credentials(fakeToken, fakeEnvName, fakeVariant, null, null)

        // When
        Datadog.initialize(appContext.mockInstance, credentials, config, TrackingConsent.GRANTED)
        Datadog.enableRumDebugging(true)

        // Then
        assertThat(RumFeature.debugActivityLifecycleListener).isNotNull
    }

    @Test
    fun `𝕄 disable RUM debugging 𝕎 enableRumDebugging(false)`() {
        // Given
        val config = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        )
            .build()
        val credentials = Credentials(fakeToken, fakeEnvName, fakeVariant, null, null)

        // When
        Datadog.initialize(appContext.mockInstance, credentials, config, TrackingConsent.GRANTED)
        Datadog.enableRumDebugging(false)

        // Then
        assertThat(RumFeature.debugActivityLifecycleListener).isNull()
    }

    // region Internal

    private fun stubApplicationInfo(mockContext: Context, isDebuggable: Boolean) {
        val applicationInfo = mockContext.applicationInfo
        applicationInfo.flags = if (isDebuggable) ApplicationInfo.FLAG_DEBUGGABLE else 0
    }

    // endregion

    companion object {
        val appContext = ApplicationContextTestConfiguration(Application::class.java)
        val mainLooper = MainLooperTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(appContext, mainLooper)
        }
    }
}
