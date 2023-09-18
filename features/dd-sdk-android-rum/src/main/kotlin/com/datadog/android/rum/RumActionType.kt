/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

/**
 * Describe the type of a RUM Action.
 * @see [RumMonitor]
 */
enum class RumActionType {
    /** User tapped on a widget. */
    TAP,

    /** User scrolled a view. */
    SCROLL,

    /** User swiped on a view. */
    SWIPE,

    /** User clicked on a widget (not used on Mobile). */
    CLICK,

    /** User navigated back. */
    BACK,

    /** A custom action. */
    CUSTOM,

    /** A Media Action */
    MEDIA,

    /** A Play Action */
    PLAY,

    /** A Pause Action */
    PAUSE,

    /** A Seek Action */
    SEEK
}
