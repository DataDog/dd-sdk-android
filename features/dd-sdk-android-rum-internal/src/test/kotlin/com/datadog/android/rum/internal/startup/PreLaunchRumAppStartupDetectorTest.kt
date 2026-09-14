/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.startup

import android.app.Activity
import com.datadog.android.rum.internal.domain.Time
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.lang.ref.WeakReference

internal class PreLaunchRumAppStartupDetectorTest {
    @AfterEach
    fun tearDown() = PreLaunchRumAppStartupDetector.resetForTests()

    @Test
    fun `M replay events in order W attach { events captured before attach }`() {
        val scenario = fakeScenario()
        val received = mutableListOf<String>()
        val listener = recordingListener(received)

        PreLaunchRumAppStartupDetector.onAppStartupDetected(scenario)
        PreLaunchRumAppStartupDetector.onTTIDComputed(scenario, 42L, true)
        PreLaunchRumAppStartupDetector.attach(listener)

        assertThat(received).containsExactly("app-start", "ttid:42:true")
    }

    @Test
    fun `M buffer events again W detach then reattach`() {
        val scenario = fakeScenario()
        val first = mutableListOf<String>()
        val firstListener = recordingListener(first)
        PreLaunchRumAppStartupDetector.attach(firstListener)
        PreLaunchRumAppStartupDetector.detach(firstListener)

        PreLaunchRumAppStartupDetector.onAppStartupDetected(scenario)
        assertThat(first).isEmpty()

        val second = mutableListOf<String>()
        PreLaunchRumAppStartupDetector.attach(recordingListener(second))
        assertThat(second).containsExactly("app-start")
    }

    private fun recordingListener(events: MutableList<String>) =
        object : RumAppStartupDetector.Listener {
            override fun onAppStartupDetected(scenario: RumStartupScenario) {
                events += "app-start"
            }

            override fun onTTIDComputed(
                scenario: RumStartupScenario,
                durationNs: Long,
                wasForwarded: Boolean
            ) {
                events += "ttid:$durationNs:$wasForwarded"
            }
        }

    private fun fakeScenario() = RumStartupScenario.Cold(
        hasSavedInstanceStateBundle = false,
        activity = WeakReference(mock<Activity>()),
        appStartActivityOnCreateGapNs = 10L,
        initialTime = Time(1L, 2L)
    )
}
