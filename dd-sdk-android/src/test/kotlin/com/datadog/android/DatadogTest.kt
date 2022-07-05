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
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.Credentials
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.persistence.DataReader
import com.datadog.android.core.internal.persistence.PersistenceStrategy
import com.datadog.android.core.model.UserInfo
import com.datadog.android.error.internal.CrashReportsFeature
import com.datadog.android.log.internal.LogsFeature
import com.datadog.android.log.internal.user.MutableUserInfoProvider
import com.datadog.android.log.model.LogEvent
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.tracing.internal.TracingFeature
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.config.CoreFeatureTestConfiguration
import com.datadog.android.utils.config.LoggerTestConfiguration
import com.datadog.android.utils.config.MainLooperTestConfiguration
import com.datadog.android.utils.extension.mockChoreographerInstance
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.webview.internal.log.WebViewLogsFeature
import com.datadog.android.webview.internal.rum.WebViewRumFeature
import com.datadog.opentracing.DDSpan
import com.datadog.tools.unit.annotations.ProhibitLeavingStaticMocksIn
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.ProhibitLeavingStaticMocksExtension
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.google.gson.JsonObject
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
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
@ProhibitLeavingStaticMocksIn(
    CoreFeature::class,
    RumFeature::class,
    LogsFeature::class,
    TracingFeature::class,
    WebViewLogsFeature::class,
    WebViewRumFeature::class,
    CrashReportsFeature::class
)
internal class DatadogTest {

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

    @Forgery
    lateinit var fakeConsent: TrackingConsent

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeConsent = forge.aValueFrom(TrackingConsent::class.java)

        whenever(appContext.mockInstance.getSystemService(Context.CONNECTIVITY_SERVICE))
            .doReturn(mockConnectivityMgr)

        // Prevent crash when initializing RumFeature
        mockChoreographerInstance()

        CoreFeature.disableKronosBackgroundSync = true
        CoreFeature.sdkVersion = CoreFeature.DEFAULT_SDK_VERSION
        CoreFeature.sourceName = CoreFeature.DEFAULT_SOURCE_NAME
    }

    @AfterEach
    fun `tear down`() {
        Datadog.setVerbosity(Int.MAX_VALUE)

        Datadog.stop()
    }

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
    fun `𝕄 warn 𝕎 initialize() + initialize()`() {
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
        Datadog.initialize(appContext.mockInstance, credentials, configuration, fakeConsent)

        // Then
        verify(logger.mockDevLogHandler).handleLog(
            AndroidLog.WARN,
            Datadog.MESSAGE_ALREADY_INITIALIZED
        )
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

    @Test
    fun `𝕄 clear data in all features 𝕎 clearAllData()`() {
        val config = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        )
            .build()
        val credentials = Credentials(fakeToken, fakeEnvName, fakeVariant, null, null)
        val dataReaders: Array<DataReader> = Array(6) { mock() }

        // When
        Datadog.initialize(appContext.mockInstance, credentials, config, TrackingConsent.GRANTED)
        LogsFeature.persistenceStrategy = mock<PersistenceStrategy<LogEvent>>().apply {
            whenever(getReader()) doReturn dataReaders[0]
        }
        CrashReportsFeature.persistenceStrategy = mock<PersistenceStrategy<LogEvent>>().apply {
            whenever(getReader()) doReturn dataReaders[1]
        }
        RumFeature.persistenceStrategy = mock<PersistenceStrategy<Any>>().apply {
            whenever(getReader()) doReturn dataReaders[2]
        }
        TracingFeature.persistenceStrategy = mock<PersistenceStrategy<DDSpan>>().apply {
            whenever(getReader()) doReturn dataReaders[3]
        }
        WebViewLogsFeature.persistenceStrategy = mock<PersistenceStrategy<JsonObject>>().apply {
            whenever(getReader()) doReturn dataReaders[4]
        }
        WebViewRumFeature.persistenceStrategy = mock<PersistenceStrategy<Any>>().apply {
            whenever(getReader()) doReturn dataReaders[5]
        }
        Datadog.clearAllData()

        // Then
        dataReaders.forEach {
            verify(it).dropAll()
        }
    }

    companion object {
        val appContext = ApplicationContextTestConfiguration(Application::class.java)
        val mainLooper = MainLooperTestConfiguration()
        val logger = LoggerTestConfiguration()
        val coreFeature = CoreFeatureTestConfiguration(appContext)

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(logger, appContext, mainLooper, coreFeature)
        }
    }
}
