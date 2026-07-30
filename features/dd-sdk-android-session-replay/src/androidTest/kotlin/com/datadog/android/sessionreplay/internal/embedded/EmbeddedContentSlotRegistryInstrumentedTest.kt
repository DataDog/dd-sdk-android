/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.embedded

import android.view.View
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.sessionreplay.R
import com.datadog.android.sessionreplay._SessionReplayInternalProxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class EmbeddedContentSlotRegistryInstrumentedTest {

    @Test
    fun markedViewRetainsSlotWhenRemovedAndRestored() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = FrameLayout(context)
        val embeddedView = View(context)
        val fakeSlotId = "flutter/slot"

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            root.addView(embeddedView)
            _SessionReplayInternalProxy.setEmbeddedContentSlotId(embeddedView, fakeSlotId)

            assertTrue(EmbeddedContentSlotRegistry.hasMarkedSlots())

            root.removeView(embeddedView)
            assertEquals(
                fakeSlotId,
                embeddedView.getTag(R.id.datadog_session_replay_slot_id)
            )

            root.addView(embeddedView)
            assertTrue(EmbeddedContentSlotRegistry.hasMarkedSlots())
            assertTrue(EmbeddedContentSlotRegistry.isSlotMarked(fakeSlotId))

            _SessionReplayInternalProxy.setEmbeddedContentSlotId(embeddedView, null)
            assertFalse(EmbeddedContentSlotRegistry.hasMarkedSlots())
            assertFalse(EmbeddedContentSlotRegistry.isSlotMarked(fakeSlotId))
        }
    }
}
