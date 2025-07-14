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
import android.provider.Settings
import android.provider.Settings.Secure.ACCESSIBILITY_DISPLAY_INVERSION_ENABLED
import android.view.accessibility.AccessibilityManager
import androidx.annotation.VisibleForTesting
import com.datadog.android.api.InternalLogger
import java.util.concurrent.atomic.AtomicInteger

internal class DatadogAccessibilityReader(
    private val internalLogger: InternalLogger,
    private val applicationContext: Context,
    private val resources: Resources? = Resources.getSystem(),
    private val activityManager: ActivityManager? =
        applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager,
    private val accessibilityManager: AccessibilityManager? =
        applicationContext.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager,
    private val secureWrapper: SecureWrapper = SecureWrapper(),
    private val globalWrapper: GlobalWrapper = GlobalWrapper(),
    private val cacheTimeoutMilliseconds: Int = CACHE_TIMEOUT_MILLISECONDS
) : AccessibilityReader {

    private var cacheMisses = AtomicInteger(0)

    @Volatile
    private var cachedAccessibilityState: Pair<Long, Accessibility>? = null

    @Synchronized
    override fun getState(): Map<String, Any> {
        val (cacheTime, cacheValue) = cachedAccessibilityState ?: Pair(null, null)

        return if (cacheValue != null && cacheTime != null && shouldUseCache(cacheTime)) {
            cacheValue.toMap()
        } else {
            cacheMisses.incrementAndGet()
            val accessibilityManager = accessibilityManager ?: return Accessibility.EMPTY_STATE.toMap()

            val accessibilityState = Accessibility(
                textSize = getTextSize(),
                isScreenReaderEnabled = getScreenReaderEnabled(accessibilityManager),
                isColorInversionEnabled = getSecureInt(ACCESSIBILITY_DISPLAY_INVERSION_ENABLED),
                isClosedCaptioningEnabled = getSecureInt(CAPTIONING_ENABLED_KEY),
                isReducedAnimationsEnabled = getReducedAnimationsEnabled(),
                isScreenPinningEnabled = getLockToScreenEnabled()
            )

            cachedAccessibilityState = Pair(System.currentTimeMillis(), accessibilityState)

            accessibilityState.toMap()
        }
    }

    @VisibleForTesting
    internal fun numCacheMisses(): Int {
        return cacheMisses.get()
    }

    private fun shouldUseCache(cacheTime: Long): Boolean {
        return System.currentTimeMillis() - cacheTime < cacheTimeoutMilliseconds
    }

    private fun getLockToScreenEnabled(): Boolean? {
        val localManager = activityManager ?: return null

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            localManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
        } else {
            @Suppress("DEPRECATION")
            localManager.isInLockTaskMode
        }
    }

    private fun getReducedAnimationsEnabled(): Boolean? {
        return globalWrapper.getFloat(
            applicationContext = applicationContext,
            internalLogger = internalLogger,
            key = Settings.Global.ANIMATOR_DURATION_SCALE
        )?.let {
            it == 0.0f
        }
    }

    private fun getSecureInt(key: String): Boolean? {
        return secureWrapper.getInt(
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

        // Check the accessibility state not more than once every 30 seconds
        private const val CACHE_TIMEOUT_MILLISECONDS = 30_000
    }
}
