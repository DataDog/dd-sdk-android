/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:OptIn(ExperimentalFlagsApi::class)

package com.datadog.android.flags.internal.signing

import com.datadog.android.flags.ExperimentalFlagsApi
import com.datadog.android.flags.ResponseSigningFailureReason

/** A bounded validation failure that keeps its reason; it is an [IllegalArgumentException] for existing catches. */
internal class SigningCheckException(val reason: ResponseSigningFailureReason, cause: Throwable? = null) :
    IllegalArgumentException("Invalid response signing v1 input", cause)
