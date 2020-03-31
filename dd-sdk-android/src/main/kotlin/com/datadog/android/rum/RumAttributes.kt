/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import com.datadog.android.Datadog
import com.datadog.android.DatadogConfig

/**
 * This class holds constant rum attribute keys.
 */
@Suppress("unused")
object RumAttributes {

    /**
     * The UUID of the application. (String)
     * This value is filled automatically by the [RumMonitor].
     * @see [DatadogConfig.Builder]
     */
    const val APPLICATION_ID: String = "application_id"

    /**
     * The package name of the application. (String)
     * This value is extracted from your application's manifest and
     * filled automatically by the [RumMonitor].
     */
    const val APPLICATION_PACKAGE: String = "application.package"

    /**
     * The human readable version of the application. (String)
     * This value is extracted from your application's manifest and
     * filled automatically by the [RumMonitor].
     */
    const val APPLICATION_VERSION: String = "application.version"

    /**
     * The date when the log is fired as an ISO-8601 String. (String)
     * This value is filled automatically by the [RumMonitor].
     */
    const val DATE: String = "date"

    /**
     * A duration of any kind in nanoseconds. (Number)
     * This value is filled automatically by the [RumMonitor] for
     * Views, Resources and Actions.
     */
    const val DURATION: String = "duration"

    /**
     * The error type or kind (or code is some cases). (String)
     * This value is filled automatically by the [RumMonitor] when you pass in a [Throwable].
     * @see [RumMonitor.addError]
     * @see [RumMonitor.stopResourceWithError]
     */
    const val ERROR_KIND: String = "error.kind"

    /**
     * A concise, human-readable, one-line message explaining the event. (String)
     * This value is filled automatically by the [RumMonitor] when you pass in a [Throwable].
     * @see [RumMonitor.addError]
     * @see [RumMonitor.stopResourceWithError]
     */
    const val ERROR_MESSAGE: String = "message"

    /**
     * Value among: agent, console, network, source, logger. (String)
     * This value is filled automatically by the [RumMonitor].
     * @see [RumMonitor.addError]
     */
    const val ERROR_ORIGIN: String = "error.origin"

    /**
     * The stack trace or the complementary information about the error. (String)
     * This value is filled automatically by the [RumMonitor] when you pass in a [Throwable].
     * @see [RumMonitor.addError]
     * @see [RumMonitor.stopResourceWithError]
     */
    const val ERROR_STACK: String = "error.stack"

    /**
     * The category of the event. (String)
     * This value is filled automatically by the [RumMonitor].
     */
    const val EVT_CATEGORY: String = "evt.category"

    /**
     * The UUID of the user action. (String)
     * This value is filled automatically by the [RumMonitor].
     */
    const val EVT_ID: String = "evt.id"

    /**
     * The name of the user action. (String)
     * @see [RumMonitor.addUserAction]
     */
    const val EVT_NAME: String = "evt.name"

    /**
     * The UUID of the active user action. (String)
     * This is used to link errors and resources with a user action.
     * This value is filled automatically by the [RumMonitor].
     */
    const val EVT_USER_ACTION_ID: String = "evt.user_action_id"

    /**
     * Indicates the desired action to be performed for a given resource. (String)
     * This value is filled automatically by the [RumMonitor] for Resources,
     * as well as by the [RumInterceptor].
     * @see [RumMonitor.startResource]
     */
    const val HTTP_METHOD: String = "http.method"

    /** Duration of the initial connection phase. (Number) */
    const val HTTP_PERFORMANCE_CONNECT_DURATION: String = "http.performance.connect.duration"

    /** Duration between the start of the page and the initial connection phase. (Number) */
    const val HTTP_PERFORMANCE_CONNECT_START: String = "http.performance.connect.start"

    /** Duration of the dns lookup phase. (Number) */
    const val HTTP_PERFORMANCE_DNS_DURATION: String = "http.performance.dns.duration"

    /** Duration between the start of the page and the dns lookup phase. (Number) */
    const val HTTP_PERFORMANCE_DNS_START: String = "http.performance.dns.start"

    /** Duration of the content download phase. (Number) */
    const val HTTP_PERFORMANCE_DOWNLOAD_DURATION: String = "http.performance.download.duration"

    /** Duration between the start of the page and the content download phase. (Number) */
    const val HTTP_PERFORMANCE_DOWNLOAD_START: String = "http.performance.download.start"

