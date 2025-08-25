/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.compose

/**
 * Resolves a stable key for an item in a backstack.
 * A stable key means that the same item will always return the same key during its lifetime in the
 * backstack.
 * This is used to identify items in the backstack and track them as Views in RUM.
 *
 * @param T the type of item in the backstack.
 */
interface BackstackKeyResolver<T> {

    /**
     * Returns a stable key for the given item.
     *
     * @param item the item to get the stable key for.
     * @return a stable key for the item.
     */
    fun getStableKey(item: T): String
}
