/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.compose

/**
 * Resolves a stable key for an item in a back stack.
 * A stable key means that the same item will always return the same key during its lifetime in the
 * back stack, and that two different items should not return the same key.
 *
 * This is used in [com.datadog.android.rum.RumMonitor.startView] and
 * [com.datadog.android.rum.RumMonitor.stopView] as the key to identify items in the back
 * stack and track them as Views in RUM.
 *
 * @param T the type of item in the back stack.
 */
interface BackStackKeyResolver<T : Any> {

    /**
     * Returns a stable key for the given item.
     *
     * @param item the item to get the stable key for.
     * @return a stable key for the item.
     */
    fun getStableKey(item: T): String
}
