/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.model

/**
 * Represents the current state of a [com.datadog.android.flags.FlagsClient].
 *
 * Note: A STALE state is not currently defined as there is no mechanism to determine
 * whether configuration is stale. This may be added in a future release.
 */
enum class FlagsClientState {
    /**
     * The client has been created but no evaluation context has been set.
     * No flags are available for evaluation in this state.
     */
    NOT_READY,

    /**
     * The client has successfully loaded flags and they are available for evaluation.
     * This is the normal operational state.
     */
    READY,

    /**
     * The client is currently fetching new flags for a context change.
     * Cached flags may still be available for evaluation during this state.
     */
    RECONCILING,

    /**
     * An unrecoverable error has occurred.
     * The client cannot provide flag evaluations in this state.
     */
    ERROR
}
