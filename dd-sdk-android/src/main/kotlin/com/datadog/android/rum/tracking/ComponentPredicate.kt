/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.tracking

/**
 * Used to decide which Android component of your application should be considered
 * as a RUM View Event instance in a [ViewTrackingStrategy].
 * @see FragmentViewTrackingStrategy
 * @see ActivityViewTrackingStrategy
 */
interface ComponentPredicate<T> {
    /**
     * Decides whether the provided component should be considered as a RUM View.
     * Make sure you are consistent when you validate a component otherwise you may experience
     * some weird behaviours (e.g. by returning true in a first interrogation for a component
     * followed by false in a second interrogation we might alter the data in the in progress
     * View event or we might close the current valid View prematurely).
     * @param component a component whose state changed
     * @return true if you want the component to be tracked as a View, false otherwise
     *
     */
    fun accept(component: T): Boolean

    /**
     * Sets a custom name for the tracked RUM View.
     * @return the name to use for this view (if null or blank, the default will be used)
     */
    fun getViewName(component: T): String?
}
