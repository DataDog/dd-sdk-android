/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.integration.tests.utils

import fr.xgouchet.elmyr.Forge

internal fun <T : Any, R : Any?> MutableMap<T, R>.removeRandomEntries(forge: Forge): Map<T, R> {
    val removedEntries = mutableMapOf<T, R>()
    val keys = this.keys.toList()
    val numberOfElementsToRemove = forge.anInt(1, keys.size)
    repeat(numberOfElementsToRemove) {
        val randomIndex = keys.indices.random()
        val key = keys[randomIndex]
        this.remove(key)?.let { removedEntries[key] = it }
    }

    return removedEntries
}
