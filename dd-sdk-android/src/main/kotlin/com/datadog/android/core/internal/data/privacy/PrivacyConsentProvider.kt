/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.privacy

import java.util.LinkedList

internal class PrivacyConsentProvider(consent: Consent = Consent.PENDING) :
    ConsentProvider {

    private val callbacks: LinkedList<ConsentProviderCallback> = LinkedList()

    @Volatile
    private var consent: Consent

    // region ConsentProvider

    init {
        this.consent = consent
    }

    override fun getConsent(): Consent {
        return consent
    }

    // We need to synchronize everything as we are not sure from which thread the client
    // will update the consent and initialize the SDK.
    @Synchronized
    override fun setConsent(consent: Consent) {
        if (consent == this.consent) {
            return
        }
        val previous = this.consent
        this.consent = consent
        notifyCallbacks(previous, consent)
    }

    @Synchronized
    override fun registerCallback(callback: ConsentProviderCallback) {
        callbacks.add(callback)
    }

    // endregion

    // region Internal

    private fun notifyCallbacks(previous: Consent, new: Consent) {
        callbacks.forEach {
            it.onConsentUpdated(previous, new)
        }
    }

    // endregion
}
