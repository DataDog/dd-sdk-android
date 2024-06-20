/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.monitor

import com.datadog.android.lint.InternalApi
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.RumResourceMethod
import com.datadog.android.rum.internal.domain.event.ResourceTiming
import com.datadog.android.rum.resource.ResourceId

/**
 * FOR INTERNAL USAGE ONLY.
 */
@SuppressWarnings("UndocumentedPublicFunction")
@InternalApi
interface AdvancedNetworkRumMonitor {

    @InternalApi
    fun waitForResourceTiming(key: Any)

    @InternalApi
    fun addResourceTiming(key: Any, timing: ResourceTiming)

    @InternalApi
    fun notifyInterceptorInstantiated()

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
    @InternalApi
    fun startResource(
        key: ResourceId,
        method: RumResourceMethod,
        url: String,
        attributes: Map<String, Any?> = emptyMap()
    )

    /**
     * Stops a previously started Resource, linked with the [key] instance.
     * @param key the instance that represents the active view (usually your
     * request or network call instance).
     * @param statusCode the status code of the resource (if any)
     * @param uploadSize the size of the resource, in bytes
     * @param downloadSize the size of the resource, in bytes
     * @param kind the type of resource loaded
     * @param attributes additional custom attributes to attach to the resource. Attributes can be
     * nested up to 9 levels deep. Keys using more than 9 levels will be sanitized by SDK.
     * @see [startResource]
     * @see [stopResourceWithError]
     */
    @InternalApi
    fun stopResource(
        key: ResourceId,
        statusCode: Int?,
        uploadSize: Long?,
        downloadSize: Long?,
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
     * @param attributes additional custom attributes to attach to the error. Attributes can be
     * nested up to 9 levels deep. Keys using more than 9 levels will be sanitized by SDK. Users
     * that want to supply a custom fingerprint for this error can add a value under the key
     * [RumAttributes.ERROR_FINGERPRINT]
     * @see [startResource]
     * @see [stopResource]
     */
    @InternalApi
    fun stopResourceWithError(
        key: ResourceId,
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
    @InternalApi
    @SuppressWarnings("LongParameterList")
    fun stopResourceWithError(
        key: ResourceId,
        statusCode: Int?,
        message: String,
        source: RumErrorSource,
        stackTrace: String,
        errorType: String?,
        attributes: Map<String, Any?> = emptyMap()
    )
}
