/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

/**
 * Identifies the certificate in the validated TLS chain that a [TlsPinningProfile] pin must match.
 */
enum class TlsPinTarget {
    /**
     * The pins identify the public key of the server (leaf) certificate.
     *
     * The SDK compares the SHA-256 hash of the leaf certificate SubjectPublicKeyInfo with the pins.
     * A pin that matches only an issuer certificate in the chain does not satisfy this target.
     *
     * Use this target only when you control the server key, because a new key that is not pinned breaks the
     * connection.
     */
    LEAF,

    /**
     * The pins identify the public key of an issuer certificate: an intermediate or root certificate authority.
     *
     * After normal TLS validation, at least one certificate of the validated chain, other than the leaf, must have
     * a SubjectPublicKeyInfo SHA-256 hash in the pins. The leaf certificate never satisfies this target. The
     * validated chain is the chain that the platform builds from the server certificate to a trusted root. It is
     * not the list of certificates that the server sends, so an unrelated certificate appended by the server does
     * not satisfy this target.
     *
     * This target allows routine leaf key changes, for example when a CDN renews a managed certificate. It
     * accepts any otherwise valid certificate for the origin that a pinned authority issued.
     */
    ISSUER
}
