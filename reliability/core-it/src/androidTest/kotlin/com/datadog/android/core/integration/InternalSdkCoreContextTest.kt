/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.integration

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.datadog.android.BuildConfig
import com.datadog.android.Datadog
import com.datadog.android.DatadogSite
import com.datadog.android._InternalProxy
import com.datadog.android.api.context.DeviceType
import com.datadog.android.api.context.NetworkInfo
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.stub.StubStorageBackedFeature
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.integration.tests.MockServerTest
import com.datadog.android.core.integration.tests.forge.factories.ConfigurationCoreForgeryFactory
import com.datadog.android.core.integration.tests.utils.clientToken
import com.datadog.android.core.integration.tests.utils.env
import com.datadog.android.core.integration.tests.utils.getFirstPartyHostsWithHeaderTypes
import com.datadog.android.core.integration.tests.utils.isCrashReportsEnabled
import com.datadog.android.core.integration.tests.utils.isDeveloperModeEnabled
import com.datadog.android.core.integration.tests.utils.removeRandomEntries
import com.datadog.android.core.integration.tests.utils.service
import com.datadog.android.core.integration.tests.utils.site
import com.datadog.android.core.integration.tests.utils.variant
import com.datadog.android.core.thread.FlushableExecutorService
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.trace.TracingHeaderType
import com.datadog.tools.unit.forge.exhaustiveAttributes
import com.datadog.tools.unit.forge.useToolsFactories
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit4.ForgeRule
import fr.xgouchet.elmyr.jvm.useJvmFactories
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Provides the tests for the InternalSdkCore API not related with the writing operations.
 */
@RunWith(AndroidJUnit4::class)
class InternalSdkCoreContextTest : MockServerTest() {

    @get:Rule
    var forge = ForgeRule().useJvmFactories().useToolsFactories().withFactory(ConfigurationCoreForgeryFactory())

    @StringForgery(type = StringForgeryType.ALPHABETICAL)
    lateinit var fakeUserId: String

    @StringForgery(regex = "[A-Z][a-z]+ [A-Z]\\. [A-Z][a-z]+")
    lateinit var fakeUserName: String

    @StringForgery(regex = "[a-z]+\\.[a-z]+@[a-z]+\\.[a-z]{3}")
    lateinit var fakeUserEmail: String
    private var fakeUserAdditionalProperties: Map<String, Any?> = emptyMap()
    private lateinit var stubFeature: Feature

    @StringForgery(type = StringForgeryType.ALPHABETICAL)
    lateinit var fakeFeatureName: String

    private lateinit var fakeTrackingConsent: TrackingConsent

    private lateinit var testedInternalSdkCore: InternalSdkCore

    @Forgery
    lateinit var fakeConfiguration: Configuration
    private var currentDeviceTimeInMs: Long = 0L

    @Before
    fun setUp() {
        currentDeviceTimeInMs = System.currentTimeMillis()
        stubFeature = StubStorageBackedFeature(
            forge,
            fakeFeatureName,
            getMockServerWrapper().getServerUrl()
        )
        fakeTrackingConsent = forge.aValueFrom(TrackingConsent::class.java)
        fakeUserAdditionalProperties = forge.exhaustiveAttributes(excludedKeys = setOf("id", "name", "email"))
        testedInternalSdkCore = Datadog.initialize(
            ApplicationProvider.getApplicationContext(),
            fakeConfiguration,
            fakeTrackingConsent
        ) as InternalSdkCore
        Datadog.setUserInfo(fakeUserId, fakeUserName, fakeUserEmail, fakeUserAdditionalProperties)
        testedInternalSdkCore.registerFeature(stubFeature)
    }

    @After
    fun tearDown() {
        Datadog.stopInstance()
    }

    // region get Datadog Context

