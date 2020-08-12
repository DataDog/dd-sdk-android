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

    lateinit var testedTimer: ViewLoadingTimer

    @BeforeEach
    fun `set up`() {
        testedTimer = ViewLoadingTimer()
    }

    @Test
    fun `it returns the right time and view state first time the view is created`() {
        // given
        val view: Any = mock()
        testedTimer.onCreated(view)
        testedTimer.onStartLoading(view)
        testedTimer.onFinishedLoading(view)

        // when
        val loadingTime = testedTimer.getLoadingTime(view)
        val firstTimeLoading = testedTimer.isFirstTimeLoading(view)

        // then
        assertThat(loadingTime).isGreaterThan(0)
        assertThat(firstTimeLoading).isTrue()
    }

    @Test
    fun `it returns the right time and view state when the view is resumed from background`() {
        // given
        val view: Any = mock()
        testedTimer.onCreated(view)
        testedTimer.onStartLoading(view)
        testedTimer.onFinishedLoading(view)
        testedTimer.onPaused(view)
        testedTimer.onStartLoading(view)
        testedTimer.onFinishedLoading(view)

        // when
        val loadingTime = testedTimer.getLoadingTime(view)
        val firstTimeLoading = testedTimer.isFirstTimeLoading(view)

        // then
        assertThat(loadingTime).isGreaterThan(0)
        assertThat(firstTimeLoading).isFalse()
    }

    @Test
    fun `it returns the right time and view state when finishedLoading called multiple times`() {
        // given
        val view: Any = mock()
        testedTimer.onCreated(view)
        testedTimer.onStartLoading(view)
        testedTimer.onFinishedLoading(view)
        testedTimer.onFinishedLoading(view)

        // when
        val loadingTime = testedTimer.getLoadingTime(view)
        val firstTimeLoading = testedTimer.isFirstTimeLoading(view)

        // then
        assertThat(loadingTime).isGreaterThan(0)
        assertThat(firstTimeLoading).isTrue()
    }

    @Test
    fun `at first launch it will compute the time between onCreate and onFinishedLoading`() {
        // given
        val view: Any = mock()
        testedTimer.onCreated(view)
        Thread.sleep(500) // to simulate a long first time layout rendering
        testedTimer.onStartLoading(view)
        testedTimer.onFinishedLoading(view)
        val loadingTimeFirstLaunch = testedTimer.getLoadingTime(view)

        // when
        testedTimer.onPaused(view)
        testedTimer.onStartLoading(view)
        testedTimer.onFinishedLoading(view)
        val loadingTimeSecondLaunch = testedTimer.getLoadingTime(view)

        // then
        assertThat(loadingTimeFirstLaunch).isGreaterThan(loadingTimeSecondLaunch)
    }

    @Test
    fun `it returns the right time and state for different views in different states`() {
        // given
        val view1: Any = mock()
        val view2: Any = mock()
        testedTimer.onCreated(view1)
        testedTimer.onCreated(view2)
        testedTimer.onStartLoading(view1)
        testedTimer.onFinishedLoading(view1)
        testedTimer.onStartLoading(view2)
        testedTimer.onFinishedLoading(view2)
        testedTimer.onPaused(view2)
        testedTimer.onStartLoading(view2)
        Thread.sleep(10)
        testedTimer.onFinishedLoading(view2)

        // when
        val loadingTimeView1 = testedTimer.getLoadingTime(view1)
        val firstTimeLoadingView1 = testedTimer.isFirstTimeLoading(view1)
        val loadingTimeView2 = testedTimer.getLoadingTime(view2)
        val firstTimeLoadingView2 = testedTimer.isFirstTimeLoading(view2)

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
        testedTimer.onCreated(view)
        testedTimer.onStartLoading(view)
        testedTimer.onFinishedLoading(view)
        testedTimer.onPaused(view)
        testedTimer.onFinishedLoading(view)

        // when
        val loadingTime = testedTimer.getLoadingTime(view)
        val firstTimeLoading = testedTimer.isFirstTimeLoading(view)

        // then
        assertThat(loadingTime).isEqualTo(0)
        assertThat(firstTimeLoading).isFalse()
    }

    @Test
    fun `it will return false for firstTimeLoading after the view was hidden`() {
        // given
        val view: Any = mock()
        testedTimer.onCreated(view)
        testedTimer.onStartLoading(view)
        testedTimer.onFinishedLoading(view)
        testedTimer.onPaused(view)
        testedTimer.onFinishedLoading(view)

        // when
        val loadingTime = testedTimer.getLoadingTime(view)
        val firstTimeLoading = testedTimer.isFirstTimeLoading(view)

        // then
        assertThat(loadingTime).isEqualTo(0)
        assertThat(firstTimeLoading).isFalse()
    }

    @Test
    fun `it clear the references if the view is destroyed`() {
        // given
        val view: Any = mock()
        testedTimer.onCreated(view)
        testedTimer.onStartLoading(view)
        testedTimer.onFinishedLoading(view)
        testedTimer.onPaused(view)
        testedTimer.onStartLoading(view)
        testedTimer.onFinishedLoading(view)
        testedTimer.onPaused(view)
        testedTimer.onDestroyed(view)

        // when
        val loadingTime = testedTimer.getLoadingTime(view)
        val firstTimeLoading = testedTimer.isFirstTimeLoading(view)

        // then
        assertThat(loadingTime).isNull()
        assertThat(firstTimeLoading).isFalse()
    }
}
