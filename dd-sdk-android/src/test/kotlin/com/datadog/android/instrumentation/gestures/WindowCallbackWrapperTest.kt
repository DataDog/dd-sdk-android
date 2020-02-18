package com.datadog.android.instrumentation.gestures

import android.view.ActionMode
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.SearchEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.datadog.android.instrumentation.gesture.WindowCallbackWrapper
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(
        MockitoExtension::class,
        ForgeExtension::class
    )
)
@ForgeConfiguration(Configurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class WindowCallbackWrapperTest {

    lateinit var underTest: WindowCallbackWrapper

    @Mock
    lateinit var mockCallback: Window.Callback

    @BeforeEach
    fun `set up`() {
        underTest = WindowCallbackWrapper(mockCallback)
    }

    @Test
    fun `onActionModeFinished will delegate to wrapper`() {
        // when
        val actionMode = mock<ActionMode>()
        underTest.onActionModeFinished(actionMode)

        // then
        verify(mockCallback).onActionModeFinished(actionMode)
    }

    @Test
    fun `onCreatePanelView will delegate to wrapper`(forge: Forge) {
        // when
        val featureId = forge.anInt()
        underTest.onCreatePanelView(featureId)

        // then
        verify(mockCallback).onCreatePanelView(featureId)
    }

    @Test
    fun `dispatchTouchEvent will delegate to wrapper`() {
        // when
        val motionEvent: MotionEvent = mock()
        underTest.dispatchTouchEvent(motionEvent)

        // then
        verify(mockCallback).dispatchTouchEvent(motionEvent)
    }

    @Test
    fun `onCreatePanelMenu will delegate to wrapper`(forge: Forge) {
        // when
        val featureId = forge.anInt()
        val menu: Menu = mock()
        underTest.onCreatePanelMenu(featureId, menu)

        // then
        verify(mockCallback).onCreatePanelMenu(featureId, menu)
    }

    @Test
    fun `onWindowStartingActionMode will delegate to wrapper`() {
        // when
        val callback = mock<ActionMode.Callback>()
        underTest.onWindowStartingActionMode(callback)

        // then
        verify(mockCallback).onWindowStartingActionMode(callback)
    }

    @Test
    fun `onWindowStartingActionMode with type will delegate to wrapper`(forge: Forge) {
        // when
        val type = forge.anInt()
        val callback = mock<ActionMode.Callback>()
        underTest.onWindowStartingActionMode(callback, type)

        // then
        verify(mockCallback).onWindowStartingActionMode(callback, type)
    }

    @Test
    fun `onAttachedToWindow will delegate to wrapper`() {
        // when
        underTest.onAttachedToWindow()

        // then
        verify(mockCallback).onAttachedToWindow()
    }

    @Test
    fun `dispatchGenericMotionEvent will delegate to wrapper`() {
        // when
        val motionEvent: MotionEvent = mock()
        underTest.dispatchGenericMotionEvent(motionEvent)

        // then
        verify(mockCallback).dispatchGenericMotionEvent(motionEvent)
    }

    @Test
    fun `dispatchPopulateAccessibilityEvent will delegate to wrapper`() {
        // when
        val event = mock<AccessibilityEvent>()
        underTest.dispatchPopulateAccessibilityEvent(event)

        // then
        verify(mockCallback).dispatchPopulateAccessibilityEvent(event)
    }

    @Test
    fun `dispatchTrackballEvent will delegate to wrapper`() {
        // when
        val event = mock<MotionEvent>()
        underTest.dispatchTrackballEvent(event)

        // then
        verify(mockCallback).dispatchTrackballEvent(event)
    }

    @Test
    fun `dispatchKeyShortcutEvent will delegate to wrapper`() {
        // when
        val event = mock<KeyEvent>()
        underTest.dispatchKeyShortcutEvent(event)

        // then
        verify(mockCallback).dispatchKeyShortcutEvent(event)
    }

    @Test
    fun `dispatchKeyEvent will delegate to wrapper`() {
        // when
        val event = mock<KeyEvent>()
        underTest.dispatchKeyEvent(event)

        // then
        verify(mockCallback).dispatchKeyEvent(event)
    }

    @Test
    fun `onMenuOpened will delegate to wrapper`(forge: Forge) {
        // when
        val featureId = forge.anInt()
        val menu: Menu = mock()
        underTest.onMenuOpened(featureId, menu)

        // then
        verify(mockCallback).onMenuOpened(featureId, menu)
    }

    @Test
    fun `onPanelClosed will delegate to wrapper`(forge: Forge) {
        // when
        val featureId = forge.anInt()
        val menu: Menu = mock()
        underTest.onPanelClosed(featureId, menu)

        // then
        verify(mockCallback).onPanelClosed(featureId, menu)
    }

    @Test
    fun `onMenuItemSelected will delegate to wrapper`(forge: Forge) {
        // when
        val featureId = forge.anInt()
        val menuItem: MenuItem = mock()
        underTest.onMenuItemSelected(featureId, menuItem)

        // then
        verify(mockCallback).onMenuItemSelected(featureId, menuItem)
    }

    @Test
    fun `onDetachedFromWindow will delegate to wrapper`() {
        // when
        underTest.onDetachedFromWindow()

        // then
        verify(mockCallback).onDetachedFromWindow()
    }

    @Test
    fun `onPreparePanel will delegate to wrapper`(forge: Forge) {
        // when
        val featureId = forge.anInt()
        val menu: Menu = mock()
        val view: View = mock()
        underTest.onPreparePanel(featureId, view, menu)

        // then
        verify(mockCallback).onPreparePanel(featureId, view, menu)
    }

    @Test
    fun `onWindowAttributesChanged will delegate to wrapper`() {
        // when
        val attrs = mock<WindowManager.LayoutParams>()
        underTest.onWindowAttributesChanged(attrs)

        // then
        verify(mockCallback).onWindowAttributesChanged(attrs)
    }

    @Test
    fun `onWindowFocusChanged will delegate to wrapper`(forge: Forge) {
        // when
        val hasFocus = forge.aBool()
        underTest.onWindowFocusChanged(hasFocus)

        // then
        verify(mockCallback).onWindowFocusChanged(hasFocus)
    }

    @Test
    fun `onContentChanged will delegate to wrapper`() {
        // when
        underTest.onContentChanged()

        // then
        verify(mockCallback).onContentChanged()
    }

    @Test
    fun `onSearchRequested will delegate to wrapper`() {
        // when
        underTest.onSearchRequested()

        // then
        verify(mockCallback).onSearchRequested()
    }

    @Test
    fun `onSearchRequested for search event will delegate to wrapper`() {
        // when
        val searchEvent = mock<SearchEvent>()
        underTest.onSearchRequested(searchEvent)

        // then
        verify(mockCallback).onSearchRequested(searchEvent)
    }

    @Test
    fun `onActionModeStarted will delegate to wrapper`() {
        // when
        val mode = mock<ActionMode>()
        underTest.onActionModeStarted(mode)

        // then
        verify(mockCallback).onActionModeStarted(mode)
    }
}
