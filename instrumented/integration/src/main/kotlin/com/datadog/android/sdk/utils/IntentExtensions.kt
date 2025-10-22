/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.utils

import android.content.Intent
import android.os.Build
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.TouchPrivacy

internal const val TRACKING_CONSENT_KEY = "tracking_consent"
internal const val SR_PRIVACY_LEVEL = "sr_privacy_level"
internal const val SR_IMAGE_PRIVACY = "sr_image_privacy"
internal const val SR_TOUCH_PRIVACY = "sr_touch_privacy"
internal const val SR_TEXT_AND_INPUT_PRIVACY = "sr_text_and_input_privacy"
internal const val SR_SAMPLE_RATE = "sr_sample_rate"
private const val SAMPLE_IN_ALL_SESSIONS = 100f

internal fun Intent.getTrackingConsent(): TrackingConsent {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        extras?.getSerializable(TRACKING_CONSENT_KEY, TrackingConsent::class.java)
            ?: TrackingConsent.NOT_GRANTED
    } else {
        @Suppress("DEPRECATION")
        extras?.getSerializable(TRACKING_CONSENT_KEY) as? TrackingConsent
            ?: TrackingConsent.NOT_GRANTED
    }
}

internal fun Intent.getSessionReplayPrivacy(): SessionReplayPrivacy {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        extras?.getSerializable(SR_PRIVACY_LEVEL, SessionReplayPrivacy::class.java)
            ?: SessionReplayPrivacy.ALLOW
    } else {
        @Suppress("DEPRECATION")
        extras?.getSerializable(SR_PRIVACY_LEVEL) as? SessionReplayPrivacy
            ?: SessionReplayPrivacy.ALLOW
    }
}

internal fun Intent.getImagePrivacy(): ImagePrivacy? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        extras?.getSerializable(SR_IMAGE_PRIVACY, ImagePrivacy::class.java)
    } else {
        @Suppress("DEPRECATION")
        extras?.getSerializable(SR_IMAGE_PRIVACY) as? ImagePrivacy
    }
}

internal fun Intent.getTouchPrivacy(): TouchPrivacy? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        extras?.getSerializable(SR_TOUCH_PRIVACY, TouchPrivacy::class.java)
    } else {
        @Suppress("DEPRECATION")
        extras?.getSerializable(SR_TOUCH_PRIVACY) as? TouchPrivacy
    }
}

internal fun Intent.getTextAndInputPrivacy(): TextAndInputPrivacy? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        extras?.getSerializable(SR_TEXT_AND_INPUT_PRIVACY, TextAndInputPrivacy::class.java)
    } else {
        @Suppress("DEPRECATION")
        extras?.getSerializable(SR_TEXT_AND_INPUT_PRIVACY) as? TextAndInputPrivacy
    }
}

internal fun Intent.getSrSampleRate(): Float {
    return getFloatExtra(SR_SAMPLE_RATE, SAMPLE_IN_ALL_SESSIONS)
}

internal const val FORGE_SEED_KEY = "forge_seed"

@Suppress("CheckInternal")
internal fun Intent.getForgeSeed(): Long {
    check(hasExtra(FORGE_SEED_KEY)) {
        "$FORGE_SEED_KEY value should be provided."
    }
    return getLongExtra(FORGE_SEED_KEY, -1)
}
