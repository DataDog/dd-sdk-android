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
        val ndkCrashesDirs =
            File(
                config.context.filesDir.absolutePath +
                    File.separator +
                    config.featurePersistenceDirName
            )
        registerSignalHandler(
            ndkCrashesDirs.absolutePath,
            config.serviceName,
            config.envName
        )
    }

    override fun unregister() {
        if (!nativeLibraryLoaded) {
            return
        }
        unregisterSignalHandler()
    }

    override fun onContextChanged(context: DatadogContext) {
        // TODO: RUMM-637 Only update the rum context if the `bundleWithRum` config attribute is true
        context.rum?.let {
            updateRumContext(it.applicationId, it.sessionId, it.viewId)
        }
    }

    // endregion

    // region NDK

    private external fun registerSignalHandler(
        storagePath: String,
        serviceName: String,
        environment: String
    )

    private external fun unregisterSignalHandler()

    private external fun updateRumContext(
        applicationId: String?,
        sessionId: String?,
        viewId: String?
    )

    // endregion

    private companion object {
        private const val TAG: String = "NdkCrashReportsPlugin"
        private const val ERROR_LOADING_NATIVE_MESSAGE: String =
            "We could not load the native library"
    }
}
