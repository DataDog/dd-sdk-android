/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.model

import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
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
    fun `M snapshot keys W caller mutates source`(
        @StringForgery fakeFirstKey: String,
        @StringForgery fakeSecondKey: String
    ) {
        // Given
        val keys = mutableListOf(fakeFirstKey, fakeSecondKey, fakeFirstKey)
        val tested = FlagsClientEvent(FlagsClientEventType.CONFIGURATION_CHANGED, keys)

        // When
        keys.clear()

        // Then
        assertThat(tested.flagsChanged).containsExactly(fakeFirstKey, fakeSecondKey, fakeFirstKey)
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
    fun `M expose two final fields W event received`() {
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
