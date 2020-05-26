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
import com.datadog.android.Datadog
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
     * @param name the name of the view (usually your [Activity] or [Fragment] full class name)
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
     * @param attributes additional custom attributes to attach to the action
     * @see [startAction]
     * @see [stopAction]
     */
    fun addAction(
        type: String,
        attributes: Map<String, Any?> = emptyMap()
    )

    /**
     * Notifies that a User Action started.
     * This is used to track long running user actions (e.g.: scroll). Such a user action must
     * be stopped with [stopAction], and will be stopped automatically if it lasts more than
     * 10 seconds.
     * @param type the action type
     * @param attributes additional custom attributes to attach to the action
     * @see [stopAction]
     * @see [addAction]
     */
    fun startAction(
        type: String,
        attributes: Map<String, Any?> = emptyMap()
    )

    /**
     * Notifies that a User Action stopped.
     * This is used to stop tracking long running user actions (e.g.: scroll), started
     * with [startAction].
     * @param type the action type (overriding the last started action)
     * @param attributes additional custom attributes to attach to the action
     * @see [addAction]
     * @see [startAction]
     */
    fun stopAction(
        type: String,
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
     * @param mimeType the (nullable) mime type of the loaded resource, used to categorize it
     * @param attributes additional custom attributes to attach to the resource
     * @see [startResource]
     * @see [stopResourceWithError]
     */
    fun stopResource(
        key: String,
        type: RumResourceType,
        attributes: Map<String, Any?> = emptyMap()
    )

    /**
     * Stops a previously started Resource that failed loading, linked with the [key] instance.
     * @param key the instance that represents the active view (usually your
     * request or network call instance).
     * @param message a message explaining the error
     * @param source the source of the error (eg: "network", "sqlight", "assets", …)
     * @param throwable the throwable
     * @see [startResource]
     * @see [stopResource]
     */
    fun stopResourceWithError(
        key: String,
        message: String,
        source: String,
        throwable: Throwable
    )

    /**
     * Notifies that an error occurred in the active View.
     * @param message a message explaining the error
     * @param source the source of the error (eg: "network", "sqlight", "assets", …)
     * @param throwable the throwable
     * @param attributes additional custom attributes to attach to the error
     */
    fun addError(
        message: String,
        source: String,
        throwable: Throwable?,
        attributes: Map<String, Any?>
    )

    // region Builder

    /**
     * A Builder class for a [RumMonitor].
     */
    class Builder {

        /**
         * Builds a [RumMonitor] based on the current state of this Builder.
         */
        fun build(): RumMonitor {
            return if (!RumFeature.isInitialized()) {
                devLogger.e(Datadog.MESSAGE_NOT_INITIALIZED)
                NoOpRumMonitor()
            } else {
                DatadogRumMonitor(
                    applicationId = RumFeature.applicationId,
                    writer = RumFeature.persistenceStrategy.getWriter(),
                    handler = Handler(Looper.getMainLooper())
                )
            }
        }
    }
}
