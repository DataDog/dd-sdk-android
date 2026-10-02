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
        val absent = FlagsClientEvent(FlagsClientEventType.CONFIGURATION_CHANGED)
        val explicitNull = FlagsClientEvent(FlagsClientEventType.CONFIGURATION_CHANGED, null)
        val empty = FlagsClientEvent(FlagsClientEventType.CONFIGURATION_CHANGED, emptyList())
        assertThat(absent.flagsChanged).isNull()
        assertThat(empty.flagsChanged).isEmpty()
        assertThat(explicitNull.flagsChanged).isNull()
    }

    @Test
    @Suppress("DontDowncastCollectionTypes") // Exercise mutation attempts against the actual returned list.
    fun `M snapshot keys and prevent mutation W caller mutates source or result`() {
        val keys = mutableListOf("b", "a", "b")
        val tested = FlagsClientEvent(FlagsClientEventType.CONFIGURATION_CHANGED, keys)
        keys.clear()
        assertThat(tested.flagsChanged).containsExactly("b", "a", "b")
        assertThrows<UnsupportedOperationException> {
            (tested.flagsChanged as MutableList<String>).add("later")
        }
        assertThat(tested.flagsChanged).containsExactly("b", "a", "b")
    }

    @Test
    fun `M expose only configuration changed W enum mapping`() {
        assertThat(FlagsClientEventType.entries.map { it.name }).containsExactly("CONFIGURATION_CHANGED")
        FlagsClientEventType.entries.forEach { type ->
            assertThat(FlagsClientEvent(type).type).isEqualTo(type)
        }
    }

    @Test
    fun `M expose two immutable fields and Java overload W public API`() {
        val eventClass = FlagsClientEvent::class.java
        assertThat(eventClass.declaredFields.filterNot { it.isSynthetic }.map { it.name })
            .containsExactlyInAnyOrder("type", "flagsChanged")
        assertThat(eventClass.declaredFields.filterNot { it.isSynthetic })
            .allMatch { java.lang.reflect.Modifier.isFinal(it.modifiers) }
        val javaConstructor = eventClass.getConstructor(FlagsClientEventType::class.java)
        val tested = javaConstructor.newInstance(FlagsClientEventType.CONFIGURATION_CHANGED)
        assertThat(tested.flagsChanged).isNull()
    }
}
