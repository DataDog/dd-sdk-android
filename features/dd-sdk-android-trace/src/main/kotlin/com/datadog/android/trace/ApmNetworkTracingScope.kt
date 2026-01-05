/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace

/**
 * Defines the scope of network tracing instrumentation.
 *
 * This enum controls how detailed the tracing will be for network requests,
 * specifically whether internal network operations (redirects, retries) should be traced
 * in addition to the application-level requests.
 */
enum class ApmNetworkTracingScope {
    /**
     * Default one.
     * With this scope the Datadog SDK will trace both the application level requests and the network
     * layer requests (redirect, retries).
     */
    ALL,

    /**
     * Only application level request is gonna be traced.
     * In this mode the Datadog SDK still able to link trace spans to RUM Resources making possible to navigate
     * from one to another but the internal requests like redirects and retries ( if networking library allows that)
     * will not be traced.
     */
    APPLICATION_LEVEL_REQUESTS_ONLY
}
