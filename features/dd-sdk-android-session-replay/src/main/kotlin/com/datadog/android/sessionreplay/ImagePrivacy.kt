/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

/**
 * Defines the Session Replay privacy policy when recording images.
 * @see ImagePrivacy.MASK_NONE
 * @see ImagePrivacy.MASK_LARGE_ONLY
 * @see ImagePrivacy.MASK_ALL
 */
enum class ImagePrivacy : PrivacyLevel {
    /**
     * All images will be recorded, including those downloaded from the Internet during app runtime.
     */
    MASK_NONE,

    /**
     * Mask images that we consider to be content images based on them being larger than 100x100 dp.
     * In the replay such images will be replaced with placeholders with the label: Content Image.
     */
    MASK_LARGE_ONLY,

    /**
     * No images will be recorded.
     * In the replay images will be replaced with placeholders with the label: Image.
     */
    MASK_ALL
}
