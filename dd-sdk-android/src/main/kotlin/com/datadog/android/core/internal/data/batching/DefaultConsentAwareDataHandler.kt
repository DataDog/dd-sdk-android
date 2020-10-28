/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.batching

import com.datadog.android.core.internal.privacy.PrivacyConsent
import com.datadog.android.core.internal.privacy.PrivacyConsentProvider

internal class DefaultContentAwareDataHandler<T>(

    val consentProvider: PrivacyConsentProvider,
    val intermediaryDataProcessor: DataProcessor<T>,
    val targetDataProcessor: DataProcessor<T>,
    val batchedDataMigrator: BatchedDataMigrator

):ContentAwareDataHandler<T>, PrivacyConsentProvider.OnConsentUpdatedCallback {

    init {
        consentProvider.registerCallback(this)
    }

    override fun consume(event: T) {
    }

    override fun handle(consent: PrivacyConsent) {
    }

    override fun onConsentUpdated(consent: PrivacyConsent) {
        TODO("Not yet implemented")
    }
}