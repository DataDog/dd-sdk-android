/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.callback;

import android.os.Build;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.KeyboardShortcutGroup;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SearchEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.List;

/**
 * A wrapper for {@link Window.Callback} that annotates parameters as {@code @Nullable}
 * where Android can pass null values, preventing Kotlin's non-null assertions from crashing.
 * This fixes the known Android bug: https://issuetracker.google.com/issues/188568911
 */
public class FixedWindowCallback implements Window.Callback {

    @NonNull
    private final Window.Callback delegate;

    public FixedWindowCallback(@NonNull Window.Callback delegate) {
        this.delegate = delegate;
    }

    @NonNull
    public Window.Callback getDelegate() {
        return delegate;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        return delegate.dispatchGenericMotionEvent(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return delegate.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchKeyShortcutEvent(KeyEvent event) {
        return delegate.dispatchKeyShortcutEvent(event);
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        return delegate.dispatchPopulateAccessibilityEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(@Nullable MotionEvent event) {
        return delegate.dispatchTouchEvent(event);
    }

    @Override
    public boolean dispatchTrackballEvent(MotionEvent event) {
        return delegate.dispatchTrackballEvent(event);
    }

    @Override
    public void onActionModeFinished(ActionMode mode) {
        delegate.onActionModeFinished(mode);
    }

    @Override
    public void onActionModeStarted(ActionMode mode) {
        delegate.onActionModeStarted(mode);
    }

    @Override
    public void onAttachedToWindow() {
        delegate.onAttachedToWindow();
    }

    @Override
    public void onContentChanged() {
        delegate.onContentChanged();
    }

    @Override
    public boolean onCreatePanelMenu(int featureId, @Nullable Menu menu) {
        if (menu == null) return false;
        return delegate.onCreatePanelMenu(featureId, menu);
    }

    @Nullable
    @Override
    public View onCreatePanelView(int featureId) {
        return delegate.onCreatePanelView(featureId);
    }

    @Override
    public void onDetachedFromWindow() {
        delegate.onDetachedFromWindow();
    }

    @Override
    public boolean onMenuItemSelected(int featureId, @Nullable MenuItem item) {
        if (item == null) return false;
        return delegate.onMenuItemSelected(featureId, item);
    }

    @Override
    public boolean onMenuOpened(int featureId, @Nullable Menu menu) {
        if (menu == null) return false;
        return delegate.onMenuOpened(featureId, menu);
    }

    @Override
    public void onPanelClosed(int featureId, @Nullable Menu menu) {
        if (menu == null) return;
        delegate.onPanelClosed(featureId, menu);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {
        delegate.onPointerCaptureChanged(hasCapture);
    }

    @Override
    public boolean onPreparePanel(int featureId, @Nullable View view, @Nullable Menu menu) {
        if (menu == null) return false;
        return delegate.onPreparePanel(featureId, view, menu);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onProvideKeyboardShortcuts(List<KeyboardShortcutGroup> data, @Nullable Menu menu, int deviceId) {
        delegate.onProvideKeyboardShortcuts(data, menu, deviceId);
    }

    @Override
    public boolean onSearchRequested() {
        return delegate.onSearchRequested();
    }

    @Override
    public boolean onSearchRequested(SearchEvent searchEvent) {
        return delegate.onSearchRequested(searchEvent);
    }

    @Override
    public void onWindowAttributesChanged(WindowManager.LayoutParams attrs) {
        delegate.onWindowAttributesChanged(attrs);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        delegate.onWindowFocusChanged(hasFocus);
    }

    @Nullable
    @Override
    public ActionMode onWindowStartingActionMode(ActionMode.Callback callback) {
        return delegate.onWindowStartingActionMode(callback);
    }

    @Nullable
    @Override
    public ActionMode onWindowStartingActionMode(ActionMode.Callback callback, int type) {
        return delegate.onWindowStartingActionMode(callback, type);
    }
}
