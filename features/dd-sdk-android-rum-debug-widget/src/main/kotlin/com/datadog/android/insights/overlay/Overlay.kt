/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.insights.overlay

import android.app.Activity

/**
 * Defines an overlay that can be attached to an [Activity].
 */
interface Overlay {
    /**
     * Attaches the overlay to the given [activity].
     */
    fun attach(activity: Activity)

    /**
     * Destroys the overlay and cleans up any resources.
     */
    fun destroy()
}
