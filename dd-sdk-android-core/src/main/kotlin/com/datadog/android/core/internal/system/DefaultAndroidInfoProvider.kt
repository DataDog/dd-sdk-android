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
import android.hardware.display.DisplayManager
import android.os.Build
import android.telephony.TelephonyManager
import android.view.Display
import com.datadog.android.api.context.DeviceType
import com.datadog.android.internal.system.BuildSdkVersionProvider
import java.util.Locale
import java.util.TimeZone

internal class DefaultAndroidInfoProvider(
    appContext: Context,
    rawDeviceBrand: String,
    rawDeviceModel: String,
    rawDeviceId: String,
    rawOsVersion: String,
    private val buildSdkVersionProvider: BuildSdkVersionProvider = BuildSdkVersionProvider.DEFAULT
) : AndroidInfoProvider {

    constructor(appContext: Context) : this(
        appContext,
        Build.BRAND.orEmpty(),
        Build.MODEL.orEmpty(),
        Build.ID.orEmpty(),
        Build.VERSION.RELEASE.orEmpty()
    )

    // lazy is just to avoid breaking the tests (because without lazy type is resolved at the
    // construction time and Build.MODEL is null in unit-tests) and also to have value resolved
    // once to avoid different values for foldables during the application lifecycle
    override val deviceType: DeviceType by lazy(LazyThreadSafetyMode.PUBLICATION) {
        resolveDeviceType(rawDeviceModel, appContext)
    }

    override val deviceName: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (deviceBrand.isBlank()) {
            deviceModel
        } else if (deviceModel.contains(deviceBrand)) {
            deviceModel
        } else {
            "$deviceBrand $deviceModel"
        }
    }

    override val locales: List<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val resources = appContext.resources
        val languageList = mutableListOf<String>()

        if (buildSdkVersionProvider.isAtLeastN) {
            val locales = resources.configuration.locales
            val localesSize = locales.size()

            for (localeIndex in 0 until localesSize) {
                locales[localeIndex]?.toLanguageTag()?.let {
                    languageList.add(it)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            resources.configuration.locale?.toLanguageTag()?.let {
                languageList.add(it)
            }
        }

        languageList
    }

    override val currentLocale: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val resources = appContext.resources
        val currentLocale = if (buildSdkVersionProvider.isAtLeastN) {
            resources.configuration.locales[0]?.toLanguageTag()
        } else {
            @Suppress("DEPRECATION")
            resources.configuration.locale?.toLanguageTag()
        }

        // null shouldn't happen, but if it does this ensures that we return a valid languageTag
        currentLocale ?: Locale.getDefault().toLanguageTag()
    }

    override val timeZone: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        TimeZone.getDefault().id
    }

    override val deviceBrand: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        rawDeviceBrand.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
        }
    }

    override val deviceModel: String = rawDeviceModel

    override val deviceBuildId: String = rawDeviceId

    override val osName: String = "Android"

    override val osVersion: String = rawOsVersion

    override val osMajorVersion: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        // result of split always have at least 1 element
        @Suppress("UnsafeThirdPartyFunctionCall")
        osVersion.split('.').first()
    }

    override val architecture: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        System.getProperty("os.arch") ?: "unknown"
    }

    override val numberOfDisplays: Int? by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val displayManager = appContext.getSystemService(Context.DISPLAY_SERVICE)
            as? DisplayManager ?: return@lazy null

        displayManager.displays.count {
            it.state !in setOf(
                Display.STATE_OFF,
                Display.STATE_UNKNOWN
            )
        }
    }

    companion object {

        const val FEATURE_GOOGLE_ANDROID_TV = "com.google.android.tv"
        const val MIN_TABLET_WIDTH_DP = 800

        private fun resolveDeviceType(model: String, appContext: Context): DeviceType {
            return if (isTv(appContext)) {
                DeviceType.TV
            } else if (isTablet(model, appContext)) {
                DeviceType.TABLET
            } else if (isMobile(model, appContext)) {
                DeviceType.MOBILE
            } else {
                DeviceType.OTHER
            }
        }

        private fun isTv(appContext: Context): Boolean {
            val uiModeManager =
                appContext.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
            if (uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
                return true
            }

            return hasTvFeature(appContext.packageManager)
        }

        private fun hasTvFeature(
            packageManager: PackageManager
        ): Boolean {
            return when {
                packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) -> true
                packageManager.hasSystemFeature(FEATURE_GOOGLE_ANDROID_TV) -> true
                else -> false
            }
        }

        private fun isTablet(
            model: String,
            appContext: Context
        ): Boolean {
            with(model.lowercase(Locale.US)) {
                if (contains("tablet") || contains("sm-t")) return true
            }
            return appContext.resources.configuration.smallestScreenWidthDp >= MIN_TABLET_WIDTH_DP
        }

        private fun isMobile(
            model: String,
            appContext: Context
        ): Boolean {
            if (model.lowercase(Locale.US).contains("phone")) return true

            val telephonyManager =
                appContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            return telephonyManager?.phoneType != TelephonyManager.PHONE_TYPE_NONE
        }
    }
}
