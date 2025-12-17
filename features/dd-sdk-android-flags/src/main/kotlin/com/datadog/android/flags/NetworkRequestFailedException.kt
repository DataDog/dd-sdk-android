/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

/**
 * Exception thrown when a network request for flag evaluations fails.
 *
 * This exception is used internally to communicate network failures when updating evaluation
 * contexts. It provides a clear signal that the failure was due to network connectivity issues
 * rather than application logic errors.
 *
 * @param message A descriptive message about the network failure
 */
internal class NetworkRequestFailedException(message: String) : RuntimeException(message)
