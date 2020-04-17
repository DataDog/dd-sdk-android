package com.datadog.android.rum.internal.tracking

import android.app.Activity
import android.app.Fragment
import android.app.FragmentManager
import android.os.Build
import androidx.annotation.RequiresApi
import com.datadog.android.core.internal.utils.resolveViewName
import com.datadog.android.rum.GlobalRum

@Suppress("DEPRECATION")
@RequiresApi(Build.VERSION_CODES.O)
internal class OreoFragmentLifecycleCallbacks(
    private val argumentsProvider: (Fragment) -> Map<String, Any?>
) : FragmentLifecycleCallbacks<Activity>, FragmentManager.FragmentLifecycleCallbacks() {

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

    override fun register(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.fragmentManager.registerFragmentLifecycleCallbacks(this, true)
        }
    }

    override fun unregister(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.fragmentManager.unregisterFragmentLifecycleCallbacks(this)
        }
    }
}
