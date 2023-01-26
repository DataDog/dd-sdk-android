/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.callback

import android.view.Window
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks
import com.datadog.android.sessionreplay.internal.recorder.Recorder

internal class RecorderFragmentLifecycleCallback(
    private val recorder: Recorder
) : FragmentLifecycleCallbacks() {

    override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
        super.onFragmentResumed(fm, f)
        f.asValidDialogFragment {
            val ownerActivity = it.dialog?.ownerActivity ?: return@asValidDialogFragment
            it.getWindowsToRecord()?.let { windows ->
                recorder.startRecording(windows, ownerActivity)
            }
        }
    }

    override fun onFragmentPaused(fm: FragmentManager, f: Fragment) {
        f.asValidDialogFragment {
            it.getWindowsToRecord()?.let { windows ->
                recorder.stopRecording(windows)
                // in case the dialogWindow != ownerActivityWindow (windows size is >1)
                // we need to resume recording on the ownerActivityWindow if it is not finishing.
                // In the eventual case the activity is paused right after,
                // the recorder will be stopped in the `SessionReplayLifecycleCallback`
                val ownerActivity = it.dialog?.ownerActivity
                if (windows.size > 1 && ownerActivity != null && !(ownerActivity.isFinishing)) {
                    recorder.startRecording(listOf(windows[0]), ownerActivity)
                }
            }
        }
        super.onFragmentPaused(fm, f)
    }

    private fun DialogFragment.getWindowsToRecord(): List<Window>? {
        // a dialog can be displayed in the same activity as main UI but in a different window
        // using the Activity WindowManager. In order to correctly record the dialog whenever
        // this is displayed we will stop recording the main activity window and start recording
        // the dialog window + main activity window in the same time. We will cover here the
        // case where the Dialog window might be the same as the ownerActivity window. In this case
        // we want to do nothing as the current activity window is already recorded. We will just
        // return null in this case.
        val dialogWindow = dialog?.window
        val dialogOwnerActivity = dialog?.ownerActivity
        val ownerActivityWindow = dialogOwnerActivity?.window
        if (dialogWindow == null || dialogOwnerActivity == null || ownerActivityWindow == null) {
            return null
        }
        return if (dialogWindow != ownerActivityWindow) {
            // the order is very important here as it must have the activity window at the bottom
            listOf(ownerActivityWindow, dialogWindow)
        } else {
            null
        }
    }

    private fun Fragment.asValidDialogFragment(block: (DialogFragment) -> Unit) {
        if (this is DialogFragment && context != null) {
            block(this)
        }
    }
}
