/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.utils

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.datadog.android.sessionreplay.compose.internal.mappers.semantics.TextLayoutInfo
import com.datadog.android.sessionreplay.compose.test.elmyr.SessionReplayComposeForgeConfigurator
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.quality.Strictness

@Extensions(ExtendWith(MockitoExtension::class), ExtendWith(ForgeExtension::class))
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(SessionReplayComposeForgeConfigurator::class)
internal class ComposeTextCaptureCacheTest {
    private val testedCache = ComposeTextCaptureCache()
    private val stubModifier: Modifier = mock()
    private val fakeParagraph = Any()
    private val fakeState = ComposeTextCaptureCache.State(fakeParagraph, 10L, 20L)

    @Test
    fun `M reuse mapped text W get() {same measured paragraph and modifier}`() {
        // Given
        val fakeResult = result("Current text")
        testedCache.put(1, fakeState, stubModifier, fakeResult)

        // When / Then
        assertThat(testedCache.get(1, fakeState, stubModifier)).isEqualTo(fakeResult)
    }

    @Test
    fun `M invalidate mapped text W get() {paragraph constraints size modifier or node changes}`() {
        // Given
        val fakeResult = result("Current text")
        testedCache.put(1, fakeState, stubModifier, fakeResult)
        val stubOtherModifier: Modifier = mock()
        val changedStates = listOf(
            ComposeTextCaptureCache.State(Any(), 10L, 20L),
            ComposeTextCaptureCache.State(fakeParagraph, 11L, 20L),
            ComposeTextCaptureCache.State(fakeParagraph, 10L, 21L)
        )

        // When / Then
        changedStates.forEach { assertThat(testedCache.get(1, it, stubModifier)).isNull() }
        assertThat(testedCache.get(1, fakeState, stubOtherModifier)).isNull()
        assertThat(testedCache.get(2, fakeState, stubModifier)).isNull()
        assertThat(testedCache.get(1, null, stubModifier)).isNull()
        assertThat(testedCache.get(1, fakeState, null)).isNull()
    }

    @Test
    fun `M retain newest entries W put() {entry limit exceeded}`() {
        // Given
        val fakeResult = result("x")

        // When
        repeat(300) { testedCache.put(it, fakeState, stubModifier, fakeResult) }

        // Then
        assertThat(testedCache.get(0, fakeState, stubModifier)).isNull()
        assertThat(testedCache.get(299, fakeState, stubModifier)).isEqualTo(fakeResult)
    }

    @Test
    fun `M bound text budget W put() {large entries below individual limit}`() {
        // Given
        val fakeResult = result("x".repeat(16_384))

        // When
        repeat(20) { testedCache.put(it, fakeState, stubModifier, fakeResult) }

        // Then
        assertThat(testedCache.get(0, fakeState, stubModifier)).isNull()
        assertThat(testedCache.get(19, fakeState, stubModifier)).isEqualTo(fakeResult)
    }

    @Test
    fun `M discard old entry W put() {replacement exceeds individual limit}`() {
        // Given
        val fakeResult = result("small")
        testedCache.put(1, fakeState, stubModifier, fakeResult)
        val fakeLargeResult = result("x".repeat(16_385))

        // When
        testedCache.put(1, fakeState, stubModifier, fakeLargeResult)

        // Then
        assertThat(testedCache.get(1, fakeState, stubModifier)).isNull()
    }

    @Test
    fun `M use current result W put() {same node is recaptured}`() {
        // Given
        val fakeResult = result("Before")
        val fakeUpdatedResult = result("After")
        testedCache.put(1, fakeState, stubModifier, fakeResult)

        // When
        testedCache.put(1, fakeState, stubModifier, fakeUpdatedResult)

        // Then
        assertThat(testedCache.get(1, fakeState, stubModifier)).isEqualTo(fakeUpdatedResult)
    }

    private fun result(text: String) = TextLayoutInfo(text, Color.Red.value, 12L, FontFamily.Default)
}
