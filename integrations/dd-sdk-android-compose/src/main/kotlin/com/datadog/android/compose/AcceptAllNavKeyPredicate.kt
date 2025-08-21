/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.compose

import com.datadog.android.rum.tracking.ComponentPredicate

/**
 * A [ComponentPredicate] that accepts all keys of navigation backstack.
 *
 * This predicate is used when you want to track all navigation keys without any filtering.
 *
 * @param T the type of the component.
 */
open class AcceptAllNavKeyPredicate<T> : ComponentPredicate<T> {
    override fun accept(component: T): Boolean {
        return true
    }

    override fun getViewName(component: T): String? {
        return null
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return true
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }
}
