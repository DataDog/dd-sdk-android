/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

/**
 * Experimental. Why an assignment response failed verification. [code] is the same kebab-case value in errors, logs
 * and telemetry on the Android, iOS and browser SDKs.
 *
 * @property code the cross-SDK reason code.
 * @param failure what failed, for the user log message.
 * @param hint how to fix it, for the user log message.
 */
@ExperimentalFlagsApi
enum class ResponseSigningFailureReason(
    val code: String,
    internal val failure: String,
    internal val hint: String
) {
    /** The SDK cannot sign this request shape. */
    UNSUPPORTED_REQUEST(
        "unsupported-request",
        "The SDK cannot sign this request shape",
        "Use the default flags endpoint and request options"
    ),

    /** The platform crypto API is not available. */
    UNAVAILABLE_CRYPTO(
        "unavailable-crypto",
        "The platform crypto API is not available",
        "Use a supported OS version"
    ),

    /** No trusted root is configured for this site. */
    MISSING_ANCHOR(
        "missing-anchor",
        "No trusted root is configured for this site",
        "Response signing supports only the staging site in this release"
    ),

    /** The response has no signature headers. */
    MISSING_PROOF(
        "missing-proof",
        "The response has no signature headers",
        "A proxy, CDN or custom endpoint removed the signature headers, or the organization is not enrolled"
    ),

    /** The signature headers are present but not valid. */
    INVALID_PROOF(
        "invalid-proof",
        "The signature headers are present but not valid",
        "A proxy or CDN changed the response headers"
    ),

    /** The body is larger than the signed size limit. */
    RESPONSE_TOO_LARGE(
        "response-too-large",
        "The body is larger than the signed size limit",
        "Contact Datadog support"
    ),

    /** The certificate chain does not chain to the pinned root, or a chain rule fails. */
    INVALID_CERTIFICATE(
        "invalid-certificate",
        "The certificate chain does not chain to the pinned root, or a chain rule fails",
        "The response did not come from Datadog, or the network intercepts TLS. Contact Datadog support"
    ),

    /** The signature does not match the request and response. */
    INVALID_SIGNATURE(
        "invalid-signature",
        "The signature does not match the request and response",
        "The response changed in transit"
    ),

    /** The proof is expired or not yet valid. */
    EXPIRED_PROOF(
        "expired-proof",
        "The proof is expired or not yet valid",
        "Check the device clock"
    ),

    /** Signing is not available on this Datadog site. */
    UNSUPPORTED_SITE(
        "unsupported-site",
        "Signing is not available on this Datadog site",
        "Response signing supports only the staging site in this release. Production is not supported yet"
    ),

    /** Draft revocation: the leaf is older than the stored minimum leaf not-before. */
    REVOKED_CERTIFICATE(
        "revoked-certificate",
        "The leaf certificate is older than the stored minimum leaf not-before",
        "Contact Datadog support"
    );

    /** `Flag response signing failed (<code>): <what failed>. <fix hint>.` */
    internal val message: String get() = "Flag response signing failed ($code): $failure. $hint."
}
