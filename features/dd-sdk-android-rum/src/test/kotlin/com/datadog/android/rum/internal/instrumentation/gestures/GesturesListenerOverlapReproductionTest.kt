/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.instrumentation.gestures

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.lang.ref.WeakReference

/**
 * Reproduction tests for RUMS-5868 / RUM-16187.
 *
 * Customer-reported symptom: when a `BottomNavigationView` is laid out on top of a
 * `ViewPager2` whose content (a `NestedScrollView`) hosts a clickable item whose layout
 * bounds extend into the region occupied by the nav bar, taps on the `BottomNavigationView`
 * are attributed to the underlying clickable widget in the scroll view instead of to the
 * nav-bar item that actually received the click.
 *
 * The customer-attached reproducer (`ddrum-repro 2.zip`) and Héctor Morillo's independent
 * reproduction on a similar layout (RUM-16187) both confirm this happens on SDK
 * 2.13.1, 2.23.0, and 3.9.1.
 *
 * Root cause (verified in source against `develop`):
 *
 *   `GesturesListener.findTarget(...)` performs a breadth-first traversal of the view
 *   hierarchy. Every time a child satisfies the per-view `findTargetForTap` predicate
 *   (`hitTest && isClickable && isVisible`), it OVERWRITES the running `target`. As a
 *   consequence the LAST matching clickable view in BFS order wins, regardless of:
 *
 *     - which view Android's own touch dispatch would route the event to
 *       (`ViewGroup.dispatchTouchEvent` honours z-order / elevation), or
 *     - whether the matched view is visually clipped by a scrolling ancestor.
 *
 *   `AndroidActionTrackingStrategy.hitTest(...)` uses `view.getLocationInWindow(...)` plus
 *   the view's raw `width`/`height` to test containment. It never asks whether the view
 *   is actually visible at the tap coordinate within its scrolling parent's clip rect —
 *   a `NestedScrollView` child whose lower half is scrolled behind the nav bar still
 *   reports `getHitRect()` containing the tap point.
 *
 * The two tests below exercise the two halves of this misbehaviour:
 *
 *   1. `wrong target picked when nav-bar item and deeper scroll-view button both match`
 *      — symmetric to the customer repro: a `BottomNavigationView`-like clickable child
 *      at shallow depth and a `Button`-like clickable child at greater depth both
 *      hit-test true; the SDK reports the deeper view instead of the nav item.
 *
 *   2. `clickable view clipped by scrolling parent is treated as a valid tap target`
 *      — a single clickable view whose `getLocationInWindow + width/height` claim the
 *      tap coordinate even though the tap point falls outside the parent
 *      `NestedScrollView`'s visible clip rect; the SDK still picks it as the target.
 *
 * Both tests assert the desired (post-fix) behaviour and therefore FAIL on the current
 * `develop`. When the SDK gesture target resolution starts honouring z-order / elevation
 * and clip-by-scrolling-parent semantics, both tests should pass.
 */
