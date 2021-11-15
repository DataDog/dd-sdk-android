/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.ndk

import android.util.Log
import com.datadog.android.plugin.DatadogContext
import com.datadog.android.plugin.DatadogPlugin
import com.datadog.android.plugin.DatadogPluginConfig
import com.datadog.android.privacy.TrackingConsent
import java.io.File
import java.lang.NullPointerException

/**
 * An implementation of the [DatadogPlugin] which will allow to intercept and report the
 * NDK crashes to our logs dashboard.
 */
@SuppressWarnings("TooGenericExceptionCaught")
class NdkCrashReportsPlugin : DatadogPlugin {
    private var nativeLibraryLoaded = false

    init {
        var exception: Throwable? = null
        try {
            System.loadLibrary("datadog-native-lib")
            nativeLibraryLoaded = true
        } catch (e: SecurityException) {
            exception = e
        } catch (e: NullPointerException) {
            exception = e
        } catch (e: UnsatisfiedLinkError) {
            exception = e
        }
        exception?.let {
            Log.e(TAG, ERROR_LOADING_NATIVE_MESSAGE, exception)
        }
    }

    // region Plugin
    override fun register(config: DatadogPluginConfig) {
        if (!nativeLibraryLoaded) {
            return
        }
        val ndkCrashesDirs = File(
            config.context.cacheDir,
            NDK_CRASH_REPORTS_FOLDER
        )
        try {
            ndkCrashesDirs.mkdirs()
        } catch (e: SecurityException) {
            Log.e("Datadog", "Unable to create NDK Crash Report folder $ndkCrashesDirs")
            return
        }
        registerSignalHandler(
            ndkCrashesDirs.absolutePath,
            consentToInt(config.trackingConsent)
        )
    }

    override fun unregister() {
        if (!nativeLibraryLoaded) {
            return
        }
        unregisterSignalHandler()
    }

    override fun onContextChanged(context: DatadogContext) {}

    // endregion

    // region TrackingConsentProviderCallback

    override fun onConsentUpdated(previousConsent: TrackingConsent, newConsent: TrackingConsent) {
        if (!nativeLibraryLoaded) {
            return
        }
        updateTrackingConsent(consentToInt(newConsent))
    }

    internal fun consentToInt(newConsent: TrackingConsent): Int {
        return when (newConsent) {
            TrackingConsent.PENDING -> TRACKING_CONSENT_PENDING
            TrackingConsent.GRANTED -> TRACKING_CONSENT_GRANTED
            else -> TRACKING_CONSENT_NOT_GRANTED
        }
    }

    // endregion

    // region NDK

    private external fun registerSignalHandler(
        storagePath: String,
        consent: Int
    )

    private external fun unregisterSignalHandler()

    private external fun updateTrackingConsent(consent: Int)

    // endregion

    companion object {
        internal const val NDK_CRASH_REPORTS_FOLDER = "ndk_crash_reports"
        private const val TAG: String = "NdkCrashReportsPlugin"
        private const val ERROR_LOADING_NATIVE_MESSAGE: String =
            "We could not load the native library"
        internal const val TRACKING_CONSENT_PENDING = 0
        internal const val TRACKING_CONSENT_GRANTED = 1
        internal const val TRACKING_CONSENT_NOT_GRANTED = 2
    }
}
