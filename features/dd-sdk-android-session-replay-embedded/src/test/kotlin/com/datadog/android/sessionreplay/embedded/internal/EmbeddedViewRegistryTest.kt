/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.embedded.internal

import android.view.View
import com.datadog.android.sessionreplay.utils.DefaultViewIdentifierResolver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(ExtendWith(MockitoExtension::class))
@MockitoSettings(strictness = Strictness.LENIENT)
internal class EmbeddedViewRegistryTest {

    @Mock
    lateinit var mockView: View

    @AfterEach
    fun `tear down`() {
        EmbeddedViewRegistry.unregister(ENGINE_KEY)
    }

    @Test
    fun `M resolve the slot id of the registered view W resolveSlotId()`() {
        // Given
        EmbeddedViewRegistry.register(ENGINE_KEY, mockView)

        // When
        val slotId = EmbeddedViewRegistry.resolveSlotId(ENGINE_KEY)

        // Then
        assertThat(slotId).isEqualTo(DefaultViewIdentifierResolver.resolveViewId(mockView).toString())
    }

    @Test
    fun `M return null W resolveSlotId() { never registered }`() {
        // When
        val slotId = EmbeddedViewRegistry.resolveSlotId(ENGINE_KEY)

        // Then
        assertThat(slotId).isNull()
    }

    @Test
    fun `M return null W resolveSlotId() { unregistered }`() {
        // Given
        EmbeddedViewRegistry.register(ENGINE_KEY, mockView)
        EmbeddedViewRegistry.unregister(ENGINE_KEY)

        // When
        val slotId = EmbeddedViewRegistry.resolveSlotId(ENGINE_KEY)

        // Then
        assertThat(slotId).isNull()
    }

    @Test
    fun `M scope registrations by engine key W resolveSlotId()`() {
        // Given
        val mockOtherView: View = org.mockito.kotlin.mock()
        EmbeddedViewRegistry.register(ENGINE_KEY, mockView)
        EmbeddedViewRegistry.register(OTHER_ENGINE_KEY, mockOtherView)

        // When
        val slotId = EmbeddedViewRegistry.resolveSlotId(ENGINE_KEY)
        val otherSlotId = EmbeddedViewRegistry.resolveSlotId(OTHER_ENGINE_KEY)

        // Then
        assertThat(slotId).isEqualTo(DefaultViewIdentifierResolver.resolveViewId(mockView).toString())
        assertThat(otherSlotId).isEqualTo(DefaultViewIdentifierResolver.resolveViewId(mockOtherView).toString())
        EmbeddedViewRegistry.unregister(OTHER_ENGINE_KEY)
    }

    companion object {
        private val ENGINE_KEY = Any()
        private val OTHER_ENGINE_KEY = Any()
    }
}
