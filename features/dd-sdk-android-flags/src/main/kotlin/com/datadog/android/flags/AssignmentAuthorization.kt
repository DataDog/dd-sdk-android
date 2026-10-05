/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

import java.util.Date

/**
 * A cached customer token that authorizes protected assignment requests.
 *
 * The application owns durable token storage and refresh. The SDK keeps this value in memory.
 */
data class AssignmentAuthorization(
    /** The compact JWT that the customer backend issued. */
    val bearerToken: String,
    /** The time after which the SDK must stop using the token. */
    val expiresAt: Date
) {
    override fun toString(): String =
        "AssignmentAuthorization(bearerToken=<redacted>, expiresAt=$expiresAt)"
}
