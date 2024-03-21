/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log

import com.datadog.android.api.SdkCore

/**
 * This class holds constant log attribute keys.
 *
 * You can find more information on Datadog's log attributes at
 * https://docs.datadoghq.com/logs/processing/attributes_naming_convention/ .
 */
@Suppress("unused")
object LogAttributes {

    /**
     * The package name of the application. (String)
     * This value is extracted from your application's manifest and
     * filled automatically by the [Logger].
     */
    const val APPLICATION_PACKAGE: String = "application.package"

    /**
     * The human readable version of the application. (String)
     * This value is extracted from your application's manifest and
     * filled automatically by the [Logger].
     */
    const val APPLICATION_VERSION: String = "version"

    /**
     * The custom environment name. (Number)
     * This value is filled automatically by the [Logger].
     */
    const val ENV: String = "env"

    /**
     * The date when the log is fired as an ISO-8601 String. (String)
     * This value is filled automatically by the [Logger].
     */
    const val DATE: String = "date"

    /**
     * Database instance name. (String)
     */
    const val DB_INSTANCE: String = "db.instance"

    /**
     * The operation that was performed (“query”, “update”, “delete”,…). (String)
     */
    const val DB_OPERATION: String = "db.operation"

    /**
     * A database statement for the given database type. (String)
     * E.g., for mySQL: "SELECT * FROM wuser_table";
     */
    const val DB_STATEMENT: String = "db.statement"

    /**
     * User that performs the operation. (String)
     */
    const val DB_USER: String = "db.user"

    /**
     * The id of the active Span. (String)
     * This lets the Trace and Logs features to be linked.
     * This value is filled automatically by the [Logger].
     * @see [Logger.Builder.bundleWithTraceEnabled]
     */
    const val DD_SPAN_ID: String = "dd.span_id"

    /**
     * The id of the active Trace. (String)
     * This lets the Trace and Logs features to be linked.
     * This value is filled automatically by the [Logger].
     * @see [Logger.Builder.bundleWithTraceEnabled]
     */
    const val DD_TRACE_ID: String = "dd.trace_id"

    /**
     * A duration of any kind in nanoseconds. (Number)
     * E.g.: HTTP response time, database query time, latency, etc.
     */
    const val DURATION: String = "duration"

    /**
     * The error type or kind (or code is some cases). (String)
     * This value is filled automatically by the [Logger] when you pass in a [Throwable].
     */
    const val ERROR_KIND: String = "error.kind"

    /**
     * A concise, human-readable, one-line message explaining the event (String)
     * This value is filled automatically by the [Logger] when you pass in a [Throwable].
     */
    const val ERROR_MESSAGE: String = "error.message"

    /**
     * The stack trace or the complementary information about the error (String)
     * This value is filled automatically by the [Logger] when you pass in a [Throwable].
     */
    const val ERROR_STACK: String = "error.stack"

    /**
     * The source type of the error. This value is used to indicate the language or platform
     * that the error originates from, such as Flutter, React Native, or the NDK. (String)
     */
    const val ERROR_SOURCE_TYPE: String = "error.source_type"

    /**
     * The name of the originating host as defined in metrics. (String)
     * This value is automatically filled by the Datadog framework.
     */
    const val HOST: String = "host"

    /**
     * Indicates the desired action to be performed for a given resource. (String)
     */
    const val HTTP_METHOD: String = "http.method"

    /**
     * HTTP header field that identifies the address of the web page
     * that linked to the resource being requested. (String)
     */
    const val HTTP_REFERRER: String = "http.referrer"

    /**
     *  The ID of the HTTP request. (String)
     */
    const val HTTP_REQUEST_ID: String = "http.request_id"

    /**
     * The HTTP response status code. (Number)
     */
    const val HTTP_STATUS_CODE: String = "http.status_code"

    /**
     * The URL of the HTTP request. (String)
     */
    const val HTTP_URL: String = "http.url"

    /**
     * The User-Agent as it is sent (raw format). (String)
     * This value is automatically filled by the Datadog framework, using the System's "http.agent" property.
     */
    const val HTTP_USERAGENT: String = "http.useragent"

    /**
     * The version of HTTP used for the request. (String)
     */
    const val HTTP_VERSION: String = "http.version"

    /**
     * The class method name. (String)
     */
    const val LOGGER_METHOD_NAME: String = "logger.method_name"

    /**
     * The name of the logger. (String)
     * This value is filled automatically by the [Logger].
     * @see [Logger.Builder.setName]
     */
    const val LOGGER_NAME: String = "logger.name"

    /**
     * The name of the current thread when the log is fired. (String)
     * This value is filled automatically by the [Logger].
     */
    const val LOGGER_THREAD_NAME: String = "logger.thread_name"

    /**
     * The version of the logger. (String)
     * This value is filled automatically by the [Logger].
     */
    const val LOGGER_VERSION: String = "logger.version"

    /**
     * The body of the log entry. (String)
     * This value is filled automatically by the [Logger].
     */
    const val MESSAGE: String = "message"

