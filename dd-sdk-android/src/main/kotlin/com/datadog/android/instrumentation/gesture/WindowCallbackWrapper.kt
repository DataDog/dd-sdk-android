package com.datadog.android.instrumentation.gesture

import android.os.Build
import android.view.ActionMode
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.SearchEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi

internal class WindowCallbackWrapper(val wrappedCallback: Window.Callback) : Window.Callback {

    // region Window.Callback

    override fun onActionModeFinished(mode: ActionMode?) {
        wrappedCallback.onActionModeFinished(mode)
    }

    override fun onCreatePanelView(featureId: Int): View? {
        return wrappedCallback.onCreatePanelView(featureId)
    }

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        return wrappedCallback.dispatchTouchEvent(event)
    }

    override fun onCreatePanelMenu(featureId: Int, menu: Menu): Boolean {
        return wrappedCallback.onCreatePanelMenu(featureId, menu)
    }

    override fun onWindowStartingActionMode(callback: ActionMode.Callback?): ActionMode? {

        return wrappedCallback.onWindowStartingActionMode(callback)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onWindowStartingActionMode(
        callback: ActionMode.Callback?,
        type: Int
    ): ActionMode? {

        return wrappedCallback.onWindowStartingActionMode(callback, type)
    }

    override fun onAttachedToWindow() {

        wrappedCallback.onAttachedToWindow()
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent?): Boolean {

        return wrappedCallback.dispatchGenericMotionEvent(event)
    }

    @Suppress("FunctionMaxLength")
    override fun dispatchPopulateAccessibilityEvent(event: AccessibilityEvent?): Boolean {

        return wrappedCallback.dispatchPopulateAccessibilityEvent(event)
    }

    override fun dispatchTrackballEvent(event: MotionEvent?): Boolean {

        return wrappedCallback.dispatchTrackballEvent(event)
    }

    override fun dispatchKeyShortcutEvent(event: KeyEvent?): Boolean {

        return wrappedCallback.dispatchKeyShortcutEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {

        return wrappedCallback.dispatchKeyEvent(event)
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {

        return wrappedCallback.onMenuOpened(featureId, menu)
    }

    override fun onPanelClosed(featureId: Int, menu: Menu) {

        return wrappedCallback.onPanelClosed(featureId, menu)
    }

    override fun onMenuItemSelected(featureId: Int, item: MenuItem): Boolean {

        return wrappedCallback.onMenuItemSelected(featureId, item)
    }

    override fun onDetachedFromWindow() {

        wrappedCallback.onDetachedFromWindow()
    }

    override fun onPreparePanel(featureId: Int, view: View?, menu: Menu): Boolean {

        return wrappedCallback.onPreparePanel(featureId, view, menu)
    }

    override fun onWindowAttributesChanged(attrs: WindowManager.LayoutParams?) {

        return wrappedCallback.onWindowAttributesChanged(attrs)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {

        return wrappedCallback.onWindowFocusChanged(hasFocus)
    }

    override fun onContentChanged() {

        wrappedCallback.onContentChanged()
    }

    override fun onSearchRequested(): Boolean {

        return wrappedCallback.onSearchRequested()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onSearchRequested(searchEvent: SearchEvent?): Boolean {

        return wrappedCallback.onSearchRequested(searchEvent)
    }

    override fun onActionModeStarted(mode: ActionMode?) {

        wrappedCallback.onActionModeStarted(mode)
    }

    // endregion
}
