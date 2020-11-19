/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.privacy

/**
 * A TrackingConsentProvider callback. It is going to be called whenever the tracking consent
 * was changed.
 * @see TrackingConsent.PENDING
 * @see TrackingConsent.GRANTED
 * @see TrackingConsent.NOT_GRANTED
 */
interface TrackingConsentProviderCallback {
    /**
     * Notifies whenever the [TrackingConsent] was changed.
     * @param previousConsent the previous value of the [TrackingConsent]
     * @param newConsent the new value of the [TrackingConsent]
     * @see TrackingConsent.PENDING
     * @see TrackingConsent.GRANTED
     * @see TrackingConsent.NOT_GRANTED
     */
    fun onConsentUpdated(previousConsent: TrackingConsent, newConsent: TrackingConsent)
}
