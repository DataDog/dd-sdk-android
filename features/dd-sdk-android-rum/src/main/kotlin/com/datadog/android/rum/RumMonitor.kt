/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import android.app.Activity
import androidx.fragment.app.Fragment
import com.datadog.android.rum.featureoperations.FailureReason
import com.datadog.tools.annotation.NoOpImplementation

/**
 *  A class enabling Datadog RUM features.
 *
 *  It allows you to record User events that can be explored and analyzed in Datadog Dashboards.
 *
 *  You can only have one active RumMonitor, and should retrieve it from the [GlobalRumMonitor] object.
 */
@Suppress("ComplexInterface", "TooManyFunctions")
@NoOpImplementation
interface RumMonitor {
    /**
     * Get the current active session ID. The session ID will be null if no session is active or
     * if the session has been sampled out.
     *
     * This method uses an asynchronous callback to ensure all pending RUM events have been processed
     * up to the moment of the call.
     *
     * @param callback the callback to be invoked with the current session id.
     */
    fun getCurrentSessionId(callback: (String?) -> Unit)

    /**
     * Notifies that a View is being shown to the user, linked with the [key] instance.
     * @param key the instance that represents the active view (usually your
     * [Activity] or [Fragment] instance).
     * @param name the name of the view (usually your [Activity] or [Fragment] full class name)
     * @param attributes additional custom attributes to attach to the view. Attributes can be
     * nested up to 9 levels deep. Keys using more than 9 levels will be sanitized by SDK.
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
     * @param attributes additional custom attributes to attach to the view. Attributes can be
     * nested up to 9 levels deep. Keys using more than 9 levels will be sanitized by SDK.
     * @see [startView]
     */
    fun stopView(
        key: Any,
        attributes: Map<String, Any?> = emptyMap()
    )

    /**
     * Notifies that an action happened.
     * This is used to track discrete actions (e.g.: tap).
     * @param type the action type
     * @param name the action identifier
     * @param attributes additional custom attributes to attach to the action. Attributes can be
     * nested up to 9 levels deep. Keys using more than 9 levels will be sanitized by SDK.
     * @see [startAction]
     * @see [stopAction]
     */
    fun addAction(
        type: RumActionType,
        name: String,
        attributes: Map<String, Any?> = emptyMap()
    )

    /**
     * Notifies that an action started.
     * This is used to track long running actions (e.g.: scroll). Such an action must
     * be stopped with [stopAction], and will be stopped automatically if it lasts more than
     * 10 seconds.
     * @param type the action type
     * @param name the action identifier
     * @param attributes additional custom attributes to attach to the action. Attributes can be
     * nested up to 9 levels deep. Keys using more than 9 levels will be sanitized by SDK.
     * @see [stopAction]
     * @see [addAction]
     */
    fun startAction(
        type: RumActionType,
        name: String,
        attributes: Map<String, Any?> = emptyMap()
    )

