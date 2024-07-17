/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

/**
 * Defines the Session Replay privacy policy when recording images.
 * @see ImagePrivacy.ALL
 * @see ImagePrivacy.CONTEXTUAL
 * @see ImagePrivacy.NONE
 */
enum class ImagePrivacy {
    /**
     * All images will be recorded, including those downloaded from the Internet during app runtime.
     */
    ALL,

    /**
     * Mask images that we consider to be contextual.
     * In the replay images will be replaced with placeholders with the label: Content Image.
     */
    CONTEXTUAL,

    /**
     * No images will be recorded.
     * In the replay images will be replaced with placeholders with the label: Image.
     */
    NONE
}
