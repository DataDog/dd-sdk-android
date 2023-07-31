/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.utils

import android.content.Intent
import com.datadog.android.privacy.TrackingConsent

internal const val TRACKING_CONSENT_KEY = "tracking_consent"
internal const val PENDING = 1
internal const val GRANTED = 2
internal const val NOT_GRANTED = 3

internal fun Intent.getTrackingConsent(): TrackingConsent {
    return when (getIntExtra(TRACKING_CONSENT_KEY, PENDING)) {
        PENDING -> TrackingConsent.PENDING
        GRANTED -> TrackingConsent.GRANTED
        else -> TrackingConsent.NOT_GRANTED
    }
}

internal const val FORGE_SEED_KEY = "forge_seed"

@Suppress("CheckInternal")
internal fun Intent.getForgeSeed(): Long {
    check(hasExtra(FORGE_SEED_KEY)) {
        "$FORGE_SEED_KEY value should be provided."
    }
    return getLongExtra(FORGE_SEED_KEY, -1)
}
