/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.rum

/**
 * Reason a RUM session was started.
 *
 * @property asString Value published in the RUM feature context.
 */
enum class RumSessionStartReason(val asString: String) {
    /** Session started by an application launch or the first user interaction. */
    USER_APP_LAUNCH("user_app_launch"),

    /** Session renewed after the inactivity timeout. */
    INACTIVITY_TIMEOUT("inactivity_timeout"),

    /** Session renewed after reaching its maximum duration. */
    MAX_DURATION("max_duration"),

    /** Session started while tracking background events. */
    BACKGROUND_LAUNCH("background_launch"),

    /** Session attributed to application prewarming. */
    PREWARM("prewarm"),

    /** Session transitioned from a non-interactive session. */
    FROM_NON_INTERACTIVE_SESSION("from_non_interactive_session"),

    /** Session renewed following an explicit reset request. */
    EXPLICIT_STOP("explicit_stop")
}
