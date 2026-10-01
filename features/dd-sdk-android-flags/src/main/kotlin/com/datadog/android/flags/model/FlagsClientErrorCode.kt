/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.model

/** Standard error codes in event details, independent of evaluation errors. */
enum class FlagsClientErrorCode {
    /** The provider is not ready. */
    PROVIDER_NOT_READY,

    /** The flag was not found. */
    FLAG_NOT_FOUND,

    /** Parsing failed. */
    PARSE_ERROR,

    /** The value has an unexpected type. */
    TYPE_MISMATCH,

    /** A required targeting key is missing. */
    TARGETING_KEY_MISSING,

    /** The evaluation context is invalid. */
    INVALID_CONTEXT,

    /** The provider cannot recover. */
    PROVIDER_FATAL,

    /** An otherwise unclassified error occurred. */
    GENERAL
}