    /**
     * Notifies that an action stopped, and update the action's type and name.
     * This is used to stop tracking long running actions (e.g.: scroll), started
     * with [startAction].
     * @param type the action type (overriding the last started action)
     * @param name the action identifier (overriding the last started action)
     * @param attributes additional custom attributes to attach to the action. Attributes can be
     * nested up to 9 levels deep. Keys using more than 9 levels will be sanitized by SDK.
     * @see [addAction]
     * @see [startAction]
     */
    fun stopAction(
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
     * @param attributes additional custom attributes to attach to the resource. Attributes can be
     * nested up to 9 levels deep. Keys using more than 9 levels will be sanitized by SDK.
     * @see [stopResource]
     * @see [stopResourceWithError]
     */
    fun startResource(
        key: String,
        method: RumResourceMethod,
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
     * @param attributes additional custom attributes to attach to the resource. Attributes can be
     * nested up to 9 levels deep. Keys using more than 9 levels will be sanitized by SDK.
     * @see [startResource]
     * @see [stopResourceWithError]
     */
    fun stopResource(
        key: String,
        statusCode: Int?,
        size: Long?,
        kind: RumResourceKind,
        attributes: Map<String, Any?> = emptyMap()
    )

    /**
     * Stops a previously started Resource that failed loading, linked with the [key] instance.
     * @param key the instance that represents the active view (usually your
     * request or network call instance).
     * @param statusCode the status code of the resource (if any)
     * @param message a message explaining the error
     * @param source the source of the error
     * @param throwable the throwable
     * @param attributes additional custom attributes to attach to the error. Attributes can be
     * nested up to 9 levels deep. Keys using more than 9 levels will be sanitized by SDK. Users
     * that want to supply a custom fingerprint for this error can add a value under the key
     * [RumAttributes.ERROR_FINGERPRINT]
     * @see [startResource]
     * @see [stopResource]
     */
    fun stopResourceWithError(
        key: String,
        statusCode: Int?,
        message: String,
        source: RumErrorSource,
        throwable: Throwable,
        attributes: Map<String, Any?> = emptyMap()
    )

    /**
     * Stops a previously started Resource that failed loading, linked with the [key] instance by
     * providing the intercepted stacktrace.
     * Note: This method should only be used from hybrid application.
     * @param key the instance that represents the active view (usually your
     * request or network call instance).
     * @param statusCode the status code of the resource (if any)
     * @param message a message explaining the error
     * @param source the source of the error
     * @param stackTrace the error stacktrace
     * @param errorType the type of the error. Usually it should be the canonical name of the
     * of the Exception class.
     * @param attributes additional custom attributes to attach to the error. Attributes can be
     * nested up to 9 levels deep. Keys using more than 9 levels will be sanitized by SDK. Users
     * that want to supply a custom fingerprint for this error can add a value under the key
     * [RumAttributes.ERROR_FINGERPRINT]
     * @see [startResource]
     * @see [stopResource]
     */
    @SuppressWarnings("LongParameterList")
    fun stopResourceWithError(
        key: String,
        statusCode: Int?,
        message: String,
        source: RumErrorSource,
        stackTrace: String,
        errorType: String?,
        attributes: Map<String, Any?> = emptyMap()
    )

    /**
     * Notifies that an error occurred in the active View.
     * @param message a message explaining the error
     * @param source the source of the error
     * @param throwable the throwable
     * @param attributes additional custom attributes to attach to the error. Attributes can be
     * nested up to 9 levels deep. Keys using more than 9 levels will be sanitized by SDK. Users
     * that want to supply a custom fingerprint for this error can add a value under the key
     * [RumAttributes.ERROR_FINGERPRINT]
     */
    fun addError(
        message: String,
        source: RumErrorSource,
        throwable: Throwable?,
        attributes: Map<String, Any?> = emptyMap()
    )

    /**
     * Notifies that an error occurred in the active View.
     *
     * This method is meant for non-native or cross platform frameworks (such as React Native or
     * Flutter) to send error information to Datadog. Although it can be used directly, it is
     * recommended to pass a Throwable instead.
     *
     * @param message a message explaining the error
     * @param source the source of the error
     * @param stacktrace the error stacktrace information
     * @param attributes additional custom attributes to attach to the error. Users
     * that want to supply a custom fingerprint for this error can add a value under the key
     * [RumAttributes.ERROR_FINGERPRINT]
     */
    fun addErrorWithStacktrace(
        message: String,
        source: RumErrorSource,
        stacktrace: String?,
        attributes: Map<String, Any?> = emptyMap()
    )

    /**
     * Adds a specific timing in the active View. The timing duration will be computed as the
     * difference between the time the View was started and the time this function was called.
     * @param name the name of the new custom timing attribute. Timings can be
     * nested up to 8 levels deep. Names using more than 8 levels will be sanitized by SDK.
     */
    fun addTiming(
        name: String
    )

    /**
     * Adds result of evaluating a feature flag to the view.
     * Feature flag evaluations are local to the active view and are cleared when the view is stopped.
     * If you need to submit more than one feature flag evaluation at the same time, consider using the
     * [addFeatureFlagEvaluations] method instead.
     * @param name the name of the feature flag
     * @param value the value the feature flag evaluated to
     */
    fun addFeatureFlagEvaluation(
        name: String,
        value: Any
    )

    /**
     * Adds result of evaluating a set of feature flag to the view.
     * Feature flag evaluations are local to the active view and are cleared when the view is stopped.
     * @param featureFlags the map of feature flags
     */
    fun addFeatureFlagEvaluations(featureFlags: Map<String, Any>)

    /**
     * Adds a global attribute to all future RUM events.
     * @param key the attribute key (non null)
     * @param value the attribute value (or null)
     */
    fun addAttribute(key: String, value: Any?)

    /**
     * Removes a global attribute from all future RUM events.
     * @param key the attribute key (non null)
     */
    fun removeAttribute(key: String)

    /**
     * @return the global attributes added to this monitor
     */
    fun getAttributes(): Map<String, Any?>

    /**
     * Clear all the global attributes added to this monitor.
     */
    fun clearAttributes()

    /**
     * Stops the current session.
     * A new session will start in response to a call to [startView], [addAction], or
     * [startAction]. If the session is started because of a call to [addAction],
     * or [startAction], the last know view is restarted in the new session.
     */
    fun stopSession()

    /**
     * Adds view loading time to the active view based on the time elapsed since the view was started.
     * The view loading time is automatically calculated as the difference between the current time
     * and the start time of the view.
     * This method should be called only once per view.
     * If no view is started or active, this method does nothing.
     * @param overwrite controls if the method overwrites the previously calculated view loading time.
     */
    @ExperimentalRumApi
    fun addViewLoadingTime(overwrite: Boolean)

    /**
     * Adds attributes to the current active View. They will be propagated to all future RUM events within this View
     * until it stops being active.
     * @param attributes the attributes to add to the view
     */
    fun addViewAttributes(attributes: Map<String, Any?>)

    /**
     * Removes attributes from the current active View. Future RUM events within this view won't be having these
     * attributes anymore.
     * @param attributes the attribute keys to remove from the view
     */
    fun removeViewAttributes(attributes: Collection<String>)

    /**
     * Starts the [name] feature operation.
     *
     * @param name the name of the operation.
     * @param operationKey optional operation key. Allows to track multiple operations of the same [name].
     * For example, multiple network requests (photo or file uploads) for the same URL.
     * @param attributes additional custom attributes to attach to the feature operation.
     */
    @ExperimentalRumApi
    fun startFeatureOperation(name: String, operationKey: String? = null, attributes: Map<String, Any?> = emptyMap())

    /**
     * Finishes the [name] feature operation with successful status.
     *
     * @param name the name of the operation.
     * @param operationKey optional operation key identifying a specific operation
     * instance from the list of feature operations of the same [name]. Should be provided if [operationKey] was
     * provided during [startFeatureOperation] invocation.
     * @param attributes additional custom attributes to attach to the feature operation. Can be
     * used to add some result data produced as the result of the operation.
     */
    @ExperimentalRumApi
    fun succeedFeatureOperation(name: String, operationKey: String? = null, attributes: Map<String, Any?> = emptyMap())

    /**
     * Finishes the [name] feature operation with failure status.
     *
     * @param name the name of the operation.
     * @param operationKey optional operation key identifying a specific operation
     * instance from the list of feature operations of the same [name]. Should be provided if [operationKey] was
     * provided during [startFeatureOperation] invocation.
     * @param failureReason the reason for the operation failure.
     * @param attributes additional custom attributes to attach to the feature operation. Can be
     * used to add some result data produced as the result of the operation.
     */
    @ExperimentalRumApi
    fun failFeatureOperation(
        name: String,
        operationKey: String? = null,
        failureReason: FailureReason,
        attributes: Map<String, Any?> = emptyMap()
    )

    /**
     * This method can be used to mark the moment in time when the UI of the app is considered fully displayed.
     * The duration between the application launch and this moment of time will be shown as TTFD (time to full display)
     * in the RUM session explorer. Only the first call to this method will have any effect for a given RUM session.
     */
    @ExperimentalRumApi
    fun reportAppFullyDisplayed()

    /**
     * Utility setting to inspect the active RUM View.
     * If set, a debugging outline will be displayed on top of the application, describing the name
     * of the active RUM View in the default SDK instance (if any).
     * May be used to debug issues with RUM instrumentation in your app.
     *
     * Default value is `false`.
     */
    var debug: Boolean

    /**
     * For Datadog internal use only.
     *
     * @see _RumInternalProxy
     */
    @Suppress("FunctionName")
    @JvmSynthetic
    fun _getInternal(): _RumInternalProxy?
}
