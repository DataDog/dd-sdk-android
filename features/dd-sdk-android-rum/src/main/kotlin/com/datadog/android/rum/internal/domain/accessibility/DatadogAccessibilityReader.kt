/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.accessibility

import android.app.ActivityManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.provider.Settings.Secure.ACCESSIBILITY_DISPLAY_INVERSION_ENABLED
import android.view.accessibility.AccessibilityManager
import com.datadog.android.api.InternalLogger

internal class DatadogAccessibilityReader(
    private val internalLogger: InternalLogger,
    private val applicationContext: Context,
    private val resources: Resources? = Resources.getSystem(),
    private val activityManager: ActivityManager? =
        applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager,
    private val accessibilityManager: AccessibilityManager? =
        applicationContext.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager,
    private val secureWrapper: SecureWrapper = SecureWrapper(),
    private val globalWrapper: GlobalWrapper = GlobalWrapper()
) : AccessibilityReader {

    override fun getState(): Map<String, Any> {
        val accessibilityManager = accessibilityManager ?: return Accessibility.EMPTY_STATE.toMap()

        /**
         * No way to get the following:
         * mono_audio_enabled
         * isSwitchAccessEnabled
         * select_to_speak
         */
        return Accessibility(
            textSize = getTextSize(),
            isScreenReaderEnabled = getScreenReaderEnabled(accessibilityManager),
            isColorInversionEnabled = getSecureInt(ACCESSIBILITY_DISPLAY_INVERSION_ENABLED),
            isClosedCaptioningEnabled = getSecureInt(CAPTIONING_ENABLED_KEY),
            isReducedAnimationsEnabled = getReducedAnimationsEnabledSafely(),
            isScreenPinningEnabled = getLockToScreenEnabled()
        ).toMap()
    }

    private fun getLockToScreenEnabled(): Boolean? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            activityManager?.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
        } else {
            @Suppress("DEPRECATION")
            activityManager?.isInLockTaskMode
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun getReducedAnimationsEnabledSafely(): Boolean? {
        return globalWrapper.isReducedAnimationsEnabled(
            applicationContext = applicationContext,
            internalLogger = internalLogger
        )
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun getSecureInt(key: String): Boolean? {
        return secureWrapper.getIsSettingActive(
            internalLogger = internalLogger,
            applicationContext = applicationContext,
            key = key
        )
    }

    private fun getTextSize(): Float? {
        return resources?.configuration?.fontScale
    }

    private fun getScreenReaderEnabled(accessibilityManager: AccessibilityManager): Boolean {
        return accessibilityManager.isTouchExplorationEnabled
    }

    internal companion object {
        // https://android.googlesource.com/platform/frameworks/base/+/android-4.4.2_r2/core/java/android/provider/Settings.java
        internal const val CAPTIONING_ENABLED_KEY = "accessibility_captioning_enabled"
    }
}
