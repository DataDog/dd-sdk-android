/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

/**
 * Determines when initial loading can complete with usable assignments.
 * Native cached evaluations may already be available before this readiness boundary.
 * Cache-first availability and accepted network results use Ready; retained assignments after a failed refresh use Stale.
 */
enum class ClientReadyPolicy {
    /**
     * Wait for the initial network attempt to succeed OR fail.
     * Failure can complete initialization from installed configuration. If disk is still pending, wait for it
     * or the initialization deadline; exhaustion without a configuration fails initialization.
     */
    NETWORK,

    /**
     * Complete initialization when a cached configuration is installed or a network result qualifies.
     * Installed configurations can be empty or belong to a previous context, and can load before context is set.
     * After network failure, pending disk work can still supply a configuration before the initialization deadline.
     */
    CACHE_OR_NETWORK
}