    /** Duration of the time to first byte phase. (Number) */
    const val HTTP_PERFORMANCE_FIRST_BYTE_DURATION: String = "http.performance.first_byte.duration"

    /** Duration between the start of the page and the time to first byte phase. (Number) */
    const val HTTP_PERFORMANCE_FIRST_BYTE_START: String = "http.performance.first_byte.start"

    /** Duration of the redirect phase. (Number) */
    const val HTTP_PERFORMANCE_REDIRECT_DURATION: String = "http.performance.redirect.duration"

    /** Start of the redirect phase. (Number) */
    const val HTTP_PERFORMANCE_REDIRECT_START: String = "http.performance.redirect.start"

    /** Duration of the secure connection phase. (Number) */
    const val HTTP_PERFORMANCE_SSL_DURATION: String = "http.performance.ssl.duration"

    /** Duration between the start of the page and the secure connection phase. (Number) */
    const val HTTP_PERFORMANCE_SSL_START: String = "http.performance.ssl.start"

    /**
     * HTTP header field that identifies the address of the web page
     * that linked to the resource being requested. (String)
     */
    const val HTTP_REFERRER: String = "http.referrer"

    /**
     * The HTTP response status code. (Number)
     * This value is filled automatically by the [RumInterceptor].
     */
    const val HTTP_STATUS_CODE: String = "http.status_code"

    /**
     * The URL of the HTTP request. (String)
     * This value is filled automatically by the [RumMonitor] for Resources,
     * as well as by the [RumInterceptor].
     * @see [RumMonitor.startResource]
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
     * Total number of bytes transmitted from the client to the server. (Number)
     */
    const val NETWORK_BYTES_READ: String = "network.bytes_read"

    /**
     * Total number of bytes transmitted from the server to the client. (Number)
     * This value is filled automatically by the [RumInterceptor].
     */
    const val NETWORK_BYTES_WRITTEN: String = "network.bytes_written"

    /** Value among: document, xhr, beacon, fetch, css, js, image, font, media, other. (String) */
    const val RESOURCE_KIND: String = "resource.kind"

    /**
     * Version of the view. (Number)
     * This value is filled automatically by the [RumMonitor].
     */
    const val RUM_DOCUMENT_VERSION: String = "rum.document_version"

    /**
     * The UUID of the session. (String)
     * This value is filled automatically by the [RumMonitor].
     */
    const val SESSION_ID: String = "session_id"

    /**
     * The technology from which the log originated. (String)
     * This value is filled automatically by the [RumMonitor].
     */
    const val SOURCE: String = "source"

    /**
     * Trace Id related to the resource loading. (Number)
     * This value is filled automatically by the [RumMonitor].
     */
    const val TRACE_ID: String = "trace_id"

    /**
     * The user email. (String)
     * This value is filled automatically by the [RumMonitor].
     * @see [Datadog.setUserInfo]
     */
    const val USER_EMAIL: String = "user.email"

    /**
     * The user identifier. (String)
     * This value is filled automatically by the [RumMonitor].
     * @see [Datadog.setUserInfo]
     */
    const val USER_ID: String = "user.id"

    /**
     * The user friendly name. (String)
     * This value is filled automatically by the [RumMonitor].
     * @see [Datadog.setUserInfo]
     */
    const val USER_NAME: String = "user.name"

    /** The UUID of the view. (String) */
    const val VIEW_ID: String = "view.id"

    /** Number of errors collected on the view. (Number)
     * This value is filled automatically by the [RumMonitor].
     */
    const val VIEW_MEASURES_ERROR_COUNT: String = "view.measures.error_count"

    /** Number of resources collected on the view. (Number)
     * This value is filled automatically by the [RumMonitor].
     */
    const val VIEW_MEASURES_RESOURCE_COUNT: String = "view.measures.resource_count"

    /** Number of user actions collected on the view. (Number)
     * This value is filled automatically by the [RumMonitor].
     */
    const val VIEW_MEASURES_USER_ACTION_COUNT: String = "view.measures.user_action_count"

    /**
     * The Url of the page that linked to the current page. (String)
     */
    const val VIEW_REFERRER: String = "view.referrer"

    /**
     * The Url of the view. (String)
     */
    const val VIEW_URL: String = "view.url"
}
