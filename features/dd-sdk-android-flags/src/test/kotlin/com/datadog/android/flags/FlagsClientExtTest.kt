/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions

internal class FlagsClientExtTest {
    @Test
    fun `M ignore unsupported implementations W setEvaluationTelemetryOptOut`() {
        // Given
        val mockClient = mock<FlagsClient>()
        val customClient = object : FlagsClient by mockClient {}

        // When
        customClient.setEvaluationTelemetryOptOut(true)
        customClient.setEvaluationTelemetryOptOut(false)

        // Then
        verifyNoInteractions(mockClient)
    }
}
