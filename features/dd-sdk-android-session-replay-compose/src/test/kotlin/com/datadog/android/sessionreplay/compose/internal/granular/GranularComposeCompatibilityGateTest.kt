/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.granular

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class GranularComposeCompatibilityGateTest {

    private val testedGate = GranularComposeCompatibilityGate()

    @Test
    fun `M be available W isAvailable() { fresh gate }`() {
        // When / Then
        assertThat(testedGate.isAvailable()).isTrue()
    }

    @Test
    fun `M no longer be available W markIncompatible()`() {
        // When
        testedGate.markIncompatible()

        // Then
        assertThat(testedGate.isAvailable()).isFalse()
    }

    @Test
    fun `M stay unavailable W markIncompatible() { called again }`() {
        // Given
        testedGate.markIncompatible()

        // When
        testedGate.markIncompatible()

        // Then
        assertThat(testedGate.isAvailable()).isFalse()
    }
}
