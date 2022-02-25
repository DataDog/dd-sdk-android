/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event

import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent
import java.util.Locale

internal class RumEventSourceProvider(source: String) {

    val viewEventSource: ViewEvent.Source? by lazy {
        try {
            ViewEvent.Source.fromJson(source)
        } catch (e: NoSuchElementException) {
            devLogger.e(UNKNOWN_SOURCE_WARNING_MESSAGE_FORMAT.format(Locale.US, source), e)
            null
        }
    }

    val longTaskEventSource: LongTaskEvent.Source? by lazy {
        try {
            LongTaskEvent.Source.fromJson(source)
        } catch (e: NoSuchElementException) {
            devLogger.e(UNKNOWN_SOURCE_WARNING_MESSAGE_FORMAT.format(Locale.US, source), e)
            null
        }
    }

    val errorEventSource: ErrorEvent.ErrorEventSource? by lazy {
        try {
            ErrorEvent.ErrorEventSource.fromJson(source)
        } catch (e: NoSuchElementException) {
            devLogger.e(UNKNOWN_SOURCE_WARNING_MESSAGE_FORMAT.format(Locale.US, source), e)
            null
        }
    }

    val actionEventSource: ActionEvent.Source? by lazy {
        try {
            ActionEvent.Source.fromJson(source)
        } catch (e: NoSuchElementException) {
            devLogger.e(UNKNOWN_SOURCE_WARNING_MESSAGE_FORMAT.format(Locale.US, source), e)
            null
        }
    }

    val resourceEventSource: ResourceEvent.Source? by lazy {
        try {
            ResourceEvent.Source.fromJson(source)
        } catch (e: NoSuchElementException) {
            devLogger.e(UNKNOWN_SOURCE_WARNING_MESSAGE_FORMAT.format(Locale.US, source), e)
            null
        }
    }

    companion object {
        internal const val UNKNOWN_SOURCE_WARNING_MESSAGE_FORMAT = "You are using an unknown " +
            "source %s for your events"
    }
}
