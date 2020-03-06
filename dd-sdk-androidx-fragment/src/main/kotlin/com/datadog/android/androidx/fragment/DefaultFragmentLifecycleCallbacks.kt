package com.datadog.android.androidx.fragment

import android.app.Fragment
import android.app.FragmentManager
import android.os.Build
import androidx.annotation.RequiresApi
import com.datadog.android.rum.GlobalRum

@RequiresApi(Build.VERSION_CODES.O)
internal object DefaultFragmentLifecycleCallbacks : FragmentManager.FragmentLifecycleCallbacks() {

    override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
        super.onFragmentResumed(fm, f)
        GlobalRum.get().startView(f, f.javaClass.canonicalName!!)
    }

    override fun onFragmentPaused(fm: FragmentManager, f: Fragment) {
        super.onFragmentPaused(fm, f)
        GlobalRum.get().stopView(f)
    }
}
