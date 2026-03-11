/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.compose

import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
class DatadogSemanticsElementTest {

    @Test
    fun `M create node with correct properties W create()`(@StringForgery name: String, @BoolForgery isImage: Boolean) {
        // Given
        val element = DatadogSemanticsElement(name, isImage)

        // When
        val node = element.create()

        // Then
        assertThat(node.name).isEqualTo(name)
        assertThat(node.isImage).isEqualTo(isImage)
    }

    @Test
    fun `M update node properties W update()`(
        @StringForgery initialName: String,
        @StringForgery updatedName: String,
        @BoolForgery initialIsImage: Boolean
    ) {
        // Given
        val element = DatadogSemanticsElement(initialName, initialIsImage)
        val node = element.create()
        val updatedElement = DatadogSemanticsElement(updatedName, !initialIsImage)

        // When
        updatedElement.update(node)

        // Then
        assertThat(node.name).isEqualTo(updatedName)
        assertThat(node.isImage).isEqualTo(!initialIsImage)
    }

    @Test
    fun `M be equal W same properties`(@StringForgery name: String, @BoolForgery isImage: Boolean) {
        // Given
        val element1 = DatadogSemanticsElement(name, isImage)
        val element2 = DatadogSemanticsElement(name, isImage)

        // Then
        assertThat(element1).isEqualTo(element2)
        assertThat(element1.hashCode()).isEqualTo(element2.hashCode())
    }

    @Test
    fun `M not be equal W different name`(
        @StringForgery name1: String,
        @StringForgery name2: String,
        @BoolForgery isImage: Boolean
    ) {
        // Given
        val element1 = DatadogSemanticsElement(name1, isImage)
        val element2 = DatadogSemanticsElement(name2, isImage)

        // Then
        if (name1 != name2) {
            assertThat(element1).isNotEqualTo(element2)
        }
    }

    @Test
    fun `M not be equal W different isImage`(@StringForgery name: String) {
        // Given
        val element1 = DatadogSemanticsElement(name, true)
        val element2 = DatadogSemanticsElement(name, false)

        // Then
        assertThat(element1).isNotEqualTo(element2)
    }

    @Test
    fun `M node not merge descendants W shouldMergeDescendantSemantics`(
        @StringForgery name: String,
        @BoolForgery isImage: Boolean
    ) {
        // Given
        val element = DatadogSemanticsElement(name, isImage)
        val node = element.create()

        // Then
        assertThat(node.shouldMergeDescendantSemantics).isFalse()
    }

    @Test
    fun `M node not clear descendants W shouldClearDescendantSemantics`(
        @StringForgery name: String,
        @BoolForgery isImage: Boolean
    ) {
        // Given
        val element = DatadogSemanticsElement(name, isImage)
        val node = element.create()

        // Then
        assertThat(node.shouldClearDescendantSemantics).isFalse()
    }
}
