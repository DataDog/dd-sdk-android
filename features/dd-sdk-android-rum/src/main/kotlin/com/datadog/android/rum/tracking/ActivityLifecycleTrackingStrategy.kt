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
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor

/**
 * The ActivityLifecycleTrackingStrategy as an [Application.ActivityLifecycleCallbacks]
 * based implementation of the [TrackingStrategy].
 */
abstract class ActivityLifecycleTrackingStrategy :
    Application.ActivityLifecycleCallbacks,
    TrackingStrategy {

    /** The [FeatureSdkCore] this [TrackingStrategy] reports to. */
    protected lateinit var sdkCore: FeatureSdkCore

    internal val internalLogger: InternalLogger
        get() = if (this::sdkCore.isInitialized) {
            sdkCore.internalLogger
        } else {
            InternalLogger.UNBOUND
        }

    // region TrackingStrategy

    override fun register(sdkCore: SdkCore, context: Context) {
        if (context is Application) {
            this.sdkCore = sdkCore as FeatureSdkCore
            context.registerActivityLifecycleCallbacks(this)
        } else {
            (sdkCore as FeatureSdkCore).internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                {
                    "In order to use the RUM automatic tracking feature you will have" +
                        " to use the Application context when initializing the SDK"
                }
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
        val intent = activity.intent
        val extras = intent.safeExtras
        val testId = extras?.getString("_dd.synthetics.test_id")
        val resultId = extras?.getString("_dd.synthetics.result_id")
        if (!testId.isNullOrBlank() && !resultId.isNullOrBlank()) {
            (GlobalRumMonitor.get(sdkCore) as? AdvancedRumMonitor)
                ?.setSyntheticsAttribute(
                    testId,
                    resultId
                )
        }
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

        intent.safeExtras?.let { bundle ->
            bundle.keySet().forEach {
                // TODO RUM-503 Bundle#get is deprecated, but there is no replacement for it.
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
            // TODO RUM-503 Bundle#get is deprecated, but there is no replacement for it.
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
    protected fun <T> withSdkCore(block: (FeatureSdkCore) -> T): T? {
        return if (this::sdkCore.isInitialized) {
            block(sdkCore)
        } else {
            InternalLogger.UNBOUND.log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.USER,
                {
                    RumFeature.RUM_FEATURE_NOT_YET_INITIALIZED +
                        " Cannot provide SDK instance for view tracking."
                }
            )
            null
        }
    }

    private val Intent.safeExtras: Bundle?
        get() = try {
            // old Androids can throw different exceptions here making native calls
            extras
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { "Error getting Intent extras, ignoring it." },
                e
            )
            null
        }

    internal companion object {
        internal const val ARGUMENT_TAG = "view.arguments"
        internal const val INTENT_ACTION_TAG = "view.intent.action"
        internal const val INTENT_URI_TAG = "view.intent.uri"

        internal const val EXTRA_SYNTHETICS_TEST_ID = "_dd.synthetics.test_id"
        internal const val EXTRA_SYNTHETICS_RESULT_ID = "_dd.synthetics.result_id"
    }
}
