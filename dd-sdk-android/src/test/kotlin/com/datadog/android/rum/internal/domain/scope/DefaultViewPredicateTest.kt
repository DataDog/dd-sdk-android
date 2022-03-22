/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test

internal class DefaultViewPredicateTest {

    lateinit var testedViewPredicate: DefaultViewUpdatePredicate

    @Before
    fun `set up`() {
        testedViewPredicate = DefaultViewUpdatePredicate()
    }

    @Test
    fun `M return true W canUpdateView { first call, isViewComplete false }`() {
        // When
        val canUpdateView = testedViewPredicate.canUpdateView(false)

        // Then
        assertThat(canUpdateView).isTrue
    }

    @Test
    fun `M return true W canUpdateView { first call, isViewComplete true }`() {
        // When
        val canUpdateView = testedViewPredicate.canUpdateView(true)

        // Then
        assertThat(canUpdateView).isTrue
    }

    @Test
    fun `M return true W canUpdateView { second call after threshold, isViewComplete false }`() {
        // Given
        testedViewPredicate = DefaultViewUpdatePredicate(TimeUnit.MILLISECONDS.toNanos(300))
        val canUpdateViewFirst = testedViewPredicate.canUpdateView(false)

        // When
        Thread.sleep(400)
        val canUpdateViewSecond = testedViewPredicate.canUpdateView(false)

        // Then
        assertThat(canUpdateViewFirst).isTrue
        assertThat(canUpdateViewSecond).isTrue
    }

    @Test
    fun `M return true W canUpdateView { second call before threshold, isViewComplete true }`() {
        // Given
        val canUpdateViewFirst = testedViewPredicate.canUpdateView(false)

        // When
        val canUpdateViewSecond = testedViewPredicate.canUpdateView(true)

        // Then
        assertThat(canUpdateViewFirst).isTrue
        assertThat(canUpdateViewSecond).isTrue
    }

    @Test
    fun `M return false W canUpdateView { second call before threshold, isViewComplete false }`() {
        // Given
        testedViewPredicate = DefaultViewUpdatePredicate(TimeUnit.MILLISECONDS.toNanos(300))
        val canUpdateViewFirst = testedViewPredicate.canUpdateView(false)

        // When
        val canUpdateViewSecond = testedViewPredicate.canUpdateView(false)

        // Then
        assertThat(canUpdateViewFirst).isTrue
        assertThat(canUpdateViewSecond).isFalse
    }
}