    /**
     * The unique id of the Carrier attached to the SIM card. (Number)
     * This value is filled automatically by the [Logger].
     * @see [Logger.Builder.setNetworkInfoEnabled]
     */
    const val NETWORK_CARRIER_ID: String = "network.client.sim_carrier.id"

    /**
     * The name of the Carrier attached to the SIM card. (String)
     * This value is filled automatically by the [Logger].
     * @see [Logger.Builder.setNetworkInfoEnabled]
     */
    const val NETWORK_CARRIER_NAME: String = "network.client.sim_carrier.name"

    /**
     * The IP address of the client that initiated the TCP connection. (String)
     * This value is automatically filled by the Datadog framework.
     */
    const val NETWORK_CLIENT_IP: String = "network.client.ip"

    /**
     * The port of the client that initiated the connection. (Number)
     */
    const val NETWORK_CLIENT_PORT: String = "network.client.port"

    /**
     * The connectivity status of the device. (String)
     * This value is filled automatically by the [Logger].
     * @see [Logger.Builder.setNetworkInfoEnabled]
     */
    const val NETWORK_CONNECTIVITY: String = "network.client.connectivity"

    /**
     * The downstream bandwidth for the current network in Kbps. (Number)
     * This value is filled automatically by the [Logger].
     * @see [Logger.Builder.setNetworkInfoEnabled]
     */
    const val NETWORK_DOWN_KBPS: String = "network.client.downlink_kbps"

    /**
     * The bearer specific signal strength. (Number)
     * This value is filled automatically by the [Logger].
     * @see [Logger.Builder.setNetworkInfoEnabled]
     */
    const val NETWORK_SIGNAL_STRENGTH: String = "network.client.signal_strength"

    /**
     * The upstream bandwidth for the current network in Kbps. (Number)
     * This value is filled automatically by the [Logger].
     * @see [Logger.Builder.setNetworkInfoEnabled]
     */
    const val NETWORK_UP_KBPS: String = "network.client.uplink_kbps"

    /**
     * The RUM Application ID. (String)
     * This lets the RUM and Logs features to be linked.
     * This value is filled automatically by the [Logger].
     * @see [Logger.Builder.bundleWithRumEnabled]
     */
    const val RUM_APPLICATION_ID: String = "application_id"

    /**
     * The id of the active RUM session. (String)
     * This lets the RUM and Logs features to be linked.
     * This value is filled automatically by the [Logger].
     * @see [Logger.Builder.bundleWithRumEnabled]
     */
    const val RUM_SESSION_ID: String = "session_id"

    /**
     * The id of the active RUM View. (String)
     * This lets the RUM and Logs features to be linked.
     * This value is filled automatically by the [Logger].
     * @see [Logger.Builder.bundleWithRumEnabled]
     */
    const val RUM_VIEW_ID: String = "view.id"

    /**
     * The id of the active RUM Action. (String)
     * This lets the RUM and Logs features to be linked.
     * This value is filled automatically by the [Logger].
     * @see [Logger.Builder.bundleWithRumEnabled]
     */
    const val RUM_ACTION_ID: String = "user_action.id"

    /**
     * The name of the application or service generating the log events. (String)
     * This value is filled automatically by the [Logger].
     * @see [Configuration.Builder.service]
     * @see [Logger.Builder.setService]
     */
    const val SERVICE_NAME: String = "service"

    /**
     * The technology from which the log originated. (String)
     * This value is filled automatically by the [Logger].
     */
    const val SOURCE: String = "source"

    /**
     * The level/severity of a log. (String)
     * This value is filled automatically by the [Logger].
     */
    const val STATUS: String = "status"

    /**
     * Group containing user properties.
     *
     * @see USR_EMAIL
     * @see USR_ID
     * @see USR_NAME
     */
    const val USR_ATTRIBUTES_GROUP: String = "usr"

    /**
     * The user email. (String)
     * This value is filled automatically by the [Logger].
     * @see [SdkCore.setUserInfo]
     */
    const val USR_EMAIL: String = "$USR_ATTRIBUTES_GROUP.email"

    /**
     * The user identifier. (String)
     * This value is filled automatically by the [Logger].
     * @see [SdkCore.setUserInfo]
     */
    const val USR_ID: String = "$USR_ATTRIBUTES_GROUP.id"

    /**
     * The user friendly name. (String)
     * This value is filled automatically by the [Logger].
     * @see [SdkCore.setUserInfo]
     */
    const val USR_NAME: String = "$USR_ATTRIBUTES_GROUP.name"

    /**
     * The application variant. (String)
     * This value is filled automatically by the [Logger].
     */
    const val VARIANT: String = "variant"

    /**
     * The source type of an error. Used by cross platform tools to indicate the language
     * or platform that the error originates from, such as Flutter or React Native (String).
     */
    const val SOURCE_TYPE: String = "_dd.error.source_type"

    /**
     * Specifies a custom error fingerprint for the supplied log. (String)
     */
    const val ERROR_FINGERPRINT: String = "_dd.error.fingerprint"
}
