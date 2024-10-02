/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

/**
 * Defines the Session Replay privacy policy when recording touch interactions.
 * @see TouchPrivacy.SHOW
 * @see TouchPrivacy.HIDE
 */
enum class TouchPrivacy {
    /**
     * All touch interactions will be recorded.
     */
    SHOW,

    /**
     * No touch interactions will be recorded.
     */
    HIDE
}
