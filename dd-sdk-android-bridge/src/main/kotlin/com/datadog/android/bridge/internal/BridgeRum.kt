/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.bridge.internal

import com.datadog.android.bridge.DdRum
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceKind
import java.util.Locale

internal class BridgeRum : DdRum {

    override fun startView(key: String, name: String, timestamp: Long, context: Map<String, Any?>) {
        val attributes = context.toMutableMap().apply {
            put(RumAttributes.INTERNAL_TIMESTAMP, timestamp)
        }
        GlobalRum.get().startView(
            key = key,
            name = name,
            attributes = attributes
        )
    }

    override fun stopView(key: String, timestamp: Long, context: Map<String, Any?>) {
        val attributes = context.toMutableMap().apply {
            put(RumAttributes.INTERNAL_TIMESTAMP, timestamp)
        }
        GlobalRum.get().stopView(
            key = key,
            attributes = attributes
        )
    }

    override fun startAction(
        type: String,
        name: String,
        timestamp: Long,
        context: Map<String, Any?>
    ) {
        val attributes = context.toMutableMap().apply {
            put(RumAttributes.INTERNAL_TIMESTAMP, timestamp)
        }
        GlobalRum.get().startUserAction(
            type = type.asRumActionType(),
            name = name,
            attributes = attributes
        )
    }

    override fun stopAction(timestamp: Long, context: Map<String, Any?>) {
        val attributes = context.toMutableMap().apply {
            put(RumAttributes.INTERNAL_TIMESTAMP, timestamp)
        }
        GlobalRum.get().stopUserAction(
            attributes = attributes
        )
    }

    override fun addAction(
        type: String,
        name: String,
        timestamp: Long,
        context: Map<String, Any?>
    ) {
        val attributes = context.toMutableMap().apply {
            put(RumAttributes.INTERNAL_TIMESTAMP, timestamp)
        }
        GlobalRum.get().addUserAction(
            type = type.asRumActionType(),
            name = name,
            attributes = attributes
        )
    }

    override fun startResource(
        key: String,
        method: String,
        url: String,
        timestamp: Long,
        context: Map<String, Any?>
    ) {
        val attributes = context.toMutableMap().apply {
            put(RumAttributes.INTERNAL_TIMESTAMP, timestamp)
        }
        GlobalRum.get().startResource(
            key = key,
            method = method,
            url = url,
            attributes = attributes
        )
    }

    override fun stopResource(
        key: String,
        statusCode: Long,
        kind: String,
        timestamp: Long,
        context: Map<String, Any?>
    ) {
        val attributes = context.toMutableMap().apply {
            put(RumAttributes.INTERNAL_TIMESTAMP, timestamp)
        }
        GlobalRum.get().stopResource(
            key = key,
            statusCode = statusCode.toInt(),
            kind = kind.asRumResourceKind(),
            size = null,
            attributes = attributes
        )
    }

    override fun addError(
        message: String,
        source: String,
        stacktrace: String,
        timestamp: Long,
        context: Map<String, Any?>
    ) {
        val attributes = context.toMutableMap().apply {
            put(RumAttributes.INTERNAL_TIMESTAMP, timestamp)
        }
        GlobalRum.get().addErrorWithStacktrace(
            message = message,
            source = source.asErrorSource(),
            stacktrace = stacktrace,
            attributes = attributes
        )
    }

    // region Internal

    private fun String.asRumActionType(): RumActionType {
        return when (toLowerCase(Locale.US)) {
            "tap" -> RumActionType.TAP
            "scroll" -> RumActionType.SCROLL
            "swipe" -> RumActionType.SWIPE
            "click" -> RumActionType.CLICK
            else -> RumActionType.CUSTOM
        }
    }

    private fun String.asRumResourceKind(): RumResourceKind {
        return when (toLowerCase(Locale.US)) {
            "xhr" -> RumResourceKind.XHR
            "fetch" -> RumResourceKind.FETCH
            "document" -> RumResourceKind.DOCUMENT
            "beacon" -> RumResourceKind.BEACON
            "js" -> RumResourceKind.JS
            "image" -> RumResourceKind.IMAGE
            "font" -> RumResourceKind.FONT
            "css" -> RumResourceKind.CSS
            "media" -> RumResourceKind.MEDIA
            "other" -> RumResourceKind.OTHER
            else -> RumResourceKind.UNKNOWN
        }
    }

    private fun String.asErrorSource(): RumErrorSource {
        return when (toLowerCase(Locale.US)) {
            "agent" -> RumErrorSource.AGENT
            "console" -> RumErrorSource.CONSOLE
            "logger" -> RumErrorSource.LOGGER
            "network" -> RumErrorSource.NETWORK
            "source" -> RumErrorSource.SOURCE
            "webview" -> RumErrorSource.WEBVIEW
            else -> RumErrorSource.SOURCE
        }
    }

    // endregion
}
