/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core

import android.app.Application
import android.os.Build
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.NetworkInfo
import com.datadog.android.api.context.TimeInfo
import com.datadog.android.api.context.UserInfo
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureContextUpdateReceiver
import com.datadog.android.api.feature.FeatureEventReceiver
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.internal.ContextProvider
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.SdkFeature
import com.datadog.android.core.internal.lifecycle.ProcessLifecycleMonitor
import com.datadog.android.core.internal.net.DefaultFirstPartyHostHeaderTypeResolver
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.core.internal.system.BuildSdkVersionProvider
import com.datadog.android.core.internal.time.NoOpTimeProvider
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.core.internal.user.MutableUserInfoProvider
import com.datadog.android.core.thread.FlushableExecutorService
import com.datadog.android.ndk.internal.NdkCrashHandler
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.google.gson.JsonObject
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
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
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

    private lateinit var testedCore: DatadogCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockPersistenceExecutorService: FlushableExecutorService

    @Mock
    lateinit var mockBuildSdkVersionProvider: BuildSdkVersionProvider

    @Forgery
    lateinit var fakeConfiguration: Configuration

    @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL)
    lateinit var fakeInstanceId: String

    @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL)
    lateinit var fakeInstanceName: String

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

        testedCore = DatadogCore(
            appContext.mockInstance,
            fakeInstanceId,
            fakeInstanceName,
            internalLoggerProvider = { mockInternalLogger },
            executorServiceFactory = { _, _ -> mockPersistenceExecutorService },
            buildSdkVersionProvider = mockBuildSdkVersionProvider
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
    fun `M register feature W registerFeature()`(
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
    fun `M handle NDK crash for RUM W registerFeature() {RUM feature}`(
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
    fun `M handle NDK crash for Logs W registerFeature() {Logs feature}`(
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
    fun `M update userInfoProvider W setUserInfo()`(
        @StringForgery(type = StringForgeryType.HEXADECIMAL) id: String,
        @StringForgery name: String,
        @StringForgery(regex = "\\w+@\\w+") email: String,
        @MapForgery(
            key = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHA_NUMERICAL)]),
            value = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHA_NUMERICAL)])
        ) fakeUserProperties: Map<String, String>
    ) {
        // Given
        val mockUserInfoProvider = mock<MutableUserInfoProvider>()
        testedCore.coreFeature.userInfoProvider = mockUserInfoProvider

        // When
        testedCore.setUserInfo(id, name, email, fakeUserProperties)

        // Then
        verify(mockUserInfoProvider).setUserInfo(
            UserInfo(
                id = id,
                name = name,
                email = email,
                additionalProperties = fakeUserProperties
            )
        )
    }

    @Test
    fun `M set additional user info W addUserProperties() is called`(
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
        testedCore.setUserInfo(id, name, email)
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
    fun `M update feature context W updateFeatureContext()`(
        @StringForgery feature: String,
        @MapForgery(
            key = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHABETICAL)]),
            value = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHABETICAL)])
        ) context: Map<String, String>,
        forge: Forge
    ) {
        // Given
        val mockContextProvider = mock<ContextProvider>()
        val mockFeature = mock<SdkFeature>()
        val otherFeatures = mapOf(
            forge.anAlphaNumericalString() to mock<SdkFeature>()
        )
        testedCore.features[feature] = mockFeature
        testedCore.features.putAll(otherFeatures)
        testedCore.coreFeature.contextProvider = mockContextProvider

        // When
        testedCore.updateFeatureContext(feature) {
            it.putAll(context)
        }

        // Then
        verify(mockContextProvider).setFeatureContext(feature, context)
        otherFeatures.forEach { (_, otherFeature) ->
            verify(otherFeature).notifyContextUpdated(feature, context)
            verifyNoMoreInteractions(otherFeature)
        }
        verify(mockFeature, never()).notifyContextUpdated(feature, context)
    }

    @Test
    fun `M do nothing W updateFeatureContext() { feature is not registered}`(
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
    fun `M set event receiver W setEventReceiver()`(
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
    fun `M notify no feature registered W setEventReceiver() { feature is not registered }`(
        @StringForgery feature: String
    ) {
        // Given
        val fakeReceiver = FeatureEventReceiver { }
        reset(mockInternalLogger)

        // When
        testedCore.setEventReceiver(feature, fakeReceiver)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            DatadogCore.MISSING_FEATURE_FOR_EVENT_RECEIVER.format(Locale.US, feature)
        )
    }

    @Test
    fun `M notify receiver exists W setEventReceiver() { feature already has receiver }`(
        @StringForgery feature: String
    ) {
        // Given
        val mockFeature = mock<SdkFeature>()
        val mockEventReceiverRef = mock<AtomicReference<FeatureEventReceiver>>()
        whenever(mockFeature.eventReceiver) doReturn mockEventReceiverRef
        whenever(mockEventReceiverRef.get()) doReturn mock()
        testedCore.features[feature] = mockFeature
        val fakeReceiver = FeatureEventReceiver { }
        reset(mockInternalLogger)

        // When
        testedCore.setEventReceiver(feature, fakeReceiver)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            DatadogCore.EVENT_RECEIVER_ALREADY_EXISTS.format(Locale.US, feature)
        )
    }

    @Test
    fun `M remove receiver W removeEventReceiver()`(
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
    fun `M set context update listener W setContextUpdateListener()`(
        @StringForgery feature: String
    ) {
        // Given
        val mockFeature = mock<SdkFeature>()
        val mockContextUpdateListener: FeatureContextUpdateReceiver = mock()
        testedCore.features[feature] = mockFeature

        // When
        testedCore.setContextUpdateReceiver(feature, mockContextUpdateListener)

        // Then
        verify(mockFeature).setContextUpdateListener(mockContextUpdateListener)
    }

    @Test
    fun `M notify no feature registered W setContextUpdateListener() { feature is not registered }`(
        @StringForgery feature: String
    ) {
        // Given
        val mockContextUpdateListener: FeatureContextUpdateReceiver = mock()

        // When
        testedCore.setContextUpdateReceiver(feature, mockContextUpdateListener)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            DatadogCore.MISSING_FEATURE_FOR_CONTEXT_UPDATE_LISTENER.format(Locale.US, feature)
        )
    }

    @Test
    fun `M remove context update listener W removeContextUpdateListener()`(
        @StringForgery feature: String
    ) {
        // Given
        val mockFeature = mock<SdkFeature>()
        val mockContextUpdateListener: FeatureContextUpdateReceiver = mock()
        testedCore.features[feature] = mockFeature

        // When
        testedCore.removeContextUpdateReceiver(feature, mockContextUpdateListener)

        // Then
        verify(mockFeature).removeContextUpdateListener(mockContextUpdateListener)
    }

    @Test
    fun `M provide name W name(){}`() {
        // When+Then
        assertThat(testedCore.name).isEqualTo(fakeInstanceName)
    }

    @Test
    fun `M provide time info W time()`(
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
    fun `M provide time info without correction W time() {NoOpTimeProvider}`() {
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
    fun `M provide service W service()`(
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
    fun `M provide first party host resolver W firstPartyHostResolver()`() {
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
    fun `M provide network info W networkInfo()`(
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
    fun `M provide last view event W lastViewEvent()`(
        @Forgery fakeLastViewEvent: JsonObject
    ) {
        // Given
        testedCore.coreFeature = mock()
        whenever(testedCore.coreFeature.lastViewEvent) doReturn fakeLastViewEvent

        // When
        val lastViewEvent = testedCore.lastViewEvent

        // Then
        assertThat(lastViewEvent).isSameAs(fakeLastViewEvent)
    }

    @Test
    fun `M provide last fatal ANR sent W lastFatalAnrSent()`(
        @LongForgery(min = 0L) fakeLastFatalAnrSent: Long
    ) {
        // Given
        testedCore.coreFeature = mock()
        whenever(testedCore.coreFeature.lastFatalAnrSent) doReturn fakeLastFatalAnrSent

        // When
        val lastFatalAnrSent = testedCore.lastFatalAnrSent

        // Then
        assertThat(lastFatalAnrSent).isEqualTo(fakeLastFatalAnrSent)
    }

    @Test
    fun `M provide app start time W appStartTimeNs()`(
        @LongForgery(min = 0L) fakeAppStartTimeNs: Long
    ) {
        // Given
        testedCore.coreFeature = mock()
        whenever(testedCore.coreFeature.appStartTimeNs) doReturn fakeAppStartTimeNs

        // When
        val appStartTimeNs = testedCore.appStartTimeNs

        // Then
        assertThat(appStartTimeNs).isEqualTo(fakeAppStartTimeNs)
    }

    @Test
    fun `M return tracking consent W trackingConsent()`(
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
    fun `M return root storage dir W rootStorageDir()`() {
        // When + Then
        assertThat(testedCore.rootStorageDir).isEqualTo(testedCore.coreFeature.storageDir)
    }

    @Test
    fun `M persist the event W writeLastViewEvent(){ NDK feature registered }`(
        @StringForgery viewEvent: String
    ) {
        // Given
        val fakeViewEvent = viewEvent.toByteArray()
        testedCore.features += Feature.NDK_CRASH_REPORTS_FEATURE_NAME to mock()
        val mockCoreFeature = mock<CoreFeature>()
        testedCore.coreFeature = mockCoreFeature

        // When
        testedCore.writeLastViewEvent(fakeViewEvent)

        // Then
        verify(mockCoreFeature).writeLastViewEvent(fakeViewEvent)
    }

    @Test
    fun `M persist the event W writeLastViewEvent(){ R+ }`(
        @StringForgery viewEvent: String,
        @IntForgery(min = Build.VERSION_CODES.R) fakeSdkVersion: Int
    ) {
        // Given
        val fakeViewEvent = viewEvent.toByteArray()
        whenever(mockBuildSdkVersionProvider.version) doReturn fakeSdkVersion
        val mockCoreFeature = mock<CoreFeature>()
        testedCore.coreFeature = mockCoreFeature

        // When
        testedCore.writeLastViewEvent(fakeViewEvent)

        // Then
        verify(mockCoreFeature).writeLastViewEvent(fakeViewEvent)
    }

    @Test
    fun `M log info when writing last view event W writeLastViewEvent(){ below R and no NDK feature }`(
        @StringForgery viewEvent: String,
        @IntForgery(min = 1, max = Build.VERSION_CODES.R) fakeSdkVersion: Int
    ) {
        // Given
        val mockCoreFeature = mock<CoreFeature>()
        whenever(mockBuildSdkVersionProvider.version) doReturn fakeSdkVersion
        testedCore.coreFeature = mockCoreFeature
        reset(mockInternalLogger)

        // When
        testedCore.writeLastViewEvent(viewEvent.toByteArray())

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.INFO,
            InternalLogger.Target.MAINTAINER,
            DatadogCore.NO_NEED_TO_WRITE_LAST_VIEW_EVENT
        )
    }

    @Test
    fun `M delete last view event W deleteLastViewEvent()`() {
        // Given
        val mockCoreFeature = mock<CoreFeature>()
        testedCore.coreFeature = mockCoreFeature

        // When
        testedCore.deleteLastViewEvent()

        // Then
        verify(mockCoreFeature).deleteLastViewEvent()
    }

    @Test
    fun `M write last fatal ANR sent W writeLastFatalAnrSent()`(
        @LongForgery(min = 0L) fakeLastFatalAnrSent: Long
    ) {
        // Given
        val mockCoreFeature = mock<CoreFeature>()
        testedCore.coreFeature = mockCoreFeature

        // When
        testedCore.writeLastFatalAnrSent(fakeLastFatalAnrSent)

        // Then
        verify(mockCoreFeature).writeLastFatalAnrSent(fakeLastFatalAnrSent)
    }

    @Test
    fun `M clear data in all features W clearAllData()`(
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
        val mockCoreFeature = mock<CoreFeature>()
        testedCore.coreFeature = mockCoreFeature

        // When
        testedCore.clearAllData()

        // Then
        testedCore.features.forEach {
            verify(it.value).clearAllData()
        }
        verify(mockCoreFeature).deleteLastFatalAnrSent()
        verify(mockCoreFeature).deleteLastViewEvent()
    }

    @Test
    fun `M flush data in all features W flushStoredData()`(
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
    fun `M stop all features W stop()`(
        @StringForgery fakeFeatureNames: Set<String>
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

    @Test
    fun `M unregister process lifecycle monitor W stop()`() {
        // Given
        val expectedInvocations = if (fakeConfiguration.crashReportsEnabled) 2 else 1

        // When
        testedCore.stop()

        // Then
        argumentCaptor<ProcessLifecycleMonitor> {
            verify(appContext.mockInstance, times(expectedInvocations))
                .unregisterActivityLifecycleCallbacks(capture())
            assertThat(lastValue).isInstanceOf(ProcessLifecycleMonitor::class.java)
        }
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