    @Test
    fun must_returnCorrectDatadogContext_when_getDatadogContext() {
        // Given
        val expectedServiceName = fakeConfiguration.service() ?: PACKAGE_NAME

        // When
        val context = testedInternalSdkCore.getDatadogContext()

        // Then
        checkNotNull(context)
        assertThat(context.sdkVersion).isEqualTo(BuildConfig.SDK_VERSION_NAME)
        assertThat(context.clientToken).isEqualTo(fakeConfiguration.clientToken())
        assertThat(context.env).isEqualTo(fakeConfiguration.env())
        assertThat(context.service).isEqualTo(expectedServiceName)
        assertThat(context.variant).isEqualTo(fakeConfiguration.variant())
        assertThat(context.site).isEqualTo(fakeConfiguration.site())
        assertThat(context.trackingConsent).isEqualTo(fakeTrackingConsent)
        assertThat(context.version).isEqualTo(getAppVersion())
        assertThat(context.appBuildId).isEqualTo(BUILD_ID)
        assertThat(context.source).isEqualTo(ANDROID_SOURCE)
        assertThat(context.processInfo.isMainProcess).isTrue()
        assertThat(context.networkInfo.connectivity).isEqualTo(NetworkInfo.Connectivity.NETWORK_WIFI)
        assertThat(context.networkInfo.carrierName).isNull()
        assertThat(context.deviceInfo.deviceName).isNotEmpty()
        assertThat(context.deviceInfo.deviceBrand).isNotEmpty()
        assertThat(context.deviceInfo.deviceType).isEqualTo(DeviceType.MOBILE)
        assertThat(context.deviceInfo.deviceModel).isNotEmpty()
        assertThat(context.time.serverTimeNs).isGreaterThan(0)
        assertThat(context.time.deviceTimeNs).isCloseTo(
            TimeUnit.MILLISECONDS.toNanos(currentDeviceTimeInMs),
            Offset.offset(DEVICE_TIME_OFFSET_NS)
        )
        assertThat(context.userInfo.id).isEqualTo(fakeUserId)
        assertThat(context.userInfo.name).isEqualTo(fakeUserName)
        assertThat(context.userInfo.email).isEqualTo(fakeUserEmail)
        assertThat(context.userInfo.additionalProperties)
            .containsExactlyInAnyOrderEntriesOf(fakeUserAdditionalProperties)
        assertThat(context.featuresContext).isEmpty()
    }

    @Test
    fun mustReturnTheUpdatedFeatureContext_getDatadogContext_featureContextWasSet() {
        // Given
        val fakeKeyValues = forge.aMap { forge.anAlphabeticalString() to forge.anAlphabeticalString() }
        testedInternalSdkCore.updateFeatureContext(fakeFeatureName) {
            fakeKeyValues.forEach { (key, value) ->
                it[key] = value
            }
        }

        // When
        val context = testedInternalSdkCore.getDatadogContext()

        // Then
        checkNotNull(context)
        assertThat(context.featuresContext.entries).hasSize(1)
        assertThat(context.featuresContext[fakeFeatureName]).containsExactlyInAnyOrderEntriesOf(fakeKeyValues)
    }

    @Test
    fun mustUseAtomicOperations_when_updateFeatureContext_addNewValues() {
        // Given
        val fakeKeyValues1 = forge.aMap(
            size = forge.anInt(
                min = 1,
                max = 10
            )
        ) { forge.anAlphabeticalString() to forge.anAlphabeticalString() }
        val fakeKeyValues2 = forge.aMap(
            size = forge.anInt(
                min = 1,
                max = 10
            )
        ) { forge.anAlphabeticalString() to forge.anAlphabeticalString() }
        val expectedKeyValues = fakeKeyValues1 + fakeKeyValues2
        val countDownLatch = CountDownLatch(2)
        testedInternalSdkCore.setContextUpdateReceiver(fakeFeatureName) { _, _ -> countDownLatch.countDown() }
        Thread {
            testedInternalSdkCore.updateFeatureContext(fakeFeatureName) {
                fakeKeyValues1.forEach { (key, value) ->
                    it[key] = value
                }
            }
        }.start()
        Thread {
            testedInternalSdkCore.updateFeatureContext(fakeFeatureName) {
                fakeKeyValues2.forEach { (key, value) ->
                    it[key] = value
                }
            }
        }.start()
        countDownLatch.await(SHORT_WAIT_MS, TimeUnit.MILLISECONDS)

        // When
        val context = testedInternalSdkCore.getDatadogContext()

        // Then
        checkNotNull(context)
        assertThat(context.featuresContext.entries).hasSize(1)
        val featureContext = context.featuresContext[fakeFeatureName]
        assertThat(featureContext).containsExactlyInAnyOrderEntriesOf(expectedKeyValues)
    }

