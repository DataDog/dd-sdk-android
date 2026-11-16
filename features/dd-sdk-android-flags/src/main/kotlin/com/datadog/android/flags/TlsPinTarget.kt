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
     */
    LEAF
}
