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
import android.os.Build
import android.telephony.TelephonyManager
import com.datadog.android.v2.api.context.DeviceType
import java.util.Locale

internal class DefaultAndroidInfoProvider(
    appContext: Context,
    sdkVersionProvider: BuildSdkVersionProvider = DefaultBuildSdkVersionProvider()
) : AndroidInfoProvider {

    // lazy is just to avoid breaking the tests (because without lazy type is resolved at the
    // construction time and Build.MODEL is null in unit-tests) and also to have value resolved
    // once to avoid different values for foldables during the application lifecycle
    override val deviceType: DeviceType by lazy(LazyThreadSafetyMode.PUBLICATION) {
        resolveDeviceType(
            appContext,
            sdkVersionProvider
        )
    }

    override val deviceName: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (deviceBrand.isEmpty()) {
            deviceModel
        } else if (deviceModel.contains(deviceBrand)) {
            deviceModel
        } else {
            "$deviceBrand $deviceModel"
        }
    }

    override val deviceBrand: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        Build.BRAND.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
        }
    }

    override val deviceModel: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        Build.MODEL
    }

    override val deviceBuildId: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        Build.ID
    }

    override val osName: String = "Android"

    override val osVersion: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        Build.VERSION.RELEASE
    }

    override val osMajorVersion: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        // result of split always have at least 1 element
        @Suppress("UnsafeThirdPartyFunctionCall")
        osVersion.split('.').first()
    }

    override val architecture: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        System.getProperty("os.arch") ?: "unknown"
    }

    companion object {

        const val FEATURE_GOOGLE_ANDROID_TV = "com.google.android.tv"
        const val MIN_TABLET_WIDTH_DP = 800

        private fun resolveDeviceType(
            appContext: Context,
            sdkVersionProvider: BuildSdkVersionProvider
        ): DeviceType {
            return if (isTv(appContext, sdkVersionProvider)) {
                DeviceType.TV
            } else if (isTablet(appContext)) {
                DeviceType.TABLET
            } else if (isMobile(appContext)) {
                DeviceType.MOBILE
            } else {
                DeviceType.OTHER
            }
        }

        private fun isTv(
            appContext: Context,
            sdkVersionProvider: BuildSdkVersionProvider
        ): Boolean {
            val uiModeManager =
                appContext.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
            if (uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
                return true
            }

            return hasTvFeature(appContext.packageManager, sdkVersionProvider)
        }

        private fun hasTvFeature(
            packageManager: PackageManager,
            sdkVersionProvider: BuildSdkVersionProvider
        ): Boolean {
            val sdkVersion = sdkVersionProvider.version()
            return when {
                sdkVersion >= Build.VERSION_CODES.LOLLIPOP &&
                    packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) -> true
                sdkVersion < Build.VERSION_CODES.LOLLIPOP &&
                    @Suppress("DEPRECATION")
                    packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION) -> true
                packageManager.hasSystemFeature(FEATURE_GOOGLE_ANDROID_TV) -> true
                else -> false
            }
        }

        private fun isTablet(
            appContext: Context
        ): Boolean {
            with(Build.MODEL.lowercase(Locale.US)) {
                if (contains("tablet") || contains("sm-t")) return true
            }
            return appContext.resources.configuration.smallestScreenWidthDp >= MIN_TABLET_WIDTH_DP
        }

        private fun isMobile(
            appContext: Context
        ): Boolean {
            if (Build.MODEL.lowercase(Locale.US).contains("phone")) return true

            val telephonyManager =
                appContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            return telephonyManager?.phoneType != TelephonyManager.PHONE_TYPE_NONE
        }
    }
}
