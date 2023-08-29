/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.utils

import android.content.Intent
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.sessionreplay.SessionReplayPrivacy

internal const val TRACKING_CONSENT_KEY = "tracking_consent"
internal const val SR_PRIVACY_LEVEL = "sr_privacy_level"
internal const val SR_SAMPLE_RATE = "sr_sample_rate"
internal const val PENDING = 1
internal const val GRANTED = 2
internal const val NOT_GRANTED = 3
internal const val ALLOW = 1
internal const val MASK_USER_INPUT = 2
internal const val MASK = 3
private const val SAMPLE_IN_ALL_SESSIONS = 100f

internal fun Intent.getTrackingConsent(): TrackingConsent {
    return when (getIntExtra(TRACKING_CONSENT_KEY, PENDING)) {
        PENDING -> TrackingConsent.PENDING
        GRANTED -> TrackingConsent.GRANTED
        else -> TrackingConsent.NOT_GRANTED
    }
}

internal fun Intent.getSessionReplayPrivacy(): SessionReplayPrivacy {
    return when (getIntExtra(SR_PRIVACY_LEVEL, ALLOW)) {
        ALLOW -> SessionReplayPrivacy.ALLOW
        MASK_USER_INPUT -> SessionReplayPrivacy.MASK_USER_INPUT
        MASK -> SessionReplayPrivacy.MASK
        else -> SessionReplayPrivacy.ALLOW
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
