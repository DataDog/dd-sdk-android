package com.datadog.android.androidx.fragment

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.datadog.android.rum.GlobalRum

internal object CompatFragmentLifecycleCallbacks : FragmentManager.FragmentLifecycleCallbacks() {
    override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
        super.onFragmentResumed(fm, f)
        val javaClass = f.javaClass
        GlobalRum.get().startView(f, javaClass.canonicalName ?: javaClass.simpleName)
    }

    override fun onFragmentPaused(fm: FragmentManager, f: Fragment) {
        super.onFragmentPaused(fm, f)
        GlobalRum.get().stopView(f)
    }
}
