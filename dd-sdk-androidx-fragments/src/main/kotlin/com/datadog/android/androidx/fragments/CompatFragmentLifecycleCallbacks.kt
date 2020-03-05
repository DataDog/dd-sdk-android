package com.datadog.android.androidx.fragments

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
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
