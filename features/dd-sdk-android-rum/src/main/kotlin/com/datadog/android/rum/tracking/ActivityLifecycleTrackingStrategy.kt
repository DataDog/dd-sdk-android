/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.tracking

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.MainThread
import com.datadog.android.rum.RumFeature
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.SdkCore

/**
 * The ActivityLifecycleTrackingStrategy as an [Application.ActivityLifecycleCallbacks]
 * based implementation of the [TrackingStrategy].
 */
abstract class ActivityLifecycleTrackingStrategy :
    Application.ActivityLifecycleCallbacks,
    TrackingStrategy {

    private lateinit var sdkCore: SdkCore

    internal val internalLogger: InternalLogger
        get() = if (this::sdkCore.isInitialized) {
            sdkCore._internalLogger
        } else {
            InternalLogger.UNBOUND
        }

    // region TrackingStrategy

    override fun register(sdkCore: SdkCore, context: Context) {
        if (context is Application) {
            this.sdkCore = sdkCore
            context.registerActivityLifecycleCallbacks(this)
        } else {
            sdkCore._internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                "In order to use the RUM automatic tracking feature you will have" +
                    " to use the Application context when initializing the SDK"
            )
        }
    }

    override fun unregister(context: Context?) {
        if (context is Application) {
            context.unregisterActivityLifecycleCallbacks(this)
        }
    }

    // endregion

    // region Application.ActivityLifecycleCallbacks

    @MainThread
    override fun onActivityPaused(activity: Activity) {
        // No Op
    }

    @MainThread
    override fun onActivityStarted(activity: Activity) {
        // No Op
    }

    @MainThread
    override fun onActivityDestroyed(activity: Activity) {
        // No Op
    }

    @MainThread
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        // No Op
    }

    @MainThread
    override fun onActivityStopped(activity: Activity) {
        // No Op
    }

    @MainThread
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        // No Op
    }

    @MainThread
    override fun onActivityResumed(activity: Activity) {
        // No Op
    }

    // endregion

    // region Utils

    /**
     * Maps the Bundle key - value properties into compatible attributes for the Rum Events.
     * @param intent the [Intent] we need to transform. Returns an empty Map if this is null.
     */
    protected fun convertToRumAttributes(intent: Intent?): Map<String, Any?> {
        if (intent == null) return emptyMap()

        val attributes = mutableMapOf<String, Any?>()

        intent.action?.let {
            attributes[INTENT_ACTION_TAG] = it
        }
        intent.dataString?.let {
            attributes[INTENT_URI_TAG] = it
        }

        intent.extras?.let { bundle ->
            bundle.keySet().forEach {
                // TODO RUMM-2717 Bundle#get is deprecated, but there is no replacement for it.
                // Issue is opened in the Google Issue Tracker.
                @Suppress("DEPRECATION")
                attributes["$ARGUMENT_TAG.$it"] = bundle.get(it)
            }
        }

        return attributes
    }

    /**
     * Maps the Bundle key - value properties into compatible attributes for the Rum Events.
     * @param bundle the Bundle we need to transform. Returns an empty Map if this is null.
     */
    protected fun convertToRumAttributes(bundle: Bundle?): Map<String, Any?> {
        if (bundle == null) return emptyMap()

        val attributes = mutableMapOf<String, Any?>()

        bundle.keySet().forEach {
            // TODO RUMM-2717 Bundle#get is deprecated, but there is no replacement for it.
            // Issue is opened in the Google Issue Tracker.
            @Suppress("DEPRECATION")
            attributes["$ARGUMENT_TAG.$it"] = bundle.get(it)
        }

        return attributes
    }

    // endregion

    // region Helper

    /**
     * Runs a block if this tracking strategy is bound with an [SdkCore] instance.
     * @param T the return type for the block
     * @param block the block to run accepting the current SDK instance
     * @return the result of the block, or null if no SDK instance is available yet
     */
    protected fun <T> withSdkCore(block: (SdkCore) -> T): T? {
        return if (this::sdkCore.isInitialized) {
            block(sdkCore)
        } else {
            InternalLogger.UNBOUND.log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.USER,
                RumFeature.RUM_FEATURE_NOT_YET_INITIALIZED +
                    " Cannot provide SDK instance for view tracking."
            )
            null
        }
    }

    internal companion object {
        internal const val ARGUMENT_TAG = "view.arguments"
        internal const val INTENT_ACTION_TAG = "view.intent.action"
        internal const val INTENT_URI_TAG = "view.intent.uri"
    }
}
