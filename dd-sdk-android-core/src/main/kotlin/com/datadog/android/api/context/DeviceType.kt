/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.api.context

/**
 * Device type.
 */
enum class DeviceType {
    /**
     * Mobile device type.
     */
    MOBILE,

    /**
     * Tablet device type.
     */
    TABLET,

    /**
     * TV device type.
     */
    TV,

    /**
     * Desktop device type.
     */
    DESKTOP,

    /**
     * Gaming console device type.
     */
    GAMING_CONSOLE,

    /**
     * Bot type.
     */
    BOT,

    /**
     * Other device type.
     */
    OTHER
}
