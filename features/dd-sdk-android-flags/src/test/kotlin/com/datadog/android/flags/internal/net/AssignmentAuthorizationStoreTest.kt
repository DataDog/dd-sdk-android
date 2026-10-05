/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.net

import com.datadog.android.flags.AssignmentAuthorization
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Date

internal class AssignmentAuthorizationStoreTest {

    @Test
    fun `M clear authorization W expireIfMatches() { matching token expired }`() {
        val authorization = AssignmentAuthorization("header.payload.signature", Date(2_000))
        val store = AssignmentAuthorizationStore(authorization)
        val snapshot = requireNotNull(store.snapshot(nowMillis = 1_000).authorization)

        val expired = store.expireIfMatches(snapshot, nowMillis = 2_001)

        assertThat(expired).isTrue()
        assertThat(store.snapshot(nowMillis = 2_001).isEnabled).isTrue()
        assertThat(store.snapshot(nowMillis = 2_001).authorization).isNull()
    }

    @Test
    fun `M retain authorization W expireIfMatches() { stale scheduled token }`() {
        val store = AssignmentAuthorizationStore(
            AssignmentAuthorization("old", Date(2_000))
        )
        val oldSnapshot = requireNotNull(store.snapshot(nowMillis = 1_000).authorization)
        store.update(AssignmentAuthorization("new", Date(3_000)))

        val expired = store.expireIfMatches(oldSnapshot, nowMillis = 2_001)

        assertThat(expired).isFalse()
        assertThat(store.snapshot(nowMillis = 2_001).authorization?.bearerToken).isEqualTo("new")
    }

    @Test
    fun `M redact bearer token W toString()`() {
        val authorization = AssignmentAuthorization("sensitive-token", Date(2_000))

        assertThat(authorization.toString()).doesNotContain("sensitive-token")
        assertThat(authorization.toString()).contains("<redacted>")
    }
}
