/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.rules

import android.app.Activity
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.sdk.integration.sessionreplay.ACTIVITY_TEARDOWN_TIMEOUT_MS
import com.datadog.android.sdk.integration.sessionreplay.overrideProcessImportance
import com.datadog.android.sdk.utils.addExtras
import com.datadog.tools.unit.ConditionWatcher
import java.util.concurrent.atomic.AtomicBoolean

internal open class SessionReplayTestRule<T : Activity>(
    activityClass: Class<T>,
    keepRequests: Boolean = false,
    trackingConsent: TrackingConsent = TrackingConsent.PENDING,
    private val intentExtras: Map<String, Any?> = emptyMap()
) : LifecycleCallbackTestRule<T>(activityClass, keepRequests, trackingConsent) {

    // region ActivityTestRule

    override fun beforeActivityLaunched() {
        // wait for the previous activity to be removed. Espresso seems to have moments
        // when it tries to launch the new activity while the previous one is still somehow
        // in the process of being removed. This creates an issue with our SR recorder which
        // calls the WindowInspector.getGlobalWindowViews() which can return the previous window +
        // the current window and alters the tests.
        waitForPreviousActivityTeardown()
        removeCallbacks(listOf(Class.forName(SESSION_REPLAY_LIFECYCLE_CALLBACK_CLASS_NAME)))
        super.beforeActivityLaunched()
        overrideProcessImportance()
    }

    override fun afterActivityFinished() {
        removeCallbacks(listOf(Class.forName(SESSION_REPLAY_LIFECYCLE_CALLBACK_CLASS_NAME)))
        super.afterActivityFinished()
    }

    override fun getActivityIntent(): Intent {
        return super.getActivityIntent().apply { addExtras(intentExtras) }
    }

    // endregion

    // region Internal

    private fun waitForPreviousActivityTeardown() {
        try {
            ConditionWatcher(pollingIntervalMs = TEARDOWN_POLLING_INTERVAL_MS) {
                noActivityAlive()
            }.doWait(timeoutMs = ACTIVITY_TEARDOWN_TIMEOUT_MS)
        } catch (@Suppress("SwallowedException") _: ConditionWatcher.TimeoutException) {
            // an activity outliving the timeout is not necessarily fatal, and this wait used to
            // be an unconditional sleep of the same duration, so keep going rather than failing
            // a test that would previously have passed.
        }
        // the activity being destroyed does not guarantee its window is already gone: removal is
        // posted to the main looper. Let it drain so WindowInspector.getGlobalWindowViews() does
        // not still see the previous window.
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun noActivityAlive(): Boolean {
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        val noneAlive = AtomicBoolean(false)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            noneAlive.set(
                Stage.values()
                    .filter { it != Stage.DESTROYED }
                    .all { monitor.getActivitiesInStage(it).isEmpty() }
            )
        }
        return noneAlive.get()
    }

    // endregion

    companion object {
        private const val SESSION_REPLAY_LIFECYCLE_CALLBACK_CLASS_NAME =
            "com.datadog.android.sessionreplay.internal.SessionReplayLifecycleCallback"

        private const val TEARDOWN_POLLING_INTERVAL_MS = 50L
    }
}
