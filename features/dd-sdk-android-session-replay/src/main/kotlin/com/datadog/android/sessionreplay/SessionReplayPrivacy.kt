/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

/**
 * Defines the Session Replay privacy policy when recording the sessions.
 * @see SessionReplayPrivacy.ALLOW
 * @see SessionReplayPrivacy.MASK
 * @see SessionReplayPrivacy.MASK_USER_INPUT
 *
 */
enum class SessionReplayPrivacy {
    /** Does not apply any privacy rule on the recorded data with an exception for strong privacy
     * sensitive EditTextViews.
     * The EditTextViews which have email, password, postal address or phone number
     * inputType will be masked no matter what the privacy option with space-preserving "x" mask
     * (each char individually)
     **/
    ALLOW,

    /**
     *  Masks all the elements. All the characters in texts will be replaced by X, images will be
     *  replaced with just a placeholder and switch buttons, check boxes and radio buttons will also
     *  be masked. This is the default privacy rule.
     **/
    MASK,

    /**
     * Masks most form fields such as inputs, checkboxes, radio buttons, switchers, sliders, etc.
     * while recording all other text as is. Inputs are replaced with three asterisks (***).
     */
    MASK_USER_INPUT
}
