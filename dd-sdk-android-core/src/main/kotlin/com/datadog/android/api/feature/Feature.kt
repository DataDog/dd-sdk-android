/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.api.feature

import android.content.Context

/**
 * Interface to be implemented by the feature, which doesn't require any storage, to be
 * registered with [SdkCore].
 */
interface Feature {
    /**
     * Name of the feature.
     */
    val name: String

    /**
     * This method is called during feature initialization. At this stage feature should setup itself.
     *
     * @param appContext Application context.
     */
    fun onInitialize(appContext: Context)

    /**
     * This method is called during feature de-initialization. At this stage feature should stop
     * itself and release resources held.
     */
    fun onStop()

    companion object {
        // names of main features to have a single place where they are defined

        /**
         * Logs feature name.
         */
        const val LOGS_FEATURE_NAME: String = "logs"

        /**
         * RUM feature name.
         */
        const val RUM_FEATURE_NAME: String = "rum"

        /**
         * Flags feature name.
         */
        const val FLAGS_FEATURE_NAME: String = "flags"

        /**
         * Tracing feature name.
         */
        const val TRACING_FEATURE_NAME: String = "tracing"

        /**
         * Session Replay feature name.
         */
        const val SESSION_REPLAY_FEATURE_NAME: String = "session-replay"

        /**
         * Session Replay Resources sub-feature name.
         */
        const val SESSION_REPLAY_RESOURCES_FEATURE_NAME: String = "session-replay-resources"

        /**
         * NDK Crash Reports feature name.
         */
        const val NDK_CRASH_REPORTS_FEATURE_NAME: String = "ndk-crash-reporting"
    }
}
