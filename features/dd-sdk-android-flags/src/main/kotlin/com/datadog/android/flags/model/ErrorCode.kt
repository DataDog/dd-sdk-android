/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.model

/**
 * Error codes for flag resolution failures, aligned with the OpenFeature specification.
 *
 * These codes provide standardized error categorization for flag evaluation failures,
 * enabling consistent error handling across different flag providers.
 */
enum class ErrorCode {
    /**
     * The value was resolved before the provider was initialized.
     */
    PROVIDER_NOT_READY,

    /**
     * The flag could not be found.
     */
    FLAG_NOT_FOUND,

    /**
     * An error was encountered parsing data, such as a flag configuration.
     */
    PARSE_ERROR,

    /**
     * The type of the flag value does not match the expected type.
     */
    TYPE_MISMATCH,

    /**
     * The provider requires a targeting key and one was not provided in the evaluation context.
     */
    TARGETING_KEY_MISSING,

    /**
     * The evaluation context does not meet provider requirements.
     */
    INVALID_CONTEXT,

    /**
     * The provider has entered an irrecoverable error state.
     */
    PROVIDER_FATAL,

    /**
     * The error was for a reason not enumerated above.
     */
    GENERAL
}
