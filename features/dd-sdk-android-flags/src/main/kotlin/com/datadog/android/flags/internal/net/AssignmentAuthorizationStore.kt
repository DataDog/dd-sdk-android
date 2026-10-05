/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.net

import com.datadog.android.flags.AssignmentAuthorization
import com.datadog.android.internal.time.DefaultTimeProvider

internal data class AssignmentAuthorizationSnapshot(
    val isEnabled: Boolean,
    val authorization: AssignmentAuthorization?
)

internal class AssignmentAuthorizationStore(
    initialAuthorization: AssignmentAuthorization?,
    private val currentTimeMillis: () -> Long = DefaultTimeProvider()::getServerTimestampMillis
) {
    private val lock = Any()
    private var isEnabled = initialAuthorization != null
    private var authorization = initialAuthorization?.immutableCopy()

    fun snapshot(nowMillis: Long = currentTimeMillis()): AssignmentAuthorizationSnapshot = synchronized(lock) {
        AssignmentAuthorizationSnapshot(
            isEnabled = isEnabled,
            authorization = authorization
                ?.takeIf { it.expiresAt.time > nowMillis }
                ?.immutableCopy()
        )
    }

    fun update(newAuthorization: AssignmentAuthorization?) = synchronized(lock) {
        isEnabled = true
        authorization = newAuthorization?.immutableCopy()
    }

    fun expireIfMatches(expected: AssignmentAuthorization, nowMillis: Long = currentTimeMillis()): Boolean =
        synchronized(lock) {
            val current = authorization
            if (
                current?.bearerToken != expected.bearerToken ||
                current.expiresAt.time != expected.expiresAt.time ||
                current.expiresAt.time > nowMillis
            ) {
                false
            } else {
                authorization = null
                true
            }
        }

    private fun AssignmentAuthorization.immutableCopy(): AssignmentAuthorization =
        AssignmentAuthorization(bearerToken, java.util.Date(expiresAt.time))
}
