/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.startup

import android.app.Activity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

internal class DefaultAppStartupActivityPredicateTest {

    @Test
    fun `M return true W shouldTrackStartup()`() {
        // Given
        val predicate = DefaultAppStartupActivityPredicate()
        val activity = mock<Activity>()

        // When
        val result = predicate.shouldTrackStartup(activity)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M be equal W equals() with same type`() {
        // Given
        val predicate1 = DefaultAppStartupActivityPredicate()
        val predicate2 = DefaultAppStartupActivityPredicate()

        // When & Then
        assertThat(predicate1).isEqualTo(predicate2)
        assertThat(predicate1.hashCode()).isEqualTo(predicate2.hashCode())
    }
}
