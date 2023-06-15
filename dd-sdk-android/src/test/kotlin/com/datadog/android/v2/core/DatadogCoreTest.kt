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
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.core.internal.time.NoOpTimeProvider
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.core.internal.user.MutableUserInfoProvider
import com.datadog.android.ndk.DatadogNdkCrashHandler
import com.datadog.android.ndk.NdkCrashHandler
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.security.Encryption
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.Feature
import com.datadog.android.v2.api.FeatureEventReceiver
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.context.NetworkInfo
import com.datadog.android.v2.api.context.TimeInfo
import com.datadog.android.v2.api.context.UserInfo
import com.datadog.android.v2.core.internal.ContextProvider
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.AdvancedForgery
import fr.xgouchet.elmyr.annotation.Forgery
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
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.File
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

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Forgery
    lateinit var fakeCredentials: Credentials

    @Forgery
    lateinit var fakeConfiguration: Configuration

    @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL)
    lateinit var fakeInstanceId: String

    @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL)
    lateinit var fakeInstanceName: String

    @BeforeEach
    fun `set up`() {
        CoreFeature.disableKronosBackgroundSync = true

        testedCore = DatadogCore(
            appContext.mockInstance,
            fakeCredentials,
            fakeInstanceId,
            fakeInstanceName,
            internalLoggerProvider = { mockInternalLogger }
        ).apply {
            initialize(fakeConfiguration)
        }
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
    fun `𝕄 register feature 𝕎 registerFeature()`(
        @Mock mockFeature: Feature,
        @StringForgery fakeFeatureName: String
    ) {
        // Given
        whenever(mockFeature.name) doReturn fakeFeatureName

        // When
        testedCore.registerFeature(mockFeature)

        // Then
        assertThat(testedCore.features).containsKey(fakeFeatureName)
        verify(mockFeature).onInitialize(appContext.mockInstance)
    }

    @Test
    fun `𝕄 handle NDK crash for RUM 𝕎 registerFeature() {RUM feature}`(
        @Mock mockFeature: Feature
    ) {
        // Given
        val mockNdkCrashHandler = mock<NdkCrashHandler>()
        testedCore.coreFeature.ndkCrashHandler = mockNdkCrashHandler
        whenever(mockFeature.name) doReturn Feature.RUM_FEATURE_NAME

        // When
        testedCore.registerFeature(mockFeature)

        // Then
        verify(testedCore.coreFeature.ndkCrashHandler)
            .handleNdkCrash(testedCore, NdkCrashHandler.ReportTarget.RUM)
    }

    @Test
    fun `𝕄 handle NDK crash for Logs 𝕎 registerFeature() {Logs feature}`(
        @Mock mockFeature: Feature
    ) {
        // Given
        val mockNdkCrashHandler = mock<NdkCrashHandler>()
        testedCore.coreFeature.ndkCrashHandler = mockNdkCrashHandler
        whenever(mockFeature.name) doReturn Feature.LOGS_FEATURE_NAME

        // When
        testedCore.registerFeature(mockFeature)

        // Then
        verify(testedCore.coreFeature.ndkCrashHandler)
            .handleNdkCrash(testedCore, NdkCrashHandler.ReportTarget.LOGS)
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
        verifyNoInteractions(mockContextProvider)
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
        verify(mockInternalLogger).log(
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
        verify(mockInternalLogger).log(
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
    fun `𝕄 provide name 𝕎 name(){}`() {
        // When+Then
        assertThat(testedCore.name).isEqualTo(fakeInstanceName)
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
    fun `𝕄 provide network info 𝕎 networkInfo()`(
        @Forgery fakeNetworkInfo: NetworkInfo
    ) {
        // Given
        testedCore.coreFeature = mock()
        val mockNetworkInfoProvider = mock<NetworkInfoProvider>()
        whenever(mockNetworkInfoProvider.getLatestNetworkInfo()) doReturn fakeNetworkInfo
        whenever(testedCore.coreFeature.networkInfoProvider) doReturn mockNetworkInfoProvider

        // When
        val networkInfo = testedCore.networkInfo

        // Then
        assertThat(networkInfo).isSameAs(fakeNetworkInfo)
    }

    @Test
    fun `𝕄 return tracking consent 𝕎 trackingConsent()`(
        @Forgery fakeTrackingConsent: TrackingConsent
    ) {
        // Given
        testedCore.coreFeature = mock()
        val mockConsentProvider = mock<ConsentProvider>()
        whenever(mockConsentProvider.getConsent()) doReturn fakeTrackingConsent
        whenever(testedCore.coreFeature.trackingConsentProvider) doReturn mockConsentProvider

        // When
        val trackingConsent = testedCore.trackingConsent

        // When + Then
        assertThat(trackingConsent).isEqualTo(fakeTrackingConsent)
    }

    @Test
    fun `𝕄 return root storage dir 𝕎 rootStorageDir()`() {
        // When + Then
        assertThat(testedCore.rootStorageDir).isEqualTo(testedCore.coreFeature.storageDir)
    }

    @Test
    fun `𝕄 persist the event into the NDK crash folder 𝕎 writeLastViewEvent(){ViewEvent+dir exists}`(
        @TempDir tempStorageDir: File,
        @StringForgery viewEvent: String
    ) {
        // Given
        val fakeViewEvent = viewEvent.toByteArray()

        val ndkReportsFolder = File(
            tempStorageDir,
            DatadogNdkCrashHandler.NDK_CRASH_REPORTS_FOLDER_NAME
        )
        ndkReportsFolder.mkdir()
        val mockCoreFeature = mock<CoreFeature>()
        whenever(mockCoreFeature.storageDir) doReturn tempStorageDir

        val mockEncryption = mock<Encryption>()
        whenever(mockCoreFeature.localDataEncryption) doReturn mockEncryption
        whenever(mockEncryption.encrypt(fakeViewEvent)) doReturn fakeViewEvent.reversedArray()

        testedCore.coreFeature = mockCoreFeature

        // When
        testedCore.writeLastViewEvent(fakeViewEvent)

        // Then
        val lastViewEventFile = File(
            ndkReportsFolder,
            DatadogNdkCrashHandler.RUM_VIEW_EVENT_FILE_NAME
        )
        assertThat(lastViewEventFile).exists()

        val fileContent = lastViewEventFile.readBytes()
        // file will have batch file format, so beginning will contain some metadata,
        // we need to skip it for the comparison
        val payload = fileContent.takeLast(fakeViewEvent.size).toByteArray()
        assertThat(payload)
            .isEqualTo(fakeViewEvent.reversedArray())
    }

    @Test
    fun `𝕄 log info when writing last view event 𝕎 writeLastViewEvent(){ ViewEvent+no crash dir }`(
        @TempDir tempStorageDir: File,
        @StringForgery viewEvent: String
    ) {
        // Given
        val ndkReportsFolder = File(
            tempStorageDir,
            DatadogNdkCrashHandler.NDK_CRASH_REPORTS_FOLDER_NAME
        )
        val mockCoreFeature = mock<CoreFeature>()
        whenever(mockCoreFeature.storageDir) doReturn tempStorageDir

        testedCore.coreFeature = mockCoreFeature

        // When
        testedCore.writeLastViewEvent(viewEvent.toByteArray())

        // Then
        assertThat(ndkReportsFolder).doesNotExist()
        verify(mockInternalLogger).log(
            InternalLogger.Level.INFO,
            InternalLogger.Target.MAINTAINER,
            DatadogCore.LAST_VIEW_EVENT_DIR_MISSING_MESSAGE.format(
                Locale.US,
                ndkReportsFolder
            )
        )
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
    fun `𝕄 stop all features 𝕎 stop()`(
        @StringForgery fakeFeatureNames: List<String>
    ) {
        // Given
        val mockCoreFeature = mock<CoreFeature>()
        whenever(mockCoreFeature.initialized).thenReturn(mock())
        testedCore.coreFeature = mockCoreFeature

        val sdkFeatureMocks = fakeFeatureNames.map {
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

        assertThat(testedCore.contextProvider).isNull()
        assertThat(testedCore.isActive).isFalse
        assertThat(testedCore.features).isEmpty()
    }

    companion object {
        val appContext = ApplicationContextTestConfiguration(Application::class.java)

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(appContext)
        }
    }
}
