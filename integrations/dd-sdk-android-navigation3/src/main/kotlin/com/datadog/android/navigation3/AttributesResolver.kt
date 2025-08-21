/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.navigation3

/**
 * Interface for resolving attributes from a key of navigation back stack.
 *
 * @param T the type of the key of navigation back stack.
 */
interface AttributesResolver<T> {
    fun resolveAttributes(
        destination: T
    ): Map<String, Any?>?
}
