/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class FlagsClientEventTest {
    @Test
    fun `M preserve absent versus empty keys W construction`() {
        // When
        val absent = FlagsClientEvent(FlagsClientEventType.CONFIGURATION_CHANGED)
        val explicitNull = FlagsClientEvent(FlagsClientEventType.CONFIGURATION_CHANGED, null)
        val empty = FlagsClientEvent(FlagsClientEventType.CONFIGURATION_CHANGED, emptyList())

        // Then
        assertThat(absent.flagsChanged).isNull()
        assertThat(empty.flagsChanged).isEmpty()
        assertThat(explicitNull.flagsChanged).isNull()
    }

    @Test
    @Suppress("DontDowncastCollectionTypes") // Exercise mutation attempts against the actual returned list.
    fun `M snapshot keys and prevent mutation W caller mutates source or result`() {
        // Given
        val keys = mutableListOf("b", "a", "b")
        val tested = FlagsClientEvent(FlagsClientEventType.CONFIGURATION_CHANGED, keys)

        // When
        keys.clear()

        // Then
        assertThat(tested.flagsChanged).containsExactly("b", "a", "b")
        assertThrows<UnsupportedOperationException> {
            (tested.flagsChanged as MutableList<String>).add("later")
        }
        assertThat(tested.flagsChanged).containsExactly("b", "a", "b")
    }

    @Test
    fun `M expose only configuration changed W enum mapping`() {
        // When
        val eventNames = FlagsClientEventType.entries.map { it.name }

        // Then
        assertThat(eventNames).containsExactly("CONFIGURATION_CHANGED")
        FlagsClientEventType.entries.forEach { type ->
            assertThat(FlagsClientEvent(type).type).isEqualTo(type)
        }
    }

    @Test
    fun `M expose two immutable fields W event received`() {
        // Given
        val tested = FlagsClientEvent(FlagsClientEventType.CONFIGURATION_CHANGED)

        // When
        val fields = tested.javaClass.declaredFields.filterNot { it.isSynthetic }

        // Then
        assertThat(fields.map { it.name }).containsExactlyInAnyOrder("type", "flagsChanged")
        assertThat(fields).allMatch { java.lang.reflect.Modifier.isFinal(it.modifiers) }
        assertThat(tested.flagsChanged).isNull()
    }
}
