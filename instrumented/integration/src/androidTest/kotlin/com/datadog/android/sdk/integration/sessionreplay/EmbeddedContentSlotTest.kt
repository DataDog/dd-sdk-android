/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay

import android.view.View
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.sessionreplay.R
import com.datadog.android.sessionreplay._SessionReplayInternalProxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class EmbeddedContentSlotTest {

    @Test
    fun markedViewRetainsSlotWhenRemovedAndRestored() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val root = FrameLayout(instrumentation.targetContext)
        val embeddedView = View(instrumentation.targetContext)

        instrumentation.runOnMainSync {
            root.addView(embeddedView)
            try {
                _SessionReplayInternalProxy.setEmbeddedContentSlotId(embeddedView, FAKE_SLOT_ID)
                val registration = embeddedView.getTag(
                    R.id.datadog_session_replay_slot_registration
                )
                assertNotNull(registration)

                root.removeView(embeddedView)
                assertEquals(
                    FAKE_SLOT_ID,
                    embeddedView.getTag(R.id.datadog_session_replay_slot_id)
                )
                assertSame(
                    registration,
                    embeddedView.getTag(R.id.datadog_session_replay_slot_registration)
                )

                root.addView(embeddedView)
                _SessionReplayInternalProxy.setEmbeddedContentSlotId(embeddedView, FAKE_SLOT_ID)
                assertSame(
                    registration,
                    embeddedView.getTag(R.id.datadog_session_replay_slot_registration)
                )

                _SessionReplayInternalProxy.setEmbeddedContentSlotId(embeddedView, null)
                assertNull(embeddedView.getTag(R.id.datadog_session_replay_slot_id))
                assertNull(embeddedView.getTag(R.id.datadog_session_replay_slot_registration))
            } finally {
                _SessionReplayInternalProxy.setEmbeddedContentSlotId(embeddedView, null)
            }
        }
    }

    companion object {
        private const val FAKE_SLOT_ID = "flutter/slot"
    }
}