@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class GesturesListenerOverlapReproductionTest : AbstractGesturesListenerTest() {

    // region Symptom 1 — BottomNavigationView vs deeper scroll-view item

    @Test
    fun `M report nav item W onTap {BottomNavigationView overlaps deeper clickable in ViewPager scroll subtree}`(
        forge: Forge
    ) {
        // Given
        // Layout structure mirrors the customer repro (ddrum-repro 2.zip):
        //
        //   decorView (ConstraintLayout)
        //   ├── viewPagerSubtree (ViewGroup, not clickable, fills the screen)
        //   │   └── scrollContainer (ViewGroup, not clickable)
        //   │       └── deeperScrollButton (clickable Button, hit-tests true) ← the bug picks this
        //   └── bottomNavSubtree (ViewGroup, not clickable, elevation=8dp)
        //       └── navMenuItem (clickable nav item, hit-tests true)        ← user actually tapped this
        //
        // Both the deeperScrollButton and navMenuItem hit-test true at the tap coordinates
        // because the scroll item's layout bounds extend into the nav-bar zone — the exact
        // overlap condition the customer hit. The correct behaviour is to report navMenuItem
        // (the topmost visually-elevated view at the touch point). The current BFS-with-
        // overwrite logic instead reports deeperScrollButton because it is dequeued LAST.
        val mockEvent: MotionEvent = forge.getForgery()

        val deeperScrollButton: View = mockView(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = true,
            clickable = true,
            forge = forge
        )
        val scrollContainer: ViewGroup = mockView(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = true,
            clickable = false,
            forge = forge
        ) {
            whenever(it.childCount).thenReturn(1)
            whenever(it.getChildAt(0)).thenReturn(deeperScrollButton)
        }
        val viewPagerSubtree: ViewGroup = mockView(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = true,
            clickable = false,
            forge = forge
        ) {
            whenever(it.childCount).thenReturn(1)
            whenever(it.getChildAt(0)).thenReturn(scrollContainer)
        }

        val navMenuItem: View = mockView(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = true,
            clickable = true,
            forge = forge
        )
        val bottomNavSubtree: ViewGroup = mockView(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = true,
            clickable = false,
            forge = forge
        ) {
            whenever(it.childCount).thenReturn(1)
            whenever(it.getChildAt(0)).thenReturn(navMenuItem)
        }

        mockDecorView = mockDecorView<ViewGroup>(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = true,
            clickable = false,
            forge = forge
        ) {
            whenever(it.childCount).thenReturn(2)
            // ViewPager-like subtree added first (typical ConstraintLayout child order),
            // BottomNavigationView added second on top — mirrors the customer layout.
            whenever(it.getChildAt(0)).thenReturn(viewPagerSubtree)
            whenever(it.getChildAt(1)).thenReturn(bottomNavSubtree)
        }

        val expectedNavResourceName = forge.anAlphabeticalString()
        mockResourcesForTarget(navMenuItem, expectedNavResourceName)
        // Also stub the (wrongly chosen) deeper button's resource name so the bug path
        // can resolve and we can distinguish the verifyMonitor argument.
        mockResourcesForTarget(deeperScrollButton, forge.anAlphabeticalString())

        testedListener = GesturesListener(
            rumMonitor.mockSdkCore,
            WeakReference(mockWindow),
            contextRef = WeakReference(mockAppContext),
            internalLogger = mockInternalLogger
        )

        // When
        testedListener.onSingleTapUp(mockEvent)

        // Then
        // Desired (post-fix) behaviour: the SDK reports navMenuItem because it is the
        // topmost visually-elevated clickable at the touch point.
        // Current behaviour: this assertion fails because BFS overwrite picks
        // deeperScrollButton (later in BFS order, deeper in the tree).
        verify(rumMonitor.mockInstance).addAction(
            eq(RumActionType.TAP),
            eq(""),
            argThat {
                val expectedClassName = navMenuItem.javaClass.canonicalName
                this[RumAttributes.ACTION_TARGET_CLASS_NAME] == expectedClassName &&
                    this[RumAttributes.ACTION_TARGET_RESOURCE_ID] == expectedNavResourceName
            }
        )
    }

    // endregion

    // region Symptom 1 variant — view clipped by scrolling parent

    @Test
    fun `M ignore clipped scroll child W onTap {hit rect contains tap but view is clipped by scroll parent}`(
        forge: Forge
    ) {
        // Given
        // Single clickable scroll-view child whose getLocationInWindow + width/height
        // claim the tap coordinate (i.e. its layout bounds extend into the tap region),
        // but the tap coordinate falls *outside* the parent NestedScrollView's visible
        // clip rect — the child is visually hidden behind the BottomNavigationView at
        // that point. Android's own touch dispatch would never route the touch to this
        // child; the SDK should agree and pick no target (or, equivalently, decline to
        // emit a tap-with-target event for this view).
        //
        // We simulate the clip-by-parent semantics by marking the child as not visible
        // even though hitTest returns true. The current `AndroidActionTrackingStrategy`
        // intersects `isClickable && isVisible` with `hitTest`, so a non-visible view IS
        // correctly excluded — BUT a real `NestedScrollView` child remains
        // `View.VISIBLE` while its drawn region is clipped by the parent's scroll
        // offset. There is currently no API call in the SDK that asks "is this
        // coordinate inside the visible drawn region of the view inside its scrolling
        // ancestor?". To express the intended post-fix behaviour with the existing
        // mocking helpers, we model the conceptually-clipped child by leaving it
        // VISIBLE in mocks (matching Android reality) and asserting that the SDK still
        // declines to report it because it should treat clipped-by-scroll regions as
        // not-a-valid-hit-region. This assertion fails today.
        val mockEvent: MotionEvent = forge.getForgery()
        val clippedScrollChild: View = mockView(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = true,
            clickable = true,
            visible = true, // VISIBLE per Android; the clipping is enforced by the parent
            forge = forge
        )
        val nestedScrollParent: ViewGroup = mockView(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = true,
            clickable = false,
            visible = true,
            forge = forge
        ) {
            whenever(it.childCount).thenReturn(1)
            whenever(it.getChildAt(0)).thenReturn(clippedScrollChild)
        }
        mockDecorView = mockDecorView<ViewGroup>(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = false, // tap landed inside scroll parent's bounds but in clipped area
            clickable = false,
            forge = forge
        ) {
            whenever(it.childCount).thenReturn(1)
            whenever(it.getChildAt(0)).thenReturn(nestedScrollParent)
        }

        mockResourcesForTarget(clippedScrollChild, forge.anAlphabeticalString())

        testedListener = GesturesListener(
            rumMonitor.mockSdkCore,
            WeakReference(mockWindow),
            contextRef = WeakReference(mockAppContext),
            internalLogger = mockInternalLogger
        )

        // When
        testedListener.onSingleTapUp(mockEvent)

        // Then
        // Desired (post-fix) behaviour: the SDK does NOT report a tap action targeting
        // the clipped child because the tap coordinate falls outside the visible drawn
        // region of that child within its scrolling parent.
        // Current behaviour: the SDK emits addAction(TAP, …) with this clipped child as
        // the target, because `AndroidActionTrackingStrategy.hitTest` only consults
        // `getLocationInWindow + width/height` and never the parent's clip rect.
        org.mockito.kotlin.verifyNoInteractions(rumMonitor.mockInstance)
    }

    // endregion
}
