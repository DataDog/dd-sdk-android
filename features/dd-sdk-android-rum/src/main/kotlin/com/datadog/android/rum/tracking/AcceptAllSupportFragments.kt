/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.tracking

import androidx.fragment.app.Fragment

/**
 * A predefined [ComponentPredicate] which accepts all [Fragment] to be tracked as RUM View
 * event. This is the default behaviour of the [FragmentViewTrackingStrategy].
 */
open class AcceptAllSupportFragments : ComponentPredicate<Fragment> {

    /** @inheritdoc */
    override fun accept(component: Fragment): Boolean {
        return true
    }

    /** @inheritdoc */
    override fun getViewName(component: Fragment): String? {
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