    @Test
    fun mustUseAtomicOperations_when_updateFeatureContext_modifyValues() {
        // Given
        val fakeKeyValues1 = forge.aMap(
            size = forge.anInt(
                min = 1,
                max = 10
            )
        ) { forge.anAlphabeticalString() to forge.anAlphabeticalString() }
        val fakeKeyValues2 = forge.aMap(
            size = forge.anInt(
                min = 1,
                max = 10
            )
        ) { forge.anAlphabeticalString() to forge.anAlphabeticalString() }
        val fakeModifiedValues = fakeKeyValues2.mapValues { (_, _) -> forge.anAlphabeticalString() }
        val expectedKeyValues = fakeKeyValues1 + fakeModifiedValues
        val countDownLatch = CountDownLatch(3)
        testedInternalSdkCore.setContextUpdateReceiver(fakeFeatureName) { _, _ -> countDownLatch.countDown() }
        Thread {
            testedInternalSdkCore.updateFeatureContext(fakeFeatureName) {
                fakeKeyValues1.forEach { (key, value) ->
                    it[key] = value
                }
            }
        }.start()
        Thread {
            testedInternalSdkCore.updateFeatureContext(fakeFeatureName) {
                fakeKeyValues2.forEach { (key, value) ->
                    it[key] = value
                }
            }
        }.start()
        // Give some time to the first 2 threads to start
        Thread.sleep(100)
        Thread {
            testedInternalSdkCore.updateFeatureContext(fakeFeatureName) {
                fakeKeyValues2.forEach { (key, _) ->
                    it[key] = fakeModifiedValues[key]
                }
            }
        }.start()
        countDownLatch.await(SHORT_WAIT_MS, TimeUnit.MILLISECONDS)

        // When
        val context = testedInternalSdkCore.getDatadogContext()

        // Then
        checkNotNull(context)
        assertThat(context.featuresContext.entries).hasSize(1)
        val featureContext = context.featuresContext[fakeFeatureName]
        assertThat(featureContext).containsExactlyInAnyOrderEntriesOf(expectedKeyValues)
    }

    @Test
    fun mustUseAtomicOperations_when_updateFeatureContext_removeValues() {
        // Given
        val fakeKeyValues1 = forge.aMap(
            size = forge.anInt(
                min = 1,
                max = 10
            )
        ) { forge.anAlphabeticalString() to forge.anAlphabeticalString() }
        val fakeKeyValues2 = forge.aMap(
            size = forge.anInt(
                min = 1,
                max = 10
            )
        ) { forge.anAlphabeticalString() to forge.anAlphabeticalString() }
        val expectedKeyValues = (fakeKeyValues1 + fakeKeyValues2).toMutableMap()
        val droppedKeyValues = expectedKeyValues.removeRandomEntries(forge)
        val countDownLatch = CountDownLatch(3)
        testedInternalSdkCore.setContextUpdateReceiver(fakeFeatureName) { _, _ -> countDownLatch.countDown() }
        Thread {
            testedInternalSdkCore.updateFeatureContext(fakeFeatureName) {
                fakeKeyValues1.forEach { (key, value) ->
                    it[key] = value
                }
            }
        }.start()
        Thread {
            testedInternalSdkCore.updateFeatureContext(fakeFeatureName) {
                fakeKeyValues2.forEach { (key, value) ->
                    it[key] = value
                }
            }
        }.start()
        // give some time to the firs 2 threads to start
        Thread.sleep(100)
        Thread {
            testedInternalSdkCore.updateFeatureContext(fakeFeatureName) {
                droppedKeyValues.forEach { (key, _) ->
                    it.remove(key)
                }
            }
        }.start()
        countDownLatch.await(SHORT_WAIT_MS, TimeUnit.MILLISECONDS)

        // When
        val context = testedInternalSdkCore.getDatadogContext()

        // Then
        checkNotNull(context)
        assertThat(context.featuresContext.entries).hasSize(1)
        val featureContext = context.featuresContext[fakeFeatureName]
        assertThat(featureContext).containsExactlyInAnyOrderEntriesOf(expectedKeyValues)
    }

    // endregion

    // region get Network Info

    @Test
    fun mustReturnTheCorrectNetworkInfo_when_getNetworkInfo() {
        // Given
        val expectedConnectivity = NetworkInfo.Connectivity.NETWORK_WIFI

        // When
        val networkInfo = testedInternalSdkCore.networkInfo

        // Then
        assertThat(networkInfo.connectivity).isEqualTo(expectedConnectivity)
        assertThat(networkInfo.carrierName).isNull()
        assertThat(networkInfo.carrierId).isNull()
        assertThat(networkInfo.upKbps).isGreaterThan(0)
        assertThat(networkInfo.downKbps).isGreaterThan(0)
        assertThat(networkInfo.strength).isNotNull()
        assertThat(networkInfo.cellularTechnology).isNull()
    }

    // endregion

    // region Tracking consent

