/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample

import androidx.navigation.NavDestination
import androidx.navigation.fragment.FragmentNavigator
import com.datadog.android.rum.tracking.ComponentPredicate
import com.datadog.android.sample.compose.ComposeFragment

class SampleNavigationPredicate : ComponentPredicate<NavDestination> {
    override fun accept(component: NavDestination): Boolean {
        if (component is FragmentNavigator.Destination &&
            component.className == ComposeFragment::class.java.canonicalName
        ) {
            // we will ignore this one, because it is just a host, not a screen itself,
            // exact screens are defined by Compose Navigation
            return false
        }
        return true
    }

    override fun getViewName(component: NavDestination): String {
        return component.label.toString()
    }
}
