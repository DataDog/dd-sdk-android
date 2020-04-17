/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.tracking

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.datadog.android.core.internal.utils.resolveViewName
import com.datadog.android.rum.GlobalRum

internal class AndroidXFragmentLifecycleCallbacks(
    internal val argumentsProvider: (Fragment) -> Map<String, Any?>
) : FragmentLifecycleCallbacks<FragmentActivity>, FragmentManager.FragmentLifecycleCallbacks() {
    override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
        super.onFragmentResumed(fm, f)
        GlobalRum.get()
            .startView(
                f,
                f.resolveViewName(),
                argumentsProvider(f)
            )
    }

    override fun onFragmentPaused(fm: FragmentManager, f: Fragment) {
        super.onFragmentPaused(fm, f)
        GlobalRum.get().stopView(f)
    }

    override fun register(activity: FragmentActivity) {
        activity.supportFragmentManager.registerFragmentLifecycleCallbacks(this, true)
    }

    override fun unregister(activity: FragmentActivity) {
        activity.supportFragmentManager.unregisterFragmentLifecycleCallbacks(this)
    }
}
