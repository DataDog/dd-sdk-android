/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder.resources

import android.content.res.Resources
import android.graphics.drawable.Drawable

/**
 * Default implementation of [DrawableCopier] interface, it copies the drawable from constant state.
 *
 * Some drawables return themselves from `ConstantState.newDrawable` (for example the border of a
 * Material `FloatingActionButton`). Drawing such a "copy" off the main thread would touch the
 * view's own drawable, so [copy] returns null for them.
 */
class DefaultDrawableCopier : DrawableCopier {
    override fun copy(originalDrawable: Drawable, resources: Resources): Drawable? {
        val copy = originalDrawable.constantState?.newDrawable(resources)
        return if (copy === originalDrawable) null else copy
    }
}
