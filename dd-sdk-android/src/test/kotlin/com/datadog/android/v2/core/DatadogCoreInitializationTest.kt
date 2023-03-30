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
import com.datadog.android.core.configuration.BatchSize
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.Credentials
import com.datadog.android.core.configuration.UploadFrequency
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.SdkFeature
import com.datadog.android.error.internal.CrashReportsFeature
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.forge.CustomAttributes
import com.datadog.android.v2.api.Feature
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
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

    @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL)
    lateinit var fakeInstanceName: String

    @BeforeEach
    fun `set up`() {
        CoreFeature.disableKronosBackgroundSync = true
    }

    @AfterEach
    fun `tear down`() {
        if (this::testedCore.isInitialized) {
            testedCore.stop()
        }
    }

    @RepeatedTest(4)
    fun `𝕄 initialize requested features 𝕎 initialize()`(
        @BoolForgery crashReportEnabled: Boolean
    ) {
        // Given
        val configuration = Configuration.Builder(
            crashReportsEnabled = crashReportEnabled
        ).build()

        // When
        testedCore = DatadogCore(
            appContext.mockInstance,
            fakeCredentials,
            fakeInstanceId,
            fakeInstanceName
        ).apply {
            initialize(configuration)
        }

        // Then
        assertThat(testedCore.coreFeature.initialized.get()).isTrue()
        assertThat(testedCore.contextProvider).isNotNull

        assertThat(testedCore.getFeature(CrashReportsFeature.CRASH_FEATURE_NAME)).let {
            if (crashReportEnabled) {
                it.isNotNull
            } else {
                it.isNull()
            }
        }
    }

    @Test
    fun `𝕄 throw an error 𝕎 initialize() {envName not valid, isDebug=false}`(
        @IntForgery fakeFlags: Int,
        @StringForgery(regex = "[\\$%\\*@][a-zA-Z0-9_:./-]{0,200}") invalidEnvName: String
    ) {
        // Given
        appContext.fakeAppInfo.flags = fakeFlags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
        fakeCredentials = fakeCredentials.copy(envName = invalidEnvName)

        // When
        val exception = assertThrows<IllegalArgumentException> {
            // When
            testedCore = DatadogCore(
                appContext.mockInstance,
                fakeCredentials,
                fakeInstanceId,
                fakeInstanceName
            )
        }

        // Then
        assertThat(exception)
            .hasMessage(DatadogCore.MESSAGE_ENV_NAME_NOT_VALID)
    }

    @Test
    fun `𝕄 throw an error 𝕎 initialize() {envName not valid, isDebug=true}`(
        @IntForgery fakeFlags: Int,
        @StringForgery(regex = "[\\$%\\*@][a-zA-Z0-9_:./-]{0,200}") invalidEnvName: String
    ) {
        // Given
        appContext.fakeAppInfo.flags = fakeFlags or ApplicationInfo.FLAG_DEBUGGABLE
        fakeCredentials = fakeCredentials.copy(envName = invalidEnvName)

        // When
        val exception = assertThrows<IllegalArgumentException> {
            testedCore = DatadogCore(
                appContext.mockInstance,
                fakeCredentials,
                fakeInstanceId,
                fakeInstanceName
            )
        }

        // Then
        assertThat(exception)
            .hasMessage(DatadogCore.MESSAGE_ENV_NAME_NOT_VALID)
    }

    @Test
    fun `𝕄 initialize the ConsentProvider with PENDING 𝕎 initializing()`() {
        // Given
        val configuration = Configuration.Builder(
            crashReportsEnabled = true
        ).build()

        // When
        testedCore = DatadogCore(
            appContext.mockInstance,
            fakeCredentials,
            fakeInstanceId,
            fakeInstanceName
        ).apply {
            initialize(configuration)
        }

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
            crashReportsEnabled = true
        )
            .setUseDeveloperModeWhenDebuggable(true)
            .build()

        // When
        testedCore = DatadogCore(
            appContext.mockInstance,
            fakeCredentials,
            fakeInstanceId,
            fakeInstanceName
        ).apply {
            initialize(configuration)
        }

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
            crashReportsEnabled = true
        )
            .setUseDeveloperModeWhenDebuggable(true)
            .build()

        // When
        testedCore = DatadogCore(
            appContext.mockInstance,
            fakeCredentials,
            fakeInstanceId,
            fakeInstanceName
        ).apply {
            initialize(configuration)
        }

        // Then
        assertThat(testedCore.libraryVerbosity)
            .isEqualTo(Log.VERBOSE)
    }

    @Test
    fun `𝕄 submit core config telemetry 𝕎 initializing()`(
        forge: Forge
    ) {
        // Given
        val trackErrors = forge.aBool()
        val useProxy = forge.aBool()
        val useLocalEncryption = forge.aBool()
        val batchSize = forge.aValueFrom(BatchSize::class.java)
        val uploadFrequency = forge.aValueFrom(UploadFrequency::class.java)

        val configuration = Configuration.Builder(
            crashReportsEnabled = trackErrors
        ).apply {
            if (useProxy) {
                setProxy(mock(), forge.aNullable { mock() })
            }
            if (useLocalEncryption) {
                setEncryption(mock())
            }
        }
            .setBatchSize(batchSize)
            .setUploadFrequency(uploadFrequency)
            .build()

        // When
        testedCore = DatadogCore(
            appContext.mockInstance,
            fakeCredentials,
            fakeInstanceId,
            fakeInstanceName
        ).apply {
            initialize(configuration)
        }

        // Then
        val mockRumFeature = mock<SdkFeature>()
        testedCore.features += Feature.RUM_FEATURE_NAME to mockRumFeature

        testedCore.coreFeature.uploadExecutorService.queue
            .toTypedArray()
            .forEach {
                it.run()
            }
        testedCore.coreFeature.uploadExecutorService.shutdownNow()

        verify(mockRumFeature)
            .sendEvent(
                mapOf(
                    "type" to "telemetry_configuration",
                    "use_proxy" to useProxy,
                    "use_local_encryption" to useLocalEncryption,
                    "batch_size" to batchSize.windowDurationMs,
                    "batch_upload_frequency" to uploadFrequency.baseStepMs,
                    "track_errors" to trackErrors
                )
            )
    }

    // region AdditionalConfig

    @Test
    fun `𝕄 apply source name 𝕎 applyAdditionalConfig(config) { with source name }`(
        @StringForgery(type = StringForgeryType.ALPHABETICAL) source: String
    ) {
        // Given
        val configuration = Configuration.Builder(
            crashReportsEnabled = true
        )
            .setAdditionalConfiguration(mapOf(Datadog.DD_SOURCE_TAG to source))
            .build()

        // When
        testedCore = DatadogCore(
            appContext.mockInstance,
            fakeCredentials,
            fakeInstanceId,
            fakeInstanceName
        ).apply {
            initialize(configuration)
        }

        // Then
        assertThat(testedCore.coreFeature.sourceName).isEqualTo(source)
    }

    @Test
    fun `𝕄 use default source name 𝕎 applyAdditionalConfig(config) { with empty source name }`(
        @StringForgery(type = StringForgeryType.WHITESPACE) source: String
    ) {
        // Given
        val configuration = Configuration.Builder(
            crashReportsEnabled = true
        )
            .setAdditionalConfiguration(mapOf(Datadog.DD_SOURCE_TAG to source))
            .build()

        // When
        testedCore = DatadogCore(
            appContext.mockInstance,
            fakeCredentials,
            fakeInstanceId,
            fakeInstanceName
        ).apply {
            initialize(configuration)
        }

        // Then
        assertThat(testedCore.coreFeature.sourceName).isEqualTo(CoreFeature.DEFAULT_SOURCE_NAME)
    }

    @Test
    fun `𝕄 use default source name 𝕎 applyAdditionalConfig(config) { with source name !string }`(
        @IntForgery source: Int
    ) {
        // Given
        val configuration = Configuration.Builder(
            crashReportsEnabled = true
        )
            .setAdditionalConfiguration(mapOf(Datadog.DD_SOURCE_TAG to source))
            .build()

        // When
        testedCore = DatadogCore(
            appContext.mockInstance,
            fakeCredentials,
            fakeInstanceId,
            fakeInstanceName
        ).apply {
            initialize(configuration)
        }

        // Then
        assertThat(testedCore.coreFeature.sourceName).isEqualTo(CoreFeature.DEFAULT_SOURCE_NAME)
    }

    @Test
    fun `𝕄 use default source name 𝕎 applyAdditionalConfig(config) { without source name }`(
        @Forgery customAttributes: CustomAttributes
    ) {
        // Given
        val configuration = Configuration.Builder(
            crashReportsEnabled = true
        )
            .setAdditionalConfiguration(customAttributes.nonNullData)
            .build()

        // When
        testedCore = DatadogCore(
            appContext.mockInstance,
            fakeCredentials,
            fakeInstanceId,
            fakeInstanceName
        ).apply {
            initialize(configuration)
        }

        // Then
        assertThat(testedCore.coreFeature.sourceName).isEqualTo(CoreFeature.DEFAULT_SOURCE_NAME)
    }

    @Test
    fun `𝕄 apply sdk version 𝕎 applyAdditionalConfig(config) { with sdk version }`(
        @StringForgery(regex = "[0-9]+(\\.[0-9]+)+") sdkVersion: String
    ) {
        // Given
        val configuration = Configuration.Builder(
            crashReportsEnabled = true
        )
            .setAdditionalConfiguration(mapOf(Datadog.DD_SDK_VERSION_TAG to sdkVersion))
            .build()

        // When
        testedCore = DatadogCore(
            appContext.mockInstance,
            fakeCredentials,
            fakeInstanceId,
            fakeInstanceName
        ).apply {
            initialize(configuration)
        }

        // Then
        assertThat(testedCore.coreFeature.sdkVersion).isEqualTo(sdkVersion)
    }

    @Test
    fun `𝕄 use default sdk version 𝕎 applyAdditionalConfig(config) { with empty sdk version }`(
        @StringForgery(type = StringForgeryType.WHITESPACE) sdkVersion: String
    ) {
        // Given
        val configuration = Configuration.Builder(
            crashReportsEnabled = true
        )
            .setAdditionalConfiguration(
                mapOf(Datadog.DD_SDK_VERSION_TAG to sdkVersion)
            )
            .build()

        // When
        testedCore = DatadogCore(
            appContext.mockInstance,
            fakeCredentials,
            fakeInstanceId,
            fakeInstanceName
        ).apply {
            initialize(configuration)
        }

        // Then
        assertThat(testedCore.coreFeature.sdkVersion).isEqualTo(CoreFeature.DEFAULT_SDK_VERSION)
    }

    @Test
    fun `𝕄 use default sdk version 𝕎 applyAdditionalConfig(config) { with sdk version !string }`(
        @Forgery sdkVersion: URL
    ) {
        // Given
        val configuration = Configuration.Builder(
            crashReportsEnabled = true
        )
            .setAdditionalConfiguration(mapOf(Datadog.DD_SDK_VERSION_TAG to sdkVersion))
            .build()

        // When
        testedCore = DatadogCore(
            appContext.mockInstance,
            fakeCredentials,
            fakeInstanceId,
            fakeInstanceName
        ).apply {
            initialize(configuration)
        }

        // Then
        assertThat(testedCore.coreFeature.sdkVersion).isEqualTo(CoreFeature.DEFAULT_SDK_VERSION)
    }

    @Test
    fun `𝕄 use default sdk version 𝕎 applyAdditionalConfig(config) { without sdk version }`(
        @Forgery customAttributes: CustomAttributes
    ) {
        // Given
        val configuration = Configuration.Builder(
            crashReportsEnabled = true
        )
            .setAdditionalConfiguration(customAttributes.nonNullData)
            .build()

        // When
        testedCore = DatadogCore(
            appContext.mockInstance,
            fakeCredentials,
            fakeInstanceId,
            fakeInstanceName
        ).apply {
            initialize(configuration)
        }

        // Then
        assertThat(testedCore.coreFeature.sdkVersion).isEqualTo(CoreFeature.DEFAULT_SDK_VERSION)
    }

    @Test
    fun `𝕄 apply app version 𝕎 applyAdditionalConfig(config) { with app version }`(
        @StringForgery appVersion: String
    ) {
        // Given
        val configuration = Configuration.Builder(
            crashReportsEnabled = true
        )
            .setAdditionalConfiguration(mapOf(Datadog.DD_APP_VERSION_TAG to appVersion))
            .build()

        // When
        testedCore = DatadogCore(
            appContext.mockInstance,
            fakeCredentials,
            fakeInstanceId,
            fakeInstanceName
        ).apply {
            initialize(configuration)
        }

        // Then
        assertThat(testedCore.coreFeature.packageVersionProvider.version).isEqualTo(appVersion)
    }

    @Test
    fun `𝕄 use default app version 𝕎 applyAdditionalConfig(config) { with empty app version }`(
        forge: Forge
    ) {
        // Given
        val configuration = Configuration.Builder(
            crashReportsEnabled = true
        )
            .setAdditionalConfiguration(
                mapOf(Datadog.DD_APP_VERSION_TAG to forge.aWhitespaceString())
            )
            .build()

        // When
        testedCore = DatadogCore(
            appContext.mockInstance,
            fakeCredentials,
            fakeInstanceId,
            fakeInstanceName
        ).apply {
            initialize(configuration)
        }

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
            crashReportsEnabled = true
        )
            .setAdditionalConfiguration(mapOf(Datadog.DD_APP_VERSION_TAG to forge.anInt()))
            .build()

        // When
        testedCore = DatadogCore(
            appContext.mockInstance,
            fakeCredentials,
            fakeInstanceId,
            fakeInstanceName
        ).apply {
            initialize(configuration)
        }

        // Then
        assertThat(testedCore.coreFeature.packageVersionProvider.version).isEqualTo(
            appContext.fakeVersionName
        )
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
