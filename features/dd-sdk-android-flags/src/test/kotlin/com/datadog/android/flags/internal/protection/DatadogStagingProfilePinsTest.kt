/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.protection

import com.datadog.android.flags.TlsPinTarget
import com.datadog.android.flags.TlsPinningProfile
import okhttp3.CertificatePinner
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * Computes the SPKI pins of the real Certainly CA certificates in the test resources and compares them with the
 * bundled [TlsPinningProfile.DATADOG_STAGING_V1] constants.
 */
internal class DatadogStagingProfilePinsTest {

    @Test
    fun `M match the Certainly CA certificates W DATADOG_STAGING_V1 pins`() {
        // Given
        val expectedPins = listOf(
            "tls/certainly-intermediate-r1.pem",
            "tls/certainly-root-r1.pem",
            "tls/certainly-root-e1.pem"
        ).map { CertificatePinner.pin(loadCertificate(it)) }

        // When
        val profile = TlsPinningProfile.DATADOG_STAGING_V1

        // Then
        assertThat(profile.pins).containsExactlyInAnyOrderElementsOf(expectedPins)
        assertThat(profile.pinTarget).isEqualTo(TlsPinTarget.ISSUER)
        assertThat(profile.origin).isEqualTo("https://protected.ff-cdn.datad0g.com")
    }

    @Test
    fun `M identify the expected certificate authorities W DATADOG_STAGING_V1 resources`() {
        // When
        val subjects = listOf(
            "tls/certainly-intermediate-r1.pem",
            "tls/certainly-root-r1.pem",
            "tls/certainly-root-e1.pem"
        ).map { loadCertificate(it) }

        // Then
        assertThat(subjects.map { it.subjectX500Principal.name }).containsExactly(
            "CN=Certainly Intermediate R1,O=Certainly,C=US",
            "CN=Certainly Root R1,O=Certainly,C=US",
            "CN=Certainly Root E1,O=Certainly,C=US"
        )
        assertThat(subjects.map { it.basicConstraints }).allMatch { it >= 0 }
    }

    @Test
    fun `M not pin the Starfield cross-signing root W DATADOG_STAGING_V1 pins`() {
        // Given
        val starfieldPin = CertificatePinner.pin(loadCertificate("tls/starfield-root-g2.pem"))

        // When / Then
        assertThat(starfieldPin).isEqualTo("sha256/gI1os/q0iEpflxrOfRBVDXqVoWN3Tz7Dav/7IT++THQ=")
        assertThat(TlsPinningProfile.DATADOG_STAGING_V1.pins).doesNotContain(starfieldPin)
    }

    private fun loadCertificate(resource: String): X509Certificate {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream(resource)) { "Missing $resource" }
        return stream.use {
            CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
        }
    }
}
