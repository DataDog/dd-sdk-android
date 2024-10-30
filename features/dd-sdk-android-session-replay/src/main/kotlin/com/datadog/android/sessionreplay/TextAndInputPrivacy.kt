/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

/**
 * Defines the Session Replay privacy policy when recording text and inputs.
 * @see TextAndInputPrivacy.MASK_SENSITIVE_INPUTS
 * @see TextAndInputPrivacy.MASK_ALL_INPUTS
 * @see TextAndInputPrivacy.MASK_ALL
 */
enum class TextAndInputPrivacy : PrivacyLevel {

    /**
     * All text and inputs considered sensitive will be masked.
     * Sensitive text includes passwords, emails and phone numbers.
     */
    MASK_SENSITIVE_INPUTS,

    /**
     * All inputs will be masked.
     */
    MASK_ALL_INPUTS,

    /**
     * All text and inputs will be masked.
     */
    MASK_ALL
}
