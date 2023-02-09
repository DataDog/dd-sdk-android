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
import com.datadog.android.core.internal.SdkFeature
import com.datadog.android.core.internal.net.DefaultFirstPartyHostHeaderTypeResolver
import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.core.internal.time.NoOpTimeProvider
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.core.internal.user.MutableUserInfoProvider
import com.datadog.android.error.internal.CrashReportsFeature
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.tracing.internal.TracingFeature
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.config.InternalLoggerTestConfiguration
import com.datadog.android.utils.config.MainLooperTestConfiguration
import com.datadog.android.utils.extension.mockChoreographerInstance
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.Feature
import com.datadog.android.v2.api.FeatureEventReceiver
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.context.TimeInfo
import com.datadog.android.v2.api.context.UserInfo
import com.datadog.android.v2.core.internal.ContextProvider
import com.datadog.android.webview.internal.log.WebViewLogsFeature
import com.datadog.android.webview.internal.rum.WebViewRumFeature
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.AdvancedForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
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
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * This region groups all test about DatadogCore instance (except Initialization).
 */
@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogCoreTest {

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
        val mockConsentProvider = mock<ConsentProvider>()
        testedCore.coreFeature.trackingConsentProvider = mockConsentProvider

        // When
        testedCore.setTrackingConsent(fakeConsent)

        // Then
        verify(mockConsentProvider).setConsent(fakeConsent)
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
        whenever(testedCore.coreFeature.initialized).thenReturn(AtomicBoolean())
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
    fun `𝕄 update feature context 𝕎 updateFeatureContext()`(
        @StringForgery feature: String,
        @MapForgery(
            key = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHABETICAL)]),
            value = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHABETICAL)])
        ) context: Map<String, String>
    ) {
        // Given
        val mockContextProvider = mock<ContextProvider>()
        testedCore.features[feature] = mock()
        testedCore.coreFeature.contextProvider = mockContextProvider

        // When
        testedCore.updateFeatureContext(feature) {
            it.putAll(context)
        }

        // Then
        verify(mockContextProvider).setFeatureContext(feature, context)
    }

    @Test
    fun `𝕄 do nothing 𝕎 updateFeatureContext() { feature is not registered}`(
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
        testedCore.updateFeatureContext(feature) {
            it.putAll(context)
        }

        // Then
        verifyZeroInteractions(mockContextProvider)
    }

    @Test
    fun `𝕄 set event receiver 𝕎 setEventReceiver()`(
        @StringForgery feature: String
    ) {
        // Given
        val mockFeature = mock<SdkFeature>()
        val mockEventReceiverRef = mock<AtomicReference<FeatureEventReceiver>>()
        whenever(mockFeature.eventReceiver) doReturn mockEventReceiverRef
        testedCore.features[feature] = mockFeature

        val fakeReceiver = FeatureEventReceiver { }

        // When
        testedCore.setEventReceiver(feature, fakeReceiver)

        // Then
        verify(mockEventReceiverRef).set(fakeReceiver)
    }

    @Test
    fun `𝕄 notify no feature registered 𝕎 setEventReceiver() { feature is not registered }`(
        @StringForgery feature: String
    ) {
        // Given
        val fakeReceiver = FeatureEventReceiver { }

        // When
        testedCore.setEventReceiver(feature, fakeReceiver)

        // Then
        verify(logger.mockInternalLogger).log(
            InternalLogger.Level.INFO,
            InternalLogger.Target.USER,
            DatadogCore.MISSING_FEATURE_FOR_EVENT_RECEIVER.format(Locale.US, feature)
        )
    }

    @Test
    fun `𝕄 notify receiver exists 𝕎 setEventReceiver() { feature already has receiver }`(
        @StringForgery feature: String
    ) {
        // Given
        val mockFeature = mock<SdkFeature>()
        val mockEventReceiverRef = mock<AtomicReference<FeatureEventReceiver>>()
        whenever(mockFeature.eventReceiver) doReturn mockEventReceiverRef
        whenever(mockEventReceiverRef.get()) doReturn mock()
        testedCore.features[feature] = mockFeature

        val fakeReceiver = FeatureEventReceiver { }

        // When
        testedCore.setEventReceiver(feature, fakeReceiver)

        // Then
        verify(logger.mockInternalLogger).log(
            InternalLogger.Level.INFO,
            InternalLogger.Target.USER,
            DatadogCore.EVENT_RECEIVER_ALREADY_EXISTS.format(Locale.US, feature)
        )
    }

    @Test
    fun `𝕄 remove receiver 𝕎 removeEventReceiver()`(
        @StringForgery feature: String
    ) {
        // Given
        val mockFeature = mock<SdkFeature>()
        val mockEventReceiverRef = mock<AtomicReference<FeatureEventReceiver>>()
        whenever(mockFeature.eventReceiver) doReturn mockEventReceiverRef
        whenever(mockEventReceiverRef.get()) doReturn mock()
        testedCore.features[feature] = mockFeature

        // When
        testedCore.removeEventReceiver(feature)

        // Then
        verify(mockEventReceiverRef).set(null)
    }

    @Test
    fun `𝕄 provide time info 𝕎 time()`(
        @LongForgery(min = 10001L) fakeDeviceTimestamp: Long,
        @LongForgery(min = -10000L, max = 10000L) fakeServerTimeOffsetMs: Long
    ) {
        // Given
        testedCore.coreFeature = mock()
        whenever(testedCore.coreFeature.initialized).thenReturn(AtomicBoolean())
        val mockTimeProvider = mock<TimeProvider>()
        whenever(testedCore.coreFeature.timeProvider) doReturn mockTimeProvider
        whenever(mockTimeProvider.getServerOffsetNanos()) doReturn TimeUnit.MILLISECONDS.toNanos(
            fakeServerTimeOffsetMs
        )
        whenever(mockTimeProvider.getServerOffsetMillis()) doReturn fakeServerTimeOffsetMs
        whenever(mockTimeProvider.getDeviceTimestamp()) doReturn fakeDeviceTimestamp
        whenever(
            mockTimeProvider.getServerTimestamp()
        ) doReturn fakeDeviceTimestamp + fakeServerTimeOffsetMs

        // When
        val time = testedCore.time

        // Then
        assertThat(time).isEqualTo(
            TimeInfo(
                deviceTimeNs = TimeUnit.MILLISECONDS.toNanos(fakeDeviceTimestamp),
                serverTimeNs = TimeUnit.MILLISECONDS.toNanos(
                    fakeDeviceTimestamp + fakeServerTimeOffsetMs
                ),
                serverTimeOffsetMs = fakeServerTimeOffsetMs,
                serverTimeOffsetNs = TimeUnit.MILLISECONDS.toNanos(fakeServerTimeOffsetMs)
            )
        )
    }

    @Test
    fun `𝕄 provide time info without correction 𝕎 time() {NoOpTimeProvider}`() {
        // Given
        testedCore.coreFeature = mock()
        whenever(testedCore.coreFeature.initialized).thenReturn(AtomicBoolean())
        whenever(testedCore.coreFeature.timeProvider) doReturn NoOpTimeProvider()

        // When
        val time = testedCore.time

        // Then
        assertThat(time.deviceTimeNs).isEqualTo(time.serverTimeNs)
        assertThat(time.serverTimeOffsetMs).isEqualTo(0)
        assertThat(time.serverTimeOffsetNs).isEqualTo(0)
    }

    @Test
    fun `𝕄 provide service 𝕎 service()`(
        @StringForgery fakeService: String
    ) {
        // Given
        testedCore.coreFeature = mock()
        whenever(testedCore.coreFeature.serviceName) doReturn fakeService

        // When
        val service = testedCore.service

        // Then
        assertThat(service).isEqualTo(fakeService)
    }

    @Test
    fun `𝕄 provide first party host resolver 𝕎 firstPartyHostResolver()`() {
        // Given
        testedCore.coreFeature = mock()
        val mockResolver = mock<DefaultFirstPartyHostHeaderTypeResolver>()
        whenever(testedCore.coreFeature.firstPartyHostHeaderTypeResolver) doReturn mockResolver

        // When
        val resolver = testedCore.firstPartyHostResolver

        // Then
        assertThat(resolver).isSameAs(mockResolver)
    }

    @Test
    fun `𝕄 clear data in all features 𝕎 clearAllData()`(
        forge: Forge
    ) {
        // Given
        // there are some non-mock features there after initialization
        testedCore.features.clear()
        testedCore.features.putAll(
            forge.aMap {
                anAlphaNumericalString() to mock()
            }
        )

        // When
        testedCore.clearAllData()

        // Then
        testedCore.features.forEach {
            verify(it.value).clearAllData()
        }
    }

    @Test
    fun `𝕄 flush data in all features 𝕎 flushStoredData()`(
        forge: Forge
    ) {
        // Given
        // there are some non-mock features there after initialization
        testedCore.features.clear()

        testedCore.features.putAll(
            forge.aMap {
                anAlphaNumericalString() to mock()
            }
        )

        // When
        testedCore.flushStoredData()

        // Then
        testedCore.features.forEach {
            verify(it.value).flushStoredData()
        }
    }

    @Test
    fun `𝕄 stop all features 𝕎 stop()`() {
        // Given
        val mockCoreFeature = mock<CoreFeature>()
        whenever(mockCoreFeature.initialized).thenReturn(mock())
        testedCore.coreFeature = mockCoreFeature
        val mockRumFeature = mock<RumFeature>()
        testedCore.rumFeature = mockRumFeature
        val mockTracingFeature = mock<TracingFeature>()
        testedCore.tracingFeature = mockTracingFeature
        val mockWebViewLogsFeature = mock<WebViewLogsFeature>()
        testedCore.webViewLogsFeature = mockWebViewLogsFeature
        val mockWebViewRumFeature = mock<WebViewRumFeature>()
        testedCore.webViewRumFeature = mockWebViewRumFeature
        val mockCrashReportsFeature = mock<CrashReportsFeature>()
        testedCore.crashReportsFeature = mockCrashReportsFeature

        val sdkFeatureMocks = listOf(
            Feature.RUM_FEATURE_NAME,
            Feature.TRACING_FEATURE_NAME,
            Feature.LOGS_FEATURE_NAME,
            WebViewLogsFeature.WEB_LOGS_FEATURE_NAME,
            WebViewRumFeature.WEB_RUM_FEATURE_NAME
        ).map {
            it to mock<SdkFeature>()
        }

        sdkFeatureMocks.forEach { testedCore.features += it }

        // When
        testedCore.stop()

        // Then
        verify(mockCoreFeature).stop()
        sdkFeatureMocks.forEach {
            verify(it.second).stop()
        }

        assertThat(testedCore.rumFeature).isNull()
        assertThat(testedCore.tracingFeature).isNull()
        assertThat(testedCore.webViewLogsFeature).isNull()
        assertThat(testedCore.webViewRumFeature).isNull()
        assertThat(testedCore.crashReportsFeature).isNull()
        assertThat(testedCore.contextProvider).isNull()

        assertThat(testedCore.features).isEmpty()
    }

    companion object {
        val appContext = ApplicationContextTestConfiguration(Application::class.java)
        val mainLooper = MainLooperTestConfiguration()
        val logger = InternalLoggerTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(logger, appContext, mainLooper)
        }
    }
}
