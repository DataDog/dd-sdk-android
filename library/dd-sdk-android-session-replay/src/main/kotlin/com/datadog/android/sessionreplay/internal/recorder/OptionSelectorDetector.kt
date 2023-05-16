/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.view.ViewGroup

/**
 * Detects if a [ViewGroup] is a parent of selectable UI elements
 * (e.g. TextView, CheckBoxes, etc.).
 * This interface is meant for internal usage but feel free to provide an implementation
 * through the [com.datadog.android.sessionreplay.ExtensionSupport] if you need it.
 */
interface OptionSelectorDetector {

    /**
     * Checks and returns true if this [ViewGroup] is considered as a container of selectable UI
     * elements, otherwise returns false.
     */
    fun isOptionSelector(view: ViewGroup): Boolean
}
