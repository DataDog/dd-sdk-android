/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.Credentials
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.SdkFeature
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.config.InternalLoggerTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.Feature
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.context.UserInfo
import com.datadog.android.v2.core.DatadogCore
import com.datadog.android.v2.core.internal.HashGenerator
import com.datadog.android.v2.core.internal.Sha256HashGenerator
import com.datadog.tools.unit.annotations.ProhibitLeavingStaticMocksIn
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.ProhibitLeavingStaticMocksExtension
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
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
    ExtendWith(ProhibitLeavingStaticMocksExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
@ProhibitLeavingStaticMocksIn(Datadog::class)
@Suppress("DEPRECATION") // TODO RUMM-3103 remove deprecated references
internal class DatadogTest {

    @Mock
    lateinit var mockConnectivityMgr: ConnectivityManager

    @StringForgery(type = StringForgeryType.HEXADECIMAL)
    lateinit var fakeToken: String

    @StringForgery
    lateinit var fakeVariant: String

    @StringForgery(regex = "[a-zA-Z0-9_:./-]{0,195}[a-zA-Z0-9_./-]")
    lateinit var fakeEnvName: String

    @Forgery
    lateinit var fakeConsent: TrackingConsent

    @BeforeEach
    fun `set up`() {
        whenever(appContext.mockInstance.getSystemService(Context.CONNECTIVITY_SERVICE))
            .doReturn(mockConnectivityMgr)

        CoreFeature.disableKronosBackgroundSync = true
    }

    @AfterEach
    fun `tear down`() {
        Datadog.setVerbosity(Int.MAX_VALUE)
        Datadog.hashGenerator = Sha256HashGenerator()
        Datadog.stop()
        Datadog.registry.clear()
    }

    // region initialize

    @Test
    fun `𝕄 return sdk instance 𝕎 initialize() + getInstance()`() {
        // Given
        val credentials = Credentials(fakeToken, fakeEnvName, fakeVariant, null)
        val configuration = Configuration.Builder(
            crashReportsEnabled = true
        ).build()

        // When
        val initialized = Datadog.initialize(
            appContext.mockInstance,
            credentials,
            configuration,
            fakeConsent
        )
        val instance = Datadog.getInstance()

        // Then
        assertThat(instance).isSameAs(initialized)
    }

    @Test
    fun `𝕄 return sdk instance 𝕎 initialize(name) + getInstance(name)`(
        @StringForgery name: String
    ) {
        // Given
        val credentials = Credentials(fakeToken, fakeEnvName, fakeVariant, null)
        val configuration = Configuration.Builder(
            crashReportsEnabled = true
        ).build()

        // When
        val initialized = Datadog.initialize(
            name,
            appContext.mockInstance,
            credentials,
            configuration,
            fakeConsent
        )
        val instance = Datadog.getInstance(name)

        // Then
        assertThat(instance).isSameAs(initialized)
    }

    @Test
    fun `𝕄 warn 𝕎 initialize() + initialize()`() {
        // Given
        val credentials = Credentials(fakeToken, fakeEnvName, fakeVariant, null)
        val configuration = Configuration.Builder(
            crashReportsEnabled = true
        ).build()

        // When
        val initialized1 = Datadog.initialize(
            appContext.mockInstance,
            credentials,
            configuration,
            fakeConsent
        )
        val initialized2 = Datadog.initialize(
            appContext.mockInstance,
            credentials,
            configuration,
            fakeConsent
        )

        // Then
        verify(logger.mockInternalLogger).log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            Datadog.MESSAGE_ALREADY_INITIALIZED
        )
        assertThat(initialized2).isSameAs(initialized1)
    }

    @Test
    fun `𝕄 warn 𝕎 initialize(name) + initialize(name)`(
        @StringForgery name: String
    ) {
        // Given
        val credentials = Credentials(fakeToken, fakeEnvName, fakeVariant, null)
        val configuration = Configuration.Builder(
            crashReportsEnabled = true
        ).build()

        // When
        Datadog.initialize(name, appContext.mockInstance, credentials, configuration, fakeConsent)
        Datadog.initialize(name, appContext.mockInstance, credentials, configuration, fakeConsent)

        // Then
        verify(logger.mockInternalLogger).log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            Datadog.MESSAGE_ALREADY_INITIALIZED
        )
    }

    @Test
    fun `𝕄 create instance ID 𝕎 initialize()`(
        @Forgery fakeCredentials: Credentials,
        @Forgery fakeConfiguration: Configuration,
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) fakeHash: String
    ) {
        // Given
        val mockHashGenerator: HashGenerator = mock()
        whenever(
            mockHashGenerator.generate(
                fakeCredentials.clientToken + fakeConfiguration.coreConfig.site.siteName
            )
        ) doReturn fakeHash
        Datadog.hashGenerator = mockHashGenerator

        // When
        val instance = Datadog.initialize(
            appContext.mockInstance,
            fakeCredentials,
            fakeConfiguration,
            fakeConsent
        )

        // Then
        check(instance is DatadogCore)
        assertThat(instance.instanceId).isEqualTo(fakeHash)
    }

    @Test
    fun `𝕄 create instance ID 𝕎 initialize(name)`(
        @StringForgery name: String,
        @Forgery fakeCredentials: Credentials,
        @Forgery fakeConfiguration: Configuration,
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) fakeHash: String
    ) {
        // Given
        val mockHashGenerator: HashGenerator = mock()
        whenever(
            mockHashGenerator.generate(
                fakeCredentials.clientToken + fakeConfiguration.coreConfig.site.siteName
            )
        ) doReturn fakeHash
        Datadog.hashGenerator = mockHashGenerator

        // When
        val instance = Datadog.initialize(
            name,
            appContext.mockInstance,
            fakeCredentials,
            fakeConfiguration,
            fakeConsent
        )

        // Then
        check(instance is DatadogCore)
        assertThat(instance.instanceId).isEqualTo(fakeHash)
    }

    @Test
    fun `𝕄 set tracking consent 𝕎 initialize()`(
        @Forgery fakeCredentials: Credentials,
        @Forgery fakeConfiguration: Configuration,
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) fakeHash: String
    ) {
        // Given
        val mockHashGenerator: HashGenerator = mock()
        whenever(
            mockHashGenerator.generate(
                fakeCredentials.clientToken + fakeConfiguration.coreConfig.site.siteName
            )
        ) doReturn fakeHash
        Datadog.hashGenerator = mockHashGenerator

        // When
        val instance = Datadog.initialize(
            appContext.mockInstance,
            fakeCredentials,
            fakeConfiguration,
            fakeConsent
        )

        // Then
        check(instance is DatadogCore)
        assertThat(instance.trackingConsent).isEqualTo(fakeConsent)
    }

    @Test
    fun `𝕄 set tracking consent 𝕎 initialize(name)`(
        @StringForgery name: String,
        @Forgery fakeCredentials: Credentials,
        @Forgery fakeConfiguration: Configuration,
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) fakeHash: String
    ) {
        // Given
        val mockHashGenerator: HashGenerator = mock()
        whenever(
            mockHashGenerator.generate(
                fakeCredentials.clientToken + fakeConfiguration.coreConfig.site.siteName
            )
        ) doReturn fakeHash
        Datadog.hashGenerator = mockHashGenerator

        // When
        val instance = Datadog.initialize(
            name,
            appContext.mockInstance,
            fakeCredentials,
            fakeConfiguration,
            fakeConsent
        )

        // Then
        check(instance is DatadogCore)
        assertThat(instance.trackingConsent).isEqualTo(fakeConsent)
    }

    @Test
    fun `𝕄 warn 𝕎 initialize() {hash generator fails}`() {
        // Given
        Datadog.hashGenerator = mock()
        whenever(Datadog.hashGenerator.generate(any())) doReturn null
        val credentials = Credentials(fakeToken, fakeEnvName, fakeVariant, null)
        val configuration = Configuration.Builder(
            crashReportsEnabled = true
        ).build()

        // When
        val instance = Datadog.initialize(
            appContext.mockInstance,
            credentials,
            configuration,
            fakeConsent
        )

        // Then
        verify(logger.mockInternalLogger).log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            Datadog.CANNOT_CREATE_SDK_INSTANCE_ID_ERROR
        )
        assertThat(instance).isNull()
    }

    @Test
    fun `𝕄 warn 𝕎 initialize(name) {hash generator fails}`(
        @StringForgery name: String
    ) {
        // Given
        Datadog.hashGenerator = mock()
        whenever(Datadog.hashGenerator.generate(any())) doReturn null
        val credentials = Credentials(fakeToken, fakeEnvName, fakeVariant, null)
        val configuration = Configuration.Builder(
            crashReportsEnabled = true
        ).build()

        // When
        val instance = Datadog.initialize(
            name,
            appContext.mockInstance,
            credentials,
            configuration,
            fakeConsent
        )
        // Then
        verify(logger.mockInternalLogger).log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            Datadog.CANNOT_CREATE_SDK_INSTANCE_ID_ERROR
        )
        assertThat(instance).isNull()
    }

    // endregion

    @Test
    fun `𝕄 do nothing 𝕎 stop() without initialize`() {
        // When
        Datadog.stop()

        // Then
        verifyZeroInteractions(appContext.mockInstance)
    }

    @Test
    fun `𝕄 clears userInfoProvider 𝕎 setUserInfo() with defaults`(
        @StringForgery(type = StringForgeryType.HEXADECIMAL) id: String,
        @StringForgery name: String,
        @StringForgery(regex = "\\w+@\\w+") email: String
    ) {
        // Given
        val mockCore = mock<DatadogCore>()
        Datadog.globalSdkCore = mockCore

        // When
        Datadog.setUserInfo(id, name, email)
        Datadog.setUserInfo()

        // Then
        verify(mockCore).setUserInfo(
            UserInfo(
                id,
                name,
                email
            )
        )
        verify(mockCore).setUserInfo(
            UserInfo(
                null,
                null,
                null
            )
        )
    }

    @Test
    fun `𝕄 set additional user info 𝕎 addUserExtraInfo() is called`(
        @StringForgery(type = StringForgeryType.HEXADECIMAL) id: String,
        @StringForgery name: String,
        @StringForgery(regex = "\\w+@\\w+") email: String
    ) {
        // Given
        val mockCore = mock<DatadogCore>()
        Datadog.globalSdkCore = mockCore

        // When
        Datadog.setUserInfo(id, name, email)
        Datadog.addUserExtraInfo(
            mapOf(
                "key1" to 1,
                "key2" to "one"
            )
        )

        // Then
        verify(mockCore).setUserInfo(
            UserInfo(
                id,
                name,
                email
            )
        )
        verify(mockCore).addUserProperties(
            extraInfo = mapOf(
                "key1" to 1,
                "key2" to "one"
            )
        )
    }

    @Test
    fun `𝕄 return true 𝕎 initialize(context, credential, , consent) + isInitialized()`() {
        // Given
        val credentials = Credentials(fakeToken, fakeEnvName, fakeVariant, null)
        val configuration = Configuration.Builder(
            crashReportsEnabled = true
        ).build()

        // When
        Datadog.initialize(appContext.mockInstance, credentials, configuration, fakeConsent)
        val initialized = Datadog.isInitialized()

        // Then
        assertThat(initialized).isTrue()
    }

    @Test
    fun `𝕄 return false 𝕎 isInitialized()`() {
        // When
        val initialized = Datadog.isInitialized()

        // Then
        assertThat(initialized).isFalse()
    }

    @Test
    fun `𝕄 enable RUM debugging 𝕎 enableRumDebugging(true)`() {
        // Given
        val config = Configuration.Builder(
            crashReportsEnabled = true
        )
            .build()
        val credentials = Credentials(fakeToken, fakeEnvName, fakeVariant, null)
        Datadog.initialize(appContext.mockInstance, credentials, config, TrackingConsent.GRANTED)
        val mockRumFeature = mock<RumFeature>()
        val mockSdkFeature = mock<SdkFeature>()
        whenever(mockSdkFeature.unwrap<RumFeature>()) doReturn mockRumFeature
        (Datadog.globalSdkCore as DatadogCore).features +=
            Feature.RUM_FEATURE_NAME to mockSdkFeature

        // When
        Datadog.enableRumDebugging(true)

        // Then
        verify(mockRumFeature).enableDebugging()
    }

    @Test
    fun `𝕄 disable RUM debugging 𝕎 enableRumDebugging(false)`() {
        // Given
        val config = Configuration.Builder(
            crashReportsEnabled = true
        )
            .build()
        val credentials = Credentials(fakeToken, fakeEnvName, fakeVariant, null)
        Datadog.initialize(appContext.mockInstance, credentials, config, TrackingConsent.GRANTED)
        val mockRumFeature = mock<RumFeature>()
        val mockSdkFeature = mock<SdkFeature>()
        whenever(mockSdkFeature.unwrap<RumFeature>()) doReturn mockRumFeature
        (Datadog.globalSdkCore as DatadogCore).features +=
            Feature.RUM_FEATURE_NAME to mockSdkFeature

        // When
        Datadog.enableRumDebugging(false)

        // Then
        verify(mockRumFeature).disableDebugging()
    }

    @Test
    fun `𝕄 clear data in all features 𝕎 clearAllData()`() {
        // Given
        val mockCore = mock<DatadogCore>()
        Datadog.globalSdkCore = mockCore

        // When
        Datadog.clearAllData()

        // Then
        verify(mockCore).clearAllData()
    }

    companion object {
        val appContext = ApplicationContextTestConfiguration(Application::class.java)
        val logger = InternalLoggerTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(logger, appContext)
        }
    }
}
