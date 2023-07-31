/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.view.View
import android.view.ViewStub
import androidx.appcompat.widget.ActionBarContextView
import androidx.appcompat.widget.Toolbar

internal class ViewUtilsInternal {

    private val systemViewIds by lazy {
        setOf(android.R.id.navigationBarBackground, android.R.id.statusBarBackground)
    }

    internal fun isNotVisible(view: View): Boolean {
        return !view.isShown || view.width <= 0 || view.height <= 0
    }

    internal fun isSystemNoise(view: View): Boolean {
        return view.id in systemViewIds ||
            view is ViewStub ||
            view is ActionBarContextView
    }

    internal fun isToolbar(view: View): Boolean {
        return Toolbar::class.java.isAssignableFrom(view::class.java) ||
            android.widget.Toolbar::class.java.isAssignableFrom(view::class.java)
    }
}
