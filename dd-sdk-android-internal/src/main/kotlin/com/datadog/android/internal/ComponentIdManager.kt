/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.internal

import android.view.View
import com.datadog.tools.annotation.NoOpImplementation

/**
 * Generates globally unique, stable identifiers for Android Views for heatmap correlation.
 */
@NoOpImplementation(publicNoOpImplementation = true)
interface ComponentIdManager {

    /**
     * Sets the current screen identifier. Takes precedence over Activity-based detection.
     * @param identifier the screen identifier (typically RUM view URL), or null to clear
     */
    fun setCurrentScreen(identifier: String?)

    /**
     * Indexes a view tree for efficient ID lookups.
     * @param root the root view of the window
     */
    fun onWindowRefreshed(root: View)

    /**
     * Returns a globally unique identifier (32 hex chars) for the view, or null if detached.
     * @param view the view to identify
     */
    fun getComponentId(view: View): String?

    companion object {
        /**
         * Key used to store the ComponentIdManager instance in the feature context.
         */
        const val FEATURE_CONTEXT_KEY: String = "_dd.component_id_manager"
    }
}
