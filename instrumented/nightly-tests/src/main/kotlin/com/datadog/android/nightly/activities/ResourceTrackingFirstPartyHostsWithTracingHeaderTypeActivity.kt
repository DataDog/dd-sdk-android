/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.activities

internal open class ResourceTrackingFirstPartyHostsWithTracingHeaderTypeActivity : ResourceTrackingActivity() {

    override val randomUrl: String = RANDOM_URL_FIRST_PARTY_HOSTS

    companion object {
        internal const val HOST = "random-data-api.com"
        internal const val RANDOM_URL_FIRST_PARTY_HOSTS =
            "https://$HOST/api/v2/users"
    }
}
