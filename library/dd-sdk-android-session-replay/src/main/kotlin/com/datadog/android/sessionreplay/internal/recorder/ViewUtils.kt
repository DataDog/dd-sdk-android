/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.os.Build
import android.view.View
import android.view.ViewStub
import androidx.appcompat.widget.ActionBarContextView
import androidx.appcompat.widget.Toolbar

internal class ViewUtils {

    private val systemViewIds by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setOf(android.R.id.navigationBarBackground, android.R.id.statusBarBackground)
        } else {
            emptySet()
        }
    }

    internal fun checkIfNotVisible(view: View): Boolean {
        return !view.isShown || view.width <= 0 || view.height <= 0
    }

    internal fun checkIfSystemNoise(view: View): Boolean {
        return view.id in systemViewIds ||
            view is ViewStub ||
            view is ActionBarContextView
    }

    internal fun checkIsToolbar(view: View): Boolean {
        return (
            Toolbar::class.java.isAssignableFrom(view::class.java) &&
                view.id == androidx.appcompat.R.id.action_bar
            ) ||
            (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
                    android.widget.Toolbar::class.java
                        .isAssignableFrom(view::class.java)
                )
    }

    internal fun resolveViewGlobalBounds(view: View, pixelsDensity: Float): ViewGlobalBounds {
        val coordinates = IntArray(2)
        // this will always have size >= 2
        @Suppress("UnsafeThirdPartyFunctionCall")
        view.getLocationOnScreen(coordinates)
        val x = coordinates[0].densityNormalized(pixelsDensity).toLong()
        val y = coordinates[1].densityNormalized(pixelsDensity).toLong()
        val height = view.height.densityNormalized(pixelsDensity).toLong()
        val width = view.width.densityNormalized(pixelsDensity).toLong()
        return ViewGlobalBounds(x = x, y = y, height = height, width = width)
    }
}
