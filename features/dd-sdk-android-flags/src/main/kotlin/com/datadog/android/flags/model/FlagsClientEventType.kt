/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.model

/** Event vocabulary for native Flags clients. Only configuration installation is emitted by onFirstFlags. */
enum class FlagsClientEventType {
    /** An accepted configuration installation. */
    CONFIGURATION_CHANGED,

    /** The client became ready. */
    READY,

    /** A client operation failed. */
    ERROR,

    /** The client configuration became stale. */
    STALE,

    /** Context reconciliation started. */
    RECONCILING,

    /** Context reconciliation completed. */
    CONTEXT_CHANGED
}
