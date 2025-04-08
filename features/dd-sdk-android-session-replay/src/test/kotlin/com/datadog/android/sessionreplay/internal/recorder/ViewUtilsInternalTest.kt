/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.graphics.drawable.Drawable
import android.view.Display
import android.view.View
import android.view.ViewStub
import android.widget.TextView
import androidx.appcompat.widget.ActionBarContextView
import androidx.appcompat.widget.Toolbar
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.resources.DefaultImageWireframeHelper
import com.datadog.tools.unit.extensions.ApiLevelExtension
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class ViewUtilsInternalTest {

    private lateinit var testViewUtilsInternal: ViewUtilsInternal

    @Mock
    lateinit var mockTextView: TextView

    @Mock
    lateinit var mockDrawable: Drawable

    @IntForgery(min = 1)
    var fakeViewPadding: Int = 0

    @IntForgery(min = 1)
    var fakeViewHeight: Int = 0

    @IntForgery(min = 1)
    var fakeViewWidth: Int = 0

    @IntForgery(min = 1)
    var fakeDrawableWidth: Int = 0

    @IntForgery(min = 1)
    var fakeDrawableHeight: Int = 0

    @BeforeEach
    fun `set up`() {
        whenever(mockTextView.paddingStart).thenReturn(fakeViewPadding)
        whenever(mockTextView.paddingEnd).thenReturn(fakeViewPadding)
        whenever(mockTextView.paddingTop).thenReturn(fakeViewPadding)
        whenever(mockTextView.paddingBottom).thenReturn(fakeViewPadding)
        whenever(mockTextView.height).thenReturn(fakeViewHeight)
        whenever(mockTextView.width).thenReturn(fakeViewWidth)
        whenever(mockDrawable.intrinsicWidth).thenReturn(fakeDrawableWidth)
        whenever(mockDrawable.intrinsicHeight).thenReturn(fakeDrawableHeight)

        testViewUtilsInternal = ViewUtilsInternal()
    }

    // region Visibility

    @Test
    fun `M return true W checkIfNotVisible(view isShown false)`(forge: Forge) {
        // Given
        val mockView = forge.aMockView<View>().apply {
            whenever(this.isShown).thenReturn(false)
        }

        // When
        assertThat(testViewUtilsInternal.isNotVisible(mockView)).isTrue
    }

    @Test
    fun `M return true W checkIfNotVisible(view width is 0)`(forge: Forge) {
        // Given
        val mockView = forge.aMockView<View>().apply {
            whenever(this.width).thenReturn(0)
        }

        // When
        assertThat(testViewUtilsInternal.isNotVisible(mockView)).isTrue
    }

    @Test
    fun `M return true W checkIfNotVisible(view height is 0)`(forge: Forge) {
        // Given
        val mockView: View = forge.aMockView<View>().apply {
            whenever(this.height).thenReturn(0)
        }

        // When
        assertThat(testViewUtilsInternal.isNotVisible(mockView)).isTrue
    }

    @Test
    fun `M return false W checkIfNotVisible(view is visible)`(forge: Forge) {
        // Given
        val mockView = forge.aMockView<View>().apply {
            whenever(this.isShown).thenReturn(true)
        }

        // When
        assertThat(testViewUtilsInternal.isNotVisible(mockView)).isFalse
    }

    // endregion

    // region System Noise

    @Test
    fun `M return true W checkIfSystemNoise(){ view the navigationBarBackground }`(forge: Forge) {
        // Given
        val mockView = forge
            .aMockView<View>()
            .apply {
                whenever(this.id).thenReturn(android.R.id.navigationBarBackground)
            }

        // Then
        assertThat(testViewUtilsInternal.isSystemNoise(mockView)).isTrue
    }

    @Test
    fun `M return true W checkIfSystemNoise(){ view is statusBarBackground }`(forge: Forge) {
        // Given
        val mockView = forge
            .aMockView<View>()
            .apply {
                whenever(this.id).thenReturn(android.R.id.statusBarBackground)
            }

        // Then
        assertThat(testViewUtilsInternal.isSystemNoise(mockView)).isTrue
    }

    @Test
    fun `M return true W checkIfSystemNoise(){ view is ViewStub }`() {
        // Given
        val mockView: ViewStub = mock()

        // Then
        assertThat(testViewUtilsInternal.isSystemNoise(mockView)).isTrue
    }

    @Test
    fun `M return true W checkIfSystemNoise(){ view is ActionBarContextView }`() {
        // Given
        val mockView: ActionBarContextView = mock()

        // Then
        assertThat(testViewUtilsInternal.isSystemNoise(mockView)).isTrue
    }

    @Test
    fun `M return false W checkIfSystemNoise(){ view is not system noise }`(forge: Forge) {
        // Given
        val mockView = forge.aMockView<View>()

        // Then
        assertThat(testViewUtilsInternal.isSystemNoise(mockView)).isFalse
    }

    // endregion

    // region Toolbar

    @Test
    fun `M return true W checkIsToolbar(){ view androidx Toolbar }`(
        forge: Forge
    ) {
        // Given
        val mockToolBar: Toolbar = forge.aMockView<Toolbar>().apply {
            whenever(this.id).thenReturn(androidx.appcompat.R.id.action_bar)
        }

        // Then
        assertThat(testViewUtilsInternal.isToolbar(mockToolBar)).isTrue
    }

    @Test
    fun `M return true W checkIsToolbar(){ view android Toolbar }`(
        forge: Forge
    ) {
        // Given
        val mockToolBar: android.widget.Toolbar = forge.aMockView()

        // Then
        assertThat(testViewUtilsInternal.isToolbar(mockToolBar)).isTrue
    }

    @Test
    fun `M return false W checkIsToolbar(){ view is not toolbar }`(forge: Forge) {
        // Given
        val mockView = forge.aMockView<View>()

        // Then
        assertThat(testViewUtilsInternal.isToolbar(mockView)).isFalse
    }

    @Test
    fun `M return globalbounds W resolveDrawableBounds()`(
        @Mock mockView: View,
        @Mock mockDrawable: Drawable,
        @IntForgery(0, 100) fakeWidth: Int,
        @IntForgery(0, 100) fakeHeight: Int
    ) {
        // Given
        whenever(mockDrawable.intrinsicWidth).thenReturn(fakeWidth)
        whenever(mockDrawable.intrinsicHeight).thenReturn(fakeHeight)

        // When
        val bounds = testViewUtilsInternal.resolveDrawableBounds(
            mockView,
            mockDrawable,
            0f
        )

        // Then
        assertThat(bounds.x).isEqualTo(0)
        assertThat(bounds.y).isEqualTo(0)
        assertThat(bounds.width).isEqualTo(fakeWidth.toLong())
        assertThat(bounds.height).isEqualTo(fakeHeight.toLong())
    }

    // endregion

    @Test
    fun `M return bounds W resolveCompoundDrawableBounds() { for left drawable }`() {
        // Given
        val viewPadding = mockTextView.paddingStart.toLong()
        val viewHeight = mockTextView.height.toLong()
        val drawableHeight = mockDrawable.intrinsicHeight.toLong()

        // When
        val actualBounds = testViewUtilsInternal.resolveCompoundDrawableBounds(
            mockTextView,
            mockDrawable,
            0f,
            DefaultImageWireframeHelper.CompoundDrawablePositions.LEFT
        )

        // Then
        assertThat(actualBounds.x).isEqualTo(viewPadding)
        assertThat(actualBounds.y).isEqualTo(viewHeight / 2 - drawableHeight / 2)
    }

    @Test
    fun `M return bounds W resolveCompoundDrawableBounds() { for top drawable }`() {
        // Given
        val viewPadding = mockTextView.paddingTop.toLong()
        val viewWidth = mockTextView.width.toLong()
        val drawableWidth = mockDrawable.intrinsicWidth.toLong()

        // When
        val actualBounds = testViewUtilsInternal.resolveCompoundDrawableBounds(
            mockTextView,
            mockDrawable,
            0f,
            DefaultImageWireframeHelper.CompoundDrawablePositions.TOP
        )

        // Then
        assertThat(actualBounds.x).isEqualTo(viewWidth / 2 - drawableWidth / 2)
        assertThat(actualBounds.y).isEqualTo(viewPadding)
    }

    @Test
    fun `M return bounds W resolveCompoundDrawableBounds() { for right drawable }`() {
        // Given
        val viewPadding = mockTextView.paddingEnd.toLong()
        val viewWidth = mockTextView.width.toLong()
        val viewHeight = mockTextView.height.toLong()
        val drawableWidth = mockDrawable.intrinsicWidth.toLong()
        val drawableHeight = mockDrawable.intrinsicHeight.toLong()

        // When
        val actualBounds = testViewUtilsInternal.resolveCompoundDrawableBounds(
            mockTextView,
            mockDrawable,
            0f,
            DefaultImageWireframeHelper.CompoundDrawablePositions.RIGHT
        )

        // Then
        assertThat(actualBounds.x).isEqualTo(viewWidth - (drawableWidth + viewPadding))
        assertThat(actualBounds.y).isEqualTo(viewHeight / 2 - drawableHeight / 2)
    }

    @Test
    fun `M return bounds W resolveCompoundDrawableBounds() { for bottom drawable }`() {
        // Given
        val viewPadding = mockTextView.paddingBottom.toLong()
        val viewWidth = mockTextView.width.toLong()
        val viewHeight = mockTextView.height.toLong()
        val drawableWidth = mockDrawable.intrinsicWidth.toLong()
        val drawableHeight = mockDrawable.intrinsicHeight.toLong()

        // When
        val actualBounds = testViewUtilsInternal.resolveCompoundDrawableBounds(
            mockTextView,
            mockDrawable,
            0f,
            DefaultImageWireframeHelper.CompoundDrawablePositions.BOTTOM
        )

        // Then
        assertThat(actualBounds.x).isEqualTo(viewWidth / 2 - drawableWidth / 2)
        assertThat(actualBounds.y).isEqualTo(viewHeight - (drawableHeight + viewPadding))
    }

    // region secondary display

    @Test
    fun `M return true W isOnSecondaryDisplay { view on secondary display }`(
        @Mock mockView: View,
        @IntForgery(min = Display.DEFAULT_DISPLAY + 1) fakeDisplayId: Int
    ) {
        // Given
        whenever(mockView.display).thenReturn(mock())
        whenever(mockView.display?.displayId).thenReturn(fakeDisplayId)

        // When
        val isOnSecondaryDisplay = testViewUtilsInternal.isOnSecondaryDisplay(mockView)

        // Then
        assertThat(isOnSecondaryDisplay).isTrue
    }

    @Test
    fun `M return false W isOnSecondaryDisplay { view on primary display }`(
        @Mock mockView: View
    ) {
        // Given
        whenever(mockView.display).thenReturn(mock())
        whenever(mockView.display?.displayId).thenReturn(Display.DEFAULT_DISPLAY)

        // When
        val isOnSecondaryDisplay = testViewUtilsInternal.isOnSecondaryDisplay(mockView)

        // Then
        assertThat(isOnSecondaryDisplay).isFalse
    }

    // endregion
}
