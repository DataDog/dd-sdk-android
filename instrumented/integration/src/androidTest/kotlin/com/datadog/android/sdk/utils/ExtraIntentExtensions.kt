/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.utils

import android.content.Intent
import com.datadog.android.privacy.TrackingConsent

fun Intent.addExtras(map: Map<String, Any?>) {
    map.forEach {
        val value = it.value
        val key = it.key
        when (value) {
            is String -> putExtra(key, value)
            is Number -> putExtra(key, value)
            is Boolean -> putExtra(key, value)
        }
    }
}

fun Intent.addTrackingConsent(consent: TrackingConsent) {
    val consentToInt = when (consent) {
        TrackingConsent.PENDING -> PENDING
        TrackingConsent.GRANTED -> GRANTED
        else -> NOT_GRANTED
    }
    this.putExtra(TRACKING_CONSENT_KEY, consentToInt)
}

fun Intent.addForgeSeed(seed: Long) = this.putExtra(FORGE_SEED_KEY, seed)
