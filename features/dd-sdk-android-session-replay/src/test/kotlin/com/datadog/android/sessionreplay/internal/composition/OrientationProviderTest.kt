/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.content.res.Configuration
import android.content.res.Resources
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class OrientationProviderTest {
    @ParameterizedTest
    @ValueSource(ints = [0, 1, 2])
    fun `M return the actual configuration orientation W currentOrientation`(orientation: Int) {
        // Given
        val resources: Resources = mock()
        val configuration = Configuration().apply { this.orientation = orientation }
        whenever(resources.configuration).thenReturn(configuration)
        mockStatic(Resources::class.java).use { system ->
            system.`when`<Resources> { Resources.getSystem() }.thenReturn(resources)

            // When + Then
            assertThat(DefaultOrientationProvider().currentOrientation()).isEqualTo(orientation)
        }
    }

    @Test
    fun `M propagate a resource failure W currentOrientation { no fabricated orientation }`() {
        // Given
        val failure = IllegalStateException("resource read failed")
        mockStatic(Resources::class.java).use { system ->
            system.`when`<Resources> { Resources.getSystem() }.thenThrow(failure)

            // When + Then
            assertThatThrownBy { DefaultOrientationProvider().currentOrientation() }.isSameAs(failure)
        }
    }
}
