/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.datadog.android.sdk.integration.sessionreplay.composition

import android.graphics.Rect
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ScrollView
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.R
import com.datadog.android.sessionreplay.TouchPrivacy
import com.datadog.android.sessionreplay.internal.composition.AndroidWindowTraversal
import com.datadog.android.sessionreplay.internal.composition.CaptureGenerationContext
import com.datadog.android.sessionreplay.internal.composition.CaptureTimeProvider
import com.datadog.android.sessionreplay.internal.composition.DefaultCapturedIdentityFactory
import com.datadog.android.sessionreplay.internal.composition.WindowCopyResult
import com.datadog.android.sessionreplay.internal.composition.WindowTouchPrivacyReader
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperRegistry
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

internal class NativeTouchClippingRegressionTest {
    @get:Rule val activityRule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var viewport: ScrollView
    private lateinit var shownChild: View

    @Before
    fun setUp() {
        activityRule.runOnUiThread {
            val activity = activityRule.activity
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val root = FrameLayout(activity)
            viewport = ScrollView(activity).apply {
                setPadding(10, 20, 30, 40)
                clipToPadding = true
                clipChildren = true
            }
            val content = FrameLayout(activity)
            shownChild = View(activity).apply { setTag(R.id.datadog_touch_privacy, TouchPrivacy.SHOW.name) }
            content.addView(shownChild, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 300))
            viewport.addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 600))
            root.addView(
                viewport,
                FrameLayout.LayoutParams(200, 200).apply {
                    leftMargin = 40
                    topMargin = 60
                }
            )
            activity.setContentView(root)
        }
        activityRule.waitForIdle()
    }

    @Test
    fun clipCapturedAndRefreshedPermissionsToScrolledPadding() {
        activityRule.runOnIdle { viewport.scrollTo(0, 75) }
        activityRule.waitForIdle()
        activityRule.runOnIdle {
            val location = IntArray(2)
            viewport.getLocationOnScreen(location)
            assertPermissions(
                Rect(location[0] + 10, location[1] + 20, location[0] + 170, location[1] + 160)
            )
        }
    }

    @Test
    fun applyExplicitClipToTheTaggedView() {
        activityRule.runOnIdle { shownChild.clipBounds = Rect(5, 30, 90, 100) }
        activityRule.waitForIdle()
        activityRule.runOnIdle {
            val location = IntArray(2)
            shownChild.getLocationOnScreen(location)
            assertPermissions(Rect(location[0] + 5, location[1] + 30, location[0] + 90, location[1] + 100))
        }
    }

    private fun assertPermissions(expected: Rect) {
        val root = activityRule.activity.window.decorView
        val visible = Rect()
        val isVisible = shownChild.getGlobalVisibleRect(visible)
        val geometry = generateSequence(shownChild) { it.parent as? View }.map {
            "${it.javaClass.simpleName}(outline=${it.clipToOutline}, animation=${it.animation}, matrix=${it.matrix})"
        }.toList()
        val identities = DefaultCapturedIdentityFactory(RumViewIdentityScope("touch-clipping"))
        val traversal = AndroidWindowTraversal(
            CapturedViewMapperRegistry(
                emptyList(),
                CapturedViewMapper { _, _ -> CapturedViewMapperResult.None },
                InternalLogger.UNBOUND
            ),
            InternalLogger.UNBOUND
        )
        val generation = CaptureGenerationContext(1, 0, Long.MAX_VALUE, CaptureTimeProvider { 0L })
        val captured = traversal.traverseWindow(root, identities.window("window"), identities, generation)
            as WindowCopyResult.Present
        assertThat(captured.touchOverrideAreas)
            .describedAs("visible=%s %s, ancestors=%s", isVisible, visible, geometry)
            .containsExactlyEntriesOf(mapOf(expected to TouchPrivacy.SHOW))
        val refreshed = WindowTouchPrivacyReader(budgetNs = Long.MAX_VALUE).read(root)
        assertThat(refreshed).isEqualTo(captured.touchOverrideAreas)
    }
}
