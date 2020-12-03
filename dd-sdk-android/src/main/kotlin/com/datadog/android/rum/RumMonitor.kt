/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import android.app.Activity
import android.app.Fragment
import android.os.Handler
import android.os.Looper
import androidx.annotation.FloatRange
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.monitor.DatadogRumMonitor
import com.datadog.tools.annotation.NoOpImplementation

/**
 *  A class enabling Datadog RUM features.
 *
 *  It allows you to record User events that can be explored and analyzed in Datadog Dashboards.
 *
 *  You can only have one active RumMonitor, and should register/retrieve it from the [GlobalRum] object.
 */
@Suppress("ComplexInterface", "TooManyFunctions")
@NoOpImplementation
interface RumMonitor {

    /**
     * Notifies that a View is being shown to the user, linked with the [key] instance.
     * @param key the instance that represents the active view (usually your
     * [Activity] or [Fragment] instance).
     * @param name the name of the view (usually your [Activity] or [Fragment] full class name)
     * @param attributes additional custom attributes to attach to the view
     * @see [stopView]
     */
    fun startView(
        key: Any,
        name: String,
        attributes: Map<String, Any?> = emptyMap()
    )

    /**
     * Stops a previously started View, linked with the [key] instance.
     * @param key the instance that represents the active view (usually your
     * [Activity] or [Fragment] instance).
     * @param attributes additional custom attributes to attach to the view
     * @see [startView]
     */
    fun stopView(
        key: Any,
        attributes: Map<String, Any?> = emptyMap()
    )

    /**
     * Notifies that a User Action happened.
     * This is used to track discrete user actions (e.g.: tap).
     * @param type the action type
     * @param name the action identifier
     * @param attributes additional custom attributes to attach to the action
     * @see [startUserAction]
     * @see [stopUserAction]
     */
    fun addUserAction(
        type: RumActionType,
        name: String,
        attributes: Map<String, Any?>
    )

    /**
     * Notifies that a User Action started.
     * This is used to track long running user actions (e.g.: scroll). Such a user action must
     * be stopped with [stopUserAction], and will be stopped automatically if it lasts more than
     * 10 seconds.
     * @param type the action type
     * @param name the action identifier
     * @param attributes additional custom attributes to attach to the action
     * @see [stopUserAction]
     * @see [addUserAction]
     */
    fun startUserAction(
        type: RumActionType,
        name: String,
        attributes: Map<String, Any?>
    )

    /**
     * Notifies that a User Action stopped.
     * This is used to stop tracking long running user actions (e.g.: scroll), started
     * with [startUserAction].
     * @param attributes additional custom attributes to attach to the action
     * @see [addUserAction]
     * @see [startUserAction]
     */
    fun stopUserAction(
        attributes: Map<String, Any?> = emptyMap()
    )

    /**
     * Notifies that a User Action stopped, and update the action's type and name.
     * This is used to stop tracking long running user actions (e.g.: scroll), started
     * with [startUserAction].
     * @param type the action type (overriding the last started action)
     * @param name the action identifier (overriding the last started action)
     * @param attributes additional custom attributes to attach to the action
     * @see [addUserAction]
     * @see [startUserAction]
     */
    fun stopUserAction(
        type: RumActionType,
        name: String,
        attributes: Map<String, Any?> = emptyMap()
    )

    /**
     * Notify that a new Resource is being loaded, linked with the [key] instance.
     * @param key the instance that represents the resource being loaded (usually your
     * request or network call instance).
     * @param method the method used to load the resource (E.g., for network: "GET" or "POST")
     * @param url the url or local path of the resource being loaded
     * @param attributes additional custom attributes to attach to the resource
     * @see [stopResource]
     * @see [stopResourceWithError]
     */
    fun startResource(
        key: String,
        method: String,
        url: String,
        attributes: Map<String, Any?> = emptyMap()
    )

    /**
     * Stops a previously started Resource, linked with the [key] instance.
     * @param key the instance that represents the active view (usually your
     * request or network call instance).
     * @param statusCode the status code of the resource (if any)
     * @param size the size of the resource, in bytes
     * @param kind the type of resource loaded
     * @param attributes additional custom attributes to attach to the resource
     * @see [startResource]
     * @see [stopResourceWithError]
     */
    fun stopResource(
        key: String,
        statusCode: Int?,
        size: Long?,
        kind: RumResourceKind,
        attributes: Map<String, Any?>
    )

    /**
     * Stops a previously started Resource that failed loading, linked with the [key] instance.
     * @param key the instance that represents the active view (usually your
     * request or network call instance).
     * @param statusCode the status code of the resource (if any)
     * @param message a message explaining the error
     * @param source the source of the error
     * @param throwable the throwable
     * @see [startResource]
     * @see [stopResource]
     */
    fun stopResourceWithError(
        key: String,
        statusCode: Int?,
        message: String,
        source: RumErrorSource,
        throwable: Throwable
    )

    /**
     * Notifies that an error occurred in the active View.
     * @param message a message explaining the error
     * @param source the source of the error
     * @param throwable the throwable
     * @param attributes additional custom attributes to attach to the error
     */
    fun addError(
        message: String,
        source: RumErrorSource,
        throwable: Throwable?,
        attributes: Map<String, Any?>
    )

    /**
     * Notifies that an error occurred in the active View.
     * @param message a message explaining the error
     * @param source the source of the error
     * @param stacktrace the error stacktrace information
     * @param attributes additional custom attributes to attach to the error
     */
    fun addErrorWithStacktrace(
        message: String,
        source: RumErrorSource,
        stacktrace: String?,
        attributes: Map<String, Any?>
    )

    // region Builder

    /**
     * A Builder class for a [RumMonitor].
     */
    class Builder {

        private var samplingRate: Float = RumFeature.samplingRate

        /**
         * Sets the sampling rate for RUM Sessions.
         *
         * @param samplingRate the sampling rate must be a value between 0 and 100. A value of 0
         * means no RUM event will be sent, 100 means all sessions will be kept.
         */
        fun sampleRumSessions(@FloatRange(from = 0.0, to = 100.0) samplingRate: Float): Builder {
            this.samplingRate = samplingRate
            return this
        }

        /**
         * Builds a [RumMonitor] based on the current state of this Builder.
         */
        fun build(): RumMonitor {
            return if (!RumFeature.isInitialized()) {
                devLogger.e(RUM_NOT_ENABLED_ERROR_MESSAGE)
                NoOpRumMonitor()
            } else {
                DatadogRumMonitor(
                    applicationId = RumFeature.applicationId,
                    samplingRate = samplingRate,
                    writer = RumFeature.persistenceStrategy.getWriter(),
                    handler = Handler(Looper.getMainLooper()),
                    firstPartyHostDetector = CoreFeature.firstPartyHostDetector
                )
            }
        }

        companion object {
            internal const val RUM_NOT_ENABLED_ERROR_MESSAGE =
                "You're trying to create a RumMonitor instance, " +
                    "but the RUM feature was disabled in your DatadogConfig. " +
                    "No RUM data will be sent."
        }
    }
}
