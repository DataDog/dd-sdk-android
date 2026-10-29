/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

/**
 * Indicates that a later context request replaced this request before its result was processed.
 * Applies only in enabled experimentation opt-out modes.
 */
class FlagsContextSupersededException internal constructor(message: String) : IllegalStateException(message)