    @Test
    fun mustReturnTheCorrectTrackingConsent_when_getTrackingConsent() {
        // When
        val trackingConsent = testedInternalSdkCore.trackingConsent

        // Then
        assertThat(trackingConsent).isEqualTo(fakeTrackingConsent)
    }

    @Test
    fun mustReturnTheCorrectTrackingConsent_when_getTrackingConsent_trackingConsentChanged() {
        // Given
        val newFakeTrackingConsent = forge.aValueFrom(TrackingConsent::class.java)
        Datadog.setTrackingConsent(newFakeTrackingConsent)

        // When
        val trackingConsent = testedInternalSdkCore.trackingConsent

        // Then
        assertThat(trackingConsent).isEqualTo(newFakeTrackingConsent)
    }

    // endregion

    // root storage dir

    @Test
    fun mustReturnTheCorrectRootStorageDir_when_getRootStorageDir() {
        // Given
        val expectedStorageDirFormat = ApplicationProvider.getApplicationContext<Context>().cacheDir.path +
            File.separator +
            DATADOG_STORAGE_DIR_NAME_FORMAT

        // When
        val rootStorageDir = testedInternalSdkCore.rootStorageDir
        val rootStorageDirPath = rootStorageDir?.path

        // Then
        assertThat(rootStorageDir).isNotNull()
        assertThat(rootStorageDirPath).matches(expectedStorageDirFormat)
    }

    // endregion

    // region isDeveloperModeEnabled

    @Test
    fun mustReturnCorrectIsDeveloperMode_when_isDeveloperModeEnabled() {
        // Given
        val expectedDeveloperMode = isAppDebuggable(ApplicationProvider.getApplicationContext()) &&
            fakeConfiguration.isDeveloperModeEnabled()

        // When
        val isDeveloperModeEnabled = testedInternalSdkCore.isDeveloperModeEnabled

        // Then
        assertThat(isDeveloperModeEnabled).isEqualTo(expectedDeveloperMode)
    }

    @Test
    fun mustReturnFalse_when_isDeveloperModeEnabled_isDeveloperModeDisabledInConfig() {
        // Given
        val fakeConfigDeveloperModeDisabled = Configuration.Builder(
            UUID.randomUUID().toString(),
            forge.anHexadecimalString(),
            forge.anHexadecimalString(),
            forge.aNullable {
                anAlphaNumericalString()
            }
        )
            .setUseDeveloperModeWhenDebuggable(false)
            .setFirstPartyHostsWithHeaderType(
                forge.aMap {
                    val fakeUrl = forge.aStringMatching("https://[a-z0-9]+\\.com")
                    fakeUrl to aList {
                        aValueFrom(
                            TracingHeaderType::class.java
                        )
                    }.toSet()
                }
            )
            .apply {
                _InternalProxy.allowClearTextHttp(this)
            }
            .setBatchSize(forge.getForgery())
            .setUploadFrequency(forge.getForgery())
            .useSite(forge.aValueFrom(DatadogSite::class.java))
            .build()

        // When
        // stop the current instance
        Datadog.stopInstance()
        Datadog.initialize(
            ApplicationProvider.getApplicationContext(),
            fakeConfigDeveloperModeDisabled,
            fakeTrackingConsent
        )

        // When
        val isDeveloperModeEnabled = testedInternalSdkCore.isDeveloperModeEnabled

        // Then
        assertThat(isDeveloperModeEnabled).isEqualTo(false)
    }

    // endregion

    // region FirstPartyHostResolver

    @Test
    fun mustReturnTheCorrectFirstPartyHostResolver_when_getFirstPartyHostResolver() {
        // Given
        val expectedFirstPartyHostsWithHeaderTypes = fakeConfiguration.getFirstPartyHostsWithHeaderTypes()

        // When
        val firstPartyHostResolver = testedInternalSdkCore.firstPartyHostResolver

        // Then
        assertThat(firstPartyHostResolver).isNotNull()
        expectedFirstPartyHostsWithHeaderTypes.forEach { (host, headerTypes) ->
            assertThat(firstPartyHostResolver.isFirstPartyUrl(host)).isTrue()
            assertThat(firstPartyHostResolver.headerTypesForUrl(host))
                .containsExactlyInAnyOrder(*headerTypes.toTypedArray())
        }
    }

    // endregion

    // region App Start Time

