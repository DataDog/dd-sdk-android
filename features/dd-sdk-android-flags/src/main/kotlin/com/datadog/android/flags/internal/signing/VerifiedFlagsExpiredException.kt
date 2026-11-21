/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.signing

/** Reported in the client error state when required verified flags expire and expireVerifiedFlags is on. */
internal class VerifiedFlagsExpiredException(message: String) : RuntimeException(message)
