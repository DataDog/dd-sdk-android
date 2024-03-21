/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.utils

import android.view.View

/**
 * A utility interface to assign a unique id to the child of a View.
 * This interface is meant for internal usage, please use it carefully.
 */
interface ViewIdentifierResolver {

    /**
     * Resolves a persistent, unique id for the given view.
     * @param view the view
     * @return an identifier unquely mapping a view to a wireframe, allowing accurate diffs
     */
    fun resolveViewId(view: View): Long

    /**
     * Generates a persistent unique identifier for a virtual child view based on its unique
     * name and its physical parent. The identifier will only be created once and persisted in
     * the parent [View] tag to provide consistency. In case there was already a value with the
     * same key in the tags and this was used by a different party we will try to use this value
     * as identifier if it's a [Long], in other case we will return null. This last scenario is
     * highly unlikely but we are doing this in order to safely treat possible collisions with
     * client tags.
     * @param parent the parent [View] of the virtual child
     * @param childName the unique name of the virtual child
     * @return the unique identifier as [Long] or null if the identifier could not be created
     */
    fun resolveChildUniqueIdentifier(parent: View, childName: String): Long?
}
