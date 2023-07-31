/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils

import com.datadog.android.api.InternalLogger
import com.datadog.android.rum.tracking.ComponentPredicate

/**
 * Executes the provided operation if the predicate verifies the argument.
 * @param T the type of component
 * @param component to be verified
 * @param internalLogger logger to use
 * @param operation to be executed
 */
@Suppress("TooGenericExceptionCaught")
internal inline fun <reified T : Any> ComponentPredicate<T>.runIfValid(
    component: T,
    internalLogger: InternalLogger,
    operation: (T) -> Unit
) {
    if (accept(component)) {
        try {
            operation(component)
        } catch (e: Exception) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { "Internal operation failed on ComponentPredicate" },
                e
            )
        }
    }
}

internal inline fun <reified T : Any> ComponentPredicate<T>.resolveViewName(component: T): String {
    val customName = getViewName(component)
    return if (customName.isNullOrBlank()) {
        component.resolveViewUrl()
    } else {
        customName
    }
}
