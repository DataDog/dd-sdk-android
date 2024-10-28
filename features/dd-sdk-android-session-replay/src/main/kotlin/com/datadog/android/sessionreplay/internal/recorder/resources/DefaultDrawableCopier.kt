/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import android.content.res.Resources
import android.graphics.drawable.Drawable
import com.datadog.android.lint.InternalApi

/**
 * Default implementation of [DrawableCopier] interface, it copies the drawable from constant state.
 */
@InternalApi
class DefaultDrawableCopier : DrawableCopier {
    override fun copy(originalDrawable: Drawable, resources: Resources): Drawable? {
        return originalDrawable.constantState?.newDrawable(resources)
    }
}
