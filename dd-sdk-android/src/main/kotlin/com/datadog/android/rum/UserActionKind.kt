/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

/**
 * Describes the type of an User action.
 * @see [RumMonitor]
 */
enum class UserActionKind(val actionName: String) {
    TAP("tap"),
    SWIPE("swipe"),
    SCROLL("scroll"),
    BACK("back"),
    CUSTOM("custom")
}
