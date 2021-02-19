/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.utils

import com.datadog.android.rum.tracking.ComponentPredicate

/**
 * Executes the provided operation if the predicate verifies the argument.
 * @param component to be verified
 * @param operation to be executed
 */
internal inline fun <reified T : Any> ComponentPredicate<T>.runIfValid(
    component: T,
    operation: (T) -> Unit
) {
    if (accept(component)) {
        operation(component)
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
