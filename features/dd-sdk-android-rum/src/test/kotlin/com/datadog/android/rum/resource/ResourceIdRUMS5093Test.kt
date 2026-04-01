/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.resource

import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.util.UUID

/**
 * Regression tests for RUMS-5093 / RUM-13514.
 *
 * Root cause: ResourceId.equals() falls back to key-only comparison when either uuid is null or
 * blank. This means a stop-event ResourceId built with generateUuid=false (uuid=null) matches ANY
 * active resource with the same URL key, regardless of which UUID was used to start it.
 *
 * When multiple concurrent requests share the same URL, the null-uuid stop-event from request R1
 * can terminate the scope that was opened for request R2, producing:
 *   1. Incorrect (too short) resource duration for R2.
 *   2. Span ID from R1 written into the RUM event that belongs to R2 (context corruption).
 *
 * The tests below FAIL on the unfixed code and PASS once the fix is applied.
 */
@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
class ResourceIdRUMS5093Test {

    // -------------------------------------------------------------------------
    // Test 1 — RUMS-5093: a null-uuid ResourceId must NOT equal a non-null-uuid
    // ResourceId for the same key.
    //
    // This is the direct unit test of the buggy equals() fallback.
    //
    // Current (broken) behaviour:
    //   ResourceId(key, null) == ResourceId(key, someUuid)  →  TRUE  ← BUG
    //
    // Expected (fixed) behaviour:
    //   ResourceId(key, null) == ResourceId(key, someUuid)  →  FALSE
    //
    // The assertion below calls assertThat(areEqual).isFalse(). On unfixed code
    // equals() returns true (key-only fallback), so isFalse() throws and the
    // test FAILS — proving the bug exists.
    // -------------------------------------------------------------------------
    @Test
    fun `M return false W equals { same key, stop-event null uuid vs start-event non-null uuid } RUMS-5093`(
        @StringForgery key: String,
        @Forgery startUuid: UUID
    ) {
        // Given
        // Simulates the ResourceId created at startResource (generateUuid=true → uuid assigned)
        val startResourceId = ResourceId(key, startUuid.toString())
        // Simulates the ResourceId created at stopResource (generateUuid=false → uuid=null)
        val stopResourceId = ResourceId(key, null)

        // When
        val areEqual = stopResourceId == startResourceId

        // Then
        // On unfixed code: areEqual == true (key-only fallback) → isFalse() FAILS → bug reproduced
        // On fixed code:   areEqual == false → isFalse() passes
        assertThat(areEqual)
            .withFailMessage(
                "RUMS-5093 regression: ResourceId with null uuid should NOT match a " +
                    "ResourceId with a non-null uuid even when their keys are equal. " +
                    "The null-uuid fallback in equals() causes stop-events to collide " +
                    "with the wrong concurrent request scope."
            )
            .isFalse()
    }

    // -------------------------------------------------------------------------
    // Test 2 — RUMS-5093: two non-null, distinct UUIDs for the same key must NOT
    // be considered equal, and a null uuid must NOT match a different non-null uuid.
    //
    // This verifies the companion invariant: once UUIDs are properly assigned
    // end-to-end (start AND stop), each request scope stays isolated even for
    // concurrent requests to the same URL.
    // -------------------------------------------------------------------------
    @Test
    fun `M return false W equals { same key, two distinct non-null uuids } RUMS-5093`(
        @StringForgery key: String,
        @Forgery uuidR1: UUID,
        @Forgery uuidR2: UUID
    ) {
        // Given — two start-event ResourceIds for concurrent requests R1 and R2 to the same URL
        val startResourceIdR1 = ResourceId(key, uuidR1.toString())
        val startResourceIdR2 = ResourceId(key, uuidR2.toString())

        // When
        val r1EqualsR2 = startResourceIdR1 == startResourceIdR2

        // Then — the existing test already covers this path, but we assert it here
        // explicitly as documentation for the desired isolated-scope behaviour.
        // This test PASSES on both fixed and unfixed code when uuids differ; it
        // documents the correct post-fix contract.
        assertThat(r1EqualsR2)
            .withFailMessage(
                "RUMS-5093: ResourceId for R1 (uuid=%s) must not equal ResourceId " +
                    "for R2 (uuid=%s) even with the same key.",
                uuidR1, uuidR2
            )
            .isFalse()
    }
}
