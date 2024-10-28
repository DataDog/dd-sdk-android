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
 * Interface of copying drawable to a new one.
 */
@InternalApi
interface DrawableCopier {

    /**
     * Called to copy the drawable.
     * @param originalDrawable the original drawable to copy
     * @param resources resources of the view.
     *
     * @return New copied drawable.
     */
    fun copy(originalDrawable: Drawable, resources: Resources): Drawable?
}
