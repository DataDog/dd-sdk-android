/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.core

import android.app.Application
import android.content.pm.ApplicationInfo
import android.util.Log
import com.datadog.android.Datadog
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.Credentials
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.error.internal.CrashReportsFeature
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.config.CoreFeatureTestConfiguration
import com.datadog.android.utils.config.InternalLoggerTestConfiguration
import com.datadog.android.utils.config.MainLooperTestConfiguration
import com.datadog.android.utils.extension.mockChoreographerInstance
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.forge.CustomAttributes
import com.datadog.android.v2.api.Feature
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.webview.internal.log.WebViewLogsFeature
import com.datadog.android.webview.internal.rum.WebViewRumFeature
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.net.URL

/**
 * This region groups all test about instantiating a DatadogCore instance.
 * Note: eventually most of the work done upon initialization will be moved
 * somewhere else
 */
@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogCoreInitializationTest {

    lateinit var testedCore: DatadogCore

    @Forgery
    lateinit var fakeCredentials: Credentials

    @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL)
    lateinit var fakeInstanceId: String

    @BeforeEach
    fun `set up`() {
        // Prevent crash when initializing RumFeature
        mockChoreographerInstance()
    }

    @AfterEach
    fun `tear down`() {
        if (this::testedCore.isInitialized) {
            testedCore.stop()
        }
    }

    @RepeatedTest(16)
    fun `𝕄 initialize requested features 𝕎 initialize()`(
        @BoolForgery logsEnabled: Boolean,
        @BoolForgery tracingEnabled: Boolean,
        @BoolForgery crashReportEnabled: Boolean,
        @BoolForgery rumEnabled: Boolean
    ) {
        // Given
        val configuration = Configuration.Builder(
            logsEnabled = logsEnabled,
            tracesEnabled = tracingEnabled,
            crashReportsEnabled = crashReportEnabled,
            rumEnabled = rumEnabled
        ).build()

        // When
        testedCore =
            DatadogCore(appContext.mockInstance, fakeCredentials, configuration, fakeInstanceId)

        // Then
        assertThat(testedCore.coreFeature.initialized.get()).isTrue()
        assertThat(testedCore.contextProvider).isNotNull

        if (logsEnabled) {
            assertThat(testedCore.logsFeature!!.initialized.get()).isTrue()
            assertThat(testedCore.webViewLogsFeature!!.initialized.get()).isTrue()
            assertThat(testedCore.getFeature(Feature.LOGS_FEATURE_NAME)).isNotNull
            assertThat(testedCore.getFeature(WebViewLogsFeature.WEB_LOGS_FEATURE_NAME)).isNotNull
        } else {
            assertThat(testedCore.logsFeature).isNull()
            assertThat(testedCore.webViewLogsFeature).isNull()
            assertThat(testedCore.getFeature(Feature.LOGS_FEATURE_NAME)).isNull()
            assertThat(testedCore.getFeature(WebViewLogsFeature.WEB_LOGS_FEATURE_NAME)).isNull()
        }
        if (tracingEnabled) {
            assertThat(testedCore.tracingFeature!!.initialized.get()).isTrue()
            assertThat(testedCore.getFeature(Feature.TRACING_FEATURE_NAME)).isNotNull
        } else {
            assertThat(testedCore.tracingFeature).isNull()
            assertThat(testedCore.getFeature(Feature.TRACING_FEATURE_NAME)).isNull()
        }
        if (crashReportEnabled) {
            assertThat(testedCore.crashReportsFeature!!.initialized.get()).isTrue()
            assertThat(testedCore.getFeature(CrashReportsFeature.CRASH_FEATURE_NAME)).isNotNull
        } else {
            assertThat(testedCore.crashReportsFeature).isNull()
            assertThat(testedCore.getFeature(CrashReportsFeature.CRASH_FEATURE_NAME)).isNull()
        }
        if (rumEnabled) {
            assertThat(testedCore.rumFeature!!.initialized.get()).isTrue()
            assertThat(testedCore.webViewRumFeature!!.initialized.get()).isTrue()
            assertThat(testedCore.getFeature(Feature.RUM_FEATURE_NAME)).isNotNull
            assertThat(testedCore.getFeature(WebViewRumFeature.WEB_RUM_FEATURE_NAME)).isNotNull
        } else {
            assertThat(testedCore.rumFeature).isNull()
            assertThat(testedCore.webViewRumFeature).isNull()
            assertThat(testedCore.getFeature(Feature.RUM_FEATURE_NAME)).isNull()
            assertThat(testedCore.getFeature(WebViewRumFeature.WEB_RUM_FEATURE_NAME)).isNull()
        }
    }

    @Test
    fun `𝕄 log a warning 𝕎 initialize() { null applicationID, rumEnabled }`() {
        // Given
        fakeCredentials = fakeCredentials.copy(rumApplicationId = null)
        val configuration = Configuration.Builder(
            logsEnabled = false,
            tracesEnabled = false,
            crashReportsEnabled = false,
            rumEnabled = true
        ).build()

        // When
        testedCore =
            DatadogCore(appContext.mockInstance, fakeCredentials, configuration, fakeInstanceId)

        // Then
        assertThat(testedCore.coreFeature.initialized.get()).isTrue()
        assertThat(testedCore.rumFeature!!.initialized.get()).isTrue()
        assertThat(testedCore.webViewRumFeature!!.initialized.get()).isTrue()
        verify(logger.mockInternalLogger).log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            DatadogCore.WARNING_MESSAGE_APPLICATION_ID_IS_NULL
        )
    }

    @Test
    fun `𝕄 do nothing 𝕎 initialize() { null applicationID, rumDisabled }`() {
        // Given
        fakeCredentials = fakeCredentials.copy(rumApplicationId = null)
        val configuration = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = false,
            crashReportsEnabled = false,
            rumEnabled = false
        ).build()

        // When
        testedCore =
            DatadogCore(appContext.mockInstance, fakeCredentials, configuration, fakeInstanceId)

        // Then
        assertThat(testedCore.coreFeature.initialized.get()).isTrue()
        assertThat(testedCore.rumFeature).isNull()
        assertThat(testedCore.webViewRumFeature).isNull()
        verify(logger.mockInternalLogger, never())
            .log(
                any(),
                any<InternalLogger.Target>(),
                eq(DatadogCore.WARNING_MESSAGE_APPLICATION_ID_IS_NULL),
                anyOrNull()
            )
    }

    @Test
    fun `𝕄 throw an error 𝕎 initialize() {envName not valid, isDebug=false}`(
        @IntForgery fakeFlags: Int,
        @StringForgery(regex = "[\\$%\\*@][a-zA-Z0-9_:./-]{0,200}") invalidEnvName: String
    ) {
        // Given
        appContext.fakeAppInfo.flags = fakeFlags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
        fakeCredentials = fakeCredentials.copy(envName = invalidEnvName)
        val configuration = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        ).build()

        // Then
        assertThrows<IllegalArgumentException>(DatadogCore.MESSAGE_ENV_NAME_NOT_VALID) {
            // When
            testedCore =
                DatadogCore(
                    appContext.mockInstance,
                    fakeCredentials,
                    configuration,
                    fakeInstanceId
                )
        }
    }

    @Test
    fun `𝕄 throw an error 𝕎 initialize() {envName not valid, isDebug=true}`(
        @IntForgery fakeFlags: Int,
        @StringForgery(regex = "[\\$%\\*@][a-zA-Z0-9_:./-]{0,200}") invalidEnvName: String
    ) {
        // Given
        appContext.fakeAppInfo.flags = fakeFlags or ApplicationInfo.FLAG_DEBUGGABLE
        fakeCredentials = fakeCredentials.copy(envName = invalidEnvName)
        val configuration = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        ).build()

        // When
        var caughtException: Exception? = null
        try {
            testedCore =
                DatadogCore(
                    appContext.mockInstance,
                    fakeCredentials,
                    configuration,
                    fakeInstanceId
                )
        } catch (e: Exception) {
            caughtException = e
        }

        // Then
        assertThat(caughtException)
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage(DatadogCore.MESSAGE_ENV_NAME_NOT_VALID)
    }

    @Test
    fun `𝕄 initialize the ConsentProvider with PENDING 𝕎 initializing()`() {
        // Given
        val configuration = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        ).build()

        // When
        testedCore =
            DatadogCore(appContext.mockInstance, fakeCredentials, configuration, fakeInstanceId)

        // Then
        assertThat(testedCore.coreFeature.trackingConsentProvider.getConsent())
            .isEqualTo(TrackingConsent.PENDING)
    }

    @Test
    fun `𝕄 not set lib verbosity 𝕎 initializing() {dev mode when debug, debug=false}`(
        @IntForgery fakeFlags: Int
    ) {
        // Given
        appContext.fakeAppInfo.flags = fakeFlags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
        val configuration = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        )
            .setUseDeveloperModeWhenDebuggable(true)
            .build()

        // When
        testedCore =
            DatadogCore(appContext.mockInstance, fakeCredentials, configuration, fakeInstanceId)

        // Then
        assertThat(testedCore.libraryVerbosity)
            .isEqualTo(Int.MAX_VALUE)
    }

    @Test
    fun `𝕄 set lib verbosity 𝕎 initializing() {dev mode when debug, debug=true}`(
        @IntForgery fakeFlags: Int
    ) {
        // Given
        appContext.fakeAppInfo.flags = fakeFlags or ApplicationInfo.FLAG_DEBUGGABLE
        val configuration = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        )
            .setUseDeveloperModeWhenDebuggable(true)
            .build()

        // When
        testedCore =
            DatadogCore(appContext.mockInstance, fakeCredentials, configuration, fakeInstanceId)

        // Then
        assertThat(testedCore.libraryVerbosity)
            .isEqualTo(Log.VERBOSE)
    }

    // region AdditionalConfig

    @Test
    fun `𝕄 apply source name 𝕎 applyAdditionalConfig(config) { with source name }`(
        @StringForgery(type = StringForgeryType.ALPHABETICAL) source: String
    ) {
        // Given
        val configuration = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        )
            .setAdditionalConfiguration(mapOf(Datadog.DD_SOURCE_TAG to source))
            .build()

        // When
        testedCore =
            DatadogCore(appContext.mockInstance, fakeCredentials, configuration, fakeInstanceId)

        // Then
        assertThat(testedCore.coreFeature.sourceName).isEqualTo(source)
    }

    @Test
    fun `𝕄 use default source name 𝕎 applyAdditionalConfig(config) { with empty source name }`(
        @StringForgery(type = StringForgeryType.WHITESPACE) source: String
    ) {
        // Given
        val configuration = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        )
            .setAdditionalConfiguration(mapOf(Datadog.DD_SOURCE_TAG to source))
            .build()

        // When
        testedCore =
            DatadogCore(appContext.mockInstance, fakeCredentials, configuration, fakeInstanceId)

        // Then
        assertThat(testedCore.coreFeature.sourceName).isEqualTo(CoreFeature.DEFAULT_SOURCE_NAME)
    }

    @Test
    fun `𝕄 use default source name 𝕎 applyAdditionalConfig(config) { with source name !string }`(
        @IntForgery source: Int
    ) {
        // Given
        val configuration = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        )
            .setAdditionalConfiguration(mapOf(Datadog.DD_SOURCE_TAG to source))
            .build()

        // When
        testedCore =
            DatadogCore(appContext.mockInstance, fakeCredentials, configuration, fakeInstanceId)

        // Then
        assertThat(testedCore.coreFeature.sourceName).isEqualTo(CoreFeature.DEFAULT_SOURCE_NAME)
    }

    @Test
    fun `𝕄 use default source name 𝕎 applyAdditionalConfig(config) { without source name }`(
        @Forgery customAttributes: CustomAttributes
    ) {
        // Given
        val configuration = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        )
            .setAdditionalConfiguration(customAttributes.nonNullData)
            .build()

        // When
        testedCore =
            DatadogCore(appContext.mockInstance, fakeCredentials, configuration, fakeInstanceId)

        // Then
        assertThat(testedCore.coreFeature.sourceName).isEqualTo(CoreFeature.DEFAULT_SOURCE_NAME)
    }

    @Test
    fun `𝕄 apply sdk version 𝕎 applyAdditionalConfig(config) { with sdk version }`(
        @StringForgery(regex = "[0-9]+(\\.[0-9]+)+") sdkVersion: String
    ) {
        // Given
        val configuration = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        )
            .setAdditionalConfiguration(mapOf(Datadog.DD_SDK_VERSION_TAG to sdkVersion))
            .build()

        // When
        testedCore =
            DatadogCore(appContext.mockInstance, fakeCredentials, configuration, fakeInstanceId)

        // Then
        assertThat(testedCore.coreFeature.sdkVersion).isEqualTo(sdkVersion)
    }

    @Test
    fun `𝕄 use default sdk version 𝕎 applyAdditionalConfig(config) { with empty sdk version }`(
        @StringForgery(type = StringForgeryType.WHITESPACE) sdkVersion: String
    ) {
        // Given
        val configuration = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        )
            .setAdditionalConfiguration(
                mapOf(Datadog.DD_SDK_VERSION_TAG to sdkVersion)
            )
            .build()

        // When
        testedCore =
            DatadogCore(appContext.mockInstance, fakeCredentials, configuration, fakeInstanceId)

        // Then
        assertThat(testedCore.coreFeature.sdkVersion).isEqualTo(CoreFeature.DEFAULT_SDK_VERSION)
    }

    @Test
    fun `𝕄 use default sdk version 𝕎 applyAdditionalConfig(config) { with sdk version !string }`(
        @Forgery sdkVersion: URL
    ) {
        // Given
        val configuration = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        )
            .setAdditionalConfiguration(mapOf(Datadog.DD_SDK_VERSION_TAG to sdkVersion))
            .build()

        // When
        testedCore =
            DatadogCore(appContext.mockInstance, fakeCredentials, configuration, fakeInstanceId)

        // Then
        assertThat(testedCore.coreFeature.sdkVersion).isEqualTo(CoreFeature.DEFAULT_SDK_VERSION)
    }

    @Test
    fun `𝕄 use default sdk version 𝕎 applyAdditionalConfig(config) { without sdk version }`(
        @Forgery customAttributes: CustomAttributes
    ) {
        // Given
        val configuration = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        )
            .setAdditionalConfiguration(customAttributes.nonNullData)
            .build()

        // When
        testedCore =
            DatadogCore(appContext.mockInstance, fakeCredentials, configuration, fakeInstanceId)

        // Then
        assertThat(testedCore.coreFeature.sdkVersion).isEqualTo(CoreFeature.DEFAULT_SDK_VERSION)
    }

    @Test
    fun `𝕄 apply app version 𝕎 applyAdditionalConfig(config) { with app version }`(
        @StringForgery appVersion: String
    ) {
        // Given
        val configuration = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        )
            .setAdditionalConfiguration(mapOf(Datadog.DD_APP_VERSION_TAG to appVersion))
            .build()

        // When
        testedCore =
            DatadogCore(appContext.mockInstance, fakeCredentials, configuration, fakeInstanceId)

        // Then
        assertThat(testedCore.coreFeature.packageVersionProvider.version).isEqualTo(appVersion)
    }

    @Test
    fun `𝕄 use default app version 𝕎 applyAdditionalConfig(config) { with empty app version }`(
        forge: Forge
    ) {
        // Given
        val configuration = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        )
            .setAdditionalConfiguration(
                mapOf(Datadog.DD_APP_VERSION_TAG to forge.aWhitespaceString())
            )
            .build()

        // When
        testedCore =
            DatadogCore(appContext.mockInstance, fakeCredentials, configuration, fakeInstanceId)

        // Then
        assertThat(testedCore.coreFeature.packageVersionProvider.version).isEqualTo(
            appContext.fakeVersionName
        )
    }

    @Test
    fun `𝕄 use default app version 𝕎 applyAdditionalConfig(config) { with app version !string }`(
        forge: Forge
    ) {
        // Given
        val configuration = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        )
            .setAdditionalConfiguration(mapOf(Datadog.DD_APP_VERSION_TAG to forge.anInt()))
            .build()

        // When
        testedCore =
            DatadogCore(appContext.mockInstance, fakeCredentials, configuration, fakeInstanceId)

        // Then
        assertThat(testedCore.coreFeature.packageVersionProvider.version).isEqualTo(
            appContext.fakeVersionName
        )
    }

    @Test
    fun `𝕄 apply configuration telemety sample rate W applyAdditionalConfig(confic) { with sample rate }`(
        @FloatForgery(0.0f, 100.0f) sampleRate: Float
    ) {
        // Given
        val configuration = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        )
            .setAdditionalConfiguration(mapOf(Datadog.DD_TELEMETRY_CONFIG_SAMPLE_RATE_TAG to sampleRate))
            .build()

        // When
        testedCore =
            DatadogCore(appContext.mockInstance, fakeCredentials, configuration, fakeInstanceId)

        // Then
        assertThat(testedCore.rumFeature?.telemetryConfigurationSamplingRate).isEqualTo(sampleRate)
    }

    // endregion

    companion object {
        val appContext = ApplicationContextTestConfiguration(Application::class.java)
        val mainLooper = MainLooperTestConfiguration()
        val logger = InternalLoggerTestConfiguration()
        val coreFeature = CoreFeatureTestConfiguration(appContext)

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(logger, appContext, mainLooper, coreFeature)
        }
    }
}
