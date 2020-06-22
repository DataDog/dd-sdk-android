/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.tracking

import com.nhaarman.mockitokotlin2.mock
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(ForgeExtension::class),
    ExtendWith(MockitoExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class ViewLoadingTimerTest {

    lateinit var underTest: ViewLoadingTimer

    @BeforeEach
    fun `set up`() {
        underTest = ViewLoadingTimer()
    }

    @Test
    fun `it returns the right time and view state first time the view is created`() {
        // given
        val view: Any = mock()
        underTest.onCreated(view)
        underTest.onStartLoading(view)
        underTest.onFinishedLoading(view)

        // when
        val loadingTime = underTest.getLoadingTime(view)
        val firstTimeLoading = underTest.isFirstTimeLoading(view)

        // then
        assertThat(loadingTime).isGreaterThan(0)
        assertThat(firstTimeLoading).isTrue()
    }

    @Test
    fun `it returns the right time and view state when the view is resumed from background`() {
        // given
        val view: Any = mock()
        underTest.onCreated(view)
        underTest.onStartLoading(view)
        underTest.onFinishedLoading(view)
        underTest.onPaused(view)
        underTest.onStartLoading(view)
        underTest.onFinishedLoading(view)

        // when
        val loadingTime = underTest.getLoadingTime(view)
        val firstTimeLoading = underTest.isFirstTimeLoading(view)

        // then
        assertThat(loadingTime).isGreaterThan(0)
        assertThat(firstTimeLoading).isFalse()
    }

    @Test
    fun `it returns the right time and state for different views in different states`() {
        // given
        val view1: Any = mock()
        val view2: Any = mock()
        underTest.onCreated(view1)
        underTest.onCreated(view2)
        underTest.onStartLoading(view1)
        underTest.onFinishedLoading(view1)
        underTest.onStartLoading(view2)
        underTest.onFinishedLoading(view2)
        underTest.onPaused(view2)
        underTest.onStartLoading(view2)
        Thread.sleep(10)
        underTest.onFinishedLoading(view2)

        // when
        val loadingTimeView1 = underTest.getLoadingTime(view1)
        val firstTimeLoadingView1 = underTest.isFirstTimeLoading(view1)
        val loadingTimeView2 = underTest.getLoadingTime(view2)
        val firstTimeLoadingView2 = underTest.isFirstTimeLoading(view2)

        // then
        assertThat(loadingTimeView1).isGreaterThan(0)
        assertThat(firstTimeLoadingView1).isTrue()
        assertThat(loadingTimeView2).isGreaterThan(0)
        assertThat(firstTimeLoadingView2).isFalse()
        assertThat(loadingTimeView2).isGreaterThan(loadingTimeView1)
    }

    @Test
    fun `it returns false for firstTimeLoading if the view was resumed without being started`() {
        // given
        val view: Any = mock()
        underTest.onCreated(view)
        underTest.onStartLoading(view)
        underTest.onFinishedLoading(view)
        underTest.onStartLoading(view)
        underTest.onFinishedLoading(view)
        underTest.onFinishedLoading(view)

        // when
        val loadingTime = underTest.getLoadingTime(view)
        val firstTimeLoading = underTest.isFirstTimeLoading(view)

        // then
        assertThat(loadingTime).isEqualTo(0)
        assertThat(firstTimeLoading).isFalse()
    }

    @Test
    fun `it will return false for firstTimeLoading after the view was hidden`() {
        // given
        val view: Any = mock()
        underTest.onCreated(view)
        underTest.onStartLoading(view)
        underTest.onFinishedLoading(view)
        underTest.onPaused(view)
        underTest.onFinishedLoading(view)

        // when
        val loadingTime = underTest.getLoadingTime(view)
        val firstTimeLoading = underTest.isFirstTimeLoading(view)

        // then
        assertThat(loadingTime).isEqualTo(0)
        assertThat(firstTimeLoading).isFalse()
    }

    @Test
    fun `it clear the references if the view is destroyed`() {
        // given
        val view: Any = mock()
        underTest.onCreated(view)
        underTest.onStartLoading(view)
        underTest.onFinishedLoading(view)
        underTest.onPaused(view)
        underTest.onStartLoading(view)
        underTest.onFinishedLoading(view)
        underTest.onPaused(view)
        underTest.onDestroyed(view)

        // when
        val loadingTime = underTest.getLoadingTime(view)
        val firstTimeLoading = underTest.isFirstTimeLoading(view)

        // then
        assertThat(loadingTime).isNull()
        assertThat(firstTimeLoading).isFalse()
    }
}
