/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

internal class VectorPainterCompatibilityTest {
    @Test
    fun `M bypass unavailable vector state W getVectorPainterState`() {
        // Given - missing optional fields and an absent renderer both require the original path.
        val testedUtils = ReflectionUtils()

        // When + Then
        assertThat(testedUtils.getVectorPainterState(mock())).isNull()
        assertThat(testedUtils.getVectorColorFilterKey(mock())).isNull()
    }
}
