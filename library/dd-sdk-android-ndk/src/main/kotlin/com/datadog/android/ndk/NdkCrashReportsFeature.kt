/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.ndk

import android.content.Context
import com.datadog.android.core.internal.utils.internalLogger
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.privacy.TrackingConsentProviderCallback
import com.datadog.android.v2.api.Feature
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.core.InternalSdkCore
import java.io.File
import java.lang.NullPointerException

/**
 * An implementation of the [Feature] which will allow to intercept and report the
 * NDK crashes to our logs dashboard.
 */
class NdkCrashReportsFeature : Feature, TrackingConsentProviderCallback {
    private var nativeLibraryLoaded = false

    override val name: String = "ndk-crash-reporting"

    // region Feature
    override fun onInitialize(
        sdkCore: SdkCore,
        appContext: Context
    ) {
        loadNativeLibrary()
        if (!nativeLibraryLoaded) {
            return
        }
        val internalSdkCore = sdkCore as InternalSdkCore
        val ndkCrashesDirs = File(
            internalSdkCore.rootStorageDir,
            NDK_CRASH_REPORTS_FOLDER
        )
        try {
            ndkCrashesDirs.mkdirs()
        } catch (e: SecurityException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                "Unable to create NDK Crash Report folder $ndkCrashesDirs",
                e
            )
            return
        }
        registerSignalHandler(
            ndkCrashesDirs.absolutePath,
            consentToInt(internalSdkCore.trackingConsent)
        )
    }

    override fun onStop() {
        if (!nativeLibraryLoaded) {
            return
        }
        unregisterSignalHandler()
    }

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

    private fun loadNativeLibrary() {
        var exception: Throwable? = null
        try {
            System.loadLibrary("datadog-native-lib")
            nativeLibraryLoaded = true
        } catch (e: SecurityException) {
            exception = e
        } catch (@SuppressWarnings("TooGenericExceptionCaught") e: NullPointerException) {
            exception = e
        } catch (e: UnsatisfiedLinkError) {
            exception = e
        }
        exception?.let {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                ERROR_LOADING_NATIVE_MESSAGE,
                exception
            )
        }
    }

    private external fun registerSignalHandler(
        storagePath: String,
        consent: Int
    )

    private external fun unregisterSignalHandler()

    private external fun updateTrackingConsent(consent: Int)

    // endregion

    internal companion object {
        internal const val NDK_CRASH_REPORTS_FOLDER = "ndk_crash_reports_v2"
        private const val ERROR_LOADING_NATIVE_MESSAGE =
            "We could not load the native library for NDK crash reporting."
        internal const val TRACKING_CONSENT_PENDING = 0
        internal const val TRACKING_CONSENT_GRANTED = 1
        internal const val TRACKING_CONSENT_NOT_GRANTED = 2
    }
}