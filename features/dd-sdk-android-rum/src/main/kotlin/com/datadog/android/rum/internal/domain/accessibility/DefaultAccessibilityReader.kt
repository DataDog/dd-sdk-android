/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.accessibility

import android.app.ActivityManager
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.provider.Settings.Secure.ACCESSIBILITY_DISPLAY_INVERSION_ENABLED
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityManager.TouchExplorationStateChangeListener
import com.datadog.android.api.InternalLogger
import com.datadog.android.rum.internal.domain.InfoProvider
import java.util.concurrent.atomic.AtomicLong

@Suppress("TooManyFunctions")
internal class DefaultAccessibilityReader(
    private val internalLogger: InternalLogger,
    private val applicationContext: Context,
    private val resources: Resources = applicationContext.resources,
    private val activityManager: ActivityManager? =
        applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager,
    private val accessibilityManager: AccessibilityManager? =
        applicationContext.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager,
    private val secureWrapper: SecureWrapper = SecureWrapper(),
    private val globalWrapper: GlobalWrapper = GlobalWrapper(),
    private val handler: Handler = Handler(Looper.getMainLooper())
) : InfoProvider<AccessibilityInfo>, ComponentCallbacks {

    private val displayInversionListener = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            val newDisplayInversion = isDisplayInversionEnabled()
            updateState { it.copy(isColorInversionEnabled = newDisplayInversion) }
        }
    }

    private val captioningListener = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            val newCaptioningState = isClosedCaptioningEnabled()
            updateState { it.copy(isClosedCaptioningEnabled = newCaptioningState) }
        }
    }

    private val animationDurationListener = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            val newReducedAnimationsEnabled = isReducedAnimationsEnabled()
            updateState { it.copy(isReducedAnimationsEnabled = newReducedAnimationsEnabled) }
        }
    }

    private val touchListener = TouchExplorationStateChangeListener {
        val newScreenReaderEnabled = isScreenReaderEnabled(accessibilityManager)
        updateState { it.copy(isScreenReaderEnabled = newScreenReaderEnabled) }
    }

    @Volatile
    private var currentState = AccessibilityInfo()

    private var lastPollTime: AtomicLong = AtomicLong(0)

    init {
        registerListeners()
        buildInitialState()
    }

    override fun cleanup() {
        accessibilityManager?.removeTouchExplorationStateChangeListener(touchListener)
        applicationContext.contentResolver.unregisterContentObserver(animationDurationListener)
        applicationContext.contentResolver.unregisterContentObserver(captioningListener)
        applicationContext.contentResolver.unregisterContentObserver(displayInversionListener)
        applicationContext.unregisterComponentCallbacks(this)
    }

    override fun onLowMemory() {
        // do nothing - there's nothing we're holding onto that takes up any significant memory
    }

    override fun onConfigurationChanged(configuration: Configuration) {
        val isRtlEnabled = getRtlEnabled()
        val newTextSize = getTextSize()
        updateState {
            it.copy(textSize = newTextSize, isRtlEnabled = isRtlEnabled)
        }
    }

    @Synchronized
    override fun getState(): AccessibilityInfo {
        val currentTime = System.currentTimeMillis()
        val shouldPoll = currentTime - lastPollTime.get() >= POLL_THRESHOLD
        if (shouldPoll) {
            lastPollTime.set(currentTime)
            pollForAttributesWithoutListeners()
        }

        return currentState
    }

    @Synchronized
    private fun updateState(updater: (AccessibilityInfo) -> AccessibilityInfo) {
        currentState = updater(currentState)
    }

    private fun buildInitialState() {
        currentState = AccessibilityInfo(
            textSize = getTextSize(),
            isScreenReaderEnabled = isScreenReaderEnabled(accessibilityManager),
            isColorInversionEnabled = isDisplayInversionEnabled(),
            isScreenPinningEnabled = isLockToScreenEnabled(),
            isReducedAnimationsEnabled = isReducedAnimationsEnabled(),
            isClosedCaptioningEnabled = isClosedCaptioningEnabled(),
            isRtlEnabled = getRtlEnabled()
        )
    }

    private fun registerListeners() {
        applicationContext.registerComponentCallbacks(this)

        applicationContext.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(ACCESSIBILITY_DISPLAY_INVERSION_ENABLED),
            false,
            displayInversionListener
        )

        applicationContext.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(CAPTIONING_ENABLED_KEY),
            false,
            captioningListener
        )

        applicationContext.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            animationDurationListener
        )

        accessibilityManager?.addTouchExplorationStateChangeListener(touchListener)
    }

    private fun isDisplayInversionEnabled(): Boolean? {
        return isSettingEnabled(ACCESSIBILITY_DISPLAY_INVERSION_ENABLED)
    }

    private fun isClosedCaptioningEnabled(): Boolean? {
        return isSettingEnabled(CAPTIONING_ENABLED_KEY)
    }

    private fun isLockToScreenEnabled(): Boolean? {
        val localManager = activityManager ?: return null

        return localManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    private fun isReducedAnimationsEnabled(): Boolean? {
        return globalWrapper.getFloat(
            applicationContext = applicationContext,
            internalLogger = internalLogger,
            key = Settings.Global.ANIMATOR_DURATION_SCALE
        )?.let {
            it == 0.0f
        }
    }

    private fun isSettingEnabled(key: String): Boolean? {
        return secureWrapper.getInt(
            internalLogger = internalLogger,
            applicationContext = applicationContext,
            key = key
        )?.let {
            it == 1
        }
    }

    private fun getTextSize(): String {
        return resources.configuration.fontScale.toString()
    }

    private fun getRtlEnabled(): Boolean {
        return resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
    }

    private fun isScreenReaderEnabled(accessibilityManager: AccessibilityManager?): Boolean? {
        return accessibilityManager?.isTouchExplorationEnabled
    }

    private fun pollForAttributesWithoutListeners() {
        val newLockScreenEnabled = isLockToScreenEnabled()
        updateState { it.copy(isScreenPinningEnabled = newLockScreenEnabled) }
    }

    internal companion object {
        // https://android.googlesource.com/platform/frameworks/base/+/android-4.4.2_r2/core/java/android/provider/Settings.java
        internal const val CAPTIONING_ENABLED_KEY = "accessibility_captioning_enabled"

        // don't poll more than once in 30 seconds for attributes without listeners
        internal const val POLL_THRESHOLD = 30_000L
    }
}
