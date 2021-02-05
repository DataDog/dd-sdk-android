/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.tracking

import android.app.Fragment

/**
 * A predefined [ComponentPredicate] which accepts all [Fragment] to be tracked as RUM View event.
 * This is the default behaviour for the [FragmentViewTrackingStrategy].
 */
@Suppress("DEPRECATION")
open class AcceptAllDefaultFragment : ComponentPredicate<Fragment> {

    /** @inheritdoc */
    override fun accept(component: Fragment): Boolean {
        return true
    }

    /** @inheritdoc */
    override fun getViewName(component: Fragment): String? {
        return null
    }
}
