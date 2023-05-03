/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit

internal class DefaultViewPredicateTest {

    lateinit var testedViewPredicate: DefaultViewUpdatePredicate

    lateinit var mockEvent: RumRawEvent

    @Before
    fun `set up`() {
        mockEvent = mock()
        testedViewPredicate = DefaultViewUpdatePredicate()
    }

    @Test
    fun `M return true W canUpdateView { first call, isViewComplete false }`() {
        // When
        val canUpdateView = testedViewPredicate.canUpdateView(false, mockEvent)

        // Then
        assertThat(canUpdateView).isTrue
    }

    @Test
    fun `M return true W canUpdateView { first call, isViewComplete true }`() {
        // When
        val canUpdateView = testedViewPredicate.canUpdateView(true, mockEvent)

        // Then
        assertThat(canUpdateView).isTrue
    }

    @Test
    fun `M return true W canUpdateView { first call, non fatal error }`() {
        // Given
        mockEvent = mock<RumRawEvent.AddError> {
            whenever(it.isFatal).thenReturn(false)
        }
        // When
        val canUpdateView = testedViewPredicate.canUpdateView(true, mockEvent)

        // Then
        assertThat(canUpdateView).isTrue
    }

    @Test
    fun `M return true W canUpdateView { first call, fatal error }`() {
        // Given
        mockEvent = mock<RumRawEvent.AddError> {
            whenever(it.isFatal).thenReturn(true)
        }
        // When
        val canUpdateView = testedViewPredicate.canUpdateView(true, mockEvent)

        // Then
        assertThat(canUpdateView).isTrue
    }

    @Test
    fun `M return true W canUpdateView { second call after threshold, isViewComplete false }`() {
        // Given
        testedViewPredicate = DefaultViewUpdatePredicate(TimeUnit.MILLISECONDS.toNanos(300))
        val canUpdateViewFirst = testedViewPredicate.canUpdateView(false, mockEvent)

        // When
        Thread.sleep(400)
        val canUpdateViewSecond = testedViewPredicate.canUpdateView(false, mockEvent)

        // Then
        assertThat(canUpdateViewFirst).isTrue
        assertThat(canUpdateViewSecond).isTrue
    }

    @Test
    fun `M return true W canUpdateView { second call after threshold, isViewComplete true }`() {
        // Given
        testedViewPredicate = DefaultViewUpdatePredicate(TimeUnit.MILLISECONDS.toNanos(300))
        val canUpdateViewFirst = testedViewPredicate.canUpdateView(false, mockEvent)

        // When
        Thread.sleep(400)
        val canUpdateViewSecond = testedViewPredicate.canUpdateView(true, mockEvent)

        // Then
        assertThat(canUpdateViewFirst).isTrue
        assertThat(canUpdateViewSecond).isTrue
    }

    @Test
    fun `M return true W canUpdateView { second call before threshold, isViewComplete true }`() {
        // Given
        val canUpdateViewFirst = testedViewPredicate.canUpdateView(false, mockEvent)

        // When
        val canUpdateViewSecond = testedViewPredicate.canUpdateView(true, mockEvent)

        // Then
        assertThat(canUpdateViewFirst).isTrue
        assertThat(canUpdateViewSecond).isTrue
    }

    @Test
    fun `M return false W canUpdateView { second call before threshold, isViewComplete false }`() {
        // Given
        val canUpdateViewFirst = testedViewPredicate.canUpdateView(false, mockEvent)

        // When
        val canUpdateViewSecond = testedViewPredicate.canUpdateView(false, mockEvent)

        // Then
        assertThat(canUpdateViewFirst).isTrue
        assertThat(canUpdateViewSecond).isFalse
    }

    @Test
    fun `M return true W canUpdateView { second call before threshold, fatal error }`() {
        // Given
        mockEvent = mock<RumRawEvent.AddError> {
            whenever(it.isFatal).thenReturn(true)
        }
        val canUpdateViewFirst = testedViewPredicate.canUpdateView(false, mockEvent)

        // When
        val canUpdateViewSecond = testedViewPredicate.canUpdateView(false, mockEvent)

        // Then
        assertThat(canUpdateViewFirst).isTrue
        assertThat(canUpdateViewSecond).isTrue
    }

    @Test
    fun `M return false W canUpdateView { second call before threshold, non fatal error }`() {
        // Given
        mockEvent = mock<RumRawEvent.AddError> {
            whenever(it.isFatal).thenReturn(false)
        }
        val canUpdateViewFirst = testedViewPredicate.canUpdateView(false, mockEvent)

        // When
        val canUpdateViewSecond = testedViewPredicate.canUpdateView(false, mockEvent)

        // Then
        assertThat(canUpdateViewFirst).isTrue
        assertThat(canUpdateViewSecond).isFalse
    }
}
