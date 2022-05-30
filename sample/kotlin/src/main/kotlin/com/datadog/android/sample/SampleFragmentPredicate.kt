/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample

import androidx.fragment.app.Fragment
import androidx.navigation.NavDestination
import com.datadog.android.rum.tracking.ComponentPredicate

class SampleFragmentPredicate : ComponentPredicate<Fragment> {
    override fun accept(component: Fragment): Boolean {
        return true
    }

    override fun getViewName(component: Fragment): String? {
        return null
    }
}
