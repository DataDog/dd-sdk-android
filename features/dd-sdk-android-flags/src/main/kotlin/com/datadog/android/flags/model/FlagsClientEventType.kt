/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.model

/** Kind of [FlagsClientEvent]. */
enum class FlagsClientEventType {
    /** The installed flag configuration changed; [FlagsClientEvent.flagsChanged] lists the keys. */
    CONFIGURATION_CHANGED
}
