/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.bridge

/**
 * The entry point to use Datadog's RUM feature.
 */
interface DdRum {

    /**
     * Start tracking a RUM View.
     */
    fun startView(key: String, name: String, timestamp: Long, context: Map<String, Any?>): Unit

    /**
     * Stop tracking a RUM View.
     */
    fun stopView(key: String, timestamp: Long, context: Map<String, Any?>): Unit

    /**
     * Start tracking a RUM Action.
     */
    fun startAction(type: String, name: String, timestamp: Long, context: Map<String, Any?>): Unit

    /**
     * Stop tracking the ongoing RUM Action.
     */
    fun stopAction(timestamp: Long, context: Map<String, Any?>): Unit

    /**
     * Add a RUM Action.
     */
    fun addAction(type: String, name: String, timestamp: Long, context: Map<String, Any?>): Unit

    /**
     * Start tracking a RUM Resource.
     */
    fun startResource(
        key: String,
        method: String,
        url: String,
        timestamp: Long,
        context: Map<String, Any?>
    ): Unit

    /**
     * Stop tracking a RUM Resource.
     */
    fun stopResource(
        key: String,
        statusCode: Long,
        kind: String,
        timestamp: Long,
        context: Map<String, Any?>
    ): Unit

    /**
     * Add a RUM Error.
     */
    fun addError(
        message: String,
        source: String,
        stacktrace: String,
        timestamp: Long,
        context: Map<String, Any?>
    ): Unit
}
