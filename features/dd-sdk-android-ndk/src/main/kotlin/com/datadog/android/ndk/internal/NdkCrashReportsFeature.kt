/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.ndk.internal

import android.content.Context
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.internal.utils.allowThreadDiskReads
import com.datadog.android.internal.utils.allowThreadDiskWrites
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.privacy.TrackingConsentProviderCallback
import java.io.File
import java.lang.NullPointerException
import java.util.concurrent.TimeUnit

/**
 * An implementation of the [Feature] which will allow to intercept and report the
 * NDK crashes to our logs dashboard.
 */
internal class NdkCrashReportsFeature(
    private val sdkCore: FeatureSdkCore
) : Feature, TrackingConsentProviderCallback {
    private var nativeLibraryLoaded = false

    override val name: String = Feature.NDK_CRASH_REPORTS_FEATURE_NAME

    // region Feature
    @Suppress("ReturnCount")
    override fun onInitialize(appContext: Context) {
        loadNativeLibrary(sdkCore.internalLogger)
        if (!nativeLibraryLoaded) {
            return
        }
        val internalSdkCore = sdkCore as InternalSdkCore
        val rootStorageDir = internalSdkCore.rootStorageDir
        if (rootStorageDir == null) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { NO_SDK_ROOT_DIR_MESSAGE }
            )
            return
        }
        val ndkCrashesDirs = File(
            rootStorageDir,
            NDK_CRASH_REPORTS_FOLDER
        )
        try {
            allowThreadDiskWrites {
                ndkCrashesDirs.mkdirs()
            }
        } catch (e: SecurityException) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { "Unable to create NDK Crash Report folder $ndkCrashesDirs" },
                e
            )
            return
        }
        val appStartTimestamp =
            TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis()) - System.nanoTime() + sdkCore.appStartTimeNs
        registerSignalHandler(
            ndkCrashesDirs.absolutePath,
            consentToInt(internalSdkCore.trackingConsent),
            TimeUnit.NANOSECONDS.toMillis(appStartTimestamp)
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

    private fun loadNativeLibrary(internalLogger: InternalLogger) {
        var exception: Throwable? = null
        try {
            allowThreadDiskReads {
                System.loadLibrary("datadog-native-lib")
            }
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
                { ERROR_LOADING_NATIVE_MESSAGE },
                exception
            )
        }
    }

    private external fun registerSignalHandler(
        storagePath: String,
        consent: Int,
        appStartTimeMs: Long
    )

    private external fun unregisterSignalHandler()

    private external fun updateTrackingConsent(consent: Int)

    // endregion

    internal companion object {
        internal const val NDK_CRASH_REPORTS_FOLDER = "ndk_crash_reports_v2"
        private const val ERROR_LOADING_NATIVE_MESSAGE =
            "We could not load the native library for NDK crash reporting."
        internal const val NO_SDK_ROOT_DIR_MESSAGE =
            "Cannot get a directory for SDK data storage. Please make sure that SDK is initialized."
        internal const val TRACKING_CONSENT_PENDING = 0
        internal const val TRACKING_CONSENT_GRANTED = 1
        internal const val TRACKING_CONSENT_NOT_GRANTED = 2
    }
}
