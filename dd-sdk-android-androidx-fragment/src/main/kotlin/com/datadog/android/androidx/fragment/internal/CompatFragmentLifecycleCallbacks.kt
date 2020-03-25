package com.datadog.android.androidx.fragment.internal

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.datadog.android.rum.GlobalRum

internal class CompatFragmentLifecycleCallbacks(
    internal val argumentsProvider: (Fragment) -> Map<String, Any?>
) : LifecycleCallbacks<FragmentActivity>, FragmentManager.FragmentLifecycleCallbacks() {
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
