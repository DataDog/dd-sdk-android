/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace

/**
 * Defines whether the trace context should be injected into all requests or only sampled ones.
 */
enum class TraceContextInjection {
    /**
     * Injects trace context into all requests irrespective of the sampling decision.
     * For example if the request trace is sampled out, the trace context will still be injected in your request
     * headers but the sampling priority will be `0`. This will mean that the client will dictate the sampling priority
     * on the server side and no trace will be created no matter the sampling rate at the server side.
     */
    ALL,

    /**
     * Injects trace context only into sampled requests.
     * For example if the request trace is sampled out neither the trace context or the sampling priority will
     * be injected into the request headers leaving the server side to make the sampling decision.
     * This will mean that if the server side sampling rate is higher than the client side sampling rate there will
     * be a chance that a trace will be created down the stream.
     */
    SAMPLED
}
