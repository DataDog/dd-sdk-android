/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

/**
 * Selects the minimum protection that the SDK requires for flag assignment delivery.
 *
 * The matching client token must use the same trusted enrollment mode at Datadog's edge.
 * The SDK never changes this mode from a response or request parameter.
 */
enum class AssignmentProtection {
    /** Accepts the existing unsigned assignment protocol. */
    DISABLED,

    /** Requires a Datadog signature for each assignment response. */
    SIGNED,

    /** Requires customer authorization and a Datadog signature for each assignment response. */
    SIGNED_AND_AUTHORIZED
}
