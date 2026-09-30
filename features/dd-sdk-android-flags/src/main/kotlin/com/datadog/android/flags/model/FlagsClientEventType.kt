/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.model

/** Events supported by the native Flags client. */
enum class FlagsClientEventType {
    /** Assignments were installed. This event does not change client readiness. */
    CONFIGURATION_CHANGED
}
