/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.core.internal

import android.app.Application
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.system.AndroidInfoProvider
import com.datadog.android.core.model.NetworkInfo
import com.datadog.android.core.model.UserInfo
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.config.CoreFeatureTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

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

    @BeforeEach
    fun setUp() {
        testedProvider = DatadogContextProvider(coreFeature.mockInstance)

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
    }

    @Test
    fun `𝕄 create a context 𝕎 context`() {
        // When
        val context = testedProvider.context

        // Then
        assertThat(context.env).isEqualTo(coreFeature.mockInstance.envName)
        assertThat(context.site).isEqualTo(coreFeature.mockInstance.site)
        assertThat(context.clientToken).isEqualTo(coreFeature.mockInstance.clientToken)
        assertThat(context.service).isEqualTo(coreFeature.mockInstance.serviceName)
        assertThat(context.env).isEqualTo(coreFeature.mockInstance.envName)
        assertThat(context.version).isEqualTo(coreFeature.mockInstance.packageVersion)
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
        assertThat(context.processInfo.processImportance)
            .isEqualTo(CoreFeature.processImportance)

        // network info
        assertThat(context.networkInfo.connectivity.name)
            .isEqualTo(fakeNetworkInfo.connectivity.name)
        assertThat(context.networkInfo.carrier?.carrierName)
            .isEqualTo(fakeNetworkInfo.carrierName)
        assertThat(context.networkInfo.carrier?.technology)
            .isEqualTo(fakeNetworkInfo.cellularTechnology)

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

        assertThat(context.trackingConsent).isEqualTo(fakeTrackingConsent)

        assertThat(context.featuresContext).isEmpty()
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
