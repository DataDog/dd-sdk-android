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
import com.datadog.tools.unit.setStaticValue
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
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

    @BeforeEach
    fun setUp(forge: Forge) {
        whenever(mockContext.getSystemService(Context.UI_MODE_SERVICE)) doReturn
            mockUiModeManager
        whenever(mockContext.getSystemService(Context.TELEPHONY_SERVICE)) doReturn
            mockTelephonyManager
        whenever(mockSdkVersionProvider.version) doReturn
            forge.anInt(min = Build.VERSION_CODES.BASE)
        whenever(mockContext.packageManager) doReturn mockPackageManager
        whenever(mockContext.resources) doReturn mockResources
        whenever(mockResources.configuration) doReturn Configuration()

        Build::class.java.setStaticValue("MODEL", "")
    }

    @AfterEach
    fun tearDown() {
        Build::class.java.setStaticValue("MODEL", "")
        Build.VERSION::class.java.setStaticValue("RELEASE", "")
    }

    // region device type

    @Test
    fun `𝕄 return TV type 𝕎 deviceType { UI_MODE_TYPE_TELEVISION }`() {
        // Given
        whenever(mockUiModeManager.currentModeType) doReturn Configuration.UI_MODE_TYPE_TELEVISION
        testedProvider = createProvider()

        // When
        val type = testedProvider.deviceType

        // Then
        assertThat(type).isEqualTo(DeviceType.TV)
    }

    @Test
    fun `𝕄 return TV type 𝕎 deviceType { FEATURE_LEANBACK }`() {
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
    fun `𝕄 return TV type 𝕎 deviceType { FEATURE_GOOGLE_ANDROID_TV }`() {
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
    fun `𝕄 return Tablet type 𝕎 deviceType { Samsung SM-T series model }`(
        forge: Forge
    ) {
        // Given
        Build::class.java.setStaticValue("MODEL", "SM-T${forge.aPositiveInt()}")
        testedProvider = createProvider()

        // When
        val type = testedProvider.deviceType

        // Then
        assertThat(type).isEqualTo(DeviceType.TABLET)
    }

    @Test
    fun `𝕄 return Tablet type 𝕎 deviceType { tablet word in model name }`(
        @StringForgery(regex = "[a-zA-Z1-9 ]{0,9}Tablet[a-zA-Z1-9 ]{0,9}") fakeModel: String
    ) {
        // Given
        Build::class.java.setStaticValue("MODEL", fakeModel)
        testedProvider = createProvider()

        // When
        val type = testedProvider.deviceType

        // Then
        assertThat(type).isEqualTo(DeviceType.TABLET)
    }

    @Test
    fun `𝕄 return Tablet type 𝕎 deviceType { smallest screen width more than 800dp }`(
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
    fun `𝕄 return Mobile type 𝕎 deviceType { phone word in model name }`(
        @StringForgery(regex = "[a-zA-Z1-9 ]{0,9}Phone[a-zA-Z1-9 ]{0,9}") fakeModel: String
    ) {
        // Given
        Build::class.java.setStaticValue("MODEL", fakeModel)

        testedProvider = createProvider()

        // When
        val type = testedProvider.deviceType

        // Then
        assertThat(type).isEqualTo(DeviceType.MOBILE)
    }

    @ParameterizedTest
    @MethodSource("phoneTypesWithDescription")
    fun `𝕄 return Mobile type 𝕎 deviceType {smallest screen width less than 800dp + telephony}`(
        phoneType: PhoneType,
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
        whenever(mockTelephonyManager.phoneType) doReturn phoneType.value

        testedProvider = createProvider()

        // When
        val type = testedProvider.deviceType

        // Then
        assertThat(type).isEqualTo(DeviceType.MOBILE)
    }

    @Test
    fun `𝕄 return Other type 𝕎 deviceType { no tv, table or mobile properties }`(
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
    fun `𝕄 return full version 𝕎 osVersion`(
        @StringForgery(regex = "[1-9]{1,3}\\.[1-9]{1,3}\\.[1-9]{1,3}") fakeVersion: String
    ) {
        // Given
        Build.VERSION::class.java.setStaticValue("RELEASE", fakeVersion)
        testedProvider = createProvider()

        // When
        val osVersion = testedProvider.osVersion

        // Then
        assertThat(osVersion).isEqualTo(fakeVersion)
    }

    @Test
    fun `𝕄 return major version 𝕎 osMajorVersion { major - minor - patch format}`(
        @StringForgery(regex = "[1-9]{1,3}\\.[1-9]{1,3}\\.[1-9]{1,3}") fakeVersion: String
    ) {
        // Given
        Build.VERSION::class.java.setStaticValue("RELEASE", fakeVersion)
        testedProvider = createProvider()

        // When
        val osMajorVersion = testedProvider.osMajorVersion

        // Then
        assertThat(osMajorVersion).isEqualTo(fakeVersion.split(".").first())
    }

    @Test
    fun `𝕄 return major version 𝕎 osMajorVersion { generic format }`(
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) fakeVersion: String
    ) {
        // Given
        Build.VERSION::class.java.setStaticValue("RELEASE", fakeVersion)
        testedProvider = createProvider()

        // When
        val osMajorVersion = testedProvider.osMajorVersion

        // Then
        assertThat(osMajorVersion).isEqualTo(fakeVersion)
    }

    // endregion

    // region device name

    @Test
    fun `𝕄 return device name 𝕎 deviceName { brand is blank }`(
        @StringForgery fakeModel: String
    ) {
        // Given
        Build::class.java.setStaticValue("BRAND", "")
        Build::class.java.setStaticValue("MODEL", fakeModel)
        testedProvider = createProvider()

        // When
        val deviceName = testedProvider.deviceName

        // Then
        assertThat(deviceName).isEqualTo(fakeModel)
    }

    @Test
    fun `𝕄 return device name 𝕎 deviceName { model contains brand }`(
        @StringForgery fakeBrand: String,
        @StringForgery modelPrefix: String,
        @StringForgery modelSuffix: String
    ) {
        // Given
        val deviceModel = modelPrefix + fakeBrand.capitalize() + modelSuffix
        Build::class.java.setStaticValue("BRAND", fakeBrand)
        Build::class.java.setStaticValue("MODEL", deviceModel)
        testedProvider = createProvider()

        // When
        val deviceName = testedProvider.deviceName

        // Then
        assertThat(deviceName).isEqualTo(deviceModel)
    }

    @Test
    fun `𝕄 return device name 𝕎 deviceName { model doesn't contain brand }`(
        @StringForgery fakeBrand: String,
        @StringForgery fakeModel: String
    ) {
        // Given
        Build::class.java.setStaticValue("BRAND", fakeBrand)
        Build::class.java.setStaticValue("MODEL", fakeModel)
        testedProvider = createProvider()

        // When
        val deviceName = testedProvider.deviceName

        // Then
        assertThat(deviceName).isEqualTo("${fakeBrand.capitalize()} $fakeModel")
    }

    // endregion

    @Test
    fun `𝕄 return device brand 𝕎 deviceBrand { model doesn't contain brand }`(
        @StringForgery fakeBrand: String
    ) {
        // Given
        Build::class.java.setStaticValue("BRAND", fakeBrand)
        testedProvider = createProvider()

        // When
        val deviceBrand = testedProvider.deviceBrand

        // Then
        assertThat(deviceBrand).isEqualTo(fakeBrand.capitalize())
    }

    // region private

    private fun createProvider(): AndroidInfoProvider = DefaultAndroidInfoProvider(mockContext)

    private fun String.capitalize() =
        replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }

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
