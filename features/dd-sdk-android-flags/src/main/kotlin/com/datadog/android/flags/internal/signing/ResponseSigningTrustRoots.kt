/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.signing

import okhttp3.HttpUrl

internal object ResponseSigningTrustRoots {
    private const val HTTPS_PORT = 443

    // Public staging root shared with browser SDK 493572e24936303cb60b5963c70a8425e3b681fb.
    private const val STAGING_ROOT =
        "MIIBpTCCAUugAwIBAgIQKsPLOMisev8X0XQFpshjkTAKBggqhkjOPQQDAjAxMS8wLQYDVQQDEyZTVEFHSU5HIE9OTFkgRkZFIHJl" +
            "c3BvbnNlIHNpZ25pbmcgcm9vdDAeFw0yNjExMDQxOTQ5NTlaFw0zNjEwMzEwMDAwMDBaMDExLzAtBgNVBAMTJlNUQUdJTkcgT05M" +
            "WSBGRkUgcmVzcG9uc2Ugc2lnbmluZyByb290MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE5WCwmxXE/POEOIwmzAPNWA+p9Tj8" +
            "5d0vGWgUBKZ2n2ahzacUTa1MNAJqhFbErodKBOUxZL91ce0hNd3CREXO+qNFMEMwDgYDVR0PAQH/BAQDAgEGMBIGA1UdEwEB/wQI" +
            "MAYBAf8CAQEwHQYDVR0OBBYEFGwi5EY0Qjo1O4IeocR1YFDZ6saYMAoGCCqGSM49BAMCA0gAMEUCIECTtDqZk0HqFTTPGsGXCkzG" +
            "CHFQkBh6CD6HUc7H1u+pAiEA67aMjoptLka70k0EIGc5cg3ShIUs48JwJ4z8qilqEHY="

    fun forEndpoint(endpoint: HttpUrl, explicitRoots: List<String>): List<String> {
        if (explicitRoots.isNotEmpty()) return explicitRoots
        // Trust follows the effective endpoint, never the application's environment label.
        val stagingOrigin = endpoint.scheme == "https" && endpoint.host == "preview.ff-cdn.datad0g.com" &&
            endpoint.port == HTTPS_PORT
        val hasCredentials = endpoint.username.isNotEmpty() || endpoint.password.isNotEmpty()
        return if (stagingOrigin && !hasCredentials) {
            listOf(STAGING_ROOT)
        } else {
            emptyList()
        }
    }
}
