@file:Suppress("PackageNaming")
/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package androidx.appcompat.widget

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable

// We need to stay in the `androidx.appcompat.widget` package to access package private fields
// Error: ActionBarContainer can only be accessed from within the same library group prefix
@Suppress("PackageNameVisibility", "RestrictedApi")
internal class DatadogActionBarContainerAccessor(
    val container: ActionBarContainer
) {

    @SuppressLint("RestrictedApi")
    fun getBackgroundDrawable(): Drawable? {
        return container.mBackground
    }

    @SuppressLint("RestrictedApi")
    fun setBackgroundDrawable(drawable: Drawable) {
        container.mBackground = drawable
    }
}
