/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.internal.rum

/**
 * Type of the RUM view represented by a feature context.
 *
 * @property asString Value published in the RUM feature context.
 */
enum class RumViewType(val asString: String) {
    /** No associated view. */
    NONE("NONE"),

    /** View tracked while the application is in the foreground. */
    FOREGROUND("FOREGROUND"),

    /** Synthetic view used to group background events. */
    BACKGROUND("BACKGROUND"),

    /** Synthetic view used to group events before the first tracked view. */
    APPLICATION_LAUNCH("APPLICATION_LAUNCH")
}
