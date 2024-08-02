/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

/**
 * Describes the type of a method associated with resource call.
 * @see [RumMonitor]
 */
enum class RumResourceMethod {
    /**
     * POST Method.
     */
    POST,

    /**
     * GET Method.
     */
    GET,

    /**
     * HEAD Method.
     */
    HEAD,

    /**
     * PUT Method.
     */
    PUT,

    /**
     * DELETE Method.
     */
    DELETE,

    /**
     * PATCH Method.
     */
    PATCH,

    /**
     * TRACE Method.
     */
    TRACE,

    /**
     * OPTIONS Method.
     */
    OPTIONS,

    /**
     * CONNECT Method.
     */
    CONNECT
}
