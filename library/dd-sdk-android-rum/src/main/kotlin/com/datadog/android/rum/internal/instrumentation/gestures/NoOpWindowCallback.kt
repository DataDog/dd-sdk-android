/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.instrumentation.gestures

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

internal class NoOpWindowCallback : Window.Callback {

    // region Window.Callback
    override fun onActionModeFinished(mode: ActionMode?) {
        // No Op
    }

    override fun onCreatePanelView(featureId: Int): View? {
        // No Op
        return null
    }

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        // No Op
        return false
    }

    override fun onCreatePanelMenu(featureId: Int, menu: Menu): Boolean {
        // No Op
        return false
    }

    override fun onWindowStartingActionMode(callback: ActionMode.Callback?): ActionMode? {
        // No Op
        return null
    }

    override fun onWindowStartingActionMode(
        callback: ActionMode.Callback?,
        type: Int
    ): ActionMode? {
        // No Op
        return null
    }

    override fun onAttachedToWindow() {
        // No Op
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent?): Boolean {
        // No Op
        return false
    }

    @Suppress("FunctionMaxLength")
    override fun dispatchPopulateAccessibilityEvent(event: AccessibilityEvent?): Boolean {
        // No Op
        return false
    }

    override fun dispatchTrackballEvent(event: MotionEvent?): Boolean {
        // No Op
        return false
    }

    override fun dispatchKeyShortcutEvent(event: KeyEvent?): Boolean {
        // No Op
        return false
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        // No Op
        return false
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        // No Op
        return false
    }

    override fun onPanelClosed(featureId: Int, menu: Menu) {
        // No Op
    }

    override fun onMenuItemSelected(featureId: Int, item: MenuItem): Boolean {
        // No Op
        return false
    }

    override fun onDetachedFromWindow() {
        // No Op
    }

    override fun onPreparePanel(featureId: Int, view: View?, menu: Menu): Boolean {
        // No Op
        return false
    }

    override fun onWindowAttributesChanged(attrs: WindowManager.LayoutParams?) {
        // No Op
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        // No Op
    }

    override fun onContentChanged() {
        // No Op
    }

    override fun onSearchRequested(): Boolean {
        // No Op
        return false
    }

    override fun onSearchRequested(searchEvent: SearchEvent?): Boolean {
        // No Op
        return false
    }

    override fun onActionModeStarted(mode: ActionMode?) {
        // No Op
    }

    // endregion
}
