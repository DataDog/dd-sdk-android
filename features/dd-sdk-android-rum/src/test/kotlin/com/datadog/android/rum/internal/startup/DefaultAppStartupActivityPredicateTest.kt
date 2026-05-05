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
        val activity = mock<Activity>()

        // When
        val result = DefaultAppStartupActivityPredicate.shouldTrackStartup(activity)

        // Then
        assertThat(result).isTrue()
    }
}
