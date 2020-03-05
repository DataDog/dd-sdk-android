package com.datadog.android.support.fragments

import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import com.datadog.android.rum.GlobalRum

internal object CompatFragmentLifecycleCallbacks : FragmentManager.FragmentLifecycleCallbacks() {
    override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
        super.onFragmentResumed(fm, f)
        GlobalRum.get().startView(f, f.javaClass.canonicalName!!)
    }

    override fun onFragmentPaused(fm: FragmentManager, f: Fragment) {
        super.onFragmentPaused(fm, f)
        GlobalRum.get().stopView(f)
    }
}
