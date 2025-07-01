/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.res.Resources
import android.provider.Settings
import android.provider.Settings.Secure.ACCESSIBILITY_DISPLAY_INVERSION_ENABLED
import android.util.Log
import android.view.accessibility.AccessibilityManager
import com.datadog.android.BuildConfig
import com.datadog.android.rum.internal.attribute.Accessibility

/**
 * Reads accessibility state from the Android system with caching for performance.
 * Thread-safe and uses application context to prevent memory leaks.
 */
internal class AccessibilityReader(private val applicationContext: Context) {
    
    // Cached state with timestamp
    @Volatile
    private var cachedState: Pair<Long, Accessibility>? = null
    private val cacheLock = Any()
    
    private val accessibilityManager: AccessibilityManager? by lazy {
        applicationContext.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    }

    private val resources: Resources by lazy {
        Resources.getSystem()
    }

    /**
     * Gets the current accessibility state with caching.
     * Cache is valid for 30 seconds to balance performance and accuracy.
     */
    internal fun getState(): Accessibility {
        synchronized(cacheLock) {
            val cached = cachedState
            val now = System.currentTimeMillis()
            
            if (cached != null && (now - cached.first) < CACHE_TIMEOUT_MS) {
                return cached.second
            }
            
            return loadAndCacheState(now)
        }
    }

    /**
     * Invalidates the cached state, forcing a fresh read on next access.
     * Useful when accessibility settings change.
     */
    internal fun invalidateCache() {
        synchronized(cacheLock) {
            cachedState = null
        }
    }

    private fun loadAndCacheState(timestamp: Long): Accessibility {
        val manager = accessibilityManager ?: return Accessibility.DEFAULT

        val state = Accessibility(
            textSize = getTextSizeSafely(),
            isScreenReaderEnabled = getScreenReaderEnabledSafely(manager),
            isColorInversionEnabled = getSecureValueSafely(ACCESSIBILITY_DISPLAY_INVERSION_ENABLED),
            isSwitchAccessEnabled = getSwitchAccessEnabledSafely(manager),
            isClosedCaptioningEnabled = getSecureValueSafely(CAPTIONING_ENABLED_KEY),
            isMonoAudioEnabled = getSecureValueSafely(MONO_AUDIO_ENABLED_KEY)
        )
        
        cachedState = timestamp to state
        return state
    }

    private fun getSecureValueSafely(key: String): Boolean? {
        return try {
            Settings.Secure.getInt(
                applicationContext.contentResolver,
                key,
                0
            ) == 1
        } catch (e: SecurityException) {
            logError("SecurityException reading accessibility settings", e)
            null
        } catch (e: Settings.SettingNotFoundException) {
            logError("Accessibility setting not found", e)
            null
        } catch (e: Exception) {
            logError("Unexpected error reading accessibility settings", e)
            null
        }
    }

    private fun getTextSizeSafely(): Float? {
        return try {
            val fontScale = resources.configuration.fontScale
            // Validate reasonable range - typical values are 0.85f to 3.0f
            if (fontScale in MIN_FONT_SCALE..MAX_FONT_SCALE) {
                fontScale
            } else {
                logError("Font scale out of expected range: $fontScale", null)
                null
            }
        } catch (e: Exception) {
            logError("Error reading font scale", e)
            null
        }
    }

    private fun getScreenReaderEnabledSafely(manager: AccessibilityManager): Boolean? {
        return try {
            manager.isEnabled && manager.isTouchExplorationEnabled
        } catch (e: SecurityException) {
            logError("SecurityException reading screen reader state", e)
            null
        } catch (e: Exception) {
            logError("Error reading screen reader state", e)
            null
        }
    }

    private fun getSwitchAccessEnabledSafely(manager: AccessibilityManager): Boolean? {
        return try {
            val enabledServices = manager
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                ?: return null
                
            // Check for Google Switch Access and other known switch access services
            enabledServices.any { serviceInfo ->
                val packageName = serviceInfo.resolveInfo?.serviceInfo?.packageName
                packageName in KNOWN_SWITCH_ACCESS_PACKAGES
            }
        } catch (e: SecurityException) {
            logError("SecurityException reading switch access state", e)
            null
        } catch (e: Exception) {
            logError("Error reading switch access state", e)
            null
        }
    }

    private fun logError(message: String, throwable: Throwable?) {
        if (BuildConfig.DEBUG) {
            Log.w(TAG, message, throwable)
        }
    }

    companion object {
        private const val TAG = "AccessibilityReader"
        private const val CACHE_TIMEOUT_MS = 30_000L // 30 seconds
        private const val CAPTIONING_ENABLED_KEY = "accessibility_captioning_enabled"
        private const val MONO_AUDIO_ENABLED_KEY = "accessibility_mono_audio_enabled"
        
        // Reasonable font scale range - values outside this are likely errors or extreme cases
        private const val MIN_FONT_SCALE = 0.5f
        private const val MAX_FONT_SCALE = 5.0f
        
        // Known switch access service packages
        private val KNOWN_SWITCH_ACCESS_PACKAGES = setOf(
            "com.google.android.marvin.switchaccess", // Google Switch Access
            "com.android.switchaccess", // AOSP Switch Access
        )
    }
}
