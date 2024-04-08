/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core

import android.app.Application
import android.content.pm.ApplicationInfo
import android.util.Log
import com.datadog.android.Datadog
import com.datadog.android.api.feature.Feature
import com.datadog.android.core.configuration.BatchProcessingLevel
import com.datadog.android.core.configuration.BatchSize
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.UploadFrequency
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.SdkFeature
import com.datadog.android.core.thread.FlushableExecutorService
import com.datadog.android.error.internal.CrashReportsFeature
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.security.Encryption
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.forge.CustomAttributes
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
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
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
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

    @Mock
    lateinit var mockPersistenceExecutorService: FlushableExecutorService

    @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL)
    lateinit var fakeInstanceId: String

    @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL)
    lateinit var fakeInstanceName: String

    @Forgery
    lateinit var fakeConfiguration: Configuration

    @BeforeEach
    fun `set up`() {
        CoreFeature.disableKronosBackgroundSync = true

        whenever(mockPersistenceExecutorService.execute(any())) doAnswer {
            it.getArgument<Runnable>(0).run()
        }
        whenever(mockPersistenceExecutorService.submit(any())) doAnswer {
            it.getArgument<Runnable>(0).run()
            mock()
        }
    }

    @AfterEach
    fun `tear down`() {
        if (this::testedCore.isInitialized) {
            testedCore.stop()
        }
    }

    @RepeatedTest(4)
    fun `M initialize requested features W initialize()`(
        @BoolForgery crashReportsEnabled: Boolean
    ) {
        // When
        testedCore = DatadogCore(
            appContext.mockInstance,
            fakeInstanceId,
            fakeInstanceName,
            executorServiceFactory = { _, _ -> mockPersistenceExecutorService }
        ).apply {
            initialize(fakeConfiguration.copy(crashReportsEnabled = crashReportsEnabled))
        }

        // Then
        assertThat(testedCore.coreFeature.initialized.get()).isTrue
        assertThat(testedCore.isActive).isTrue
        assertThat(testedCore.contextProvider).isNotNull

        assertThat(testedCore.getFeature(CrashReportsFeature.CRASH_FEATURE_NAME)).let {
            if (crashReportsEnabled) {
                it.isNotNull
            } else {
                it.isNull()
            }
        }
    }

    @Test
    fun `M throw an error W initialize() {envName not valid, isDebug=false}`(
        @IntForgery fakeFlags: Int,
        @StringForgery(regex = "[\\$%\\*@][a-zA-Z0-9_:./-]{0,200}") invalidEnvName: String
    ) {
        // Given
        appContext.fakeAppInfo.flags = fakeFlags and ApplicationInfo.FLAG_DEBUGGABLE.inv()

        // When
        val exception = assertThrows<IllegalArgumentException> {
            // When
            testedCore = DatadogCore(
                appContext.mockInstance,
                fakeInstanceId,
                fakeInstanceName
            ).apply {
                initialize(fakeConfiguration.copy(env = invalidEnvName))
            }
        }

        // Then
        assertThat(exception)
            .hasMessage(DatadogCore.MESSAGE_ENV_NAME_NOT_VALID)
    }

    @Test
    fun `M throw an error W initialize() {envName not valid, isDebug=true}`(
        @IntForgery fakeFlags: Int,
        @StringForgery(regex = "[\\$%\\*@][a-zA-Z0-9_:./-]{0,200}") invalidEnvName: String
    ) {
        // Given
        appContext.fakeAppInfo.flags = fakeFlags or ApplicationInfo.FLAG_DEBUGGABLE

        // When
        val exception = assertThrows<IllegalArgumentException> {
            testedCore = DatadogCore(
                appContext.mockInstance,
                fakeInstanceId,
                fakeInstanceName
            )
                .apply {
                    initialize(fakeConfiguration.copy(env = invalidEnvName))
                }
        }

        // Then
        assertThat(exception)
            .hasMessage(DatadogCore.MESSAGE_ENV_NAME_NOT_VALID)
    }

    @Test
    fun `M initialize the ConsentProvider with PENDING W initializing()`() {
        // When
        testedCore = DatadogCore(
            appContext.mockInstance,
            fakeInstanceId,
            fakeInstanceName,
            executorServiceFactory = { _, _ -> mockPersistenceExecutorService }
        ).apply {
            initialize(fakeConfiguration)
        }

        // Then
        assertThat(testedCore.coreFeature.trackingConsentProvider.getConsent())
            .isEqualTo(TrackingConsent.PENDING)
    }

    @Test
    fun `M not set lib verbosity W initializing() {dev mode when debug, debug=false}`(
        @IntForgery fakeFlags: Int
    ) {
        // Given
        Datadog.setVerbosity(Int.MAX_VALUE)
        appContext.fakeAppInfo.flags = fakeFlags and ApplicationInfo.FLAG_DEBUGGABLE.inv()

        // When
        testedCore = DatadogCore(
            appContext.mockInstance,
            fakeInstanceId,
            fakeInstanceName,
            executorServiceFactory = { _, _ -> mockPersistenceExecutorService }
        ).apply {
            initialize(
                fakeConfiguration.copy(
                    coreConfig = fakeConfiguration.coreConfig.copy(
                        enableDeveloperModeWhenDebuggable = true
                    )
                )
            )
        }

        // Then
        assertThat(Datadog.libraryVerbosity)
            .isEqualTo(Int.MAX_VALUE)
    }

    @Test
    fun `M set lib verbosity W initializing() {dev mode when debug, debug=true}`(
        @IntForgery fakeFlags: Int
    ) {
        // Given
        appContext.fakeAppInfo.flags = fakeFlags or ApplicationInfo.FLAG_DEBUGGABLE

        // When
        testedCore = DatadogCore(
            appContext.mockInstance,
            fakeInstanceId,
            fakeInstanceName,
            executorServiceFactory = { _, _ -> mockPersistenceExecutorService }
        ).apply {
            initialize(
                fakeConfiguration.copy(
                    coreConfig = fakeConfiguration.coreConfig.copy(
                        enableDeveloperModeWhenDebuggable = true
                    )
                )
            )
        }

        // Then
        assertThat(Datadog.libraryVerbosity)
            .isEqualTo(Log.VERBOSE)
    }

    @Test
    fun `M not set isDeveloperModeEnabled W initializing() {dev mode when debug, debug=false}`(
        @IntForgery fakeFlags: Int
    ) {
        // Given
        Datadog.setVerbosity(Int.MAX_VALUE)
        appContext.fakeAppInfo.flags = fakeFlags and ApplicationInfo.FLAG_DEBUGGABLE.inv()

        // When
        testedCore = DatadogCore(
            appContext.mockInstance,
            fakeInstanceId,
            fakeInstanceName,
            executorServiceFactory = { _, _ -> mockPersistenceExecutorService }
        ).apply {
            initialize(
                fakeConfiguration.copy(
                    coreConfig = fakeConfiguration.coreConfig.copy(
                        enableDeveloperModeWhenDebuggable = true
                    )
                )
            )
        }

        // Then
        assertThat(testedCore.isDeveloperModeEnabled)
            .isFalse
    }

    @Test
    fun `M set isDeveloperModeEnabled W initializing() {dev mode when debug, debug=true}`(
        @IntForgery fakeFlags: Int
    ) {
        // Given
        appContext.fakeAppInfo.flags = fakeFlags or ApplicationInfo.FLAG_DEBUGGABLE

        // When
        testedCore = DatadogCore(
            appContext.mockInstance,
            fakeInstanceId,
            fakeInstanceName,
            executorServiceFactory = { _, _ -> mockPersistenceExecutorService }
        ).apply {
            initialize(
                fakeConfiguration.copy(
                    coreConfig = fakeConfiguration.coreConfig.copy(
                        enableDeveloperModeWhenDebuggable = true
                    )
                )
            )
        }

        // Then
        assertThat(testedCore.isDeveloperModeEnabled)
            .isTrue
    }

    @Test
    fun `M submit core config telemetry W initializing()`(
        forge: Forge
    ) {
        // Given
        val trackErrors = forge.aBool()
        val useProxy = forge.aBool()
        val useLocalEncryption = forge.aBool()
        val usePersistenceStrategyFactory = forge.aBool()
        val batchSize = forge.aValueFrom(BatchSize::class.java)
        val uploadFrequency = forge.aValueFrom(UploadFrequency::class.java)
        val batchProcessingLevel = forge.aValueFrom(BatchProcessingLevel::class.java)

        val configuration = Configuration.Builder(
            clientToken = fakeConfiguration.clientToken,
            env = fakeConfiguration.env,
            variant = fakeConfiguration.variant,
            service = fakeConfiguration.service
        ).apply {
            if (useProxy) {
                setProxy(mock(), forge.aNullable { mock() })
            }
            if (useLocalEncryption) {
                val mockEncryption = mock<Encryption>()
                whenever(mockEncryption.encrypt(any())) doAnswer { it.getArgument<ByteArray>(0) }
                whenever(mockEncryption.decrypt(any())) doAnswer { it.getArgument<ByteArray>(0) }
                setEncryption(mockEncryption)
            }
            if (usePersistenceStrategyFactory) {
                setPersistenceStrategyFactory(mock())
            }
        }
            .setBatchSize(batchSize)
            .setUploadFrequency(uploadFrequency)
            .setBatchProcessingLevel(batchProcessingLevel)
            .setCrashReportsEnabled(trackErrors)
            .build()

        // When
        testedCore = DatadogCore(
            appContext.mockInstance,
            fakeInstanceId,
            fakeInstanceName,
            executorServiceFactory = { _, _ -> mockPersistenceExecutorService }
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
                    "track_errors" to trackErrors,
                    "batch_processing_level" to batchProcessingLevel.maxBatchesPerUploadJob,
                    "use_persistence_strategy_factory" to usePersistenceStrategyFactory
                )
            )
    }

    // region AdditionalConfig

    @Test
    fun `M apply source name W applyAdditionalConfig(config) { with source name }`(
        @StringForgery(type = StringForgeryType.ALPHABETICAL) source: String
    ) {
        // When
        testedCore = DatadogCore(
            appContext.mockInstance,
            fakeInstanceId,
            fakeInstanceName,
            executorServiceFactory = { _, _ -> mockPersistenceExecutorService }
        ).apply {
            initialize(fakeConfiguration.copy(additionalConfig = mapOf(Datadog.DD_SOURCE_TAG to source)))
        }

        // Then
        assertThat(testedCore.coreFeature.sourceName).isEqualTo(source)
    }

    @Test
    fun `M use default source name W applyAdditionalConfig(config) { with empty source name }`(
        @StringForgery(type = StringForgeryType.WHITESPACE) source: String
    ) {
        // When
        testedCore = DatadogCore(
            appContext.mockInstance,
            fakeInstanceId,
            fakeInstanceName,
            executorServiceFactory = { _, _ -> mockPersistenceExecutorService }
        ).apply {
            initialize(
                fakeConfiguration.copy(additionalConfig = mapOf(Datadog.DD_SOURCE_TAG to source))
            )
        }

        // Then
        assertThat(testedCore.coreFeature.sourceName).isEqualTo(CoreFeature.DEFAULT_SOURCE_NAME)
    }

    @Test
    fun `M use default source name W applyAdditionalConfig(config) { with source name !string }`(
        @IntForgery source: Int
    ) {
        // When
        testedCore = DatadogCore(
            appContext.mockInstance,
            fakeInstanceId,
            fakeInstanceName,
            executorServiceFactory = { _, _ -> mockPersistenceExecutorService }
        ).apply {
            initialize(
                fakeConfiguration.copy(additionalConfig = mapOf(Datadog.DD_SOURCE_TAG to source))
            )
        }

        // Then
        assertThat(testedCore.coreFeature.sourceName).isEqualTo(CoreFeature.DEFAULT_SOURCE_NAME)
    }

    @Test
    fun `M use default source name W applyAdditionalConfig(config) { without source name }`(
        @Forgery customAttributes: CustomAttributes
    ) {
        // When
        testedCore = DatadogCore(
            appContext.mockInstance,
            fakeInstanceId,
            fakeInstanceName,
            executorServiceFactory = { _, _ -> mockPersistenceExecutorService }
        ).apply {
            initialize(fakeConfiguration.copy(additionalConfig = customAttributes.nonNullData))
        }

        // Then
        assertThat(testedCore.coreFeature.sourceName).isEqualTo(CoreFeature.DEFAULT_SOURCE_NAME)
    }

    @Test
    fun `M apply sdk version W applyAdditionalConfig(config) { with sdk version }`(
        @StringForgery(regex = "[0-9]+(\\.[0-9]+)+") sdkVersion: String
    ) {
        // When
        testedCore = DatadogCore(
            appContext.mockInstance,
            fakeInstanceId,
            fakeInstanceName,
            executorServiceFactory = { _, _ -> mockPersistenceExecutorService }
        ).apply {
            initialize(
                fakeConfiguration.copy(
                    additionalConfig = mapOf(Datadog.DD_SDK_VERSION_TAG to sdkVersion)
                )
            )
        }

        // Then
        assertThat(testedCore.coreFeature.sdkVersion).isEqualTo(sdkVersion)
    }

    @Test
    fun `M use default sdk version W applyAdditionalConfig(config) { with empty sdk version }`(
        @StringForgery(type = StringForgeryType.WHITESPACE) sdkVersion: String
    ) {
        // When
        testedCore = DatadogCore(
            appContext.mockInstance,
            fakeInstanceId,
            fakeInstanceName,
            executorServiceFactory = { _, _ -> mockPersistenceExecutorService }
        ).apply {
            initialize(
                fakeConfiguration.copy(
                    additionalConfig = mapOf(Datadog.DD_SDK_VERSION_TAG to sdkVersion)
                )
            )
        }

        // Then
        assertThat(testedCore.coreFeature.sdkVersion).isEqualTo(CoreFeature.DEFAULT_SDK_VERSION)
    }

    @Test
    fun `M use default sdk version W applyAdditionalConfig(config) { with sdk version !string }`(
        @Forgery sdkVersion: URL
    ) {
        // When
        testedCore = DatadogCore(
            appContext.mockInstance,
            fakeInstanceId,
            fakeInstanceName,
            executorServiceFactory = { _, _ -> mockPersistenceExecutorService }
        ).apply {
            initialize(
                fakeConfiguration.copy(
                    additionalConfig = mapOf(Datadog.DD_SDK_VERSION_TAG to sdkVersion)
                )
            )
        }

        // Then
        assertThat(testedCore.coreFeature.sdkVersion).isEqualTo(CoreFeature.DEFAULT_SDK_VERSION)
    }

    @Test
    fun `M use default sdk version W applyAdditionalConfig(config) { without sdk version }`(
        @Forgery customAttributes: CustomAttributes
    ) {
        // When
        testedCore = DatadogCore(
            appContext.mockInstance,
            fakeInstanceId,
            fakeInstanceName,
            executorServiceFactory = { _, _ -> mockPersistenceExecutorService }
        ).apply {
            initialize(fakeConfiguration.copy(additionalConfig = customAttributes.nonNullData))
        }

        // Then
        assertThat(testedCore.coreFeature.sdkVersion).isEqualTo(CoreFeature.DEFAULT_SDK_VERSION)
    }

    @Test
    fun `M apply app version W applyAdditionalConfig(config) { with app version }`(
        @StringForgery appVersion: String
    ) {
        // When
        testedCore = DatadogCore(
            appContext.mockInstance,
            fakeInstanceId,
            fakeInstanceName,
            executorServiceFactory = { _, _ -> mockPersistenceExecutorService }
        ).apply {
            initialize(
                fakeConfiguration.copy(
                    additionalConfig = mapOf(Datadog.DD_APP_VERSION_TAG to appVersion)
                )
            )
        }

        // Then
        assertThat(testedCore.coreFeature.packageVersionProvider.version).isEqualTo(appVersion)
    }

    @Test
    fun `M use default app version W applyAdditionalConfig(config) { with empty app version }`(
        forge: Forge
    ) {
        // When
        testedCore = DatadogCore(
            appContext.mockInstance,
            fakeInstanceId,
            fakeInstanceName,
            executorServiceFactory = { _, _ -> mockPersistenceExecutorService }
        ).apply {
            initialize(
                fakeConfiguration.copy(
                    additionalConfig = mapOf(Datadog.DD_APP_VERSION_TAG to forge.aWhitespaceString())
                )
            )
        }

        // Then
        assertThat(testedCore.coreFeature.packageVersionProvider.version).isEqualTo(
            appContext.fakeVersionName
        )
    }

    @Test
    fun `M use default app version W applyAdditionalConfig(config) { with app version !string }`(
        forge: Forge
    ) {
        // When
        testedCore = DatadogCore(
            appContext.mockInstance,
            fakeInstanceId,
            fakeInstanceName,
            executorServiceFactory = { _, _ -> mockPersistenceExecutorService }
        ).apply {
            initialize(
                fakeConfiguration.copy(
                    additionalConfig = mapOf(Datadog.DD_APP_VERSION_TAG to forge.anInt())
                )
            )
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
