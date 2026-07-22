/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsModifier
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.getOrNull
import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions

@Extensions(ExtendWith(ForgeExtension::class))
@ForgeConfiguration(value = BaseConfigurator::class)
class DatadogModifierTest {

    @Test
    fun `M not set element path W instrumentedDatadog() {elementPath omitted}`(
        @StringForgery name: String
    ) {
        // When
        val modifier = Modifier.instrumentedDatadog(name = name, isImage = false)

        // Then
        assertThat(modifier.findValue(DatadogSemanticsPropertyKey)).isEqualTo(name)
        assertThat(modifier.findValue(DatadogElementPathPropertyKey)).isNull()
    }

    @Test
    fun `M set element path independently W instrumentedDatadog() {elementPath provided}`(
        @StringForgery name: String,
        @StringForgery elementPath: String
    ) {
        // When
        val modifier = Modifier.instrumentedDatadog(name = name, isImage = false, elementPath = elementPath)

        // Then
        assertThat(modifier.findValue(DatadogSemanticsPropertyKey)).isEqualTo(name)
        assertThat(modifier.findValue(DatadogElementPathPropertyKey)).isEqualTo(elementPath)
    }

    @Test
    fun `M not set element path W datadog() {manual instrumentation}`(
        @StringForgery name: String
    ) {
        // When
        val modifier = Modifier.datadog(name = name, isImage = false)

        // Then
        assertThat(modifier.findValue(DatadogSemanticsPropertyKey)).isEqualTo(name)
        assertThat(modifier.findValue(DatadogElementPathPropertyKey)).isNull()
    }

    private fun <T> Modifier.findValue(key: SemanticsPropertyKey<T>): T? {
        val configs = foldIn(mutableListOf<SemanticsConfiguration>()) { acc, element ->
            if (element is SemanticsModifier) {
                acc.add(element.semanticsConfiguration)
            }
            acc
        }
        return configs.firstNotNullOfOrNull { it.getOrNull(key) }
    }
}
