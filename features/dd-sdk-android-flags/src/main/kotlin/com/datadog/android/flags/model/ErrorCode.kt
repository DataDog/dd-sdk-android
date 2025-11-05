/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.model

/**
 * Error codes for flag resolution failures.
 */
enum class ErrorCode {
    /**
     * The provider is not ready to resolve flags.
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
    TYPE_MISMATCH
}
