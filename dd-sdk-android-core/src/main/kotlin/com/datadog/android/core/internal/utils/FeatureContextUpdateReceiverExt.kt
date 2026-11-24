/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.utils

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureContextUpdateReceiver
import java.util.Locale

internal const val CONTEXT_UPDATE_RECEIVER_FAILED =
    "Context update receiver \"%s\" threw an exception while handling the \"%s\" context update."

/**
 * Delivers a context update to the receiver, logging instead of propagating any exception it throws,
 * so that a faulty receiver can't break the caller or prevent other receivers from being notified.
 */
internal fun FeatureContextUpdateReceiver.safeOnContextUpdate(
    featureName: String,
    context: Map<String, Any?>,
    internalLogger: InternalLogger
) {
    try {
        onContextUpdate(featureName, context)
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            { CONTEXT_UPDATE_RECEIVER_FAILED.format(Locale.US, this, featureName) },
            e,
            onlyOnce = true
        )
    }
}
