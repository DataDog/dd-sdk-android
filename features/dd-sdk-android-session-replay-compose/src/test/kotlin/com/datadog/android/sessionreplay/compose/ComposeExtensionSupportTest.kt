/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose

import androidx.compose.ui.platform.ComposeView
import com.datadog.android.sessionreplay.ExtensionSupport
import com.datadog.android.sessionreplay.compose.internal.mappers.semantics.SemanticsWireframeMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(ExtendWith(MockitoExtension::class))
@MockitoSettings(strictness = Strictness.LENIENT)
class ComposeExtensionSupportTest {

    lateinit var testedExtensionSupport: ExtensionSupport

    @BeforeEach
    fun `set up`() {
        testedExtensionSupport = ComposeExtensionSupport()
    }

    @Test
    fun `M return a ComposeMapper W getCustomViewMappers()`() {
        // Given
        val mockView: ComposeView = mock()

        // When
        val customMappers = testedExtensionSupport.getCustomViewMappers()

        // Then
        val composeMapper = customMappers.firstOrNull { it.supportsView(mockView) }?.getUnsafeMapper()
        assertThat(composeMapper).isInstanceOf(SemanticsWireframeMapper::class.java)
    }
}
