/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import com.datadog.android.DatadogConfig
import com.datadog.android.DatadogInterceptor

/**
 * This class holds constant rum attribute keys.
 */
// @Suppress("unused")
object RumAttributes {

    // region Tags

    /**
     * The human readable version of the application. (String)
     * This value is extracted from your application's manifest and
     * filled automatically by the [RumMonitor].
     */
    const val APPLICATION_VERSION: String = "version"

    /**
     * The custom environment name. (Number)
     * This value is filled automatically by the [RumMonitor].
     */
    const val ENV: String = "env"

    /**
     * The name of the application or service generating the rum events. (String)
     * This values is configurable through the DatadogConfig during the SDK initialization.
     * By default it will take the application package name.
     * @see [DatadogConfig.Builder.setServiceName]
     */
    const val SERVICE_NAME: String = "service"

    /**
     * The technology from which the log originated. (String)
     * This value is filled automatically by the [RumMonitor].
     */
    const val SOURCE: String = "source"

    /**
     * Version of the current Datadog SDK.
     */
    const val SDK_VERSION: String = "sdk_version"

    // endregion

    // region Internal

    /**
     * Overrides the automatic RUM event timestamp with a custom one.
     */
    const val INTERNAL_TIMESTAMP: String = "_dd.timestamp"

    // endregion

    // region Resource

    /**
     * Trace Id related to the resource loading. (Number)
     * This value is filled automatically by the [DatadogInterceptor].
     */
    const val TRACE_ID: String = "_dd.trace_id"

    /**
     * Span Id related to the resource loading. (Number)
     * This value is filled automatically by the [DatadogInterceptor].
     */
    const val SPAN_ID: String = "_dd.span_id"

    // endregion

    // region Error

    /**
     * Indicates the action performed by the Resource which triggered the error. (String)
     * This value is filled automatically by the [RumMonitor] and the [RumInterceptor].
     * @see [RumMonitor.startResource]
     */
    const val ERROR_RESOURCE_METHOD: String = "error.resource.method"

    /**
     * The HTTP response status code for the Resource which triggered the error. (Number)
     * This value is filled automatically by the [RumInterceptor].
     * @see [RumMonitor.stopResourceWithError]
     */
    const val ERROR_RESOURCE_STATUS_CODE: String = "error.resource.status_code"

    /**
     * The URL of a loaded Resource which triggered the error. (String)
     * This value is filled automatically by the [RumMonitor] and the [RumInterceptor].
     * @see [RumMonitor.stopResourceWithError]
     */
    const val ERROR_RESOURCE_URL: String = "error.resource.url"

    /**
     * The version of the Database that triggered the error.
     * This value is filled automatically by the [RumMonitor].
     */
    const val ERROR_DATABASE_VERSION: String = "error.database.version"

    /**
     * The path of the Database that triggered the error.
     * This value is filled automatically by the [RumMonitor].
     */
    const val ERROR_DATABASE_PATH: String = "error.database.path"

    // endregion

    // region Action

    /**
     * The touch target class name. (String)
     * @see [RumMonitor.addAction]
     * @see [RumMonitor.startAction]
     * @see [RumMonitor.stopAction]
     */
    const val ACTION_TARGET_CLASS_NAME: String = "action.target.classname"

    /**
     * The title of the action's target. (String)
     * @see [RumMonitor.addAction]
     * @see [RumMonitor.startAction]
     * @see [RumMonitor.stopAction]
     */
    const val ACTION_TARGET_TITLE: String = "action.target.title"

    /**
     * The index of the touch target in the parent view. (Integer)
     * For now we only detect RecyclerView as parent.
     */
    const val ACTION_TARGET_PARENT_INDEX: String = "action.target.parent.index"

    /**
     * The class name of the touch target's parent view. (String)
     * For now we only detect RecyclerView as parent.
     */
    const val ACTION_TARGET_PARENT_CLASSNAME: String = "action.target.parent.classname"

    /**
     * The resource id of the target container in case this is a scrollable component. (String)
     * In case the resource id is missing we will provide the
     * container id in a Hexa String format (e.g. 0x1A2B1)
     * For now we only support the RecyclerView component.
     */
    const val ACTION_TARGET_PARENT_RESOURCE_ID: String = "action.target.parent.resource_id"

    /**
     * The touch target resource id. (String)
     * It can either be the resource identifier, or the raw hexadecimal value.
     * @see [RumMonitor.addAction]
     * @see [RumMonitor.startAction]
     * @see [RumMonitor.stopAction]
     */
    const val ACTION_TARGET_RESOURCE_ID: String = "action.target.resource_id"

    /**
     * The gesture event direction.
     */
    const val ACTION_GESTURE_DIRECTION: String = "action.gesture.direction"

    // endregion

    // region Network Info

    /**
     * The unique id of the Carrier attached to the SIM card. (Number)
     * This value is filled automatically by the [RumMonitor] for resources and errors.
     */
    const val NETWORK_CARRIER_ID: String = "network.client.sim_carrier.id"

    /**
     * The name of the Carrier attached to the SIM card. (String)
     * This value is filled automatically by the [RumMonitor] for resources and errors.
     */
    const val NETWORK_CARRIER_NAME: String = "network.client.sim_carrier.name"

    /**
     * The connectivity status of the device. (String)
     * This value is filled automatically by the [RumMonitor] for resources and errors.
     */
    const val NETWORK_CONNECTIVITY: String = "network.client.connectivity"

    /**
     * The downstream bandwidth for the current network in Kbps. (Number)
     * This value is filled automatically by the [RumMonitor] for resources and errors.
     */
    const val NETWORK_DOWN_KBPS: String = "network.client.downlink_kbps"

    /**
     * The bearer specific signal strength. (Number)
     * This value is filled automatically by the [RumMonitor] for resources and errors.
     */
    const val NETWORK_SIGNAL_STRENGTH: String = "network.client.signal_strength"

    /**
     * The upstream bandwidth for the current network in Kbps. (Number)
     * This value is filled automatically by the [RumMonitor] for resources and errors.
     */
    const val NETWORK_UP_KBPS: String = "network.client.uplink_kbps"

    /**
     * Total number of bytes transmitted from the client to the server. (Number)
     * TODO RUMM-469 rename to v2
     */
    const val NETWORK_BYTES_READ: String = "network.bytes_read"

    // endregion
}
