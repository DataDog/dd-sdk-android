/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal

import android.app.Application
import com.datadog.android.api.context.NetworkInfo
import com.datadog.android.api.context.UserInfo
import com.datadog.android.core.internal.system.AndroidInfoProvider
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.config.CoreFeatureTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.forge.exhaustiveAttributes
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogContextProviderTest {

    private lateinit var testedProvider: ContextProvider

    @Forgery
    lateinit var fakeNetworkInfo: NetworkInfo

    @Forgery
    lateinit var fakeUserInfo: UserInfo

    @Forgery
    lateinit var fakeAndroidInfo: AndroidInfoProvider

    @LongForgery(min = 0L)
    var fakeDeviceTimestamp: Long = 0L

    @LongForgery(min = 0L)
    var fakeServerTimestamp: Long = 0L

    @Forgery
    lateinit var fakeTrackingConsent: TrackingConsent

    lateinit var fakeFeaturesContext: Map<String, Map<String, Any?>>

    @Mock
    lateinit var mockFeatureContextProvider: FeatureContextProvider

    @BeforeEach
    fun setUp(forge: Forge) {
        testedProvider = DatadogContextProvider(coreFeature.mockInstance, mockFeatureContextProvider)

        whenever(coreFeature.mockInstance.userInfoProvider.getUserInfo()) doReturn fakeUserInfo
        whenever(
            coreFeature.mockInstance.networkInfoProvider.getLatestNetworkInfo()
        ) doReturn fakeNetworkInfo

        whenever(coreFeature.mockInstance.androidInfoProvider) doReturn fakeAndroidInfo

        whenever(coreFeature.mockInstance.timeProvider.getDeviceTimestamp()) doReturn
            fakeDeviceTimestamp
        whenever(coreFeature.mockInstance.timeProvider.getServerTimestamp()) doReturn
            fakeServerTimestamp

        whenever(coreFeature.mockInstance.trackingConsentProvider.getConsent()) doReturn
            fakeTrackingConsent
        // building nested maps with default size slows down tests quite a lot, so will use
        // an explicit small size
        fakeFeaturesContext = forge.aMap(size = 2) {
            forge.anAlphabeticalString() to forge.exhaustiveAttributes()
        }
        whenever(
            mockFeatureContextProvider.getFeaturesContexts()
        ) doReturn fakeFeaturesContext.map { it.key to { it.value } }
    }

    @Test
    fun `M create a context W context`() {
        // When
        val context = testedProvider.context

        // Then
        assertThat(context.site).isEqualTo(coreFeature.mockInstance.site)
        assertThat(context.env).isEqualTo(coreFeature.mockInstance.envName)
        assertThat(context.clientToken).isEqualTo(coreFeature.mockInstance.clientToken)
        assertThat(context.service).isEqualTo(coreFeature.mockInstance.serviceName)
        assertThat(context.env).isEqualTo(coreFeature.mockInstance.envName)
        assertThat(context.version)
            .isEqualTo(coreFeature.mockInstance.packageVersionProvider.version)
        assertThat(context.sdkVersion).isEqualTo(coreFeature.mockInstance.sdkVersion)
        assertThat(context.source).isEqualTo(coreFeature.mockInstance.sourceName)

        // time info
        assertThat(context.time.deviceTimeNs)
            .isEqualTo(TimeUnit.MILLISECONDS.toNanos(fakeDeviceTimestamp))
        assertThat(context.time.serverTimeNs)
            .isEqualTo(TimeUnit.MILLISECONDS.toNanos(fakeServerTimestamp))
        assertThat(context.time.serverTimeOffsetMs)
            .isEqualTo(fakeServerTimestamp - fakeDeviceTimestamp)
        assertThat(context.time.serverTimeOffsetNs)
            .isEqualTo(TimeUnit.MILLISECONDS.toNanos(fakeServerTimestamp - fakeDeviceTimestamp))

        // process info
        assertThat(context.processInfo.isMainProcess)
            .isEqualTo(coreFeature.mockInstance.isMainProcess)

        // network info
        assertThat(context.networkInfo.connectivity.name)
            .isEqualTo(fakeNetworkInfo.connectivity.name)
        assertThat(context.networkInfo.carrierName)
            .isEqualTo(fakeNetworkInfo.carrierName)
        assertThat(context.networkInfo.carrierId)
            .isEqualTo(fakeNetworkInfo.carrierId)
        assertThat(context.networkInfo.cellularTechnology)
            .isEqualTo(fakeNetworkInfo.cellularTechnology)
        assertThat(context.networkInfo.upKbps)
            .isEqualTo(fakeNetworkInfo.upKbps)
        assertThat(context.networkInfo.downKbps)
            .isEqualTo(fakeNetworkInfo.downKbps)
        assertThat(context.networkInfo.strength)
            .isEqualTo(fakeNetworkInfo.strength)

        // device info
        assertThat(context.deviceInfo.deviceBrand).isEqualTo(fakeAndroidInfo.deviceBrand)
        assertThat(context.deviceInfo.deviceName).isEqualTo(fakeAndroidInfo.deviceName)
        assertThat(context.deviceInfo.deviceType).isEqualTo(fakeAndroidInfo.deviceType)
        assertThat(context.deviceInfo.deviceModel).isEqualTo(fakeAndroidInfo.deviceModel)
        assertThat(context.deviceInfo.deviceBuildId).isEqualTo(fakeAndroidInfo.deviceBuildId)
        assertThat(context.deviceInfo.osName).isEqualTo(fakeAndroidInfo.osName)
        assertThat(context.deviceInfo.osVersion).isEqualTo(fakeAndroidInfo.osVersion)
        assertThat(context.deviceInfo.osMajorVersion).isEqualTo(fakeAndroidInfo.osMajorVersion)

        // user info
        assertThat(context.userInfo.id).isEqualTo(fakeUserInfo.id)
        assertThat(context.userInfo.name).isEqualTo(fakeUserInfo.name)
        assertThat(context.userInfo.email).isEqualTo(fakeUserInfo.email)
        assertThat(context.userInfo.additionalProperties)
            .isEqualTo(fakeUserInfo.additionalProperties)

        assertThat(context.appBuildId).isEqualTo(coreFeature.mockInstance.appBuildId)
        assertThat(context.trackingConsent).isEqualTo(fakeTrackingConsent)

        assertThat(context.featuresContext).isEqualTo(fakeFeaturesContext)
    }

    @Test
    fun `M create a frozen feature context W context {feature context is changed after context creation}`(
        forge: Forge
    ) {
        // Given
        val mapSize = forge.anInt(4, 20)
        val mutableFeaturesContext = forge.aMap<String, Map<String, Any?>>(mapSize) {
            aString() to forge.exhaustiveAttributes()
        }.toMutableMap()

        // create it explicitly, without relying on the same .toMap code as in the source
        val featuresContextSnapshot = mutableMapOf<String, Map<String, Any?>>()
        mutableFeaturesContext.forEach { (key, value) ->
            val featureSnapshot = mutableMapOf<String, Any?>()
            featuresContextSnapshot[key] = featureSnapshot.apply {
                value.forEach { (innerKey, innerValue) ->
                    this[innerKey] = innerValue
                }
            }
        }

        whenever(
            mockFeatureContextProvider.getFeaturesContexts()
        ) doReturn mutableFeaturesContext.map { it.key to { it.value } }

        val context = testedProvider.context

        // When
        mutableFeaturesContext.values.forEach { innerMap ->
            val keysToRemove = innerMap.keys.take(forge.anInt(min = 0, max = innerMap.keys.size))
            keysToRemove.forEach {
                (innerMap as MutableMap<*, *>).remove(it)
            }
        }
        val keysToRemove = mutableFeaturesContext.keys
            .take(forge.anInt(min = 1, max = mutableFeaturesContext.keys.size))
        keysToRemove.forEach {
            mutableFeaturesContext.remove(it)
        }

        // Then
        assertThat(mutableFeaturesContext).isNotEqualTo(featuresContextSnapshot)
        assertThat(context.featuresContext).isEqualTo(featuresContextSnapshot)
    }

    companion object {
        val appContext = ApplicationContextTestConfiguration(Application::class.java)
        val coreFeature = CoreFeatureTestConfiguration(appContext)

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(appContext, coreFeature)
        }
    }
}
