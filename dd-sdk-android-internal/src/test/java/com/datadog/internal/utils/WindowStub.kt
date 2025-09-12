/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.internal.utils

import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.InputQueue
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import android.view.Window
import org.mockito.Mockito

internal class WindowStub: Window(Mockito.mock()) {
    override fun addContentView(view: View?, params: ViewGroup.LayoutParams?) {
    }

    override fun closeAllPanels() {
    }

    override fun closePanel(featureId: Int) {
    }

    override fun getCurrentFocus(): View? {
        return null
    }

    override fun getDecorView(): View {
        TODO("Not yet implemented")
    }

    override fun getLayoutInflater(): LayoutInflater {
        TODO("Not yet implemented")
    }

    override fun getNavigationBarColor(): Int {
        TODO("Not yet implemented")
    }

    override fun getStatusBarColor(): Int {
        TODO("Not yet implemented")
    }

    override fun getVolumeControlStream(): Int {
        TODO("Not yet implemented")
    }

    override fun invalidatePanelMenu(featureId: Int) {
        TODO("Not yet implemented")
    }

    override fun isFloating(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isShortcutKey(keyCode: Int, event: KeyEvent?): Boolean {
        TODO("Not yet implemented")
    }

    override fun onActive() {
        TODO("Not yet implemented")
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        TODO("Not yet implemented")
    }

    override fun openPanel(featureId: Int, event: KeyEvent?) {
        TODO("Not yet implemented")
    }

    override fun peekDecorView(): View? {
        TODO("Not yet implemented")
    }

    override fun performContextMenuIdentifierAction(id: Int, flags: Int): Boolean {
        TODO("Not yet implemented")
    }

    override fun performPanelIdentifierAction(
        featureId: Int,
        id: Int,
        flags: Int
    ): Boolean {
        TODO("Not yet implemented")
    }

    override fun performPanelShortcut(
        featureId: Int,
        keyCode: Int,
        event: KeyEvent?,
        flags: Int
    ): Boolean {
        TODO("Not yet implemented")
    }

    override fun restoreHierarchyState(savedInstanceState: Bundle?) {
        TODO("Not yet implemented")
    }

    override fun saveHierarchyState(): Bundle? {
        TODO("Not yet implemented")
    }

    override fun setBackgroundDrawable(drawable: Drawable?) {
        TODO("Not yet implemented")
    }

    override fun setChildDrawable(featureId: Int, drawable: Drawable?) {
        TODO("Not yet implemented")
    }

    override fun setChildInt(featureId: Int, value: Int) {
        TODO("Not yet implemented")
    }

    override fun setContentView(view: View?) {
        TODO("Not yet implemented")
    }

    override fun setContentView(view: View?, params: ViewGroup.LayoutParams?) {
        TODO("Not yet implemented")
    }

    override fun setContentView(layoutResID: Int) {
        TODO("Not yet implemented")
    }

    override fun setDecorCaptionShade(decorCaptionShade: Int) {
        TODO("Not yet implemented")
    }

    override fun setFeatureDrawable(featureId: Int, drawable: Drawable?) {
        TODO("Not yet implemented")
    }

    override fun setFeatureDrawableAlpha(featureId: Int, alpha: Int) {
        TODO("Not yet implemented")
    }

    override fun setFeatureDrawableResource(featureId: Int, resId: Int) {
        TODO("Not yet implemented")
    }

    override fun setFeatureDrawableUri(featureId: Int, uri: Uri?) {
        TODO("Not yet implemented")
    }

    override fun setFeatureInt(featureId: Int, value: Int) {
        TODO("Not yet implemented")
    }

    override fun setNavigationBarColor(color: Int) {
        TODO("Not yet implemented")
    }

    override fun setResizingCaptionDrawable(drawable: Drawable?) {
        TODO("Not yet implemented")
    }

    override fun setStatusBarColor(color: Int) {
        TODO("Not yet implemented")
    }

    override fun setTitle(title: CharSequence?) {
        TODO("Not yet implemented")
    }

    override fun setTitleColor(textColor: Int) {
        TODO("Not yet implemented")
    }

    override fun setVolumeControlStream(streamType: Int) {
        TODO("Not yet implemented")
    }

    override fun superDispatchGenericMotionEvent(event: MotionEvent?): Boolean {
        TODO("Not yet implemented")
    }

    override fun superDispatchKeyEvent(event: KeyEvent?): Boolean {
        TODO("Not yet implemented")
    }

    override fun superDispatchKeyShortcutEvent(event: KeyEvent?): Boolean {
        TODO("Not yet implemented")
    }

    override fun superDispatchTouchEvent(event: MotionEvent?): Boolean {
        TODO("Not yet implemented")
    }

    override fun superDispatchTrackballEvent(event: MotionEvent?): Boolean {
        TODO("Not yet implemented")
    }

    override fun takeInputQueue(callback: InputQueue.Callback?) {
        TODO("Not yet implemented")
    }

    override fun takeKeyEvents(get: Boolean) {
        TODO("Not yet implemented")
    }

    override fun takeSurface(callback: SurfaceHolder.Callback2?) {
        TODO("Not yet implemented")
    }

    override fun togglePanel(featureId: Int, event: KeyEvent?) {
        TODO("Not yet implemented")
    }
}
