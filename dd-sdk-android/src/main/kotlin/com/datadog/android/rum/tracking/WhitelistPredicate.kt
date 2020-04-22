/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.tracking

/**
 * Used to whitelist an Activity or Fragment in order to be considered as a RUM View Event in our
 * Activity or Fragment view tracking strategies.
 * @see FragmentViewTrackingStrategy
 * @see ActivityViewTrackingStrategy
 */
interface WhitelistPredicate<T> {
    /**
     * Whether to whitelist or not a specific View.
     * @param view the View that needs to be marked or not as whitelisted
     * @return true if we want this View to be tracked, false otherwise.
     */
    fun accept(view: T): Boolean
}
