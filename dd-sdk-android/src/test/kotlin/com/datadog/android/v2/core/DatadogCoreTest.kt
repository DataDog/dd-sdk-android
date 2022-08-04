/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.core

import android.app.Application
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.Credentials
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.core.model.UserInfo
import com.datadog.android.error.internal.CrashReportsFeature
import com.datadog.android.log.internal.LogsFeature
import com.datadog.android.log.internal.user.MutableUserInfoProvider
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.sessionreplay.internal.SessionReplayFeature
import com.datadog.android.tracing.internal.TracingFeature
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.config.LoggerTestConfiguration
import com.datadog.android.utils.config.MainLooperTestConfiguration
import com.datadog.android.utils.extension.mockChoreographerInstance
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.core.internal.ContextProvider
import com.datadog.android.webview.internal.log.WebViewLogsFeature
import com.datadog.android.webview.internal.rum.WebViewRumFeature
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.ProhibitLeavingStaticMocksExtension
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.annotation.AdvancedForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.MapForgery
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

/**
 * This region groups all test about DatadogCore instance (except Initialization).
 */
@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ProhibitLeavingStaticMocksExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogCoreTest {

    // TODO RUMM-2206 handle all commented lines on this class

    lateinit var testedCore: DatadogCore

    @Forgery
    lateinit var fakeCredentials: Credentials

    @Forgery
    lateinit var fakeConfiguration: Configuration

    @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL)
    lateinit var fakeInstanceId: String

    @BeforeEach
    fun `set up`() {
        // Prevent crash when initializing RumFeature
        mockChoreographerInstance()

        testedCore = DatadogCore(
            appContext.mockInstance,
            fakeCredentials,
            fakeConfiguration,
            fakeInstanceId
        )
    }

    @AfterEach
    fun `tear down`() {
        testedCore.stop()
    }

    @ParameterizedTest
    @EnumSource(TrackingConsent::class)
    fun `M update the ConsentProvider W setConsent`(fakeConsent: TrackingConsent) {
        // Given
        @Suppress("UNUSED_VARIABLE")
        val mockedConsentProvider: ConsentProvider = mock()
        // CoreFeature.trackingConsentProvider = mockedConsentProvider

        // When
        testedCore.setTrackingConsent(fakeConsent)

        // Then
        // verify(CoreFeature.trackingConsentProvider).setConsent(fakeConsent)
    }

    @Test
    fun `𝕄 update userInfoProvider 𝕎 setUserInfo()`(
        @Forgery userInfo: UserInfo
    ) {
        // Given
        val mockUserInfoProvider = mock<MutableUserInfoProvider>()
        testedCore.coreFeature.userInfoProvider = mockUserInfoProvider

        // When
        testedCore.setUserInfo(userInfo)

        // Then
        verify(mockUserInfoProvider).setUserInfo(userInfo)
    }

    @Test
    fun `𝕄 set and get lib verbosity 𝕎 setVerbosity() + getVerbosity()`(
        @IntForgery level: Int
    ) {
        // When
        testedCore.setVerbosity(level)
        val result = testedCore.getVerbosity()

        // Then
        assertThat(result).isEqualTo(level)
    }

    @Test
    fun `𝕄 set additional user info 𝕎 addUserProperties() is called`(
        @StringForgery(type = StringForgeryType.HEXADECIMAL) id: String,
        @StringForgery name: String,
        @StringForgery(regex = "\\w+@\\w+") email: String
    ) {
        // Given
        testedCore.coreFeature = mock()
        val mockUserInfoProvider = mock<MutableUserInfoProvider>()
        whenever(testedCore.coreFeature.userInfoProvider) doReturn mockUserInfoProvider

        // When
        testedCore.setUserInfo(UserInfo(id, name, email))
        testedCore.addUserProperties(
            mapOf(
                "key1" to 1,
                "key2" to "one"
            )
        )

        // Then
        verify(mockUserInfoProvider).setUserInfo(
            UserInfo(
                id,
                name,
                email
            )
        )
        verify(mockUserInfoProvider).addUserProperties(
            properties = mapOf(
                "key1" to 1,
                "key2" to "one"
            )
        )
    }

    @Test
    fun `𝕄 set feature context 𝕎 setFeatureContext() is called`(
        @StringForgery feature: String,
        @MapForgery(
            key = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHABETICAL)]),
            value = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHABETICAL)])
        ) context: Map<String, String>
    ) {
        // Given
        val mockContextProvider = mock<ContextProvider>()
        testedCore.coreFeature.contextProvider = mockContextProvider

        // When
        testedCore.setFeatureContext(feature, context)

        // Then
        verify(mockContextProvider).setFeatureContext(feature, context)
    }

    @Test
    fun `𝕄 clear data in all features 𝕎 clearAllData()`() {
        // Given
        testedCore.rumFeature = mock()
        testedCore.tracingFeature = mock()
        testedCore.logsFeature = mock()
        testedCore.webViewLogsFeature = mock()
        testedCore.webViewRumFeature = mock()
        testedCore.crashReportsFeature = mock()
        testedCore.sessionReplayFeature = mock()

        // When
        testedCore.clearAllData()

        // Then
        verify(testedCore.rumFeature)!!.clearAllData()
        verify(testedCore.tracingFeature)!!.clearAllData()
        verify(testedCore.logsFeature)!!.clearAllData()
        verify(testedCore.webViewLogsFeature)!!.clearAllData()
        verify(testedCore.webViewRumFeature)!!.clearAllData()
        verify(testedCore.crashReportsFeature)!!.clearAllData()
        verify(testedCore.sessionReplayFeature)!!.clearAllData()
    }

    @Test
    fun `𝕄 flush data in all features 𝕎 flushStoredData()`() {
        // Given
        testedCore.coreFeature = mock()
        testedCore.rumFeature = mock()
        testedCore.tracingFeature = mock()
        testedCore.logsFeature = mock()
        testedCore.webViewLogsFeature = mock()
        testedCore.webViewRumFeature = mock()
        testedCore.crashReportsFeature = mock()
        testedCore.sessionReplayFeature = mock()

        // When
        testedCore.flushStoredData()

        // Then
        verify(testedCore.coreFeature).drainAndShutdownExecutors()
        verify(testedCore.rumFeature)!!.flushStoredData()
        verify(testedCore.tracingFeature)!!.flushStoredData()
        verify(testedCore.logsFeature)!!.flushStoredData()
        verify(testedCore.webViewLogsFeature)!!.flushStoredData()
        verify(testedCore.webViewRumFeature)!!.flushStoredData()
        verify(testedCore.crashReportsFeature)!!.flushStoredData()
        verify(testedCore.sessionReplayFeature)!!.flushStoredData()
    }

    @Test
    fun `𝕄 stop all features 𝕎 stop()`() {
        // Given
        val mockCoreFeature = mock<CoreFeature>()
        testedCore.coreFeature = mockCoreFeature
        val mockRumFeature = mock<RumFeature>()
        testedCore.rumFeature = mockRumFeature
        val mockTracingFeature = mock<TracingFeature>()
        testedCore.tracingFeature = mockTracingFeature
        val mockLogsFeature = mock<LogsFeature>()
        testedCore.logsFeature = mockLogsFeature
        val mockWebViewLogsFeature = mock<WebViewLogsFeature>()
        testedCore.webViewLogsFeature = mockWebViewLogsFeature
        val mockWebViewRumFeature = mock<WebViewRumFeature>()
        testedCore.webViewRumFeature = mockWebViewRumFeature
        val mockCrashReportsFeature = mock<CrashReportsFeature>()
        testedCore.crashReportsFeature = mockCrashReportsFeature
        val mockSessionReplayFeature = mock<SessionReplayFeature>()
        testedCore.sessionReplayFeature = mockSessionReplayFeature

        // When
        testedCore.stop()

        // Then
        verify(mockCoreFeature).stop()
        verify(mockRumFeature).stop()
        verify(mockTracingFeature).stop()
        verify(mockLogsFeature).stop()
        verify(mockWebViewRumFeature).stop()
        verify(mockWebViewLogsFeature).stop()
        verify(mockCrashReportsFeature).stop()
        verify(mockSessionReplayFeature).stop()

        assertThat(testedCore.rumFeature).isNull()
        assertThat(testedCore.tracingFeature).isNull()
        assertThat(testedCore.logsFeature).isNull()
        assertThat(testedCore.webViewLogsFeature).isNull()
        assertThat(testedCore.webViewRumFeature).isNull()
        assertThat(testedCore.crashReportsFeature).isNull()
        assertThat(testedCore.sessionReplayFeature).isNull()
        assertThat(testedCore.contextProvider).isNull()
    }

    companion object {
        val appContext = ApplicationContextTestConfiguration(Application::class.java)
        val mainLooper = MainLooperTestConfiguration()
        val logger = LoggerTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(logger, appContext, mainLooper)
        }
    }
}
