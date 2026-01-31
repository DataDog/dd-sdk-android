/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.internal.identity

import android.view.View
import com.datadog.tools.annotation.NoOpImplementation

/**
 * Resolves globally unique, stable identities for Android Views based on their canonical path
 * in the view hierarchy. Used for heatmap correlation between RUM actions and Session Replay.
 */
@NoOpImplementation(publicNoOpImplementation = true)
interface ViewIdentityResolver {

    /**
     * Sets the current screen identifier. Takes precedence over Activity-based detection.
     * @param identifier the screen identifier (typically RUM view URL), or null to clear
     */
    fun setCurrentScreen(identifier: String?)

    /**
     * Indexes a view tree for efficient identity lookups.
     * @param root the root view of the window
     */
    fun onWindowRefreshed(root: View)

    /**
     * Resolves the stable identity for a view (32 hex chars), or null if the view is detached.
     * @param view the view to identify
     * @return the stable identity hash, or null if it cannot be computed
     */
    fun resolveViewIdentity(view: View): String?

    companion object {
        /**
         * Key used to store the ViewIdentityResolver instance in the feature context.
         */
        const val FEATURE_CONTEXT_KEY: String = "_dd.view_identity_resolver"
    }
}
