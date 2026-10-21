/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.rum

/**
 * State of a RUM session shared through the feature context.
 *
 * @property asString Value published in the RUM feature context.
 */
enum class RumSessionState(val asString: String) {
    /** Session is not sampled for RUM event collection. */
    NOT_TRACKED("NOT_TRACKED"),

    /** Session is sampled for RUM event collection. */
    TRACKED(RumSessionConstants.TRACKED_SESSION_STATE),

    /** Session has expired due to inactivity. */
    EXPIRED("EXPIRED")
}
