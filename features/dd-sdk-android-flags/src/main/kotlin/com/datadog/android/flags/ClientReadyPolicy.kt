/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

/** Determines when the first flag initialization can complete successfully. */
enum class ClientReadyPolicy {
    /** Wait for the initial network attempt; usable cached assignments may satisfy a failed attempt. */
    NETWORK,

    /** Become ready when a valid disk configuration is installed, or the network attempt supplies usable assignments. */
    CACHE_OR_NETWORK
}
