/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.rules

import android.app.Activity
import android.content.Intent
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.sdk.utils.addExtras

internal open class SessionReplayTestRule<T : Activity>(
    activityClass: Class<T>,
    keepRequests: Boolean = false,
    trackingConsent: TrackingConsent = TrackingConsent.PENDING,
    private val intentExtras: Map<String, Any?> = emptyMap()
) : LifecycleCallbackTestRule<T>(activityClass, keepRequests, trackingConsent) {

    // region ActivityTestRule

    override fun beforeActivityLaunched() {
        removeCallbacks(listOf(Class.forName(SESSION_REPLAY_LIFECYCLE_CALLBACK_CLASS_NAME)))
        super.beforeActivityLaunched()
    }

    override fun afterActivityFinished() {
        removeCallbacks(listOf(Class.forName(SESSION_REPLAY_LIFECYCLE_CALLBACK_CLASS_NAME)))
        super.afterActivityFinished()
    }

    override fun getActivityIntent(): Intent {
        return super.getActivityIntent().apply { addExtras(intentExtras) }
    }

    // endregion

    companion object {
        private const val SESSION_REPLAY_LIFECYCLE_CALLBACK_CLASS_NAME =
            "com.datadog.android.sessionreplay.internal.SessionReplayLifecycleCallback"
    }
}
