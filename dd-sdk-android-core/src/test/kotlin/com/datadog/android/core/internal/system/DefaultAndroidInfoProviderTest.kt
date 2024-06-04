/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.system

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.telephony.TelephonyManager
import com.datadog.android.api.context.DeviceType
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Locale

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DefaultAndroidInfoProviderTest {

    private lateinit var testedProvider: AndroidInfoProvider

    @Mock
    lateinit var mockSdkVersionProvider: BuildSdkVersionProvider

    @Mock
    lateinit var mockContext: Context

    @Mock
    lateinit var mockUiModeManager: UiModeManager

    @Mock
    lateinit var mockTelephonyManager: TelephonyManager

    @Mock
    lateinit var mockPackageManager: PackageManager

    @Mock
    lateinit var mockResources: Resources

    @StringForgery
    lateinit var fakeDeviceBrand: String

    @StringForgery
    lateinit var fakeDeviceModel: String

    @StringForgery
    lateinit var fakeDeviceId: String

    @StringForgery(regex = "[1-9]{1,3}\\.[1-9]{1,3}\\.[1-9]{1,3}")
    lateinit var fakeOsVersion: String

    @BeforeEach
    fun setUp(forge: Forge) {
        whenever(mockContext.getSystemService(Context.UI_MODE_SERVICE)) doReturn mockUiModeManager
        whenever(mockContext.getSystemService(Context.TELEPHONY_SERVICE)) doReturn mockTelephonyManager
        whenever(mockSdkVersionProvider.version) doReturn forge.anInt(min = Build.VERSION_CODES.BASE)
        whenever(mockContext.packageManager) doReturn mockPackageManager
        whenever(mockContext.resources) doReturn mockResources
        whenever(mockResources.configuration) doReturn Configuration()

        fakeDeviceModel = ""
    }

    // region device type

    @Test
    fun `M return TV type W deviceType { UI_MODE_TYPE_TELEVISION }`() {
        // Given
        whenever(mockUiModeManager.currentModeType) doReturn Configuration.UI_MODE_TYPE_TELEVISION
        testedProvider = createProvider()

        // When
        val type = testedProvider.deviceType

        // Then
        assertThat(type).isEqualTo(DeviceType.TV)
    }

    @Test
    fun `M return TV type W deviceType { FEATURE_LEANBACK }`() {
        // Given
        whenever(
            mockPackageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        ) doReturn true
        testedProvider = createProvider()

        // When
        val type = testedProvider.deviceType

        // Then
        assertThat(type).isEqualTo(DeviceType.TV)
    }

    @Test
    fun `M return TV type W deviceType { FEATURE_GOOGLE_ANDROID_TV }`() {
        // Given
        whenever(
            mockPackageManager.hasSystemFeature(
                DefaultAndroidInfoProvider.FEATURE_GOOGLE_ANDROID_TV
            )
        ) doReturn true
        testedProvider = createProvider()

        // When
        val type = testedProvider.deviceType

        // Then
        assertThat(type).isEqualTo(DeviceType.TV)
    }

    @Test
    fun `M return Tablet type W deviceType { Samsung SM-T series model }`(
        @IntForgery(1) tModelVersion: Int
    ) {
        // Given
        fakeDeviceModel = "SM-T$tModelVersion"
        testedProvider = createProvider()

        // When
        val type = testedProvider.deviceType

        // Then
        assertThat(type).isEqualTo(DeviceType.TABLET)
    }

    @Test
    fun `M return Tablet type W deviceType { tablet word in model name }`(
        @StringForgery(regex = "[a-zA-Z1-9 ]{0,9}Tablet[a-zA-Z1-9 ]{0,9}") fakeModel: String
    ) {
        // Given
        fakeDeviceModel = fakeModel
        testedProvider = createProvider()

        // When
        val type = testedProvider.deviceType

        // Then
        assertThat(type).isEqualTo(DeviceType.TABLET)
    }

    @Test
    fun `M return Tablet type W deviceType { smallest screen width more than 800dp }`(
        @IntForgery(min = DefaultAndroidInfoProvider.MIN_TABLET_WIDTH_DP) fakeWidth: Int
    ) {
        // Given
        val mockResources = mock<Resources>()
        val fakeConfiguration = Configuration().apply {
            smallestScreenWidthDp = fakeWidth
        }

        whenever(mockContext.resources) doReturn mockResources
        whenever(mockResources.configuration) doReturn fakeConfiguration
        testedProvider = createProvider()

        // When
        val type = testedProvider.deviceType

        // Then
        assertThat(type).isEqualTo(DeviceType.TABLET)
    }

    @Test
    fun `M return Mobile type W deviceType { phone word in model name }`(
        @StringForgery(regex = "[a-zA-Z1-9 ]{0,9}Phone[a-zA-Z1-9 ]{0,9}") fakeModel: String
    ) {
        // Given
        fakeDeviceModel = fakeModel
        testedProvider = createProvider()

        // When
        val type = testedProvider.deviceType

        // Then
        assertThat(type).isEqualTo(DeviceType.MOBILE)
    }

    @ParameterizedTest
    @MethodSource("phoneTypesWithDescription")
    fun `M return Mobile type W deviceType {smallest screen width less than 800dp + telephony}`(
        phoneType: PhoneType,
        @IntForgery(min = 0, max = DefaultAndroidInfoProvider.MIN_TABLET_WIDTH_DP) fakeWidth: Int
    ) {
        // Given
        val mockResources = mock<Resources>()
        val fakeConfiguration = Configuration().apply {
            smallestScreenWidthDp = fakeWidth
        }
        whenever(mockContext.resources) doReturn mockResources
        whenever(mockResources.configuration) doReturn fakeConfiguration
        whenever(mockTelephonyManager.phoneType) doReturn phoneType.value
        testedProvider = createProvider()

        // When
        val type = testedProvider.deviceType

        // Then
        assertThat(type).isEqualTo(DeviceType.MOBILE)
    }

    @Test
    fun `M return Other type W deviceType { no tv, table or mobile properties }`(
        @IntForgery(
            min = 0,
            max = DefaultAndroidInfoProvider.MIN_TABLET_WIDTH_DP
        ) fakeWidth: Int
    ) {
        // Given
        val mockResources = mock<Resources>()
        val fakeConfiguration = Configuration().apply {
            smallestScreenWidthDp = fakeWidth
        }

        whenever(mockContext.resources) doReturn mockResources
        whenever(mockResources.configuration) doReturn fakeConfiguration
        whenever(mockTelephonyManager.phoneType) doReturn TelephonyManager.PHONE_TYPE_NONE

        testedProvider = createProvider()

        // When
        val type = testedProvider.deviceType

        // Then
        assertThat(type).isEqualTo(DeviceType.OTHER)
    }

    // endregion

    // region os version + major version

    @Test
    fun `M return full version W osVersion`() {
        // Given
        testedProvider = createProvider()

        // When
        val osVersion = testedProvider.osVersion

        // Then
        assertThat(osVersion).isEqualTo(fakeOsVersion)
    }

    @Test
    fun `M return major version W osMajorVersion { major - minor - patch format}`(
        @StringForgery(regex = "[1-9]{1,3}") fakeMajor: String,
        @StringForgery(regex = "[1-9]{1,3}") fakeMinor: String,
        @StringForgery(regex = "[1-9]{1,3}") fakeHotfix: String
    ) {
        // Given
        fakeOsVersion = "$fakeMajor.$fakeMinor.$fakeHotfix"
        testedProvider = createProvider()

        // When
        val osMajorVersion = testedProvider.osMajorVersion

        // Then
        assertThat(osMajorVersion).isEqualTo(fakeMajor)
    }

    @Test
    fun `M return major version W osMajorVersion { generic format }`(
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) fakeVersion: String
    ) {
        // Given
        fakeOsVersion = fakeVersion
        testedProvider = createProvider()

        // When
        val osMajorVersion = testedProvider.osMajorVersion

        // Then
        assertThat(osMajorVersion).isEqualTo(fakeVersion)
    }

    // endregion

    // region device name

    @Test
    fun `M return device name W deviceName { brand is blank }`() {
        // Given
        fakeDeviceBrand = ""
        testedProvider = createProvider()

        // When
        val deviceName = testedProvider.deviceName

        // Then
        assertThat(deviceName).isEqualTo(fakeDeviceModel)
    }

    @Test
    fun `M return device name W deviceName { model contains brand }`(
        @StringForgery fakeBrand: String,
        @StringForgery modelPrefix: String,
        @StringForgery modelSuffix: String
    ) {
        // Given
        val fakeModel = modelPrefix + fakeBrand.capitalize() + modelSuffix
        fakeDeviceModel = fakeModel
        fakeDeviceBrand = fakeBrand
        testedProvider = createProvider()

        // When
        val deviceName = testedProvider.deviceName

        // Then
        assertThat(deviceName).isEqualTo(fakeModel)
    }

    @Test
    fun `M return device name W deviceName { model doesn't contain brand }`(
        @StringForgery fakeBrand: String,
        @StringForgery fakeModel: String
    ) {
        // Given
        assumeFalse(fakeModel.contains(fakeBrand, ignoreCase = true))
        fakeDeviceBrand = fakeBrand
        fakeDeviceModel = fakeModel
        testedProvider = createProvider()

        // When
        val deviceName = testedProvider.deviceName

        // Then
        assertThat(deviceName).isEqualTo("${fakeBrand.capitalize()} $fakeModel")
    }

    // endregion

    @Test
    fun `M return device brand W deviceBrand { model doesn't contain brand }`(
        @StringForgery fakeBrand: String
    ) {
        // Given
        fakeDeviceBrand = fakeBrand
        testedProvider = createProvider()

        // When
        val deviceBrand = testedProvider.deviceBrand

        // Then
        assertThat(deviceBrand).isEqualTo(fakeBrand.capitalize())
    }

    // region private

    private fun createProvider(): AndroidInfoProvider = DefaultAndroidInfoProvider(
        mockContext,
        fakeDeviceBrand,
        fakeDeviceModel,
        fakeDeviceId,
        fakeOsVersion
    )

    private fun String.capitalize() = replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
    }

    companion object {

        @Suppress("unused")
        @JvmStatic
        fun phoneTypesWithDescription(): List<PhoneType> {
            return listOf(
                PhoneType("gsm", TelephonyManager.PHONE_TYPE_GSM),
                PhoneType("cdma", TelephonyManager.PHONE_TYPE_CDMA),
                PhoneType("sip", TelephonyManager.PHONE_TYPE_SIP)
            )
        }
    }

    data class PhoneType(val name: String, val value: Int)

    // endregion
}
