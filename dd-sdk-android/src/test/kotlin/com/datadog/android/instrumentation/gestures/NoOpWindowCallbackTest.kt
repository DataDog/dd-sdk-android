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
import com.datadog.android.instrumentation.gesture.NoOpWindowCallback
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
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
internal class NoOpWindowCallbackTest {

    lateinit var underTest: NoOpWindowCallback

    @Mock
    lateinit var mockCallback: Window.Callback

    @BeforeEach
    fun `set up`() {
        underTest = NoOpWindowCallback()
    }

    @Test
    fun `onActionModeFinished will do nothing`() {
        // when
        val actionMode = mock<ActionMode>()
        underTest.onActionModeFinished(actionMode)
    }

    @Test
    fun `onCreatePanelView will do nothing`(forge: Forge) {
        // when
        val featureId = forge.anInt()
        underTest.onCreatePanelView(featureId)
    }

    @Test
    fun `dispatchTouchEvent will do nothing`() {
        // when
        val motionEvent: MotionEvent = mock()
        underTest.dispatchTouchEvent(motionEvent)

        // then
        verifyZeroInteractions(motionEvent)
    }

    @Test
    fun `onCreatePanelMenu will do nothing`(forge: Forge) {
        // when
        val featureId = forge.anInt()
        val menu: Menu = mock()
        underTest.onCreatePanelMenu(featureId, menu)

        // then
        verifyZeroInteractions(menu)
    }

    @Test
    fun `onWindowStartingActionMode will do nothing`() {
        // when
        val callback = mock<ActionMode.Callback>()
        underTest.onWindowStartingActionMode(callback)

        // then
        verifyZeroInteractions(callback)
    }

    @Test
    fun `onWindowStartingActionMode with type will do nothing`(forge: Forge) {
        // when
        val type = forge.anInt()
        val callback = mock<ActionMode.Callback>()
        underTest.onWindowStartingActionMode(callback, type)

        // then
        verifyZeroInteractions(callback)
    }

    @Test
    fun `onAttachedToWindow will do nothing`() {
        // when
        underTest.onAttachedToWindow()
    }

    @Test
    fun `dispatchGenericMotionEvent will do nothing`() {
        // when
        val motionEvent: MotionEvent = mock()
        underTest.dispatchGenericMotionEvent(motionEvent)

        // then
        verifyZeroInteractions(motionEvent)
    }

    @Test
    fun `dispatchPopulateAccessibilityEvent will do nothing`() {
        // when
        val event = mock<AccessibilityEvent>()
        underTest.dispatchPopulateAccessibilityEvent(event)

        // then
        verifyZeroInteractions(event)
    }

    @Test
    fun `dispatchTrackballEvent will do nothing`() {
        // when
        val event = mock<MotionEvent>()
        underTest.dispatchTrackballEvent(event)

        // then
        verifyZeroInteractions(event)
    }

    @Test
    fun `dispatchKeyShortcutEvent will do nothing`() {
        // when
        val event = mock<KeyEvent>()
        underTest.dispatchKeyShortcutEvent(event)

        // then
        verifyZeroInteractions(event)
    }

    @Test
    fun `dispatchKeyEvent will do nothing`() {
        // when
        val event = mock<KeyEvent>()
        underTest.dispatchKeyEvent(event)

        // then
        verifyZeroInteractions(event)
    }

    @Test
    fun `onMenuOpened will do nothing`(forge: Forge) {
        // when
        val featureId = forge.anInt()
        val menu: Menu = mock()
        underTest.onMenuOpened(featureId, menu)

        // then
        verifyZeroInteractions(menu)
    }

    @Test
    fun `onPanelClosed will do nothing`(forge: Forge) {
        // when
        val featureId = forge.anInt()
        val menu: Menu = mock()
        underTest.onPanelClosed(featureId, menu)

        // then
        verifyZeroInteractions(menu)
    }

    @Test
    fun `onMenuItemSelected will do nothing`(forge: Forge) {
        // when
        val featureId = forge.anInt()
        val menuItem: MenuItem = mock()
        underTest.onMenuItemSelected(featureId, menuItem)

        // then
        verifyZeroInteractions(menuItem)
    }

    @Test
    fun `onDetachedFromWindow will do nothing`() {
        // when
        underTest.onDetachedFromWindow()
    }

    @Test
    fun `onPreparePanel will do nothing`(forge: Forge) {
        // when
        val featureId = forge.anInt()
        val menu: Menu = mock()
        val view: View = mock()
        underTest.onPreparePanel(featureId, view, menu)

        // then
        verifyZeroInteractions(view)
        verifyZeroInteractions(menu)
    }

    @Test
    fun `onWindowAttributesChanged will do nothing`() {
        // when
        val attrs = mock<WindowManager.LayoutParams>()
        underTest.onWindowAttributesChanged(attrs)

        // then
        verifyZeroInteractions(attrs)
    }

    @Test
    fun `onWindowFocusChanged will do nothing`(forge: Forge) {
        // when
        val hasFocus = forge.aBool()
        underTest.onWindowFocusChanged(hasFocus)
    }

    @Test
    fun `onContentChanged will do nothing`() {
        // when
        underTest.onContentChanged()
    }

    @Test
    fun `onSearchRequested will do nothing`() {
        // when
        underTest.onSearchRequested()
    }

    @Test
    fun `onSearchRequested for search event will do nothing`() {
        // when
        val searchEvent = mock<SearchEvent>()
        underTest.onSearchRequested(searchEvent)

        // then
        verifyZeroInteractions(searchEvent)
    }

    @Test
    fun `onActionModeStarted will do nothing`() {
        // when
        val mode = mock<ActionMode>()
        underTest.onActionModeStarted(mode)

        // then
        verifyZeroInteractions(mode)
    }
}
