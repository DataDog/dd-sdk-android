/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.bridge

/**
 * The entry point to use Datadog's Logs feature.
 */
interface DdLogs {

    /**
     * Send a log with level debug.
     */
    fun debug(message: String, context: Map<String, Any?>): Unit

    /**
     * Send a log with level info.
     */
    fun info(message: String, context: Map<String, Any?>): Unit

    /**
     * Send a log with level warn.
     */
    fun warn(message: String, context: Map<String, Any?>): Unit

    /**
     * Send a log with level error.
     */
    fun error(message: String, context: Map<String, Any?>): Unit
}
