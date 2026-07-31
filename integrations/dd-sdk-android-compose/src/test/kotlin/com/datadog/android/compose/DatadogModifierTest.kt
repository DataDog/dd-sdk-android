/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsModifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Regression tests for #3661.
 *
 * The Datadog semantics modifier must compare equal for equal inputs. Under Compose strong
 * skipping, `Modifier` is a stable parameter compared with `equals()`; if the injected modifier
 * were not value-equal across recompositions, any composable receiving it would stop skipping.
 */
@ExtendWith(ForgeExtension::class)
internal class DatadogModifierTest {

    @Test
    fun `M compare equal W instrumentedDatadog() { same inputs }`(
        @StringForgery fakeName: String
    ) {
        // When
        val first = Modifier.instrumentedDatadog(fakeName, isImage = true)
        val second = Modifier.instrumentedDatadog(fakeName, isImage = true)

        // Then
        assertThat(first).isEqualTo(second)
        assertThat(first.hashCode()).isEqualTo(second.hashCode())
    }

    @Test
    fun `M compare equal W datadog() { same inputs }`(
        @StringForgery fakeName: String
    ) {
        // When
        val first = Modifier.datadog(fakeName, isImage = false)
        val second = Modifier.datadog(fakeName, isImage = false)

        // Then
        assertThat(first).isEqualTo(second)
        assertThat(first.hashCode()).isEqualTo(second.hashCode())
    }

    @Test
    fun `M not compare equal W instrumentedDatadog() { different name }`(
        @StringForgery fakeName: String,
        @StringForgery otherName: String
    ) {
        // When
        val first = Modifier.instrumentedDatadog(fakeName, isImage = false)
        val second = Modifier.instrumentedDatadog(otherName, isImage = false)

        // Then
        assertThat(first).isNotEqualTo(second)
    }

    @Test
    fun `M not compare equal W instrumentedDatadog() { different isImage }`(
        @StringForgery fakeName: String
    ) {
        // When
        val first = Modifier.instrumentedDatadog(fakeName, isImage = false)
        val second = Modifier.instrumentedDatadog(fakeName, isImage = true)

        // Then
        assertThat(first).isNotEqualTo(second)
    }

    @Test
    fun `M not compare equal W datadog() vs instrumentedDatadog() { same inputs }`(
        @StringForgery fakeName: String
    ) {
        // When
        val manual = Modifier.datadog(fakeName, isImage = false)
        val auto = Modifier.instrumentedDatadog(fakeName, isImage = false)

        // Then
        // The two carry different auto-instrumentation provenance, so they must not collapse.
        assertThat(manual).isNotEqualTo(auto)
    }

    // region Regression tests for #3662

    // LayoutNode#getModifierInfo() reports the Modifier.Element itself (not the Modifier.Node it
    // creates), so tooling that inspects it for `is SemanticsModifier` — e.g. the Compose action
    // tracker in LayoutNodeUtils — relies on the element exposing SemanticsModifier directly.
    @Test
    fun `M expose SemanticsModifier W datadog() { for action tracking compatibility }`(
        @StringForgery fakeName: String
    ) {
        // When
        val modifier = Modifier.datadog(fakeName, isImage = true)

        // Then
        check(modifier is SemanticsModifier)
        val config = modifier.semanticsConfiguration
        assertThat(config.getOrNull(DatadogSemanticsPropertyKey)).isEqualTo(fakeName)
        assertThat(config.getOrNull(SemanticsProperties.Role)).isEqualTo(Role.Image)
    }

    @Test
    fun `M expose SemanticsModifier W instrumentedDatadog() { for action tracking compatibility }`(
        @StringForgery fakeName: String
    ) {
        // When
        val modifier = Modifier.instrumentedDatadog(fakeName, isImage = false)

        // Then
        check(modifier is SemanticsModifier)
        val config = modifier.semanticsConfiguration
        assertThat(config.getOrNull(DatadogSemanticsPropertyKey)).isEqualTo(fakeName)
        assertThat(config.getOrNull(SemanticsProperties.Role)).isNull()
    }

    // endregion
}