    @Test
    fun mustReturnTheCorrectAppStartTime_when_appStartTimeNs() {
        // When
        val appStartTimeNs = testedInternalSdkCore.appStartTimeNs

        // Then
        assertThat(appStartTimeNs).isCloseTo(
            APPROXIMATE_APP_START_TIME_NS,
            Offset.offset(APP_START_TIME_OFFSET_NS)
        )
    }

    // endregion

    // region Persistence Executor

    @Test
    fun mustReturnTheCorrectPersistenceExecutor_when_persistenceExecutor() {
        // When
        val persistenceExecutor = testedInternalSdkCore.getPersistenceExecutorService()

        // Then
        assertThat(persistenceExecutor.isShutdown).isFalse()
        assertThat(persistenceExecutor.isTerminated).isFalse()
        assertThat(persistenceExecutor).isInstanceOf(FlushableExecutorService::class.java)
    }

    // endregion

    // region Get All Features

    @Test
    fun mustReturnAllTheRegisteredFeatures_when_getAllFeatures_newFeaturesRegistered() {
        // Given
        val fakeFeatures = forge.aList(size = 2) {
            StubStorageBackedFeature(
                forge,
                forge.anAlphabeticalString(),
                getMockServerWrapper().getServerUrl()
            )
        }.onEach { testedInternalSdkCore.registerFeature(it) }
        val expectedRegisteredFeatures = fakeFeatures + stubFeature
        val expectedRegisteredFeaturesSize = if (fakeConfiguration.isCrashReportsEnabled()) {
            // + 1 for the Crash Report feature
            expectedRegisteredFeatures.size + 1
        } else {
            expectedRegisteredFeatures.size
        }

        // When
        val features = testedInternalSdkCore.getAllFeatures().map { it.unwrap<Feature>() }

        // Then
        assertThat(features).containsAll(expectedRegisteredFeatures)
        assertThat(features.size).isEqualTo(expectedRegisteredFeaturesSize)
    }

    @Test
    fun mustReturnTheCrashReportFeature_when_getAllFeatures_crashReportEnabled() {
        // Given
        val fakeConfigCrashReportsEnabled = fakeConfiguration.copy(crashReportsEnabled = true)

        // When
        // stop the current instance
        Datadog.stopInstance()
        val internalSdkCore = Datadog.initialize(
            ApplicationProvider.getApplicationContext(),
            fakeConfigCrashReportsEnabled,
            fakeTrackingConsent
        ) as InternalSdkCore

        // When
        val features = internalSdkCore.getAllFeatures().map { it.unwrap<Feature>() }

        // Then
        assertThat(features).isNotEmpty
        assertThat(features).anyMatch { it.name == "crash" }
    }

    @Test
    fun mustReturnEmptyFeatures_when_getAllFeatures_crashReportNotEnabled() {
        // Given
        val fakeConfigCrashReportsNotEnabled = fakeConfiguration.copy(crashReportsEnabled = false)

        // When
        // stop the current instance
        Datadog.stopInstance()
        val internalSdkCore = Datadog.initialize(
            ApplicationProvider.getApplicationContext(),
            fakeConfigCrashReportsNotEnabled,
            fakeTrackingConsent
        ) as InternalSdkCore

        // When
        val features = internalSdkCore.getAllFeatures().map { it.unwrap<Feature>() }

        // Then
        assertThat(features).isEmpty()
    }

    // endregion

    // region internal

    private fun getAppVersion(): String? {
        return getPackageInfo(ApplicationProvider.getApplicationContext())?.let {
            // we need to use the deprecated method because getLongVersionCode method is only
            // available from API 28 and above
            @Suppress("DEPRECATION")
            it.versionName ?: it.versionCode.toString()
        }
    }

    private fun getPackageInfo(appContext: Context): PackageInfo? {
        return try {
            with(appContext.packageManager) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    getPackageInfo(appContext.packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    getPackageInfo(appContext.packageName, 0)
                }
            }
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun isAppDebuggable(context: Context): Boolean {
        return (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    // endregion

    companion object {
        private val APP_START_TIME_OFFSET_NS = TimeUnit.SECONDS.toNanos(1)
        private val DEVICE_TIME_OFFSET_NS = TimeUnit.SECONDS.toNanos(1)
        private val APPROXIMATE_APP_START_TIME_NS = System.nanoTime()
        private const val ANDROID_SOURCE = "android"
        private const val BUILD_ID = "core_it_build_id"
        private const val PACKAGE_NAME = "com.datadog.android.core.integration.test"
        internal const val DATADOG_STORAGE_DIR_NAME_FORMAT = "datadog-(.*)"
    }
}
