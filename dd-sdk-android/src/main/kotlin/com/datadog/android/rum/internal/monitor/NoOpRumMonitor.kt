/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android.rum.internal.monitor

import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.RumResourceKind

internal class NoOpRumMonitor : RumMonitor {

    override fun addUserAction(
        action: String,
        attributes: Map<String, Any?>
    ) {
        // No Op
    }

    override fun startView(
        key: Any,
        name: String,
        attributes: Map<String, Any?>
    ) {
        // No Op
    }

    override fun stopView(
        key: Any,
        attributes: Map<String, Any?>
    ) {
        // No Op
    }

    override fun startResource(
        key: Any,
        url: String,
        attributes: Map<String, Any?>
    ) {
        // No Op
    }

    override fun stopResource(
        key: Any,
        kind: RumResourceKind,
        attributes: Map<String, Any?>
    ) {
        // No Op
    }

    override fun stopResourceWithError(
        key: Any,
        message: String,
        origin: String,
        throwable: Throwable
    ) {
        // No Op
    }

    override fun addError(
        message: String,
        origin: String,
        throwable: Throwable,
        attributes: Map<String, Any?>
    ) {
        // No Op
    }
}
